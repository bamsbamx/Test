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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.trailbook.core.geo.CoordinateFormat
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.Marker
import com.trailbook.core.model.MarkerIcons
import com.trailbook.core.model.MarkerList
import kotlinx.coroutines.launch

@Composable
fun MarkersScreen(container: AppContainer) {
    val settings by container.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var editingMarker by remember { mutableStateOf<Marker?>(null) }

    val lists = remember(version) { container.markerListRepository.getAll() }
    val markers = remember(version) {
        container.markerRepository.getAll().filter { selectedListId == null || it.listId == selectedListId }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Markers", style = MaterialTheme.typography.headlineSmall)

        Text("Marker lists", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selectedListId == null, onClick = { selectedListId = null }, label = { Text("All") })
            for (list in lists) {
                FilterChip(
                    selected = selectedListId == list.id,
                    onClick = { selectedListId = list.id },
                    label = { Text(list.name) },
                )
            }
            TextButton(onClick = {
                val list = MarkerList(
                    id = com.trailbook.app.Ids.next(),
                    name = "List ${lists.size + 1}",
                    createdAt = SystemTime.now(),
                )
                container.markerListRepository.save(list)
                version++
            }) { Text("New list") }
        }

        val editing = editingMarker
        if (editing != null) {
            MarkerEditor(container, editing, lists, onClose = { editingMarker = null; version++ })
        } else {
            TextButton(onClick = {
                editingMarker = Marker(
                    id = com.trailbook.app.Ids.next(),
                    name = "",
                    latitude = 0.0,
                    longitude = 0.0,
                    createdAt = SystemTime.now(),
                    listId = selectedListId ?: settings.defaultMarkerListId,
                )
            }) { Text("New marker") }
        }

        if (markers.isEmpty()) {
            Text("No markers here yet. Add one above or collect them from the map.")
        } else {
            LazyColumn {
                items(markers, key = { it.id }) { marker ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editingMarker = marker }) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${marker.name} (${marker.iconId})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                CoordinateFormat.format(GeoPoint(marker.latitude, marker.longitude), settings.coordinateFormat) +
                                    (marker.elevation?.let { " - ${com.trailbook.core.format.NumFormat.fixed(it, 0)} m" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row {
                                TextButton(onClick = {
                                    val gpx = com.trailbook.core.format.KmlCodec.writeMarkers(listOf(marker))
                                    scope.launch {
                                        val ok = container.filePicker.saveFile(
                                            marker.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".kml", gpx.encodeToByteArray()
                                        )
                                        message = if (ok) "Marker shared as KML" else "Sharing cancelled or unavailable"
                                    }
                                }) { Text("Share") }
                                TextButton(onClick = {
                                    container.markerRepository.delete(marker.id)
                                    version++
                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MarkerEditor(
    container: AppContainer,
    marker: Marker,
    lists: List<MarkerList>,
    onClose: () -> Unit,
) {
    var name by remember(marker.id) { mutableStateOf(marker.name) }
    var latText by remember(marker.id) { mutableStateOf(com.trailbook.core.format.NumFormat.fixed(marker.latitude, 6)) }
    var lonText by remember(marker.id) { mutableStateOf(com.trailbook.core.format.NumFormat.fixed(marker.longitude, 6)) }
    var description by remember(marker.id) { mutableStateOf(marker.description ?: "") }
    var iconId by remember(marker.id) { mutableStateOf(marker.iconId) }
    var listId by remember(marker.id) { mutableStateOf(marker.listId) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (marker.name.isEmpty()) "New marker" else "Edit marker", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = latText, onValueChange = { latText = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = lonText, onValueChange = { lonText = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Text("Icon", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (icon in MarkerIcons.ALL.take(8)) {
                    FilterChip(selected = iconId == icon, onClick = { iconId = icon }, label = { Text(icon) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (icon in MarkerIcons.ALL.drop(8)) {
                    FilterChip(selected = iconId == icon, onClick = { iconId = icon }, label = { Text(icon) })
                }
            }
            Text("List", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = listId == null, onClick = { listId = null }, label = { Text("None") })
                for (list in lists) {
                    FilterChip(selected = listId == list.id, onClick = { listId = list.id }, label = { Text(list.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    val point = if (lat != null && lon != null) GeoPoint.tryCreate(lat, lon) else null
                    if (point == null) {
                        error = "Invalid latitude/longitude"
                        return@Button
                    }
                    if (name.isBlank()) {
                        error = "Name is required"
                        return@Button
                    }
                    container.markerRepository.save(
                        marker.copy(
                            name = name,
                            latitude = point.latitude,
                            longitude = point.longitude,
                            description = description.ifBlank { null },
                            iconId = iconId,
                            listId = listId,
                            modifiedAt = SystemTime.now(),
                        )
                    )
                    onClose()
                }) { Text("Save") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
