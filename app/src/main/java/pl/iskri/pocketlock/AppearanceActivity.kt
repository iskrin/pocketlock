package pl.iskri.pocketlock

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.roundToInt

class AppearanceActivity : Activity() {

    private lateinit var preview: LockOverlayView
    private lateinit var previewContainer: FrameLayout
    private var previewScale = 1f
    private var screenW = 1
    private var screenH = 1
    private var tab = TAB_BACKGROUND
    private var updating = false

    private class SliderBinding(
        val seekBar: SeekBar,
        val label: TextView,
        val labelText: String,
        val min: Float,
        val max: Float,
        val get: () -> Float
    )

    private val sliderBindings = mutableListOf<SliderBinding>()

    private val scaleDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (tab == TAB_BACKGROUND) {
                    Prefs.setBackgroundScale(
                        this@AppearanceActivity,
                        (Prefs.backgroundScale(this@AppearanceActivity) * detector.scaleFactor)
                            .coerceIn(0.5f, 3f)
                    )
                } else {
                    Prefs.setDotScale(
                        this@AppearanceActivity,
                        (Prefs.dotScale(this@AppearanceActivity) * detector.scaleFactor)
                            .coerceIn(0.3f, 3f)
                    )
                }
                refreshPreview()
                return true
            }
        })
    }

    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleDetector.isInProgress || previewScale <= 0f) return true
                val fx = -distanceX / previewScale / screenW
                val fy = -distanceY / previewScale / screenH
                if (tab == TAB_BACKGROUND) {
                    Prefs.setBackgroundOffset(
                        this@AppearanceActivity,
                        (Prefs.backgroundOffsetX(this@AppearanceActivity) + fx).coerceIn(-0.5f, 0.5f),
                        (Prefs.backgroundOffsetY(this@AppearanceActivity) + fy).coerceIn(-0.5f, 0.5f)
                    )
                } else {
                    Prefs.setDotCenter(
                        this@AppearanceActivity,
                        (Prefs.dotCenterX(this@AppearanceActivity) + fx).coerceIn(0f, 1f),
                        (Prefs.dotCenterY(this@AppearanceActivity) + fy).coerceIn(0f, 1f)
                    )
                }
                refreshPreview()
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance)

        screenW = resources.displayMetrics.widthPixels
        screenH = resources.displayMetrics.heightPixels

        previewContainer = findViewById(R.id.preview_container)

        preview = LayoutInflater.from(this)
            .inflate(R.layout.activity_lock, previewContainer, false) as LockOverlayView
        preview.interactive = false
        preview.pivotX = 0f
        preview.pivotY = 0f
        preview.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
        previewContainer.addView(preview, FrameLayout.LayoutParams(screenW, screenH))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val content = findViewById<FrameLayout>(R.id.tabs_content)
            content.setOnApplyWindowInsetsListener { view, insets ->
                val gestureBottom = insets.getInsets(WindowInsets.Type.systemGestures()).bottom
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    dp(72) + gestureBottom
                )
                insets
            }
        }

        findViewById<Button>(R.id.btnTabBackground).setOnClickListener { setTab(TAB_BACKGROUND) }
        findViewById<Button>(R.id.btnTabDots).setOnClickListener { setTab(TAB_DOTS) }
        findViewById<Button>(R.id.btnTabColors).setOnClickListener { setTab(TAB_COLORS) }
        setTab(TAB_BACKGROUND)

        findViewById<Button>(R.id.btnChooseImage).setOnClickListener { pickImage() }
        findViewById<Button>(R.id.btnDefaultImage).setOnClickListener { useDefaultImage() }
        findViewById<Button>(R.id.btnRemoveImage).setOnClickListener { removeImage() }
        findViewById<Button>(R.id.btnResetAppearance).setOnClickListener {
            Prefs.resetAppearance(this)
            recreate()
        }

        buildSliders()
        buildColorEditors()

        previewContainer.post {
            layoutPreview()
            refreshPreview()
        }
    }

    override fun onResume() {
        super.onResume()
        layoutPreview()
        refreshPreview()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { importImage(it) }
        }
    }

    private fun setTab(newTab: Int) {
        tab = newTab
        findViewById<View>(R.id.tab_background).visibility =
            if (newTab == TAB_BACKGROUND) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tab_dots).visibility =
            if (newTab == TAB_DOTS) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tab_colors).visibility =
            if (newTab == TAB_COLORS) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnTabBackground).alpha =
            if (newTab == TAB_BACKGROUND) 1f else 0.5f
        findViewById<Button>(R.id.btnTabDots).alpha =
            if (newTab == TAB_DOTS) 1f else 0.5f
        findViewById<Button>(R.id.btnTabColors).alpha =
            if (newTab == TAB_COLORS) 1f else 0.5f
        findViewById<TextView>(R.id.tvHint).text = when (newTab) {
            TAB_BACKGROUND -> getString(R.string.appearance_hint_background)
            TAB_DOTS -> getString(R.string.appearance_hint_dots)
            else -> getString(R.string.appearance_hint_colors)
        }
    }

    private fun layoutPreview() {
        val available = previewContainer.width
        if (available <= 0) return
        val maxHeight = (screenH * 0.45f).roundToInt()
        var scale = available.toFloat() / screenW
        if ((screenH * scale).roundToInt() > maxHeight) {
            scale = maxHeight.toFloat() / screenH
        }
        previewScale = scale
        val finalW = (screenW * scale).roundToInt()
        val finalH = (screenH * scale).roundToInt()
        val params = previewContainer.layoutParams
        if (params.height != finalH) {
            params.height = finalH
            previewContainer.layoutParams = params
        }
        preview.scaleX = scale
        preview.scaleY = scale
        preview.translationX = (available - finalW) / 2f
        preview.translationY = 0f
    }

    private fun refreshPreview() {
        LockAppearance.apply(preview, this)
        preview.setPreviewState(1)
        syncSliders()
    }

    private fun buildSliders() {
        val background = findViewById<LinearLayout>(R.id.background_sliders)
        val dots = findViewById<LinearLayout>(R.id.dots_sliders)

        addSlider(background, getString(R.string.appearance_bg_zoom), 0.5f, 3f,
            { Prefs.backgroundScale(this) }) { value ->
            Prefs.setBackgroundScale(this, value)
            refreshPreview()
        }
        addSlider(background, getString(R.string.appearance_bg_x), -0.5f, 0.5f,
            { Prefs.backgroundOffsetX(this) }) { value ->
            Prefs.setBackgroundOffset(this, value, Prefs.backgroundOffsetY(this))
            refreshPreview()
        }
        addSlider(background, getString(R.string.appearance_bg_y), -0.5f, 0.5f,
            { Prefs.backgroundOffsetY(this) }) { value ->
            Prefs.setBackgroundOffset(this, Prefs.backgroundOffsetX(this), value)
            refreshPreview()
        }

        addSlider(dots, getString(R.string.appearance_dot_size), 0.3f, 3f,
            { Prefs.dotScale(this) }) { value ->
            Prefs.setDotScale(this, value)
            refreshPreview()
        }
        addSlider(dots, getString(R.string.appearance_dot_spacing), 0f, 2f,
            { Prefs.dotSpacing(this) }) { value ->
            Prefs.setDotSpacing(this, value)
            refreshPreview()
        }
        addSlider(dots, getString(R.string.appearance_dot_x), 0f, 1f,
            { Prefs.dotCenterX(this) }) { value ->
            Prefs.setDotCenter(this, value, Prefs.dotCenterY(this))
            refreshPreview()
        }
        addSlider(dots, getString(R.string.appearance_dot_y), 0f, 1f,
            { Prefs.dotCenterY(this) }) { value ->
            Prefs.setDotCenter(this, Prefs.dotCenterX(this), value)
            refreshPreview()
        }
    }

    private fun buildColorEditors() {
        buildColorEditor(findViewById(R.id.active_color_sliders), Prefs.dotActiveColor(this)) {
            Prefs.setDotActiveColor(this, it)
            refreshPreview()
        }
        buildColorEditor(findViewById(R.id.inactive_color_sliders), Prefs.dotInactiveColor(this)) {
            Prefs.setDotInactiveColor(this, it)
            refreshPreview()
        }
    }

    private fun buildColorEditor(
        parent: LinearLayout,
        initialColor: Int,
        onChange: (Int) -> Unit
    ) {
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        var hue = hsv[0]
        var saturation = hsv[1]
        var brightness = hsv[2]
        var alpha = Color.alpha(initialColor) / 255f

        val swatch = View(this)
        swatch.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
        }
        swatch.setBackgroundColor(initialColor)
        parent.addView(swatch)

        fun push() {
            val color = Color.HSVToColor(
                (alpha * 255f).roundToInt(),
                floatArrayOf(hue, saturation, brightness)
            )
            swatch.setBackgroundColor(color)
            onChange(color)
        }

        addSlider(parent, getString(R.string.appearance_hue), 0f, 360f, { hue }, false) {
            hue = it
            push()
        }
        addSlider(parent, getString(R.string.appearance_saturation), 0f, 1f, { saturation }, false) {
            saturation = it
            push()
        }
        addSlider(parent, getString(R.string.appearance_brightness), 0f, 1f, { brightness }, false) {
            brightness = it
            push()
        }
        addSlider(parent, getString(R.string.appearance_alpha), 0f, 1f, { alpha }, false) {
            alpha = it
            push()
        }
    }

    private fun addSlider(
        parent: LinearLayout,
        labelText: String,
        min: Float,
        max: Float,
        getValue: () -> Float,
        register: Boolean = true,
        onChange: (Float) -> Unit
    ): SliderBinding {
        val label = TextView(this).apply {
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
        }
        val seekBar = SeekBar(this).apply { this.max = SLIDER_STEPS.toInt() }
        val binding = SliderBinding(seekBar, label, labelText, min, max, getValue)
        if (register) {
            sliderBindings += binding
        }
        seekBar.progress = progressFor(min, max, getValue())
        label.text = "$labelText: ${formatValue(getValue())}"
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updating) return
                val value = valueFor(min, max, progress)
                label.text = "$labelText: ${formatValue(value)}"
                onChange(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        parent.addView(label)
        parent.addView(seekBar)
        return binding
    }

    private fun syncSliders() {
        updating = true
        for (binding in sliderBindings) {
            val value = binding.get()
            binding.seekBar.progress = progressFor(binding.min, binding.max, value)
            binding.label.text = "${binding.labelText}: ${formatValue(value)}"
        }
        updating = false
    }

    private fun progressFor(min: Float, max: Float, value: Float): Int =
        (((value - min) / (max - min)) * SLIDER_STEPS).roundToInt()
            .coerceIn(0, SLIDER_STEPS.toInt())

    private fun valueFor(min: Float, max: Float, progress: Int): Float =
        min + (max - min) * progress / SLIDER_STEPS

    private fun formatValue(value: Float): String =
        if (value >= 10f) value.roundToInt().toString() else "%.2f".format(value)

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, PICK_IMAGE)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.appearance_import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importImage(uri: Uri) {
        Thread {
            val bitmap = prepareBitmap(uri)
            val saved = bitmap != null && saveBackground(bitmap)
            bitmap?.recycle()
            runOnUiThread {
                if (!saved) {
                    Toast.makeText(this, R.string.appearance_import_failed, Toast.LENGTH_SHORT)
                        .show()
                    return@runOnUiThread
                }
                Prefs.setBackgroundEnabled(this, true)
                Prefs.setBackgroundBlack(this, false)
                Prefs.setBackgroundScale(this, 1f)
                Prefs.setBackgroundOffset(this, 0f, 0f)
                LockAppearance.invalidateCache()
                refreshPreview()
            }
        }.start()
    }

    private fun prepareBitmap(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDim = max(screenW, screenH)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim ||
                bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val orientation = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            bitmap = rotateBitmap(bitmap, orientation)

            val longest = max(bitmap.width, bitmap.height)
            if (longest > maxDim) {
                val ratio = maxDim.toFloat() / longest
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                    (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                bitmap = scaled
            }
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun rotateBitmap(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return source
        }
        return try {
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated != source) {
                source.recycle()
            }
            rotated
        } catch (_: Throwable) {
            source
        }
    }

    private fun saveBackground(bitmap: Bitmap): Boolean {
        return try {
            LockAppearance.backgroundFile(this).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun useDefaultImage() {
        Prefs.setBackgroundEnabled(this, false)
        Prefs.setBackgroundBlack(this, false)
        try {
            LockAppearance.backgroundFile(this).delete()
        } catch (_: Exception) {
        }
        LockAppearance.invalidateCache()
        refreshPreview()
    }

    private fun removeImage() {
        Prefs.setBackgroundEnabled(this, false)
        Prefs.setBackgroundBlack(this, true)
        try {
            LockAppearance.backgroundFile(this).delete()
        } catch (_: Exception) {
        }
        LockAppearance.invalidateCache()
        refreshPreview()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PICK_IMAGE = 1
        const val TAB_BACKGROUND = 0
        const val TAB_DOTS = 1
        const val TAB_COLORS = 2
        const val SLIDER_STEPS = 1000f
    }
}
