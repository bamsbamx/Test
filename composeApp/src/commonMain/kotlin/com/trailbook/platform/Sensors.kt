package com.trailbook.platform

import kotlinx.coroutines.flow.Flow

data class SensorReading(
    val headingDeg: Double? = null,
    val magneticFieldMicroTesla: Triple<Double, Double, Double>? = null,
    val pressureHpa: Double? = null,
    val tiltRollDeg: Double? = null,
    val tiltPitchDeg: Double? = null,
    val speedMps: Double? = null,
    val altitudeMeters: Double? = null,
)

interface SensorProvider {
    fun isCompassAvailable(): Boolean
    fun isBarometerAvailable(): Boolean
    fun isMagnetometerAvailable(): Boolean
    fun readings(): Flow<SensorReading>
}
