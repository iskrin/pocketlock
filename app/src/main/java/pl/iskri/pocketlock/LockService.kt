package pl.iskri.pocketlock

import android.app.KeyguardManager
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowInsets
import android.view.WindowManager

class LockService : Service() {

    private var screenReceiver: BroadcastReceiver? = null
    private var overlayView: LockOverlayView? = null
    private var lockArmed = false
    private var mediaMuted = false
    private var earlyResumeDone = false
    private var audioActiveCount = 0
    private var lockedPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * The lock activity is translucent, so well-behaved apps pause themselves (onPause) and
     * stay quiet. Apps that ignore onPause (e.g. Mupen64Plus AE) keep running - detected by
     * their still-active audio track - and are stopped by launching the opaque PauseActivity.
     * Stopping every app unconditionally is not an option: the extra stop/resume cycle crashes
     * RetroArch's Vulkan driver.
     */
    private val audioCheckRunnable = object : Runnable {
        override fun run() {
            if (!lockArmed || !Prefs.isEnabled(this@LockService) || overlayView == null) return
            val active = try {
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
            } catch (_: Throwable) {
                false
            }
            val foregroundPackage = lockedPackage ?: foregroundPackageBehindLock()
            val playbackPackages = activePlaybackPackages()
            val retroArchPlaying = foregroundPackage == RETROARCH_PACKAGE
                || playbackPackages?.contains(RETROARCH_PACKAGE) == true
            val canIdentifyPlayingApp = foregroundPackage != null || playbackPackages != null
            Log.i(
                TAG,
                "audio check: active=$active count=$audioActiveCount " +
                    "behind=${foregroundPackage ?: "unknown"} " +
                    "players=${playbackPackages ?: "unknown"}"
            )
            if (active && canIdentifyPlayingApp && !retroArchPlaying
                && (foregroundPackage != null || playbackPackages?.isNotEmpty() == true)
            ) {
                audioActiveCount++
                if (audioActiveCount >= AUDIO_CHECKS_TO_STOP) {
                    Log.i(TAG, "app still playing behind the lock, stopping it")
                    PauseActivity.launch(this@LockService)
                    return
                }
            } else {
                audioActiveCount = 0
            }
            handler.postDelayed(this, AUDIO_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        goForeground()
        // Safety net: if a previous instance was killed while the media stream was muted, restore
        // it now (the lock will re-mute immediately if it is still armed).
        unmuteMedia()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(TAG, "broadcast: ${intent.action}")
                if (!Prefs.isEnabled(context)) {
                    detachOverlay(unmuteMedia = true)
                    abandonAudioFocus(context)
                    LockActivity.finishIfRunning()
                    PauseActivity.finishIfRunning()
                    return
                }
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> armLock()
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(receiver, filter)
        screenReceiver = receiver

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            armLock()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Refresh the foreground notification (e.g. after the "Show notification" option changed).
        goForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        detachOverlay(unmuteMedia = true)
        PauseActivity.finishIfRunning()
        ScreenTimeout.cancel()
        abandonAudioFocus(this)
        LockActivity.finishIfRunning()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        screenReceiver = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground() {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!Prefs.isNotificationEnabled(this)) {
            hideNotification()
        }
    }

    private fun hideNotification() {
        // Android requires a notification for a foreground service, but it does not have to
        // stay visible: on Android 13 the user (and the app itself) can dismiss it while the
        // service keeps running. Post it (done above) and remove it right away; cancel once
        // more shortly after in case the system posts it asynchronously.
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                nm.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {
            }
        }, 600L)
    }

    private fun armLock() {
        // The overlay provides the visuals (attached while the screen is off, so there is no
        // flash on wake). The lock activity is launched as well: being an opaque activity above
        // the running app, it makes the system stop that app - no music or gameplay continues
        // behind the lock screen. The overlay window sits above the activity, so it is never
        // visible itself.
        //
        // The activity is launched right away (no delay): it has to be on top before the screen
        // is turned on again. Otherwise the app resumes first and gets stopped a moment later,
        // and the resulting extra stop/resume cycle crashes RetroArch's Vulkan driver.
        //
        // A fresh lock must always start with an empty press counter: if the screen went off
        // while the user had already pressed some keys, the old overlay view (and its counter)
        // would otherwise be reused and fewer presses would unlock. Re-creating the view also
        // covers the rare case of the screen going off mid-unlock.
        val wasArmed = lockArmed
        val packageBeforeLock = if (!wasArmed) recentForegroundPackage() else lockedPackage
        detachOverlay()
        if (!wasArmed) {
            // Remember whether the app in the foreground shows the system bars (non-fullscreen
            // app) or hides them (fullscreen game). On unlock the bars are only restored early
            // for the former; showing them for a fullscreen app would flash over the lock
            // screen. Not sampled again when the screen just went off while still locked: the
            // lock activity would be hiding the bars and the stored value would be wrong.
            val barsVisible = sampleSystemBarsVisible()
            Log.i(TAG, "system bars visible before lock: $barsVisible")
            Prefs.setBarsVisible(this, barsVisible)
            lockedPackage = packageBeforeLock
            Log.i(TAG, "app before lock: ${lockedPackage ?: "unknown"}")
        }
        LockActivity.resetPresses()
        lockArmed = true
        earlyResumeDone = false
        audioActiveCount = 0
        handler.removeCallbacks(audioCheckRunnable)
        PauseActivity.finishIfRunning()
        ScreenTimeout.cancel()
        attachOverlay()
        requestAudioFocus(this)
        // Silence media for the whole locked period (the app behind the lock may ignore audio
        // focus, e.g. Dolphin) and restore it on unlock / screen-off cleanup.
        muteMedia()
        Log.i(TAG, "arming lock activity")
        LockActivity.launch(this)
    }

    /**
     * LockActivity has its own task, so the task immediately below it is the app that was locked.
     * Returning null is deliberately treated as unknown: stopping an unknown app could restart
     * RetroArch's Vulkan context and bring back the crash this service is avoiding.
     */
    @Suppress("DEPRECATION")
    private fun foregroundPackageBehindLock(): String? {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getRunningTasks(10)
                .asSequence()
                .mapNotNull { it.topActivity?.packageName }
                .firstOrNull { it != packageName }
        } catch (t: Throwable) {
            Log.w(TAG, "foreground package unavailable: ${t.javaClass.simpleName}")
            null
        }
    }

    /** Finds the last non-PocketLock activity resumed before the lock task was shown. */
    private fun recentForegroundPackage(): String? {
        return try {
            val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usageStats.queryEvents(
                System.currentTimeMillis() - USAGE_LOOKBACK_MS,
                System.currentTimeMillis()
            )
            val event = UsageEvents.Event()
            var result: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    || event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    result = if (event.packageName == packageName) null else event.packageName
                }
            }
            result
        } catch (t: Throwable) {
            Log.w(TAG, "usage stats unavailable: ${t.javaClass.simpleName}")
            null
        }
    }

    /** Returns packages with an actually started audio player, when the ROM exposes this API. */
    private fun activePlaybackPackages(): Set<String>? {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.activePlaybackConfigurations
                .asSequence()
                .mapNotNull { playbackUid(it) }
                .flatMap { configuration ->
                    packageManager.getPackagesForUid(configuration)
                        ?.asSequence()
                        ?: emptySequence()
                }
                .toSet()
        } catch (t: Throwable) {
            Log.w(TAG, "active playback unavailable: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun playbackUid(configuration: AudioPlaybackConfiguration): Int? {
        val text = configuration.toString()
        if (!text.contains("state:started")) return null
        return try {
            val method = configuration.javaClass.getDeclaredMethod("getClientUid")
            method.isAccessible = true
            method.invoke(configuration) as Int
        } catch (_: Throwable) {
            Regex("u/pid:(\\d+)/").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }

    private fun onScreenOn() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Log.i(TAG, "screen on: keyguardLocked=${km.isKeyguardLocked}")
        if (km.isKeyguardLocked) {
            // A system lock screen (e.g. "Swipe") is showing and the overlay would sit below it,
            // so show the lock screen as an activity above the system keyguard.
            detachOverlay()
            LockActivity.launch(this)
        } else {
            // Watch for apps that keep playing behind the lock (they ignore onPause); they are
            // stopped once the audio has been active for a moment.
            audioActiveCount = 0
            handler.removeCallbacks(audioCheckRunnable)
            handler.postDelayed(audioCheckRunnable, AUDIO_CHECK_START_DELAY_MS)
        }
        muteMedia()
        // No system keyguard: the overlay has been attached since SCREEN_OFF, so there is
        // nothing to do here. Attaching again would flash the lock screen if this broadcast
        // arrives late (e.g. right after the user already unlocked).
        //
        // The screen-off countdown starts here (and is reset on every press); the earlier cancel
        // in armLock() makes sure a countdown never survives a screen-off.
        ScreenTimeout.start(this)
    }

    private fun attachOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return
        val view = LayoutInflater.from(this)
            .inflate(R.layout.activity_lock, null) as? LockOverlayView ?: return
        view.onPress = { onOverlayPress() }
        view.onUnlocked = { performUnlock(view) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.setTitle("PocketLock")
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            overlayView = view
            isOverlayAttached = true
            Log.i(TAG, "overlay attached")
        } catch (t: Throwable) {
            Prefs.setLastKey(this, "overlay error: ${t.javaClass.simpleName}")
        }
    }

    /**
     * Called on every counted press. On the first one the opaque PauseActivity (used only for
     * apps that ignore onPause) is finished silently: the app below resumes and re-creates its
     * window/GL context while the overlay still covers the screen, so nothing flashes when the
     * lock is revealed on the third press.
     */
    private fun onOverlayPress() {
        ScreenTimeout.start(this)
        if (earlyResumeDone) return
        earlyResumeDone = true
        if (PauseActivity.isRunning()) {
            Log.i(TAG, "early resume of the app behind the lock")
            PauseActivity.finishIfRunning()
        }
    }

    private fun performUnlock(view: LockOverlayView) {
        Log.i(TAG, "overlay unlocked")
        lockArmed = false
        earlyResumeDone = false
        handler.removeCallbacks(audioCheckRunnable)
        PauseActivity.finishIfRunning()
        ScreenTimeout.cancel()
        // While locked, this overlay window holds the focus and is therefore the system bar
        // control target. Its own "hide system bars" request would immediately re-hide the
        // bars that LockActivity restores when finishing, so the app below would be laid out
        // without them and jump when they reappear after the unlock animation. Release the
        // focus (the finishing activity/app takes over) and stop hiding the bars.
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = view.layoutParams as WindowManager.LayoutParams
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(view, params)
        } catch (_: Throwable) {
        }
        view.showSystemBars()
        // The audio focus is abandoned in detachOverlay(), i.e. after the slide: it keeps
        // media paused during the short (invisible) handoff to the app.
        LockActivity.finishIfRunning(
            showSystemBars = Prefs.barsVisible(this) ?: false
        )
        view.playExitAnimation { detachOverlay(unmuteMedia = true) }
    }

    /**
     * Whether the system bars are currently visible on the display. Called on screen-off,
     * before the lock activity hides them, so it reflects the foreground app's own state.
     */
    private fun sampleSystemBarsVisible(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                false
            } else {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val insets = windowManager.currentWindowMetrics.windowInsets
                insets.isVisible(WindowInsets.Type.statusBars())
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun detachOverlay(unmuteMedia: Boolean = false) {
        if (unmuteMedia) {
            unmuteMedia()
        }
        handler.removeCallbacks(audioCheckRunnable)
        PauseActivity.finishIfRunning()
        val view = overlayView ?: return
        overlayView = null
        lockArmed = false
        earlyResumeDone = false
        isOverlayAttached = false
        if (unmuteMedia) {
            lockedPackage = null
        }
        ScreenTimeout.cancel()
        abandonAudioFocus(this)
        Log.i(TAG, "overlay detached")
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) {
        }
    }

    /**
     * Mutes the media stream while the lock screen is up. Audio focus only pauses well-behaved
     * players; apps like Dolphin ignore it, so the stream itself is muted instead (the lock
     * screen's own sounds play on the system stream and are unaffected). The previous state is
     * persisted, so a crash cannot leave the device muted forever.
     */
    private fun muteMedia() {
        if (mediaMuted) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                mediaMuted = true
                Prefs.setMediaMuted(this, true)
                Log.i(TAG, "media muted")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "mute failed: ${t.javaClass.simpleName}")
        }
    }

    private fun unmuteMedia() {
        if (!mediaMuted && !Prefs.mediaMuted(this)) return
        mediaMuted = false
        Prefs.setMediaMuted(this, false)
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            Log.i(TAG, "media unmuted")
        } catch (_: Throwable) {
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_MIN
        )
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SetupActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lock)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PocketLock"
        private const val CHANNEL_ID = "pocketlock"
        private const val NOTIFICATION_ID = 1
        // Audio polling while locked: how long after screen-on to start, how often to check,
        // and how many consecutive "still playing" results trigger the opaque PauseActivity.
        private const val AUDIO_CHECK_START_DELAY_MS = 400L
        private const val AUDIO_CHECK_INTERVAL_MS = 400L
        private const val AUDIO_CHECKS_TO_STOP = 2
        private const val RETROARCH_PACKAGE = "com.retroarch.aarch64"
        private const val USAGE_LOOKBACK_MS = 15 * 60 * 1000L

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var isOverlayAttached = false
            private set

        private var focusRequest: AudioFocusRequest? = null

        fun requestAudioFocus(context: Context) {
            if (focusRequest != null) return
            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .build()
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.requestAudioFocus(request)
                focusRequest = request
                Log.i(TAG, "audio focus requested")
            } catch (_: Throwable) {
            }
        }

        fun abandonAudioFocus(context: Context) {
            val request = focusRequest ?: return
            focusRequest = null
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.abandonAudioFocusRequest(request)
                Log.i(TAG, "audio focus abandoned")
            } catch (_: Throwable) {
            }
        }

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, LockService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LockService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
