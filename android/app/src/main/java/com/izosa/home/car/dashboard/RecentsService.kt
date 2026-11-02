package com.izosa.home.car.dashboard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Only exists to open the system recent-apps screen: apps can't do that themselves, but an
 * accessibility service the user has switched on can, via a global action. It reads nothing.
 */
class RecentsService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        private var instance: RecentsService? = null

        /** Returns false when the service isn't switched on in the accessibility settings. */
        fun showRecents() = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
    }
}
