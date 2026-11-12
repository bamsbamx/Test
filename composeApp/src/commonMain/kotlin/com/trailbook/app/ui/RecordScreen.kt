package com.trailbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.core.format.UnitFormatter
import com.trailbook.core.model.ActivityType
import com.trailbook.core.recording.RecordingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecordScreen(container: AppContainer) {
    val settings by container.settings.collectAsState()
    val session by container.recording.session.collectAsState()
    val update by container.recording.update.collectAsState()
    val locationError by container.recordingLocationError.collectAsState()
    val scope = rememberCoroutineScope()
    val formatter = UnitFormatter(settings.units)
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var locationJob by remember { mutableStateOf<Job?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(session?.startedAt, session?.state) {
        while (session != null && session?.state != RecordingState.IDLE) {
            tick++
            delay(1000)
        }
    }

    DisposableEffect(session?.startedAt, session?.state) {
        val s = session
        if (s != null && s.state != RecordingState.IDLE) {
            container.recordingNotification.show(
                update?.liveDistanceMeters ?: 0.0,
                update?.liveDurationSeconds ?: 0.0,
            )
        } else {
            container.recordingNotification.hide()
        }
        onDispose { }
    }

    fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = scope.launch {
            val granted = container.permissionManager.request(container.locationProvider.permissionType())
            if (granted != com.trailbook.platform.PermissionStatus.GRANTED) {
                container.recordLocationError(
                    "Location permission ${granted.name.lowercase()}. Grant it in system settings to record tracks."
                )
                return@launch
            }
            container.recordLocationError(null)
            try {
                container.locationProvider.updates(2000).collect { update ->
                    when (update) {
                        is com.trailbook.platform.LocationUpdate.Success -> {
                            container.recording.onLocationUpdate(update.point, settings.recording)
                            container.recordLocationError(null)
                        }
                        is com.trailbook.platform.LocationUpdate.Denied ->
                            container.recordLocationError(update.message)
                        is com.trailbook.platform.LocationUpdate.Unavailable ->
                            container.recordLocationError(update.message)
                        is com.trailbook.platform.LocationUpdate.Failed ->
                            container.recordLocationError(update.message)
                    }
                }
            } catch (e: Exception) {
                container.recordLocationError("Location stream stopped: ${e.message}")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Record track", style = MaterialTheme.typography.headlineSmall)
        val activeSession = session
        if (activeSession == null) {
            Text("Activity type", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (type in ActivityType.entries.take(8)) {
                    FilterChip(
                        selected = settings.recording.defaultActivityTypeId == type.id,
                        onClick = { container.updateSettings { it.copy(recording = it.recording.copy(defaultActivityTypeId = type.id)) } },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Track name (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val result = container.recording.start(
                        ActivityType.byId(settings.recording.defaultActivityTypeId),
                        settings.recording,
                    )
                    result.fold(
                        onSuccess = { session ->
                            container.saveRecordingSession(session)
                            message = null
                            startLocationUpdates()
                        },
                        onFailure = { message = it.message },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start recording") }
            if (locationError != null) {
                Card(Modifier.fillMaxWidth()) {
                    Text(locationError!!, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "Recording keeps running in the background with a persistent status notification when location permission is granted.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val live = update
            StatGrid(
                listOf(
                    "Distance" to formatter.longDistance(live?.liveDistanceMeters ?: 0.0),
                    "Duration" to formatter.duration(live?.liveDurationSeconds ?: 0.0),
                    "Avg speed" to formatter.speed(live?.liveSpeedMps ?: 0.0),
                    "Points" to (live?.session?.points?.size?.toString() ?: "0"),
                )
            )
            Text(
                when (activeSession.state) {
                    RecordingState.RECORDING -> "Recording..."
                    RecordingState.PAUSED -> "Paused"
                    RecordingState.IDLE -> "Stopped"
                },
                color = if (activeSession.state == RecordingState.RECORDING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
            if (locationError != null) {
                Text(locationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (activeSession.state) {
                    RecordingState.RECORDING -> {
                        OutlinedButton(onClick = {
                            container.recording.pause()?.let { container.saveRecordingSession(it) }
                        }) { Text("Pause") }
                    }
                    RecordingState.PAUSED -> {
                        Button(onClick = {
                            container.recording.resume()?.let { container.saveRecordingSession(it) }
                            startLocationUpdates()
                        }) { Text("Resume") }
                    }
                    else -> {}
                }
                OutlinedButton(onClick = {
                    val finished = container.recording.finish()
                    if (finished != null) {
                        container.saveRecordingSession(finished)
                        message = "Stopped. Review the track below, then save or discard."
                    }
                }) { Text("Stop") }
            }
            if (activeSession.state == RecordingState.IDLE) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; container.recording.rename(it) },
                    label = { Text("Track name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val finished = container.recording.finish() ?: return@Button
                        val stats = com.trailbook.core.stats.StatisticsEngine.compute(finished.points)
                        val track = com.trailbook.core.model.Track(
                            id = com.trailbook.app.Ids.next(),
                            name = name.ifBlank { finished.name ?: "Recording" },
                            activityType = com.trailbook.core.model.ActivityType.byId(finished.activityTypeId),
                            points = finished.points,
                            createdAt = finished.startedAt,
                            modifiedAt = com.trailbook.app.SystemTime.now(),
                            source = "recorded",
                            stats = stats,
                        )
                        container.trackRepository.save(track)
                        container.recording.discard()
                        container.saveRecordingSession(null)
                        container.recordingNotification.hide()
                        locationJob?.cancel()
                        message = "Track saved: ${track.name}"
                        name = ""
                    }) { Text("Save track") }
                    TextButton(onClick = {
                        container.recording.discard()
                        container.saveRecordingSession(null)
                        container.recordingNotification.hide()
                        locationJob?.cancel()
                        message = "Recording discarded"
                    }) { Text("Discard") }
                }
            }
        }
        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun StatGrid(stats: List<Pair<String, String>>) {
    for (i in stats.indices step 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (j in i until minOf(i + 2, stats.size)) {
                Card(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stats[j].first, style = MaterialTheme.typography.labelSmall)
                        Text(stats[j].second, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
