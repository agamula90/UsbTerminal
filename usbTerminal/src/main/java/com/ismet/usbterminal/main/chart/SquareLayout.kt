package com.ismet.usbterminal.main.chart

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.min

class SquareLayout @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context!!, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        val height = measuredHeight
        val size = when (width) {
            0 -> height
            else -> min(width, height)
        }
        setMeasuredDimension(size, size)
    }
}