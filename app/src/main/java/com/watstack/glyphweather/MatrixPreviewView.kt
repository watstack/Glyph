package com.watstack.glyphweather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** An on-screen copy of the 13×13 Glyph Matrix. Shows whatever [pixels] it's given (row-major brightness, 0 = off). */
class MatrixPreviewView(context: Context) : View(context) {
    val size = 13
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var pixels: IntArray = IntArray(size * size)
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (160 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val cell = width / size.toFloat()
        for (y in 0 until size) for (x in 0 until size) {
            if (!DigitFont.hasLed(x, y, size)) continue
            val v = pixels.getOrElse(y * size + x) { 0 }
            paint.color = if (v > 0) Color.rgb(v, v, v) else UNLIT
            canvas.drawCircle((x + 0.5f) * cell, (y + 0.5f) * cell, cell * 0.4f, paint)
        }
    }

    companion object {
        private val UNLIT = Color.rgb(32, 32, 32)
    }
}
