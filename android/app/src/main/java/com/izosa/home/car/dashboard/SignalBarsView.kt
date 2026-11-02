package com.izosa.home.car.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Four rising signal bars; [level] of them (0..4) are lit. */
class SignalBarsView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.label)
    }

    var level = 0
        set(value) {
            field = value.coerceIn(0, 4)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val gap = width / 12f
        val barWidth = (width - gap * 3) / 4
        for (i in 0 until 4) {
            val barHeight = height * (i + 1) / 4f
            val left = i * (barWidth + gap)
            paint.alpha = if (i < level) 255 else 60
            canvas.drawRect(left, height - barHeight, left + barWidth, height.toFloat(), paint)
        }
    }
}
