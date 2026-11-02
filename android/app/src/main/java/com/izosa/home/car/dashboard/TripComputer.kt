package com.izosa.home.car.dashboard

/**
 * The car's trip computer as the Teyes canbus module reports it, with the codes and valid
 * ranges of the Raise (RZC) Peugeot/Citroën "ALL" protocol the head unit is set to (Car Info's
 * Travel1/2/3 pages). Out-of-range values (65535 while the car stands, say) mean no reading and
 * show as dashes, as on the car's own display. Other CAN boxes put these elsewhere.
 */
object TripComputer {

    class Field(val code: Int, private val max: Int, private val tenths: Boolean, private val none: String) {
        fun format(value: Int?): String = when {
            value == null || value !in 0..max -> none
            tenths -> "${value / 10}.${value % 10}"
            else -> value.toString()
        }
    }

    val INSTANT = Field(5, 3000, tenths = true, none = "--.-")
    val RANGE = Field(6, 2000, tenths = false, none = "---")
    val DESTINATION = Field(7, 6000, tenths = false, none = "----")

    val TRIP1_DISTANCE = Field(8, 9999, tenths = false, none = "----")
    val TRIP1_AVERAGE = Field(2, 300, tenths = true, none = "--.-")
    val TRIP1_SPEED = Field(4, 250, tenths = false, none = "--")

    val TRIP2_DISTANCE = Field(11, 9999, tenths = false, none = "----")
    val TRIP2_AVERAGE = Field(9, 300, tenths = true, none = "--.-")
    val TRIP2_SPEED = Field(12, 250, tenths = false, none = "--")
}
