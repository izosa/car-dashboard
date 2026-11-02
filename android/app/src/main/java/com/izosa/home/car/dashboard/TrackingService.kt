package com.izosa.home.car.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Replaces Traccar Client: a GPS fix every few seconds, sent to the Traccar server with the
 * OsmAnd protocol under the same device id, so the car keeps its history. Fixes that can't be
 * sent (no signal) wait in a file and go out in order once the network is back. The same fixes
 * drive the "arriving home" check that opens Parking.
 *
 * Teyes kills background apps after a couple of minutes asleep, so this can't rely on staying
 * alive: [sync] runs every time the home screen shows (which is the first thing after waking)
 * and on boot, and starts it again.
 */
class TrackingService : Service(), LocationListener {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var queueFile: File
    private val queue = ArrayDeque<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastFix: Location? = null

    /** Null until the first fix: starting inside the home radius means leaving, not arriving. */
    private var homeArmed: Boolean? = null

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification())
        queueFile = File(filesDir, QUEUE_FILE)
        if (queueFile.exists()) queue.addAll(queueFile.readLines().filter { it.isNotBlank() })
        thread = HandlerThread("tracking").apply { start() }
        handler = Handler(thread.looper)
        getSystemService(LocationManager::class.java)
            .requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, this, thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Settings may have changed (e.g. tracking switched off while home stays on).
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        if (intent?.action == ACTION_SEND_NOW) handler.post { sendNow() }
        return START_STICKY
    }

    override fun onDestroy() {
        getSystemService(LocationManager::class.java).removeUpdates(this)
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Runs on the tracking thread.
    override fun onLocationChanged(location: Location) {
        lastFix = location
        if (Prefs.isTrackingEnabled(this)) {
            enqueue(location)
            flush()
        }
        checkHome(location)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    /**
     * Tap on the clock: sends where the car is now, stamped with the current time, even when
     * tracking is off (then the service stops again unless the home check needs it).
     */
    @SuppressLint("MissingPermission")
    private fun sendNow() {
        val fix = lastFix ?: getSystemService(LocationManager::class.java).getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (fix != null) {
            enqueue(Location(fix).apply { time = System.currentTimeMillis() })
            flush()
        }
        if (!isNeeded(this)) stopSelf()
    }

    private fun enqueue(location: Location) {
        // OsmAnd protocol as Traccar Client sends it: seconds, speed in knots.
        val point = "timestamp=%d&lat=%.6f&lon=%.6f&speed=%.1f&bearing=%.1f&altitude=%.1f&accuracy=%.1f".format(
            Locale.US, location.time / 1000, location.latitude, location.longitude,
            location.speed * MS_TO_KNOTS, location.bearing, location.altitude, location.accuracy,
        )
        queue.addLast(point)
        while (queue.size > MAX_QUEUE) queue.removeFirst()
        queueFile.appendText(point + "\n")
    }

    /** Sends queued fixes oldest first; stops at the first failure and retries on the next fix. */
    private fun flush() {
        val server = Prefs.getTrackingServer(this).trimEnd('/')
        if (server.isEmpty()) return
        val id = Uri.encode(Prefs.getTrackingDeviceId(this))
        var sent = 0
        while (queue.isNotEmpty() && send("$server/?id=$id&${queue.first()}")) {
            queue.removeFirst()
            sent++
        }
        if (sent > 0) {
            queueFile.writeText(queue.joinToString("") { it + "\n" })
            onSent?.let { mainHandler.post(it) }
        }
    }

    private fun send(url: String): Boolean = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val ok = connection.responseCode == HttpURLConnection.HTTP_OK
        connection.disconnect()
        ok
    } catch (_: Exception) {
        false
    }

    /**
     * Opens Parking once per arrival: inside the radius at walking-to-parking speed. It arms
     * again only after driving well out of the radius, so manoeuvring at the edge doesn't
     * reopen it.
     */
    private fun checkHome(location: Location) {
        val home = Prefs.getHome(this) ?: return
        val radius = Prefs.getHomeRadius(this)
        val distance = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, home.first, home.second, distance)
        val inside = distance[0] < radius
        when {
            homeArmed == null -> homeArmed = !inside
            !inside && distance[0] > radius + REARM_EXTRA_M -> homeArmed = true
            inside && homeArmed == true && location.speed * MS_TO_KMH < ARRIVAL_SPEED_KMH -> {
                homeArmed = false
                if (Prefs.isAutoParkingEnabled(this)) openParking()
            }
        }
    }

    private fun openParking() {
        val intent = packageManager.getLaunchIntentForPackage(BuildConfig.PARKING_PACKAGE) ?: return
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val text = if (Prefs.isTrackingEnabled(this)) R.string.tracking_on else R.string.tracking_home_only
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_traccar)
            .setContentTitle(getString(text))
            .setOngoing(true)
            .build()
    }

    /** Starts the service on boot; Teyes reboots only after a battery cut or a crash. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) sync(context)
        }
    }

    companion object {
        private const val INTERVAL_MS = 5_000L
        private const val MAX_QUEUE = 20_000 // about a day of driving at 5 s
        private const val QUEUE_FILE = "tracking_queue.txt"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val MS_TO_KNOTS = 1.943844f
        private const val MS_TO_KMH = 3.6f
        private const val ARRIVAL_SPEED_KMH = 15f
        private const val REARM_EXTRA_M = 200f

        /** Set by the home screen while visible, to blink its status icon; runs on the main thread. */
        @Volatile
        var onSent: (() -> Unit)? = null

        private const val ACTION_SEND_NOW = "com.izosa.home.car.dashboard.SEND_NOW"

        private fun isNeeded(context: Context) =
            Prefs.isTrackingEnabled(context) || Prefs.isAutoParkingEnabled(context)

        private fun isAllowed(context: Context) =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Starts or stops the service to match the settings; safe to call often. */
        fun sync(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
            if (isNeeded(context) && isAllowed(context)) context.startForegroundService(intent) else context.stopService(intent)
        }

        /** Sends the current position right away. */
        fun sendNow(context: Context) {
            if (!isAllowed(context)) return
            context.startForegroundService(Intent(context, TrackingService::class.java).setAction(ACTION_SEND_NOW))
        }
    }
}
