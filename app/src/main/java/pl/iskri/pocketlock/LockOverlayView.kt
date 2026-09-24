package pl.iskri.pocketlock

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

class LockOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var dots: List<ImageView> = emptyList()
    private var presses = 0
    private var triggerLatched = false
    private var unlocked = false

    var onUnlocked: (() -> Unit)? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        dots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3)
        )
        isFocusable = true
        isFocusableInTouchMode = true
        updateDots()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            requestFocus()
            hideSystemBars()
        }
    }

    fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        }
        @Suppress("DEPRECATION")
        systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        handleKeyEvent(event)
        return true
    }

    fun handleKeyEvent(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Prefs.setLastKey(context, "keyCode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
            registerPress()
        }
    }

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
                Prefs.setLastKey(context, "trigger (oś=${"%.2f".format(value)})")
                registerPress()
            }
        } else if (value < 0.3f) {
            triggerLatched = false
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            Prefs.setLastKey(context, "dotyk ekranu")
            registerPress()
        }
        return true
    }

    private fun registerPress() {
        if (unlocked) return
        if (presses < REQUIRED_PRESSES) presses++
        updateDots()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (presses >= REQUIRED_PRESSES) {
            unlocked = true
            onUnlocked?.invoke()
        }
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

    companion object {
        const val REQUIRED_PRESSES = 3
    }
}
