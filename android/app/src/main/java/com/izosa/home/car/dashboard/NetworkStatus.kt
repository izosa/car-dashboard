package com.izosa.home.car.dashboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.widget.TextView

/**
 * Top-right connection indicator: network type (4G, Wi-Fi, ...) and signal bars, replacing
 * the Teyes status bar that the fullscreen home screen hides. Created on the main thread.
 */
class NetworkStatus(
    private val context: Context,
    private val label: TextView,
    private val bars: SignalBarsView,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var mobileLevel = 0

    private val phoneListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            mobileLevel = signalStrength.level
            update()
        }

        override fun onDataConnectionStateChanged(state: Int, networkType: Int) = update()
    }

    // Network callbacks arrive on a background thread.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { label.post { update() } }
        override fun onLost(network: Network) { label.post { update() } }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            label.post { update() }
        }
    }

    fun start() {
        telephony.listen(
            phoneListener,
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE,
        )
        connectivity.registerDefaultNetworkCallback(networkCallback)
        update()
    }

    fun stop() {
        telephony.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
        connectivity.unregisterNetworkCallback(networkCallback)
    }

    @Suppress("DEPRECATION")
    private fun update() {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        when {
            caps == null -> {
                label.setText(R.string.network_offline)
                bars.level = 0
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                label.setText(R.string.network_wifi)
                bars.level = WifiManager.calculateSignalLevel(wifi.connectionInfo.rssi, 5)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                label.text = generation(telephony.networkType)
                bars.level = mobileLevel
            }
            else -> {
                label.text = ""
                bars.level = 4
            }
        }
    }

    /** The same letters a phone status bar uses, so a drop from 4G to H or E is easy to spot. */
    private fun generation(type: Int) = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        NETWORK_TYPE_LTE_CA -> "4G+"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "H+"
        TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA -> "H"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE -> "E"
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_GSM -> "G"
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> "1X"
        else -> ""
    }

    private companion object {
        /** LTE carrier aggregation; a hidden TelephonyManager constant some modems report. */
        const val NETWORK_TYPE_LTE_CA = 19
    }
}
