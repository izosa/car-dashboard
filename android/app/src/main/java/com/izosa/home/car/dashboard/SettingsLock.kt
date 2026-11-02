package com.izosa.home.car.dashboard

import android.app.Activity
import android.os.SystemClock
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.security.MessageDigest

/**
 * Fixed PIN in front of Dashboard's settings, so someone else using the car (a garage, say)
 * can't switch tracking off or change the server or home point. Only its salted SHA-256 is
 * built in, from `defaults.properties`; without one the settings open freely. It guards
 * Dashboard's own settings only: Android's settings (clear data, uninstall, default launcher)
 * stay open.
 */
object SettingsLock {

    private const val PIN_SALT = "dashboard-settings:"
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 30_000L
    private var failures = 0
    private var lockedUntil = 0L

    /** Runs [onUnlocked] after the right PIN; 5 wrong ones lock it for 30 s. */
    fun unlock(activity: Activity, onUnlocked: () -> Unit) {
        if (BuildConfig.SETTINGS_PIN_SHA256.isEmpty()) return onUnlocked()
        if (SystemClock.elapsedRealtime() < lockedUntil) {
            Toast.makeText(activity, R.string.error_pin_locked, Toast.LENGTH_LONG).show()
            return
        }
        prompt(activity) { pin ->
            if (sha256(PIN_SALT + pin).equals(BuildConfig.SETTINGS_PIN_SHA256, ignoreCase = true)) {
                failures = 0
                onUnlocked()
            } else {
                failures++
                if (failures >= MAX_ATTEMPTS) {
                    failures = 0
                    lockedUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
                    Toast.makeText(activity, R.string.error_pin_locked, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, R.string.error_wrong_pin, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Numeric, hidden PIN entry with the keyboard already up. */
    private fun prompt(activity: Activity, onPin: (String) -> Unit) {
        val input = EditText(activity).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val container = FrameLayout(activity).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.title_enter_pin)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ -> onPin(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        // The keyboard's ✓ confirms, like OK.
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.setOnEditorActionListener { _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            true
        }
        dialog.show()
        input.requestFocus()
    }
}
