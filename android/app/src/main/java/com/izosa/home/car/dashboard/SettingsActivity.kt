package com.izosa.home.car.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        stripeRows()

        findViewById<SwitchCompat>(R.id.animatedBackgroundSwitch).apply {
            isChecked = Prefs.isBackgroundAnimated(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setBackgroundAnimated(this@SettingsActivity, checked)
            }
        }
        val fuelDisplayRow = findViewById<TextView>(R.id.fuelDisplayRow)
        showFuelDisplay(fuelDisplayRow)
        fuelDisplayRow.setOnClickListener { pickFuelDisplay(fuelDisplayRow) }
        findViewById<View>(R.id.changeHomeRow).setOnClickListener { changeHomeApp() }
        // The "now playing" row needs notification access, which only the system screen grants.
        findViewById<View>(R.id.nowPlayingAccessRow).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        bindSwitch(R.id.trackingSwitch, Prefs::isTrackingEnabled, Prefs::setTrackingEnabled)
        bindSwitch(R.id.autoParkingSwitch, Prefs::isAutoParkingEnabled, Prefs::setAutoParkingEnabled)
        val serverRow = findViewById<TextView>(R.id.trackingServerRow)
        val deviceIdRow = findViewById<TextView>(R.id.trackingDeviceIdRow)
        val homeRow = findViewById<TextView>(R.id.homeRow)
        val radiusRow = findViewById<TextView>(R.id.homeRadiusRow)
        if (!BuildConfig.PARKING_ON_ARRIVAL || BuildConfig.PARKING_PACKAGE.isEmpty()) {
            for (row in listOf(findViewById(R.id.parkingHeader), findViewById(R.id.autoParkingSwitch), homeRow, radiusRow)) {
                row.visibility = View.GONE
            }
        }
        refreshTracking()
        serverRow.setOnClickListener {
            editText(R.string.title_tracking_server, Prefs.getTrackingServer(this), null, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) {
                if (!it.startsWith("http://") && !it.startsWith("https://")) return@editText false
                Prefs.setTrackingServer(this, it)
                true
            }
        }
        deviceIdRow.setOnClickListener {
            editText(R.string.title_tracking_device_id, Prefs.getTrackingDeviceId(this), null, InputType.TYPE_CLASS_TEXT) {
                if (it.isEmpty()) return@editText false
                Prefs.setTrackingDeviceId(this, it)
                true
            }
        }
        homeRow.setOnClickListener { editHome() }
        radiusRow.setOnClickListener {
            editText(R.string.title_home_radius, Prefs.getHomeRadius(this).toString(), null, InputType.TYPE_CLASS_NUMBER) {
                val meters = it.toIntOrNull()?.takeIf { m -> m in 50..5000 } ?: return@editText false
                Prefs.setHomeRadius(this, meters)
                true
            }
        }
    }

    /** Edge-to-edge alternate row shading; the ripple moves to the foreground so it shows over the stripe. */
    private fun stripeRows() {
        val rows = findViewById<LinearLayout>(R.id.settingsRows)
        val padding = (48 * resources.displayMetrics.density).toInt()
        val ripple = TypedValue().also { theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true) }.resourceId
        var index = 0
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i)
            if (row.tag == "header") {
                // Section titles keep their own vertical padding and restart the stripes.
                row.setPaddingRelative(padding, row.paddingTop, padding, row.paddingBottom)
                index = 0
                continue
            }
            row.setPaddingRelative(padding, 0, padding, 0)
            if (index++ % 2 == 1) row.setBackgroundResource(R.color.settings_stripe) else row.background = null
            if (row is TextView && row !is SwitchCompat) row.foreground = getDrawable(ripple)
        }
    }

    private fun bindSwitch(id: Int, get: (Context) -> Boolean, set: (Context, Boolean) -> Unit) {
        findViewById<SwitchCompat>(id).apply {
            isChecked = get(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                set(this@SettingsActivity, checked)
                TrackingService.sync(this@SettingsActivity)
            }
        }
    }

    private fun refreshTracking() {
        findViewById<TextView>(R.id.trackingServerRow).text =
            getString(R.string.label_tracking_server, Prefs.getTrackingServer(this).ifEmpty { getString(R.string.label_not_set) })
        findViewById<TextView>(R.id.trackingDeviceIdRow).text =
            getString(R.string.label_tracking_device_id, Prefs.getTrackingDeviceId(this).ifEmpty { getString(R.string.label_not_set) })
        val home = Prefs.getHome(this)?.let { formatPoint(it.first, it.second) } ?: getString(R.string.label_not_set)
        findViewById<TextView>(R.id.homeRow).text = getString(R.string.label_home, home)
        findViewById<TextView>(R.id.homeRadiusRow).text =
            getString(R.string.label_home_radius, Prefs.getHomeRadius(this))
    }

    /** Prefilled with where the car is now, so "set home" at home is just OK. */
    @SuppressLint("MissingPermission")
    private fun editHome() {
        val here = if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locations = getSystemService(LocationManager::class.java)
            locations.getProviders(true).mapNotNull { locations.getLastKnownLocation(it) }
                .maxByOrNull { it.elapsedRealtimeNanos }
        } else {
            null
        }
        val initial = here?.let { formatPoint(it.latitude, it.longitude) }
            ?: Prefs.getHome(this)?.let { formatPoint(it.first, it.second) }
            ?: ""
        editText(R.string.title_home, initial, R.string.hint_home, InputType.TYPE_CLASS_TEXT) {
            val parts = it.split(',').map { p -> p.trim().toDoubleOrNull() }
            val lat = parts.getOrNull(0)?.takeIf { v -> v in -90.0..90.0 }
            val lon = parts.getOrNull(1)?.takeIf { v -> v in -180.0..180.0 }
            if (parts.size != 2 || lat == null || lon == null) return@editText false
            Prefs.setHome(this, lat, lon)
            true
        }
    }

    private fun formatPoint(lat: Double, lon: Double) = "%.6f, %.6f".format(Locale.US, lat, lon)

    /** Text prompt; [save] returns false for an invalid value, which keeps the old one. */
    private fun editText(title: Int, value: String, message: Int?, inputType: Int, save: (String) -> Boolean) {
        val input = EditText(this).apply {
            setText(value)
            setSingleLine()
            this.inputType = inputType
            setSelection(text.length)
        }
        val container = FrameLayout(this).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .apply { if (message != null) setMessage(message) }
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (save(input.text.toString().trim())) {
                    refreshTracking()
                    TrackingService.sync(this)
                } else {
                    Toast.makeText(this, R.string.error_invalid_value, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFuelDisplay(row: TextView) {
        row.text = getString(R.string.label_fuel_display, getString(Prefs.getFuelDisplay(this).label))
    }

    private fun pickFuelDisplay(row: TextView) {
        val choices = FuelStatus.Style.values()
        AlertDialog.Builder(this)
            .setTitle(R.string.title_fuel_display)
            .setSingleChoiceItems(
                choices.map { getString(it.label) }.toTypedArray(),
                choices.indexOf(Prefs.getFuelDisplay(this)),
            ) { dialog, which ->
                Prefs.setFuelDisplay(this, choices[which])
                showFuelDisplay(row)
                dialog.dismiss()
            }
            .show()
    }


    /**
     * Escape hatch back to the stock Teyes launcher: drops our "Always" default so the next
     * Home press asks again, then opens the system Home-app picker if this firmware has one.
     */
    private fun changeHomeApp() {
        packageManager.clearPackagePreferredActivities(packageName)
        val settings = Intent(Settings.ACTION_HOME_SETTINGS)
        val intent = if (settings.resolveActivity(packageManager) != null) {
            settings
        } else {
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
