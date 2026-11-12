package com.trailbook.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreMotion.CMAltitudeData
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

@OptIn(ExperimentalForeignApi::class)
class IosSensorProvider : SensorProvider {
    private val motionManager = CMMotionManager()

    override fun isCompassAvailable(): Boolean = motionManager.isDeviceMotionAvailable

    override fun isBarometerAvailable(): Boolean = motionManager.isAltitudeAvailable

    override fun isMagnetometerAvailable(): Boolean = motionManager.isMagnetometerAvailable

    override fun readings(): Flow<SensorReading> = callbackFlow {
        if (!motionManager.isDeviceMotionAvailable && !motionManager.isAltitudeAvailable) {
            close()
            return@callbackFlow
        }
        val queue = NSOperationQueue.mainQueue()
        if (motionManager.isDeviceMotionAvailable) {
            motionManager.deviceMotionUpdateInterval = 1.0 / 5.0
            motionManager.startDeviceMotionUpdatesToQueue(queue) { data, _ ->
                if (data != null) {
                    val gravity = data.gravity
                    val mag = data.magneticField
                    val roll = kotlin.math.atan2(gravity.y, gravity.z) * 180.0 / Math.PI
                    val pitch = kotlin.math.atan2(
                        -gravity.x,
                        kotlin.math.sqrt(gravity.y * gravity.y + gravity.z * gravity.z)
                    ) * 180.0 / Math.PI
                    trySend(
                        SensorReading(
                            headingDeg = data.heading,
                            magneticFieldMicroTesla = if (mag != null) {
                                Triple(mag.field.x, mag.field.y, mag.field.z)
                            } else null,
                            tiltRollDeg = roll,
                            tiltPitchDeg = pitch,
                        )
                    )
                }
            }
        }
        if (motionManager.isAltitudeAvailable) {
            motionManager.startAltitudeUpdatesToQueue(queue) { data, _ ->
                val altitudeData = data as? CMAltitudeData
                if (altitudeData != null) {
                    trySend(SensorReading(altitudeMeters = altitudeData.altitude))
                }
            }
        }
        awaitClose {
            motionManager.stopDeviceMotionUpdates()
            motionManager.stopAltitudeUpdates()
        }
    }
}
