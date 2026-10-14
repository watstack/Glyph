package com.watstack.glyphweather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** An on-screen copy of the 13×13 Glyph Matrix playing the current weather icon on a loop. */
class MatrixPreviewView(context: Context) : View(context) {
    private val size = 13
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = 0

    var condition: WeatherIcons.Condition? = null
        set(value) {
            field = value
            invalidate()
        }

    private val tick = object : Runnable {
        override fun run() {
            frame = (frame + 1) % WeatherIcons.FRAMES
            invalidate()
            postDelayed(this, WeatherIcons.FRAME_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, WeatherIcons.FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (160 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(side, side)
    }

    override fun onDraw(canvas: Canvas) {
        val pixels = condition?.let { WeatherIcons.frame(it, frame, size) } ?: IntArray(size * size)
        val cell = width / size.toFloat()
        for (y in 0 until size) for (x in 0 until size) {
            if (!DigitFont.hasLed(x, y, size)) continue
            val v = pixels[y * size + x]
            paint.color = if (v > 0) Color.rgb(v, v, v) else UNLIT
            canvas.drawCircle((x + 0.5f) * cell, (y + 0.5f) * cell, cell * 0.4f, paint)
        }
    }

    companion object {
        private val UNLIT = Color.rgb(32, 32, 32)
    }
}
