package pl.iskri.pocketlock

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import kotlin.math.max

/**
 * A row with a flexible content block (child 0) and an action button (child 1).
 *
 * The button is placed on the same line as the content when the content's natural width
 * leaves enough room for it; otherwise it wraps below the content, left aligned.
 */
class WrappingRowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val horizontalGap = (12 * resources.displayMetrics.density).toInt()
    private val verticalGap = (8 * resources.displayMetrics.density).toInt()

    private var stacked = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val available = (widthSize - paddingLeft - paddingRight).coerceAtLeast(0)
        val content = getChildAt(0)
        val action = getChildAt(1)
        if (content == null || action == null) {
            setMeasuredDimension(widthSize, paddingTop + paddingBottom)
            return
        }

        measureChild(action, widthMeasureSpec, heightMeasureSpec)
        val actionWidth = action.measuredWidth
        val actionHeight = action.measuredHeight

        // Natural width of the content block (longest line), capped at the available width.
        measureChild(
            content,
            MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
            heightMeasureSpec
        )
        val desiredWidth = content.measuredWidth

        stacked = desiredWidth + horizontalGap + actionWidth > available
        val contentWidth = if (stacked) {
            available
        } else {
            (available - horizontalGap - actionWidth).coerceAtLeast(0)
        }
        measureChild(
            content,
            MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
            heightMeasureSpec
        )

        val contentHeight = content.measuredHeight
        val height = if (stacked) {
            contentHeight + verticalGap + actionHeight
        } else {
            max(contentHeight, actionHeight)
        }
        setMeasuredDimension(widthSize, paddingTop + paddingBottom + height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val content = getChildAt(0) ?: return
        val action = getChildAt(1) ?: return
        val left = paddingLeft
        val available = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        if (stacked) {
            content.layout(
                left,
                paddingTop,
                left + available,
                paddingTop + content.measuredHeight
            )
            val actionTop = paddingTop + content.measuredHeight + verticalGap
            action.layout(
                left,
                actionTop,
                left + action.measuredWidth,
                actionTop + action.measuredHeight
            )
        } else {
            content.layout(
                left,
                paddingTop,
                left + content.measuredWidth,
                paddingTop + content.measuredHeight
            )
            val actionLeft = left + available - action.measuredWidth
            val actionTop = paddingTop + (content.measuredHeight - action.measuredHeight) / 2
            action.layout(
                actionLeft,
                actionTop,
                actionLeft + action.measuredWidth,
                actionTop + action.measuredHeight
            )
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        LayoutParams(context, attrs)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p != null
}
