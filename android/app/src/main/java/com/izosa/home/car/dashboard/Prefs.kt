package com.izosa.home.car.dashboard

import android.content.Context

object Prefs {
    private const val FILE = "dashboard_prefs"
    private const val KEY_ANIMATED_BACKGROUND = "animated_background"
    private const val KEY_WEATHER_JSON = "weather_json"
    private const val KEY_WEATHER_TIME = "weather_time"
    private const val KEY_LOCATION_ASKED = "location_asked"
    private const val KEY_FUEL_DISPLAY = "fuel_display"
    private const val KEY_TRACKING = "tracking"
    private const val KEY_TRACKING_SERVER = "tracking_server"
    private const val KEY_TRACKING_DEVICE_ID = "tracking_device_id"
    private const val KEY_AUTO_PARKING = "auto_parking"
    private const val KEY_HOME_LAT = "home_lat"
    private const val KEY_HOME_LON = "home_lon"
    private const val KEY_HOME_RADIUS = "home_radius"
    private const val DEFAULT_HOME_RADIUS = 350

    fun getFuelDisplay(context: Context): FuelStatus.Style =
        prefs(context).getString(KEY_FUEL_DISPLAY, null)
            ?.let { name -> FuelStatus.Style.values().firstOrNull { it.name == name } }
            ?: FuelStatus.Style.GAUGES

    fun setFuelDisplay(context: Context, style: FuelStatus.Style) =
        prefs(context).edit().putString(KEY_FUEL_DISPLAY, style.name).apply()

    fun isBackgroundAnimated(context: Context) =
        prefs(context).getBoolean(KEY_ANIMATED_BACKGROUND, true)

    fun setBackgroundAnimated(context: Context, animated: Boolean) =
        prefs(context).edit().putBoolean(KEY_ANIMATED_BACKGROUND, animated).apply()

    fun getWeatherJson(context: Context): String? = prefs(context).getString(KEY_WEATHER_JSON, null)

    fun getWeatherTime(context: Context) = prefs(context).getLong(KEY_WEATHER_TIME, 0)

    fun setWeather(context: Context, json: String, time: Long) =
        prefs(context).edit().putString(KEY_WEATHER_JSON, json).putLong(KEY_WEATHER_TIME, time).apply()

    fun wasLocationAsked(context: Context) = prefs(context).getBoolean(KEY_LOCATION_ASKED, false)

    fun setLocationAsked(context: Context) =
        prefs(context).edit().putBoolean(KEY_LOCATION_ASKED, true).apply()

    fun isTrackingEnabled(context: Context) =
        prefs(context).getBoolean(KEY_TRACKING, BuildConfig.DEFAULT_TRACKING_SERVER.isNotEmpty())

    fun setTrackingEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_TRACKING, enabled).apply()

    fun getTrackingServer(context: Context): String =
        prefs(context).getString(KEY_TRACKING_SERVER, null) ?: BuildConfig.DEFAULT_TRACKING_SERVER

    fun setTrackingServer(context: Context, url: String) =
        prefs(context).edit().putString(KEY_TRACKING_SERVER, url).apply()

    fun getTrackingDeviceId(context: Context): String =
        prefs(context).getString(KEY_TRACKING_DEVICE_ID, null) ?: BuildConfig.DEFAULT_TRACKING_DEVICE_ID

    fun setTrackingDeviceId(context: Context, id: String) =
        prefs(context).edit().putString(KEY_TRACKING_DEVICE_ID, id).apply()

    fun isAutoParkingEnabled(context: Context) =
        BuildConfig.PARKING_ON_ARRIVAL && BuildConfig.PARKING_PACKAGE.isNotEmpty() && prefs(context).getBoolean(KEY_AUTO_PARKING, true)

    fun setAutoParkingEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_PARKING, enabled).apply()

    /** Home point as (latitude, longitude), null until set here or in `defaults.properties`. */
    fun getHome(context: Context): Pair<Double, Double>? {
        val prefs = prefs(context)
        val lat = prefs.getString(KEY_HOME_LAT, null)?.toDoubleOrNull() ?: BuildConfig.DEFAULT_HOME_LAT.toDoubleOrNull()
        val lon = prefs.getString(KEY_HOME_LON, null)?.toDoubleOrNull() ?: BuildConfig.DEFAULT_HOME_LON.toDoubleOrNull()
        return if (lat != null && lon != null) lat to lon else null
    }

    fun setHome(context: Context, lat: Double, lon: Double) =
        prefs(context).edit().putString(KEY_HOME_LAT, lat.toString()).putString(KEY_HOME_LON, lon.toString()).apply()

    fun getHomeRadius(context: Context) = prefs(context).getInt(KEY_HOME_RADIUS, DEFAULT_HOME_RADIUS)

    fun setHomeRadius(context: Context, meters: Int) =
        prefs(context).edit().putInt(KEY_HOME_RADIUS, meters).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
