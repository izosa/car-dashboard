package com.izosa.home.car.dashboard

import android.view.View

/** Short tap feedback: brighten and swell, then settle back to [restAlpha]. */
fun View.pulse(restAlpha: Float) {
    animate().scaleX(1.08f).scaleY(1.08f).alpha(1f).setDuration(150).withEndAction {
        animate().scaleX(1f).scaleY(1f).alpha(restAlpha).setDuration(600).start()
    }.start()
}
