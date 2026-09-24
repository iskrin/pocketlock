package pl.iskri.pocketlock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import kotlin.math.abs

class LockActivity : Activity() {

    private lateinit var dots: List<ImageView>
    private var presses = 0
    private var unlocking = false
    private var triggerLatched = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_lock)
        dots = listOf(findViewById(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3))
        goImmersive()
        updateDots()
    }

    override fun onResume() {
        super.onResume()
        goImmersive()
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            Prefs.setLastKey(this, "keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)})")
            registerPress()
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val value = maxOf(
            abs(event.getAxisValue(MotionEvent.AXIS_LTRIGGER)),
            abs(event.getAxisValue(MotionEvent.AXIS_RTRIGGER)),
            abs(event.getAxisValue(MotionEvent.AXIS_BRAKE)),
            abs(event.getAxisValue(MotionEvent.AXIS_GAS))
        )
        if (value > 0.6f) {
            if (!triggerLatched) {
                triggerLatched = true
                Prefs.setLastKey(this, "trigger (oś=${"%.2f".format(value)})")
                registerPress()
            }
        } else if (value < 0.3f) {
            triggerLatched = false
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            Prefs.setLastKey(this, "dotyk ekranu")
            registerPress()
        }
        return true
    }

    private fun registerPress() {
        if (unlocking) return
        if (presses < REQUIRED_PRESSES) presses++
        updateDots()
        findViewById<View>(R.id.lock_root).performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (presses >= REQUIRED_PRESSES) unlock()
    }

    private fun updateDots() {
        dots.forEachIndexed { index, dot ->
            val active = index < presses
            dot.animate()
                .alpha(if (active) 1f else 0.15f)
                .scaleX(if (active) 1f else 0.75f)
                .scaleY(if (active) 1f else 0.75f)
                .setDuration(120)
                .start()
        }
    }

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

    @Suppress("DEPRECATION")
    private fun goImmersive() {
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
        const val REQUIRED_PRESSES = 3

        fun launch(context: Context) {
            val intent = Intent(context, LockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try {
                context.startActivity(intent)
            } catch (t: Throwable) {
                Prefs.setLastKey(context, "błąd startu: ${t.javaClass.simpleName}")
            }
        }
    }
}
