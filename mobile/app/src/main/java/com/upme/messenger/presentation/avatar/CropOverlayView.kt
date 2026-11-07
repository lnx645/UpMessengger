package com.upme.messenger.presentation.avatar

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * WA-style circular crop overlay: dim outside, clear circle, thin white border + grid.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA000000.toInt(); style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f * resources.displayMetrics.density / 3f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF; style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private var circleRect = RectF()
    private var radius = 0f
    private var cx = 0f; private var cy = 0f

    fun getCropRect(): RectF = RectF(circleRect)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val size = min(w, h) * 0.82f
        radius = size / 2f
        cx = w / 2f; cy = h / 2f
        circleRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    override fun onDraw(canvas: Canvas) {
        // save layer for clear
        val saved = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawCircle(cx, cy, radius, clearPaint)
        canvas.restoreToCount(saved)
        // border
        canvas.drawCircle(cx, cy, radius, borderPaint)
        // grid 3x3 inside circle (WA-style)
        val third = radius * 2 / 3f
        // vertical lines
        for (i in 1..2) {
            val x = cx - radius + third * i
            val y1 = cy - radius; val y2 = cy + radius
            // clip to circle
            canvas.save(); canvas.clipRect(circleRect)
            canvas.drawLine(x, y1, x, y2, gridPaint)
            canvas.restore()
        }
        for (i in 1..2) {
            val y = cy - radius + third * i
            val x1 = cx - radius; val x2 = cx + radius
            canvas.save(); canvas.clipRect(circleRect)
            canvas.drawLine(x1, y, x2, y, gridPaint)
            canvas.restore()
        }
    }
}
