package com.trailbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.app.SystemTime
import com.trailbook.core.format.UnitFormatter
import com.trailbook.core.model.ActivityType
import com.trailbook.core.model.Track
import kotlinx.coroutines.launch

enum class TrackSort(val label: String) {
    NEWEST("Newest"),
    NAME("Name"),
    DISTANCE("Distance"),
    DURATION("Duration"),
}

@Composable
fun TracksScreen(container: AppContainer, onOpenTrack: (String) -> Unit) {
    val settings by container.settings.collectAsState()
    val formatter = UnitFormatter(settings.units)
    val scope = rememberCoroutineScope()
    var tracksVersion by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(TrackSort.NEWEST) }
    var activityFilter by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    val allTracks = remember(tracksVersion) { container.trackRepository.getAll() }
    val filtered = allTracks
        .filter { activityFilter == null || it.activityType.id == activityFilter }
        .sortedWith(
            when (sort) {
                TrackSort.NEWEST -> compareByDescending { it.createdAt }
                TrackSort.NAME -> compareBy { it.name.lowercase() }
                TrackSort.DISTANCE -> compareByDescending { it.stats?.distanceMeters ?: 0.0 }
                TrackSort.DURATION -> compareByDescending { it.stats?.totalTimeSeconds ?: 0.0 }
            }
        )

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tracks", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (option in TrackSort.entries) {
                FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = activityFilter == null, onClick = { activityFilter = null }, label = { Text("All") })
            for (type in ActivityType.entries.take(6)) {
                FilterChip(
                    selected = activityFilter == type.id,
                    onClick = { activityFilter = type.id },
                    label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                importing = true
                scope.launch {
                    val picked = container.filePicker.openFile()
                    if (picked == null) {
                        importing = false
                        return@launch
                    }
                    try {
                        val parsed = container.formatRegistry.parse(picked.name, picked.bytes)
                        for (track in parsed.tracks) {
                            container.trackRepository.save(track)
                        }
                        for (waypoint in parsed.waypoints) {
                            container.markerRepository.save(waypoint)
                        }
                        message = buildString {
                            append("Imported ${parsed.tracks.size} track(s) from ${parsed.format}")
                            if (parsed.waypoints.isNotEmpty()) append(" and ${parsed.waypoints.size} waypoint(s)")
                            if (parsed.warnings.isNotEmpty()) append(". Warnings: " + parsed.warnings.take(3).joinToString("; "))
                        }
                        tracksVersion++
                    } catch (e: Exception) {
                        message = "Import failed: ${e.message}"
                    }
                    importing = false
                }
            }) { Text(if (importing) "Importing..." else "Import file") }
            TextButton(onClick = {
                selectionMode = !selectionMode
                if (!selectionMode) selected = setOf()
            }) { Text(if (selectionMode) "Cancel selection" else "Select") }
            if (selectionMode && selected.isNotEmpty()) {
                IconButton(onClick = {
                    val deleted = container.trackRepository.deleteAll(selected)
                    message = "Deleted $deleted track(s)"
                    selected = setOf()
                    selectionMode = false
                    tracksVersion++
                }) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
            }
        }
        if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (filtered.isEmpty()) {
            Text("No tracks yet. Record one or import a GPX/KML/KMZ/TCX/CSV/FIT file.")
        } else {
            LazyColumn {
                items(filtered, key = { it.id }) { track ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable {
                                if (selectionMode) {
                                    selected = if (track.id in selected) selected - track.id else selected + track.id
                                } else {
                                    onOpenTrack(track.id)
                                }
                            }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectionMode) {
                                Icon(
                                    if (track.id in selected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(Modifier.padding(start = if (selectionMode) 12.dp else 0.dp)) {
                                Text(track.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    buildString {
                                        append(track.activityType.label)
                                        track.stats?.let {
                                            append(" - ${formatter.longDistance(it.distanceMeters)}")
                                            append(" - ${formatter.duration(it.totalTimeSeconds)}")
                                            append(" - up ${formatter.elevation(it.elevationGain)}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
