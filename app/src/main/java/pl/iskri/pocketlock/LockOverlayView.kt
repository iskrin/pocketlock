package pl.iskri.pocketlock

import android.content.Context
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateInterpolator
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
    private var exitStarted = false
    private var exitFinished = false
    private var soundPool: SoundPool? = null
    private var pressSoundId = 0
    private var soundLoaded = false

    var interactive: Boolean = true
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
        LockAppearance.apply(this, context)
        updateDots()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        LockAppearance.apply(this, context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!interactive) return
        requestFocus()
        hideSystemBars()
        initSound()
    }

    override fun onDetachedFromWindow() {
        soundPool?.release()
        soundPool = null
        pressSoundId = 0
        soundLoaded = false
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (interactive && hasWindowFocus) {
            requestFocus()
            hideSystemBars()
        }
    }

    fun applyAppearance() {
        LockAppearance.apply(this, context)
        updateDots()
    }

    fun setPreviewState(count: Int) {
        presses = count.coerceIn(0, REQUIRED_PRESSES)
        updateDots()
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
        if (!interactive) return super.dispatchKeyEvent(event)
        handleKeyEvent(event)
        return true
    }

    fun handleKeyEvent(event: KeyEvent) {
        if (!interactive) return
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Prefs.setLastKey(context, "keyCode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
            registerPress()
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!interactive) return super.onGenericMotionEvent(event)
        val value = maxOf(
            abs(event.getAxisValue(MotionEvent.AXIS_LTRIGGER)),
            abs(event.getAxisValue(MotionEvent.AXIS_RTRIGGER)),
            abs(event.getAxisValue(MotionEvent.AXIS_BRAKE)),
            abs(event.getAxisValue(MotionEvent.AXIS_GAS))
        )
        if (value > 0.6f) {
            if (!triggerLatched) {
                triggerLatched = true
                Prefs.setLastKey(context, "trigger (axis=${"%.2f".format(value)})")
                registerPress()
            }
        } else if (value < 0.3f) {
            triggerLatched = false
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            Prefs.setLastKey(context, "screen touch")
            registerPress()
        }
        return true
    }

    fun playExitAnimation(onEnd: () -> Unit) {
        if (exitStarted) return
        exitStarted = true
        val distance =
            if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
        postDelayed({ finishExit(onEnd) }, EXIT_DURATION_MS + 150L)
        animate()
            .translationY(distance)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.7f))
            .withEndAction { finishExit(onEnd) }
            .start()
    }

    private fun finishExit(onEnd: () -> Unit) {
        if (exitFinished) return
        exitFinished = true
        onEnd()
    }

    private fun registerPress() {
        if (!interactive || unlocked) return
        if (presses < REQUIRED_PRESSES) presses++
        updateDots()
        if (Prefs.isVibrationEnabled(context)) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (Prefs.isSoundEnabled(context)) {
            playPressSound()
        }
        if (presses >= REQUIRED_PRESSES) {
            unlocked = true
            onUnlocked?.invoke()
        }
    }

    private fun initSound() {
        if (soundPool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            soundLoaded = status == 0
            Prefs.setSoundStatus(context, "status=$status sample=$sampleId")
        }
        pressSoundId = pool.load(context, R.raw.press_click, 1)
        soundPool = pool
    }

    private fun playPressSound() {
        if (!soundLoaded) return
        soundPool?.play(pressSoundId, 1f, 1f, 1, 0, 1f)
    }

    private fun updateDots() {
        val activeColor = Prefs.dotActiveColor(context)
        val inactiveColor = Prefs.dotInactiveColor(context)
        dots.forEachIndexed { index, dot ->
            val active = index < presses
            dot.imageTintList = ColorStateList.valueOf(if (active) activeColor else inactiveColor)
            dot.animate()
                .scaleX(if (active) 1f else 0.8f)
                .scaleY(if (active) 1f else 0.8f)
                .setDuration(120)
                .start()
        }
    }

    companion object {
        const val REQUIRED_PRESSES = 3
        const val EXIT_DURATION_MS = 350L
    }
}
