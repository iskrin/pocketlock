package pl.iskri.pocketlock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.util.Log
import java.lang.ref.WeakReference

class LockActivity : Activity() {

    private lateinit var lockView: LockOverlayView
    private var unlocking = false
    private var closing = false
    private var silentFinish = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = WeakReference(this)
        setShowWhenLocked(true)
        @Suppress("DEPRECATION")
        window.setWindowAnimations(0)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_lock)
        lockView = findViewById(R.id.lock_root)
        lockView.onUnlocked = { unlock() }
        lockView.onPress = { ScreenTimeout.start(this) }
        goImmersive()
    }

    override fun onDestroy() {
        Log.i("PocketLock", "LockActivity destroyed")
        val callback = destroyCallback
        destroyCallback = null
        callback?.invoke()
        if (instance?.get() === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        Log.i("PocketLock", "LockActivity paused")
    }

    override fun onStop() {
        super.onStop()
        Log.i("PocketLock", "LockActivity stopped")
    }

    override fun onResume() {
        super.onResume()
        // In the overlay mode the lock screen is drawn by the overlay window on top of this
        // activity, so this activity shows a plain black window instead. That keeps the app
        // below stopped (an opaque window) while making sure system snapshots / closing
        // windows never contain a copy of the lock screen.
        lockView.visibility = if (LockService.isOverlayAttached) View.INVISIBLE else View.VISIBLE
        goImmersive()
        lockView.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            goImmersive()
            lockView.requestFocus()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        lockView.visibility = if (LockService.isOverlayAttached) View.INVISIBLE else View.VISIBLE
        goImmersive()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // The Back button must not dismiss the lock.
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // PauseActivity deliberately covers this task to stop emulators that ignore onPause.
        // Relaunching LockActivity here would remove PauseActivity because both activities share
        // this task affinity.
        if (PauseActivity.isLaunchingOrRunning()) return
        if (!unlocking && isInteractive()) {
            handler.postDelayed({
                if (!PauseActivity.isLaunchingOrRunning()
                    && !unlocking
                    && isInteractive()
                    && !isFinishing
                    && !isDestroyed
                ) {
                    launch(this)
                }
            }, 150)
        }
    }

    // Fallback path in case the lock view did not get keyboard focus.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lockView.handleKeyEvent(event)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true

    private fun unlock() {
        unlocking = true
        ScreenTimeout.cancel()
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = exit()
                override fun onDismissError() = exit()
                override fun onDismissCancelled() = exit()
            })
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) exit()
            }, 1500)
        } else {
            exit()
        }
    }

    private fun exit() {
        if (closing) return
        closing = true
        LockService.abandonAudioFocus(this)
        if (!silentFinish) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.lock_slide_down)
        }
        finishAndRemoveTask()
    }

    private fun isInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun goImmersive() {
        // Touch the decor view first: it may not exist yet (e.g. before setContentView),
        // and window.insetsController requires it.
        val decor = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        }
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    companion object {
        private var instance: WeakReference<LockActivity>? = null
        private var destroyCallback: (() -> Unit)? = null

        fun launch(context: Context) {
            val intent = Intent(context, LockActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            try {
                context.startActivity(intent)
            } catch (t: Throwable) {
                Prefs.setLastKey(context, "start error: ${t.javaClass.simpleName}")
            }
        }

        /** Clears the press counter, e.g. when the screen is locked again. */
        fun resetPresses() {
            try {
                instance?.get()?.lockView?.resetPresses()
            } catch (_: Throwable) {
            }
        }

        fun finishIfRunning(
            onDestroyed: (() -> Unit)? = null,
            showSystemBars: Boolean = false
        ) {
            val activity = instance?.get()
            if (activity == null) {
                onDestroyed?.invoke()
                return
            }
            destroyCallback = onDestroyed
            try {
                activity.silentFinish = true
                // Hide the activity window immediately: finishing is asynchronous, and its last
                // frame (a copy of the lock screen) could otherwise flash when the overlay
                // slides away and reveals the app underneath.
                try {
                    activity.lockView.visibility = View.INVISIBLE
                } catch (_: Throwable) {
                }
                activity.window?.let { window ->
                    val attributes = window.attributes
                    attributes.alpha = 0f
                    window.attributes = attributes
                }
                // Restore the system bars before the app below becomes visible, but only when
                // the app shows them itself (non-fullscreen). Otherwise the app would be laid
                // out while the bars are still hidden and its content would jump when they
                // appear after the unlock animation. For a fullscreen app this would only
                // flash the bars over the lock screen for nothing.
                if (showSystemBars) {
                    try {
                        activity.window?.let { window ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                window.insetsController?.apply {
                                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
                                    show(WindowInsets.Type.systemBars())
                                }
                            }
                            @Suppress("DEPRECATION")
                            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                        }
                    } catch (_: Throwable) {
                    }
                }
                // Make this activity translucent right before finishing. The app below then
                // flips to "already visible" before the close transition is collected, so the
                // system does not treat it as an opening app and does not animate it (on this
                // ROM app-provided animations are ignored for task-level transitions, and the
                // framework's default taskCloseEnter is a slide in from the left). The window
                // is invisible anyway (alpha 0) and the overlay still covers the screen, so
                // this handoff is not visible; it only lasts until the activity is destroyed.
                try {
                    activity.setTranslucent(true)
                } catch (_: Throwable) {
                }
                // Plain finish(), NOT finishAndRemoveTask(): the latter removes the task
                // synchronously (blocking binder call -> removeTask) and the below activity
                // would be collected as an opening app (animated). finish() keeps the record
                // alive through the close transition; the then-empty task is destroyed right
                // after (excludeFromRecents keeps it out of recents).
                activity.finish()
                // Belt and braces: request an empty close animation (right after finish() so
                // it lands on the collecting transition). It only takes effect when the
                // framework allows app-provided animations for this transition; the actual
                // suppression is done by setTranslucent() above.
                if (Build.VERSION.SDK_INT >= 34) {
                    activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(0, 0)
                }
                Log.i("PocketLock", "LockActivity finish requested")
            } catch (_: Throwable) {
                destroyCallback = null
                onDestroyed?.invoke()
            }
            instance = null
        }
    }
}
