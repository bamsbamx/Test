package com.trailbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.app.SystemTime
import com.trailbook.core.format.UnitFormatter
import com.trailbook.core.format.SupportedFormat
import com.trailbook.core.model.ActivityType
import com.trailbook.core.routing.RoutingService
import com.trailbook.core.stats.StatisticsEngine
import kotlinx.coroutines.launch

@Composable
fun TrackDetailScreen(container: AppContainer, trackId: String, onBack: () -> Unit, onFollow: () -> Unit) {
    val settings by container.settings.collectAsState()
    val formatter = UnitFormatter(settings.units)
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val track = remember(version) { container.trackRepository.getById(trackId) }

    if (track == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Track not found")
        }
        return
    }

    val stats = track.stats ?: StatisticsEngine.compute(track.points)
    val elevationProfile = remember(version) { StatisticsEngine.elevationProfile(track.points, 120) }
    val speedProfile = remember(version) { StatisticsEngine.speedProfile(track.points, 120) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column {
                Text(track.name, style = MaterialTheme.typography.titleLarge)
                Text(track.activityType.label, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (stats != null) {
            StatGrid(
                listOf(
                    "Distance" to formatter.longDistance(stats.distanceMeters),
                    "Total time" to formatter.duration(stats.totalTimeSeconds),
                    "Moving time" to formatter.duration(stats.movingTimeSeconds),
                    "Avg speed" to formatter.speed(stats.avgSpeedMps),
                    "Moving avg" to formatter.speed(stats.avgMovingSpeedMps),
                    "Max speed" to formatter.speed(stats.maxSpeedMps),
                    "Elevation gain" to formatter.elevation(stats.elevationGain),
                    "Elevation loss" to formatter.elevation(stats.elevationLoss),
                    "Min elevation" to (stats.minElevation?.let { formatter.elevation(it) } ?: "-"),
                    "Max elevation" to (stats.maxElevation?.let { formatter.elevation(it) } ?: "-"),
                    "Points" to stats.pointCount.toString(),
                )
            )
        } else {
            Text("No statistics available: the track has no usable points.", style = MaterialTheme.typography.bodySmall)
        }
        if (settings.chart.showElevation) {
            Text("Elevation profile", style = MaterialTheme.typography.titleSmall)
            ElevationChart(elevationProfile)
        }
        if (settings.chart.showSpeed) {
            Text("Speed", style = MaterialTheme.typography.titleSmall)
            SpeedChart(speedProfile)
        }
        Text("Edit metadata", style = MaterialTheme.typography.titleSmall)
        var name by remember(track.id) { mutableStateOf(track.name) }
        var description by remember(track.id) { mutableStateOf(track.description ?: "") }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        Text("Activity type", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (type in ActivityType.entries.take(8)) {
                FilterChip(
                    selected = track.activityType == type,
                    onClick = {
                        container.trackRepository.save(track.copy(activityType = type, modifiedAt = SystemTime.now()))
                        version++
                    },
                    label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Button(onClick = {
            container.trackRepository.save(
                track.copy(name = name.ifBlank { track.name }, description = description.ifBlank { null }, modifiedAt = SystemTime.now())
            )
            message = "Metadata saved"
            version++
        }) { Text("Save metadata") }

        Text("Export and sharing", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (format in SupportedFormat.entries.filter { it.supportsExport }) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val waypoints = container.markerRepository.getAll()
                            val bytes = container.formatRegistry.export(track, format, waypoints)
                            val saved = container.filePicker.saveFile(
                                track.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + "." + format.extension, bytes
                            )
                            message = if (saved) "Exported as ${format.label}" else "Export cancelled or unavailable"
                        } catch (e: Exception) {
                            message = "Export failed: ${e.message}"
                        }
                    }
                }) { Text(format.label) }
            }
        }
        OutlinedButton(onClick = {
            scope.launch {
                val routePoints = RoutingService.routeFromTrack(track.points, track.name)
                val distance = RoutingService.distanceOf(routePoints)
                val gains = routePoints.mapNotNull { it.elevation }
                var up = 0.0
                var down = 0.0
                for (i in 1 until gains.size) {
                    val d = gains[i] - gains[i - 1]
                    if (d > 0) up += d else down -= d
                }
                container.routeRepository.save(
                    com.trailbook.core.model.Route(
                        id = com.trailbook.app.Ids.next(),
                        name = track.name,
                        profile = settings.routingProfile,
                        points = routePoints,
                        distanceMeters = distance,
                        elevationGain = up,
                        elevationLoss = down,
                        createdAt = SystemTime.now(),
                        fromTrackId = track.id,
                    )
                )
                message = "Route created from track. Open Routes to follow it."
                onFollow()
            }
        }) { Text("Use as route") }

        TextButton(onClick = {
            container.trackRepository.delete(track.id)
            onBack()
        }) { Text("Delete track", color = MaterialTheme.colorScheme.error) }

        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary)
    }
}
