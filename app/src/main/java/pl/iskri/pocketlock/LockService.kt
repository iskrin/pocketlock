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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager

class LockService : Service() {

    private var screenReceiver: BroadcastReceiver? = null
    private var overlayView: LockOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        goForeground()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!Prefs.isEnabled(context)) {
                    detachOverlay()
                    return
                }
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> attachOverlay()
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
            attachOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Refresh the foreground notification (e.g. after the "Show notification" option changed).
        goForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        detachOverlay()
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

    private fun onScreenOn() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            // A system lock screen (e.g. "Swipe") is showing and the overlay would sit below it,
            // so show the lock screen as an activity above the system keyguard.
            detachOverlay()
            LockActivity.launch(this)
        } else {
            // No system lock screen - the black overlay has been attached since SCREEN_OFF,
            // so the first frame after wake-up is the lock screen itself.
            attachOverlay()
        }
    }

    private fun attachOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) return
        val view = LayoutInflater.from(this)
            .inflate(R.layout.activity_lock, null) as? LockOverlayView ?: return
        view.onUnlocked = { view.playExitAnimation { detachOverlay() } }
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
        } catch (t: Throwable) {
            Prefs.setLastKey(this, "overlay error: ${t.javaClass.simpleName}")
        }
    }

    private fun detachOverlay() {
        val view = overlayView ?: return
        overlayView = null
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
        private const val CHANNEL_ID = "pocketlock"
        private const val NOTIFICATION_ID = 1

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
