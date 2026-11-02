package com.izosa.home.car.dashboard

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Trip computer, opened by tapping the fuel display on the home screen: trip 1, now and trip 2,
 * the same three pages the car's multifunction display cycles through. Read only; the trips are
 * reset on the car's display.
 */
class TripActivity : AppCompatActivity() {

    private class Column(val title: Int, val rows: List<Pair<TripComputer.Field, Int>>)

    private val columns = listOf(
        Column(
            R.string.trip_1,
            listOf(
                TripComputer.TRIP1_DISTANCE to R.string.unit_trip_distance,
                TripComputer.TRIP1_AVERAGE to R.string.unit_trip_average,
                TripComputer.TRIP1_SPEED to R.string.unit_trip_speed,
            ),
        ),
        Column(
            R.string.trip_now,
            listOf(
                TripComputer.INSTANT to R.string.unit_fuel_instant,
                TripComputer.RANGE to R.string.unit_fuel_range,
                TripComputer.DESTINATION to R.string.unit_trip_destination,
            ),
        ),
        Column(
            R.string.trip_2,
            listOf(
                TripComputer.TRIP2_DISTANCE to R.string.unit_trip_distance,
                TripComputer.TRIP2_AVERAGE to R.string.unit_trip_average,
                TripComputer.TRIP2_SPEED to R.string.unit_trip_speed,
            ),
        ),
    )

    private val server = TeyesServer(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip)

        val container = findViewById<LinearLayout>(R.id.tripColumns)
        for (column in columns) {
            val view = layoutInflater.inflate(R.layout.item_trip_column, container, false)
            view.findViewById<TextView>(R.id.title).setText(column.title)
            column.rows.forEachIndexed { i, (field, unit) ->
                val value = view.findViewById<TextView>(VALUE_IDS[i])
                value.text = field.format(null)
                view.findViewById<TextView>(UNIT_IDS[i]).setText(unit)
                server.watch(TeyesServer.MODULE_CANBUS, field.code) { value.text = field.format(it) }
            }
            container.addView(view)
        }

        findViewById<View>(R.id.homeButton).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        server.start()
    }

    override fun onPause() {
        super.onPause()
        server.stop()
    }

    private companion object {
        val VALUE_IDS = listOf(R.id.value0, R.id.value1, R.id.value2)
        val UNIT_IDS = listOf(R.id.unit0, R.id.unit1, R.id.unit2)
    }
}
