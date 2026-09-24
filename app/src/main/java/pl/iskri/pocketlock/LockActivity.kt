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

class LockActivity : Activity() {

    private lateinit var lockView: LockOverlayView
    private var unlocking = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        @Suppress("DEPRECATION")
        window.setWindowAnimations(0)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        goImmersive()
        setContentView(R.layout.activity_lock)
        lockView = findViewById(R.id.lock_root)
        lockView.onUnlocked = { unlock() }
        goImmersive()
    }

    override fun onResume() {
        super.onResume()
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
        goImmersive()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Przycisk Back nie może zdjąć blokady.
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!unlocking && isInteractive()) {
            handler.postDelayed({
                if (!unlocking && isInteractive() && !isFinishing && !isDestroyed) {
                    launch(this)
                }
            }, 150)
        }
    }

    // Zapasowa ścieżka, gdyby widok blokady nie dostał fokusu klawiatury.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lockView.handleKeyEvent(event)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true

    private fun unlock() {
        unlocking = true
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = close()
                override fun onDismissError() = close()
                override fun onDismissCancelled() = close()
            })
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) close()
            }, 1500)
        } else {
            close()
        }
    }

    private fun close() {
        finishAndRemoveTask()
    }

    private fun isInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun goImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    companion object {
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
                Prefs.setLastKey(context, "błąd startu: ${t.javaClass.simpleName}")
            }
        }
    }
}
