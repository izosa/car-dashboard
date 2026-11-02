package com.izosa.home.car.dashboard

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Home screen (replaces the Teyes stock launcher): Peugeot logo, clock, weather, now playing,
 * a status corner (outside temperature, network) and a bar of six shortcuts. Swiping up, or
 * long-pressing outside the bar, opens the full app list, since without the stock launcher there
 * would otherwise be no way to reach the rest of the installed apps.
 */
class MainActivity : AppCompatActivity() {

    private class Shortcut(val label: Int, val icon: Int, val packageName: String)

    /** The bar, in the order of `shortcuts` in `defaults.properties`; Parking only with `parking.package`. */
    private val shortcuts by lazy {
        val all = mapOf(
            "front" to Shortcut(R.string.action_front_camera, R.drawable.ic_front_view, getString(R.string.package_front_camera)),
            "radio" to Shortcut(R.string.action_radio, R.drawable.ic_radio, getString(R.string.package_radio)),
            "spotify" to Shortcut(R.string.action_spotify, R.drawable.ic_spotify, getString(R.string.package_spotify)),
            "traccar" to Shortcut(R.string.action_traccar, R.drawable.ic_traccar, getString(R.string.package_traccar)),
            "waze" to Shortcut(R.string.action_waze, R.drawable.ic_waze, getString(R.string.package_waze)),
            "parking" to Shortcut(R.string.action_parking, R.drawable.ic_parking, BuildConfig.PARKING_PACKAGE),
        )
        BuildConfig.SHORTCUTS.split(',').mapNotNull { all[it.trim()] }.filter { it.packageName.isNotEmpty() }
    }

    private lateinit var texture: RevealImageView
    private lateinit var glow: ImageView
    private var glowAnimator: AnimatorSet? = null
    private lateinit var networkStatus: NetworkStatus
    private lateinit var volumeStatus: VolumeStatus
    private lateinit var teyesServer: TeyesServer
    private lateinit var outsideTemperature: OutsideTemperature
    private lateinit var fuelStatus: FuelStatus
    private lateinit var weather: Weather
    private lateinit var nowPlaying: NowPlaying
    private lateinit var trackingIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkStatus = NetworkStatus(this, findViewById(R.id.networkType), findViewById(R.id.signalBars))
        teyesServer = TeyesServer(this)
        volumeStatus = VolumeStatus(
            this,
            teyesServer,
            findViewById(R.id.volume),
            findViewById(R.id.volumeIcon),
            findViewById(R.id.volumeLevel),
            findViewById(R.id.volumeDownZone),
            findViewById(R.id.volumeUpZone),
        )
        outsideTemperature = OutsideTemperature(
            this,
            teyesServer,
            findViewById(R.id.outsideTemperatureIcon),
            findViewById(R.id.outsideTemperature),
        )
        fuelStatus = FuelStatus(
            teyesServer,
            findViewById(R.id.fuelCorner),
            findViewById(R.id.fuelCornerText),
            listOf(findViewById(R.id.fuelInstantGauge), findViewById(R.id.fuelRangeGauge)),
            findViewById(R.id.fuelInstant),
            findViewById(R.id.fuelRange),
        ) { startActivity(Intent(this, TripActivity::class.java)) }
        weather = Weather(this, findViewById(R.id.weatherRow), outsideTemperature::setForecast)
        nowPlaying = NowPlaying(
            this,
            findViewById(R.id.nowPlayingRow),
            findViewById(R.id.nowPlayingText),
            findViewById(R.id.mediaPrevious),
            findViewById(R.id.mediaNext),
        )

        trackingIcon = findViewById(R.id.trackingIcon)
        // The weather strip is the "here I am" button: the position goes out now, with a pulse.
        val weatherRow = findViewById<View>(R.id.weatherRow)
        val weatherAlpha = weatherRow.alpha
        weatherRow.setOnClickListener {
            weatherRow.pulse(weatherAlpha)
            TrackingService.sendNow(this)
        }
        findViewById<View>(R.id.clock).setOnClickListener { openDrawer() }
        val logo = findViewById<View>(R.id.logo)
        val logoAlpha = logo.alpha
        logo.setOnClickListener {
            nowPlaying.togglePlayback()
            logo.pulse(logoAlpha)
        }

        texture = findViewById(R.id.backgroundTexture)
        glow = findViewById(R.id.backgroundGlow)
        glow.setImageLevel(FULL_LEVEL)
        // Alpha changes on a hardware layer are just a GPU blend, cheap enough for the head unit.
        glow.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val bar = findViewById<LinearLayout>(R.id.shortcutBar)
        for (shortcut in shortcuts) {
            val item = layoutInflater.inflate(R.layout.item_shortcut, bar, false)
            item.findViewById<ImageView>(R.id.icon).setImageResource(shortcut.icon)
            item.findViewById<TextView>(R.id.label).setText(shortcut.label)
            item.setOnClickListener { launch(shortcut.packageName, getString(shortcut.label)) }
            bar.addView(item)
        }

        findViewById<View>(R.id.root).setOnLongClickListener {
            openDrawer()
            true
        }
    }

    private fun openDrawer() {
        startActivity(Intent(this, AppsActivity::class.java))
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.stay)
    }

    override fun onResume() {
        super.onResume()
        // Teyes ships with the animator duration scale off; animators would then jump to their
        // end and leave the glow at the breathing's dimmest point, so show it fully lit instead.
        if (Prefs.isBackgroundAnimated(this) && ValueAnimator.areAnimatorsEnabled()) startGlow()
        networkStatus.start()
        volumeStatus.start()
        fuelStatus.setStyle(Prefs.getFuelDisplay(this))
        teyesServer.start()
        weather.start()
        nowPlaying.start()
        TrackingService.sync(this)
        trackingIcon.visibility = if (Prefs.isTrackingEnabled(this)) View.VISIBLE else View.GONE
        TrackingService.onSent = ::blinkTrackingIcon
    }

    override fun onPause() {
        super.onPause()
        stopGlow()
        networkStatus.stop()
        volumeStatus.stop()
        teyesServer.stop()
        weather.stop()
        nowPlaying.stop()
        TrackingService.onSent = null
    }

    /** Flashes the location icon from white to cyan and back each time positions reach the server. */
    private fun blinkTrackingIcon() {
        trackingIcon.setColorFilter(getColor(R.color.accent))
        trackingIcon.postDelayed({ trackingIcon.clearColorFilter() }, 800)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Weather.REQUEST_LOCATION) {
            weather.onLocationPermissionResult()
            TrackingService.sync(this)
        }
    }

    /**
     * The glow layer (cyan lines only, over a dimmed copy of the background) first lights up
     * from the centre outwards, like the i-Cockpit at ignition, while the hexagon texture
     * spreads out from the logo; then the glow slowly breathes. Runs only while the home
     * screen is visible.
     */
    private fun startGlow() {
        val ignition = ValueAnimator.ofInt(0, FULL_LEVEL).apply {
            duration = 2000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                glow.setImageLevel(it.animatedValue as Int)
                texture.reveal = it.animatedFraction
            }
        }
        val breathing = ObjectAnimator.ofFloat(glow, View.ALPHA, 1f, 0.15f).apply {
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        glow.alpha = 1f
        glowAnimator = AnimatorSet().apply {
            playSequentially(ignition, breathing)
            start()
        }
    }

    private fun stopGlow() {
        glowAnimator?.cancel()
        glowAnimator = null
        glow.setImageLevel(FULL_LEVEL)
        glow.alpha = 1f
        texture.reveal = 1f
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // This is the home screen; there is nothing behind it to go back to.
    }

    private fun launch(packageName: String, label: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.error_not_installed, label), Toast.LENGTH_LONG).show()
            return
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private companion object {
        /** ClipDrawable level at which the whole glow layer is shown. */
        const val FULL_LEVEL = 10_000
    }
}
