package com.izosa.home.car.dashboard

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Detects a deliberate vertical fling. Fed from `dispatchTouchEvent` so it sees touches that
 * start on buttons too, without consuming them: taps and long-presses still work as before.
 */
class VerticalSwipe(context: Context, private val onSwipe: (up: Boolean) -> Unit) {

    private val minDistance = 80 * context.resources.displayMetrics.density

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            val dy = e2.y - start.y
            if (abs(dy) < minDistance || abs(dy) < abs(e2.x - start.x) * 1.5f) return false
            onSwipe(dy < 0)
            return true
        }
    })

    fun onTouchEvent(event: MotionEvent) {
        detector.onTouchEvent(event)
    }
}
