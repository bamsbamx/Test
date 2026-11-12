package com.trailbook.platform

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

class AndroidSensorProvider : SensorProvider {
    private val manager: SensorManager
        get() = AndroidContext.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun isCompassAvailable(): Boolean =
        manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null || manager.getDefaultSensor(Sensor.TYPE_ORIENTATION) != null

    override fun isBarometerAvailable(): Boolean = manager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    override fun isMagnetometerAvailable(): Boolean = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null

    override fun readings(): Flow<SensorReading> = callbackFlow {
        val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val pressure = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var gravityValues: FloatArray? = null
        var magneticValues: FloatArray? = null
        var pressureValue: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> gravityValues = event.values.copyOf()
                    Sensor.TYPE_MAGNETIC_FIELD -> magneticValues = event.values.copyOf()
                    Sensor.TYPE_PRESSURE -> pressureValue = event.values.firstOrNull()
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val r = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(r, event.values)
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        val heading = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0)
                        val pitch = Math.toDegrees(orientation[1].toDouble())
                        val roll = Math.toDegrees(orientation[2].toDouble())
                        val mag = magneticValues
                        trySend(
                            SensorReading(
                                headingDeg = heading,
                                tiltPitchDeg = pitch,
                                tiltRollDeg = roll,
                                pressureHpa = pressureValue?.toDouble(),
                                magneticFieldMicroTesla = mag?.let {
                                    Triple(it.getOrNull(0)?.toDouble() ?: 0.0, it.getOrNull(1)?.toDouble() ?: 0.0, it.getOrNull(2)?.toDouble() ?: 0.0)
                                },
                            )
                        )
                    }
                }
                if (rotation == null && gravityValues != null) {
                    val g = gravityValues
                    val mag = magneticValues
                    if (g != null && mag != null) {
                        val r = FloatArray(9)
                        if (SensorManager.getRotationMatrix(r, null, g, mag)) {
                            val orientation = FloatArray(3)
                            SensorManager.getOrientation(r, orientation)
                            val heading = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0)
                            val magnitude = sqrt(mag.fold(0.0) { acc, v -> acc + v.toDouble() * v })
                            trySend(
                                SensorReading(
                                    headingDeg = heading,
                                    tiltPitchDeg = Math.toDegrees(orientation[1].toDouble()),
                                    tiltRollDeg = Math.toDegrees(orientation[2].toDouble()),
                                    pressureHpa = pressureValue?.toDouble(),
                                    magneticFieldMicroTesla = Triple(mag.getOrNull(0)?.toDouble() ?: 0.0, mag.getOrNull(1)?.toDouble() ?: 0.0, mag.getOrNull(2)?.toDouble() ?: 0.0).let { Triple(it.first, it.second, it.third) },
                                )
                            )
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            }
        }
        rotation?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        pressure?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        gravity?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        if (rotation == null && magnetometer == null && pressure == null) {
            trySend(SensorReading())
        }
        awaitClose { manager.unregisterListener(listener) }
    }
}
