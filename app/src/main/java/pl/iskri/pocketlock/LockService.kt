package pl.iskri.pocketlock

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private val handler = Handler(Looper.getMainLooper())

    private val armActivityRunnable = Runnable {
        if (lockArmed && Prefs.isEnabled(this) && overlayView != null) {
            Log.i(TAG, "arming lock activity")
            LockActivity.launch(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        goForeground()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.i(TAG, "broadcast: ${intent.action}")
                if (!Prefs.isEnabled(context)) {
                    detachOverlay()
                    abandonAudioFocus(context)
                    LockActivity.finishIfRunning()
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
        handler.removeCallbacks(armActivityRunnable)
        detachOverlay()
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
        // visible itself. The activity is launched with a short delay so it does not interfere
        // with the screen-off transition.
        //
        // A fresh lock must always start with an empty press counter: if the screen went off
        // while the user had already pressed some keys, the old overlay view (and its counter)
        // would otherwise be reused and fewer presses would unlock. Re-creating the view also
        // covers the rare case of the screen going off mid-unlock.
        val wasArmed = lockArmed
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
        }
        LockActivity.resetPresses()
        lockArmed = true
        ScreenTimeout.cancel()
        attachOverlay()
        requestAudioFocus(this)
        handler.removeCallbacks(armActivityRunnable)
        handler.postDelayed(armActivityRunnable, ARM_ACTIVITY_DELAY_MS)
    }

    private fun onScreenOn() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            // A system lock screen (e.g. "Swipe") is showing and the overlay would sit below it,
            // so show the lock screen as an activity above the system keyguard.
            detachOverlay()
            LockActivity.launch(this)
        }
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
        view.onPress = { ScreenTimeout.start(this) }
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

    private fun performUnlock(view: LockOverlayView) {
        Log.i(TAG, "overlay unlocked")
        lockArmed = false
        handler.removeCallbacks(armActivityRunnable)
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
        view.playExitAnimation { detachOverlay() }
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

    private fun detachOverlay() {
        val view = overlayView ?: return
        overlayView = null
        lockArmed = false
        isOverlayAttached = false
        handler.removeCallbacks(armActivityRunnable)
        ScreenTimeout.cancel()
        abandonAudioFocus(this)
        Log.i(TAG, "overlay detached")
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) {
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
        private const val ARM_ACTIVITY_DELAY_MS = 200L
        private const val ACTIVITY_DESTROY_TIMEOUT_MS = 700L

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
