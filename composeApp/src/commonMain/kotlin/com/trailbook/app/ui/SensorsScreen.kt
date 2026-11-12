package com.trailbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.core.format.NumFormat
import com.trailbook.platform.SensorReading

@Composable
fun SensorsScreen(container: AppContainer) {
    var reading by remember { mutableStateOf<SensorReading?>(null) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var permission by remember { mutableStateOf(com.trailbook.platform.PermissionStatus.UNAVAILABLE) }

    LaunchedEffect(Unit) {
        val compassAvailable = container.sensorProvider.isCompassAvailable()
        val barometerAvailable = container.sensorProvider.isBarometerAvailable()
        if (!compassAvailable && !barometerAvailable) {
            streamError = "No motion or barometer sensors are available on this device."
            return@LaunchedEffect
        }
        try {
            container.sensorProvider.readings().collect { value ->
                reading = value
                streamError = null
            }
        } catch (e: Exception) {
            streamError = "Sensor stream stopped: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Live sensors", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Compass: ${if (container.sensorProvider.isCompassAvailable()) "available" else "not available on this device"} - " +
                "Barometer: ${if (container.sensorProvider.isBarometerAvailable()) "available" else "not available"} - " +
                "Magnetometer: ${if (container.sensorProvider.isMagnetometerAvailable()) "available" else "not available"}",
            style = MaterialTheme.typography.bodySmall,
        )
        val current = reading
        if (current == null) {
            Text(streamError ?: "Waiting for sensor data...")
        } else {
            SensorCard("Compass heading", current.headingDeg?.let { NumFormat.fixed(it, 0) + "\u00b0" } ?: "No data")
            SensorCard("Tilt", buildString {
                current.tiltPitchDeg?.let { append("Pitch " + NumFormat.fixed(it, 1) + "\u00b0 ") }
                current.tiltRollDeg?.let { append("Roll " + NumFormat.fixed(it, 1) + "\u00b0") }
                if (isEmpty()) append("No data")
            })
            SensorCard("Atmospheric pressure", current.pressureHpa?.let { NumFormat.fixed(it, 1) + " hPa" } ?: "No barometer data")
            SensorCard("Altitude (barometric)", current.altitudeMeters?.let { NumFormat.fixed(it, 0) + " m" } ?: "No barometer data")
            SensorCard("Magnetic field", current.magneticFieldMicroTesla?.let { (x, y, z) ->
                NumFormat.fixed(x, 1) + ", " + NumFormat.fixed(y, 1) + ", " + NumFormat.fixed(z, 1) + " \u00b5T"
            } ?: "No magnetometer data")
        }
        if (streamError != null) {
            Text(streamError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SensorCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
