package pl.iskri.pocketlock

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.PowerManager
import android.util.AttributeSet
import android.util.Log
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
    var onPress: (() -> Unit)? = null

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

    fun resetPresses() {
        presses = 0
        updateDots()
    }

    fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
                it.show(WindowInsets.Type.systemBars())
            }
        }
        @Suppress("DEPRECATION")
        systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
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
            if (!isScreenOn()) return
            Prefs.setLastKey(context, "keyCode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
            registerPress()
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!interactive) return super.onGenericMotionEvent(event)
        if (!isScreenOn()) return true
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
            if (!isScreenOn()) return true
            Prefs.setLastKey(context, "screen touch")
            registerPress()
        }
        return true
    }

    private fun isScreenOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    fun playExitAnimation(onEnd: () -> Unit) {
        if (exitStarted) return
        exitStarted = true
        if (DISABLE_EXIT_ANIMATION) {
            // TEST BUILD: no animation - the overlay disappears instantly.
            alpha = 0f
            finishExit(onEnd)
            return
        }
        // Fallback in case an animator's end action is never delivered (e.g. the window is
        // removed mid-animation).
        postDelayed({ finishExit(onEnd) }, FALLBACK_EXIT_MS)
        when (Prefs.animationType(context)) {
            Prefs.ANIMATION_FADE -> playFadeExit(onEnd)
            Prefs.ANIMATION_ZOOM_OUT -> playZoomExit(onEnd, ZOOM_OUT_SCALE)
            Prefs.ANIMATION_ZOOM_IN -> playZoomExit(onEnd, ZOOM_IN_SCALE)
            Prefs.ANIMATION_FADE_SLIDE -> playFadeSlideExit(onEnd)
            Prefs.ANIMATION_SLIDE_UP -> playSlideExit(onEnd, up = true)
            else -> playSlideExit(onEnd, up = false)
        }
    }

    private fun exitDistance(): Float =
        if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()

    private fun playSlideExit(onEnd: () -> Unit, up: Boolean) {
        val distance = exitDistance()
        // Fade out during the last part of the slide, so that even if a final frame is drawn
        // late (busy device) it is fully transparent.
        ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
            startDelay = EXIT_DURATION_MS - FADE_DURATION_MS
            duration = FADE_DURATION_MS
        }.start()
        animate()
            .translationY(if (up) -distance else distance)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.7f))
            .withEndAction {
                alpha = 0f
                // Let the final (invisible) frame be drawn before the window is removed.
                postOnAnimation { finishExit(onEnd) }
            }
            .start()
    }

    private fun playFadeSlideExit(onEnd: () -> Unit) {
        // Fade over the whole slide instead of only its last part.
        ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
            duration = EXIT_DURATION_MS
        }.start()
        animate()
            .translationY(exitDistance())
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.7f))
            .withEndAction {
                alpha = 0f
                postOnAnimation { finishExit(onEnd) }
            }
            .start()
    }

    private fun playFadeExit(onEnd: () -> Unit) {
        animate()
            .alpha(0f)
            .setDuration(FADE_EXIT_MS)
            .withEndAction { postOnAnimation { finishExit(onEnd) } }
            .start()
    }

    private fun playZoomExit(onEnd: () -> Unit, targetScale: Float) {
        animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .alpha(0f)
            .setDuration(ZOOM_EXIT_MS)
            .setInterpolator(AccelerateInterpolator(1.2f))
            .withEndAction {
                alpha = 0f
                postOnAnimation { finishExit(onEnd) }
            }
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
        onPress?.invoke()
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
        private const val FADE_DURATION_MS = 120L
        private const val FADE_EXIT_MS = 300L
        private const val ZOOM_EXIT_MS = 300L
        private const val ZOOM_OUT_SCALE = 0.85f
        private const val ZOOM_IN_SCALE = 1.15f
        private const val FALLBACK_EXIT_MS = EXIT_DURATION_MS + 200L

        // TEST BUILD switch: when true, the unlock has no slide animation at all.
        const val DISABLE_EXIT_ANIMATION = false
    }
}
