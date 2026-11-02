package com.izosa.home.car.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.hypot
import kotlin.math.max

/**
 * ImageView drawn only inside a circle growing from the logo/clock area (the centre of the
 * background's texture vignette), so the texture spreads outwards when the screen lights up.
 */
class RevealImageView(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {

    private val clip = Path()

    /** 0 = nothing shown, 1 = fully shown. */
    var reveal = 1f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (reveal >= 1f) {
            super.onDraw(canvas)
            return
        }
        val cx = width / 2f
        val cy = height * 0.42f
        val maxRadius = hypot(max(cx, width - cx), max(cy, height - cy))
        clip.rewind()
        clip.addCircle(cx, cy, maxRadius * reveal, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        super.onDraw(canvas)
        canvas.restore()
    }
}
