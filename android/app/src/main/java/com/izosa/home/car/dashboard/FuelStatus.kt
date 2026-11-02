package com.izosa.home.car.dashboard

import android.view.View
import android.widget.TextView

/**
 * The car's trip computer from the Teyes CAN box ([TripComputer]): instant consumption and range,
 * as the multifunction display shows them. Settings → "Fuel display" picks where: [Style.CORNER]
 * puts the instant consumption in the status corner, [Style.GAUGES] shows both beside the clock
 * like the i-Cockpit's two dials. Nothing shows until the CAN box reports; while the car stands
 * the instant consumption reads "--.-", as on the car's own display. Tapping either opens
 * [onOpen] (the trip computer screen).
 */
class FuelStatus(
    server: TeyesServer,
    private val corner: View,
    private val cornerText: TextView,
    private val gauges: List<View>,
    private val instantText: TextView,
    private val rangeText: TextView,
    onOpen: () -> Unit,
) {
    enum class Style(val label: Int) {
        OFF(R.string.fuel_display_off),
        CORNER(R.string.fuel_display_corner),
        GAUGES(R.string.fuel_display_gauges),
    }

    private var style = Style.GAUGES
    private var instant: Int? = null
    private var range: Int? = null

    init {
        server.watch(TeyesServer.MODULE_CANBUS, TripComputer.INSTANT.code) {
            instant = it
            update()
        }
        server.watch(TeyesServer.MODULE_CANBUS, TripComputer.RANGE.code) {
            range = it
            update()
        }
        (gauges + corner).forEach { it.setOnClickListener { onOpen() } }
    }

    fun setStyle(style: Style) {
        this.style = style
        update()
    }

    private fun update() {
        val reporting = instant != null || range != null
        val instantValue = TripComputer.INSTANT.format(instant)
        cornerText.text = instantValue
        instantText.text = instantValue
        rangeText.text = TripComputer.RANGE.format(range)
        corner.visibility = if (reporting && style == Style.CORNER) View.VISIBLE else View.GONE
        val gaugesVisibility = if (reporting && style == Style.GAUGES) View.VISIBLE else View.GONE
        gauges.forEach { it.visibility = gaugesVisibility }
    }
}
