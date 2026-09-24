package pl.iskri.pocketlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object LockAppearance {

    const val BACKGROUND_FILE = "lock_bg.jpg"

    private var cachedBitmap: Bitmap? = null

    fun backgroundFile(context: Context): File = File(context.filesDir, BACKGROUND_FILE)

    fun invalidateCache() {
        cachedBitmap = null
    }

    fun apply(root: View, context: Context) {
        val background = root.findViewById<ImageView>(R.id.bg_image)
        val dots = root.findViewById<LinearLayout>(R.id.dots_container)
        if (background != null) {
            applyBackground(background, context)
        }
        if (dots != null) {
            applyDots(dots, context, root.width, root.height)
        }
    }

    private fun applyBackground(background: ImageView, context: Context) {
        val bitmap = loadBackground(context)
        if (bitmap == null) {
            background.visibility = View.GONE
            background.setImageDrawable(null)
            return
        }
        background.visibility = View.VISIBLE
        background.setImageBitmap(bitmap)
        val scale = Prefs.backgroundScale(context)
        background.scaleX = scale
        background.scaleY = scale
        clampBackgroundOffset(background, context)
        if (background.width > 0 && background.height > 0) {
            background.translationX = Prefs.backgroundOffsetX(context) * background.width
            background.translationY = Prefs.backgroundOffsetY(context) * background.height
        }
    }

    fun clampBackgroundOffset(background: ImageView, context: Context) {
        val drawable = background.drawable ?: return
        val viewW = background.width.toFloat()
        val viewH = background.height.toFloat()
        val imageW = drawable.intrinsicWidth.toFloat()
        val imageH = drawable.intrinsicHeight.toFloat()
        if (viewW <= 0f || viewH <= 0f || imageW <= 0f || imageH <= 0f) return
        val userScale = Prefs.backgroundScale(context)
        val cover = max(viewW / imageW, viewH / imageH)
        val drawnW = imageW * cover * userScale
        val drawnH = imageH * cover * userScale
        val maxX = max(0f, (drawnW - viewW) / 2f) / viewW
        val maxY = max(0f, (drawnH - viewH) / 2f) / viewH
        val x = Prefs.backgroundOffsetX(context).coerceIn(-maxX, maxX)
        val y = Prefs.backgroundOffsetY(context).coerceIn(-maxY, maxY)
        if (x != Prefs.backgroundOffsetX(context) || y != Prefs.backgroundOffsetY(context)) {
            Prefs.setBackgroundOffset(context, x, y)
        }
    }

    private fun applyDots(dots: LinearLayout, context: Context, rootW: Int, rootH: Int) {
        val dotScale = Prefs.dotScale(context)
        val spacing = Prefs.dotSpacing(context)
        val size = dp(context, 30f * dotScale)
        val margin = dp(context, 16f * dotScale * spacing)
        for (i in 0 until dots.childCount) {
            val dot = dots.getChildAt(i)
            val lp = dot.layoutParams as LinearLayout.LayoutParams
            if (lp.width != size || lp.height != size ||
                lp.leftMargin != margin || lp.rightMargin != margin) {
                lp.width = size
                lp.height = size
                lp.leftMargin = margin
                lp.rightMargin = margin
                dot.layoutParams = lp
            }
        }
        if (rootW > 0 && rootH > 0) {
            dots.translationX = (Prefs.dotCenterX(context) - 0.5f) * rootW
            dots.translationY = (Prefs.dotCenterY(context) - 0.5f) * rootH
        }
    }

    fun loadBackground(context: Context): Bitmap? {
        cachedBitmap?.let { if (!it.isRecycled) return it }
        val file = backgroundFile(context)
        val bitmap = when {
            Prefs.isBackgroundBlack(context) -> null
            Prefs.isBackgroundEnabled(context) && file.exists() -> decodeFile(context, file)
            else -> decodeDefault(context)
        }
        cachedBitmap = bitmap
        return bitmap
    }

    private fun decodeFile(context: Context, file: File): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > MAX_DIMENSION ||
                bounds.outHeight / sample > MAX_DIMENSION) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeDefault(context: Context): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(context.resources, R.drawable.default_background, options)
        } catch (_: Throwable) {
            null
        }
    }

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).roundToInt()

    private const val MAX_DIMENSION = 4096
}
