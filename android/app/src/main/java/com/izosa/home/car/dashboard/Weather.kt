package com.izosa.home.car.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Weather strip under the date: now, then three points about 3 hours apart, from Open-Meteo
 * (free, no API key) for the car's last known location. Cached in [Prefs] so it shows
 * instantly, refreshed every 30 minutes while the home screen is visible, and hidden if never
 * fetched or too old to trust.
 */
class Weather(
    private val activity: Activity,
    private val row: LinearLayout,
    /** Current temperature in °C, or null when unknown; feeds the top-right outside temperature. */
    private val onCurrentTemperature: (Int?) -> Unit,
) {
    private class Slot(val label: String, val code: Int, val isDay: Boolean, val temperature: Int)

    private val handler = Handler(Looper.getMainLooper())
    private val locations = activity.getSystemService(LocationManager::class.java)
    private val connectivity = activity.getSystemService(ConnectivityManager::class.java)
    private val refresh = Runnable { refresh() }

    /** Whether the forecast on screen is up to date; until then retry every minute. */
    private var current = false
    private var waitingForFix = false

    // When the head unit wakes with the ignition, 4G (and GPS) take a minute or more to come up,
    // so the first fetch usually fails; fetch again as soon as there is a network.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post { if (!current) refresh() }
        }
    }

    fun start() {
        val age = System.currentTimeMillis() - Prefs.getWeatherTime(activity)
        val shown = age < MAX_AGE_MS && show(Prefs.getWeatherJson(activity))
        if (!shown) show(null)
        current = shown && age < REFRESH_MS
        if (current) scheduleNext() else refresh()
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    fun stop() {
        handler.removeCallbacks(refresh)
        connectivity.unregisterNetworkCallback(networkCallback)
    }

    /** Called by the activity once the user answers the location permission prompt. */
    fun onLocationPermissionResult() = refresh()

    private fun scheduleNext() {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, if (current) REFRESH_MS else RETRY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        current = false
        scheduleNext()
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Ask once; if refused, the row just stays hidden instead of nagging on every resume.
            if (!Prefs.wasLocationAsked(activity)) {
                Prefs.setLocationAsked(activity)
                activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            }
            return
        }
        val last = locations.getProviders(true)
            .mapNotNull { locations.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
        if (last != null) fetch(last)
        // The car moves: a last fix from hours ago may be another town, so when it's stale ask
        // for a fresh one and fetch again once it arrives.
        val ageMs = (SystemClock.elapsedRealtimeNanos() - (last?.elapsedRealtimeNanos ?: 0)) / 1_000_000
        if (last != null && ageMs < FRESH_LOCATION_MS || waitingForFix) return
        val enabled = locations.getProviders(true)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { it in enabled } ?: return
        waitingForFix = true
        locations.requestSingleUpdate(provider, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                waitingForFix = false
                fetch(location)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }, Looper.getMainLooper())
    }

    private fun fetch(location: Location) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=%.3f&longitude=%.3f".format(Locale.US, location.latitude, location.longitude) +
            "&current=temperature_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,is_day&forecast_days=2&timezone=auto"
        Thread {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                parse(body) // validate before caching
                handler.post {
                    Prefs.setWeather(activity, body, System.currentTimeMillis())
                    show(body)
                    current = true
                    scheduleNext()
                }
            } catch (_: Exception) {
                // Offline or API hiccup: keep showing the cached forecast; the retry already
                // scheduled by refresh() (or a network coming up) tries again.
            }
        }.start()
    }

    /** Shows the strip for [json]; returns false (and hides it) when there is nothing usable. */
    private fun show(json: String?): Boolean {
        val slots = json?.let { runCatching { parse(it) }.getOrNull() }
        if (slots == null) {
            row.visibility = View.GONE
            onCurrentTemperature(null)
            return false
        }
        row.removeAllViews()
        for (slot in slots) {
            val item = activity.layoutInflater.inflate(R.layout.item_weather_slot, row, false)
            item.findViewById<TextView>(R.id.time).text = slot.label
            item.findViewById<ImageView>(R.id.icon).setImageResource(iconFor(slot.code, slot.isDay))
            item.findViewById<TextView>(R.id.temperature).text =
                activity.getString(R.string.temperature, slot.temperature)
            row.addView(item)
        }
        row.visibility = View.VISIBLE
        onCurrentTemperature(slots.first().temperature)
        return true
    }

    private fun parse(json: String): List<Slot> {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val days = hourly.getJSONArray("is_day")
        val slots = mutableListOf(
            Slot(
                activity.getString(R.string.weather_now),
                current.getInt("weather_code"),
                current.getInt("is_day") == 1,
                current.getDouble("temperature_2m").roundToInt(),
            ),
        )
        // Hourly data starts at midnight; times are local ISO strings like "2026-10-26T23:00",
        // so the current hour is the first entry not earlier than "now" truncated to the hour.
        val currentHour = current.getString("time").substring(0, 13) + ":00"
        val base = (0 until times.length()).firstOrNull { times.getString(it) >= currentHour } ?: return slots
        for (offset in HOURS_AHEAD) {
            val i = base + offset
            if (i >= times.length()) break
            slots += Slot(times.getString(i).substring(11, 16), codes.getInt(i), days.getInt(i) == 1,
                temperatures.getDouble(i).roundToInt())
        }
        return slots
    }

    /** WMO weather interpretation codes, as used by Open-Meteo. */
    private fun iconFor(code: Int, isDay: Boolean) = when (code) {
        0 -> if (isDay) R.drawable.ic_weather_clear else R.drawable.ic_weather_night
        1, 2 -> R.drawable.ic_weather_partly
        3 -> R.drawable.ic_weather_cloudy
        45, 48 -> R.drawable.ic_weather_fog
        in 71..77, 85, 86 -> R.drawable.ic_weather_snow
        in 95..99 -> R.drawable.ic_weather_storm
        else -> R.drawable.ic_weather_rain
    }

    companion object {
        const val REQUEST_LOCATION = 1
        private const val REFRESH_MS = 30 * 60 * 1000L
        private const val RETRY_MS = 60 * 1000L

        private const val FRESH_LOCATION_MS = 15 * 60 * 1000L

        /** Older than this, the "next hours" would already be in the past, so hide them. */
        private const val MAX_AGE_MS = 3 * 60 * 60 * 1000L

        /** Hours after the current one shown after "Now". */
        private val HOURS_AHEAD = listOf(3, 6, 9)
    }
}
