package com.izosa.home.car.dashboard

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Outside temperature in the status corner: the car's own sensor when the Teyes server reports
 * one (marked with a thermometer), otherwise the forecast's current temperature for where the
 * car is. At 3° and below it turns ice blue with a snowflake, like the Peugeot cluster's ice
 * warning.
 */
class OutsideTemperature(
    context: Context,
    server: TeyesServer,
    private val icon: ImageView,
    private val text: TextView,
) {
    private var carTemperature: Float? = null
    private var forecast: Int? = null
    private val normalColor = context.getColor(R.color.label)
    private val iceColor = context.getColor(R.color.ice)

    init {
        server.watch(TeyesServer.MODULE_MAIN, CODE_OUTSIDE_TEMPERATURE) {
            carTemperature = it?.let(::decode)
            update()
        }
    }

    fun setForecast(temperature: Int?) {
        forecast = temperature
        update()
    }

    private fun update() {
        val fromCar = carTemperature
        val value = fromCar ?: forecast?.toFloat()
        if (value == null) {
            text.text = null
            icon.visibility = View.GONE
            return
        }
        // The CAN boxes report whole or half degrees; show the decimal only when there is one.
        val tenths = (value * 10).roundToInt()
        text.text = text.context.getString(
            R.string.temperature_text,
            if (tenths % 10 == 0) (tenths / 10).toString() else String.format(Locale.US, "%.1f", value),
        )
        val icy = value <= ICE_WARNING
        text.setTextColor(if (icy) iceColor else normalColor)
        when {
            icy -> icon.setImageResource(R.drawable.ic_snowflake)
            fromCar != null -> icon.setImageResource(R.drawable.ic_thermometer)
        }
        icon.visibility = if (icy || fromCar != null) View.VISIBLE else View.GONE
    }

    private companion object {
        /** Main module's outside temperature, as the Teyes status bar reads it. */
        const val CODE_OUTSIDE_TEMPERATURE = 40

        /** The i-Cockpit shows its ice warning at 3 °C and below. */
        const val ICE_WARNING = 3f

        /**
         * 0 = no reading; bits 30-31 a type (0 = value, 2 = "LOW", 3 = "HIGH"); bit 28 set for °F;
         * the low 28 bits are tenths of a degree plus 1000. Decoded as the Teyes SystemUI does.
         */
        fun decode(value: Int): Float? {
            if (value == 0 || value ushr 30 != 0) return null
            return ((value and 0x0FFFFFFF) - 1000) / 10f
        }
    }
}
