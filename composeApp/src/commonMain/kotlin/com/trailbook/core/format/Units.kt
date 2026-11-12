package com.trailbook.core.format

import com.trailbook.core.prefs.UnitsSystem
import kotlin.math.abs
import kotlin.math.floor

data class UnitFormatter(val units: UnitsSystem) {

    fun distance(meters: Double): String = when (units) {
        UnitsSystem.METRIC -> shortDistance(meters)
        UnitsSystem.IMPERIAL -> {
            val feet = meters * FEET_PER_METER
            if (feet < 1056) "${NumFormat.fixed(feet, 0)} ft" else "${NumFormat.fixed(meters / METERS_PER_MILE, if (meters / METERS_PER_MILE < 10) 2 else 1)} mi"
        }
    }

    fun longDistance(meters: Double): String = when (units) {
        UnitsSystem.METRIC -> {
            val km = meters / 1000.0
            if (km < 10) "${NumFormat.fixed(km, 2)} km" else "${NumFormat.fixed(km, 1)} km"
        }
        UnitsSystem.IMPERIAL -> {
            val miles = meters / METERS_PER_MILE
            if (miles < 10) "${NumFormat.fixed(miles, 2)} mi" else "${NumFormat.fixed(miles, 1)} mi"
        }
    }

    fun shortDistance(meters: Double): String = when (units) {
        UnitsSystem.METRIC -> if (meters < 1000) "${NumFormat.fixed(meters, 0)} m" else "${NumFormat.fixed(meters / 1000.0, 2)} km"
        UnitsSystem.IMPERIAL -> {
            val feet = meters * FEET_PER_METER
            if (feet < 1056) "${NumFormat.fixed(feet, 0)} ft" else "${NumFormat.fixed(meters / METERS_PER_MILE, 2)} mi"
        }
    }

    fun elevation(meters: Double): String = when (units) {
        UnitsSystem.METRIC -> "${NumFormat.fixed(meters, 0)} m"
        UnitsSystem.IMPERIAL -> "${NumFormat.fixed(meters * FEET_PER_METER, 0)} ft"
    }

    fun elevationDelta(meters: Double): String = when (units) {
        UnitsSystem.METRIC -> "${NumFormat.fixed(meters, 0)} m"
        UnitsSystem.IMPERIAL -> "${NumFormat.fixed(meters * FEET_PER_METER, 0)} ft"
    }

    fun speed(mps: Double): String = when (units) {
        UnitsSystem.METRIC -> "${NumFormat.fixed(mps * 3.6, 1)} km/h"
        UnitsSystem.IMPERIAL -> "${NumFormat.fixed(mps * 2.236936, 1)} mph"
    }

    fun pace(mps: Double): String {
        if (mps <= 0.01) return "--"
        return when (units) {
            UnitsSystem.METRIC -> {
                val minPerKm = 1000.0 / mps / 60.0
                "${floor(minPerKm).toInt()}:${padSeconds((minPerKm - floor(minPerKm)) * 60.0)} /km"
            }
            UnitsSystem.IMPERIAL -> {
                val minPerMile = METERS_PER_MILE / mps / 60.0
                "${floor(minPerMile).toInt()}:${padSeconds((minPerMile - floor(minPerMile)) * 60.0)} /mi"
            }
        }
    }

    fun temperature(celsius: Double): String = when (units) {
        UnitsSystem.METRIC -> "${NumFormat.fixed(celsius, 0)} °C"
        UnitsSystem.IMPERIAL -> "${NumFormat.fixed(celsius * 9.0 / 5.0 + 32.0, 0)} °F"
    }

    fun duration(totalSeconds: Double): String {
        val s = abs(totalSeconds).toLong()
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return if (hours > 0) {
            "$hours:${padStartNumber(minutes, 2)}:${padStartNumber(seconds, 2)}"
        } else {
            "$minutes:${padStartNumber(seconds, 2)}"
        }
    }

    fun durationLong(totalSeconds: Double): String {
        val s = abs(totalSeconds).toLong()
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${padStartNumber(minutes, 2)}min"
            minutes > 0 -> "${minutes}min ${padStartNumber(s % 60, 2)}s"
            else -> "${s}s"
        }
    }

    fun distanceValue(meters: Double): Double = when (units) {
        UnitsSystem.METRIC -> meters / 1000.0
        UnitsSystem.IMPERIAL -> meters / METERS_PER_MILE
    }

    fun elevationValue(meters: Double): Double = when (units) {
        UnitsSystem.METRIC -> meters
        UnitsSystem.IMPERIAL -> meters * FEET_PER_METER
    }

    fun speedValue(mps: Double): Double = when (units) {
        UnitsSystem.METRIC -> mps * 3.6
        UnitsSystem.IMPERIAL -> mps * 2.236936
    }

    private fun padSeconds(v: Double): String = padStartNumber(v.toLong(), 2)

    private fun padStartNumber(value: Long, width: Int): String = value.toString().padStart(width, '0')

    companion object {
        const val FEET_PER_METER = 3.28083989501312
        const val METERS_PER_MILE = 1609.344
    }
}
