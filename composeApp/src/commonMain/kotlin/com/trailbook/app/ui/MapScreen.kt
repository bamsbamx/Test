package com.trailbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.app.SystemTime
import com.trailbook.app.ui.map.ElevationGrid
import com.trailbook.app.ui.map.MapCanvas
import com.trailbook.app.ui.map.MapCanvasState
import com.trailbook.app.ui.map.TileCache
import com.trailbook.core.geo.CoordinateFormat
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.geo.SunriseSunset
import com.trailbook.core.model.Marker
import com.trailbook.core.model.TrackPoint
import com.trailbook.core.prefs.MapLayer
import com.trailbook.core.search.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun MapScreen(container: AppContainer) {
    val settings by container.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val tileCache = remember {
        TileCache(container.httpTransport, container.fileSystem, container.dataDir + "/tiles")
    }
    val mapState = remember { MapCanvasState(tileCache) }
    var selectedPoint by remember { mutableStateOf<TrackPoint?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.trailbook.core.search.PlaceResult>>(emptyList()) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var elevationGrid by remember { mutableStateOf<ElevationGrid?>(null) }
    var elevationLookup by remember { mutableStateOf<Double?>(null) }
    var sunTimes by remember { mutableStateOf<com.trailbook.core.geo.SunTimes?>(null) }
    var lookupJob by remember { mutableStateOf<Job?>(null) }
    var snapshotMessage by remember { mutableStateOf<String?>(null) }

    mapState.style = settings.mapStyle

    val tracks = remember(settings.showTracksOnMap) {
        if (settings.showTracksOnMap) container.trackRepository.getAll() else emptyList()
    }
    val routes = remember(settings.showRoutesOnMap) {
        if (settings.showRoutesOnMap) container.routeRepository.getAll() else emptyList()
    }
    val markers = remember(settings.showMarkersOnMap) {
        if (settings.showMarkersOnMap) container.markerRepository.getAll() else emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        MapCanvas(
            state = mapState,
            tracks = tracks,
            routes = routes,
            markers = markers,
            locationPoint = null,
            elevationGrid = elevationGrid,
            selectedPoint = selectedPoint,
            onMapTap = { lat, lon ->
                selectedPoint = TrackPoint(lat, lon, null, SystemTime.now())
                elevationLookup = null
                sunTimes = SunriseSunset.compute(lat, lon, SystemTime.now())
                lookupJob?.cancel()
                lookupJob = scope.launch {
                    elevationLookup = try {
                        container.elevationService.elevationAt(GeoPoint(lat, lon))
                    } catch (e: Exception) {
                        null
                    }
                }
                if (settings.enabledLayers.isNotEmpty()) {
                    lookupJob?.invokeOnCompletion {
                        scope.launch {
                            elevationGrid = loadElevationGrid(container, lat, lon, mapState.camera.zoomInt, settings.enabledLayers.first())
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            Modifier.align(Alignment.TopStart).padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { showSearch = !showSearch },
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            )
            AssistChip(
                onClick = { showLayers = !showLayers },
                label = { Text("Layers") },
                leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null) },
            )
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            FloatingActionButton(onClick = {
                mapState.camera = mapState.camera.copy(zoom = (mapState.camera.zoom + 1).coerceAtMost(19.0))
            }) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
            FloatingActionButton(onClick = {
                mapState.camera = mapState.camera.copy(zoom = (mapState.camera.zoom - 1).coerceAtLeast(2.0))
            }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
        }

        if (showSearch) {
            ElevatedCard(Modifier.align(Alignment.TopCenter).padding(top = 52.dp).width(360.dp)) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Place name or coordinates (DD / DM / UTM)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row {
                        TextButton(onClick = {
                            searching = true
                            searchStatus = null
                            scope.launch {
                                when (val result = container.geocodingService.search(searchQuery)) {
                                    is SearchResult.Places -> {
                                        searchResults = result.results
                                        if (result.results.isEmpty()) searchStatus = "No results"
                                    }
                                    is SearchResult.Coordinates -> {
                                        container.recentSearches.record(searchQuery, SystemTime.now())
                                        mapState.moveTo(result.point.latitude, result.point.longitude, 14.0)
                                        selectedPoint = TrackPoint(result.point.latitude, result.point.longitude, null, SystemTime.now())
                                        searchStatus = result.formatted
                                        searchResults = emptyList()
                                    }
                                    is SearchResult.Failure -> searchStatus = result.message
                                }
                                searching = false
                            }
                        }) { Text("Search") }
                        if (searching) CircularProgressIndicator(Modifier.padding(8.dp))
                    }
                    if (searchStatus != null) Text(searchStatus!!, style = MaterialTheme.typography.bodySmall)
                    if (searchResults.isNotEmpty()) {
                        LazyColumn(Modifier.width(336.dp)) {
                            items(searchResults) { place ->
                                TextButton(onClick = {
                                    container.recentSearches.record(searchQuery, SystemTime.now())
                                    mapState.moveTo(place.latitude, place.longitude, 14.0)
                                    selectedPoint = TrackPoint(place.latitude, place.longitude, null, SystemTime.now())
                                    showSearch = false
                                }) {
                                    Column {
                                        Text(place.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(place.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    val recents = container.recentSearches.all()
                    if (recents.isNotEmpty()) {
                        Text("Recent", style = MaterialTheme.typography.labelSmall)
                        Row {
                            for (recent in recents.take(3)) {
                                AssistChip(onClick = { searchQuery = recent.query }, label = { Text(recent.query, maxLines = 1) })
                            }
                        }
                    }
                }
            }
        }

        if (showLayers) {
            ElevatedCard(Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 8.dp).width(260.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Map style", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (style in com.trailbook.core.prefs.MapStyle.entries) {
                            FilterChip(
                                selected = settings.mapStyleId == style.id,
                                onClick = { container.updateSettings { it.copy(mapStyleId = style.id) } },
                                label = { Text(style.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Text("Terrain layers", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    for (layer in MapLayer.entries) {
                        val enabled = layer.id in settings.enabledLayers
                        FilterChip(
                            selected = enabled,
                            onClick = {
                                container.updateSettings { s ->
                                    s.copy(enabledLayers = if (enabled) s.enabledLayers - layer.id else s.enabledLayers + layer.id)
                                }
                                elevationGrid = null
                            },
                            label = { Text(layer.label) },
                        )
                        if (layer == MapLayer.AVALANCHE_RISK && enabled) {
                            Text(
                                "No free avalanche-bulletin data source is available; this overlay is a documented limitation.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        selectedPoint?.let { point ->
            ElevatedCard(Modifier.align(Alignment.BottomStart).padding(12.dp).width(340.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Selected location", style = MaterialTheme.typography.titleSmall)
                    Text(
                        CoordinateFormat.format(GeoPoint(point.latitude, point.longitude), settings.coordinateFormat),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val elevation = elevationLookup
                    Text(
                        if (elevation != null) "Elevation: ${com.trailbook.core.format.NumFormat.fixed(elevation, 0)} m"
                        else "Elevation: unavailable (offline or lookup failed)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    sunTimes?.let { times ->
                        Text(
                            when {
                                times.polarDay -> "Polar day: sun does not set"
                                times.polarNight -> "Polar night: sun does not rise"
                                else -> "Sunrise ${times.sunriseEpochMillis?.let { formatClock(it) } ?: "--"} - Sunset ${times.sunsetEpochMillis?.let { formatClock(it) } ?: "--"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row {
                        TextButton(onClick = {
                            val marker = Marker(
                                id = com.trailbook.app.Ids.next(),
                                name = "Marker " + com.trailbook.core.format.NumFormat.fixed(point.latitude, 3),
                                latitude = point.latitude,
                                longitude = point.longitude,
                                elevation = elevationLookup,
                                createdAt = SystemTime.now(),
                                listId = settings.defaultMarkerListId,
                            )
                            container.markerRepository.save(marker)
                            snapshotMessage = "Marker saved"
                        }) { Text("Add marker") }
                        TextButton(onClick = { scope.launch { snapshotMessage = exportMapSnapshot(container, mapState, tracks, routes, markers) } }) {
                            Text("Snapshot")
                        }
                    }
                    if (snapshotMessage != null) {
                        Text(snapshotMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private suspend fun loadElevationGrid(
    container: AppContainer,
    lat: Double,
    lon: Double,
    zoom: Int,
    mode: String,
): ElevationGrid? = try {
    val spanDeg = when {
        zoom >= 14 -> 0.02
        zoom >= 12 -> 0.08
        zoom >= 10 -> 0.3
        else -> 1.0
    }
    val n = 10
    val topLat = lat + spanDeg / 2
    val leftLon = lon - spanDeg / 2
    val latStep = spanDeg / n
    val lonStep = spanDeg / n
    val points = mutableListOf<GeoPoint>()
    for (row in 0..n) {
        for (col in 0..n) {
            points.add(GeoPoint(topLat - row * latStep, leftLon + col * lonStep))
        }
    }
    val elevations = container.elevationService.elevationFor(points)
    val values = (0..n).map { row ->
        (0..n).map { col -> elevations.getOrNull(row * (n + 1) + col) }
    }
    val centerLatOfCell = topLat - latStep / 2
    val cellSize = com.trailbook.core.geo.GeoMath.haversineMeters(
        GeoPoint(topLat - row0(), leftLon), GeoPoint(topLat - row0() - latStep, leftLon)
    )
    ElevationGrid(lat, lon, topLat, leftLon, latStep, lonStep, cellSize, values, mode)
} catch (e: Exception) {
    null
}

private fun row0(): Double = 0.0

internal fun formatClock(epochMillis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
    return com.trailbook.core.format.Str.fmt(
        "%02d:%02d",
        (instant.epochSeconds / 3600) % 24,
        (instant.epochSeconds / 60) % 60,
    )
}

internal suspend fun exportMapSnapshot(
    container: AppContainer,
    mapState: com.trailbook.app.ui.map.MapCanvasState,
    tracks: List<com.trailbook.core.model.Track>,
    routes: List<com.trailbook.core.model.Route>,
    markers: List<Marker>,
): String {
    val width = 1024
    val height = 768
    val bitmap = com.trailbook.platform.ImageCodec.createImage(width, height)
        ?: return "Snapshot rendering is unavailable on this platform"
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val projection = mapState.project(width.toFloat(), height.toFloat())
    val density = androidx.compose.ui.unit.Density(1f)
    val drawScope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    drawScope.draw(density, androidx.compose.ui.unit.LayoutDirection.Ltr, canvas, androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())) {
        drawRect(androidx.compose.ui.graphics.Color(0xFFE8E4DC))
        val z = projection.camera.zoomInt
        val tileX0 = kotlin.math.floor((0f - projection.centerOffsetX) / 256f).toInt()
        val tileX1 = kotlin.math.ceil((width - projection.centerOffsetX) / 256f).toInt()
        val tileY0 = kotlin.math.floor((0f - projection.centerOffsetY) / 256f).toInt()
        val tileY1 = kotlin.math.ceil((height - projection.centerOffsetY) / 256f).toInt()
        for (tx in tileX0..tileX1) {
            for (ty in tileY0..tileY1) {
                val cached = mapState.tiles["$z/$tx/$ty"]
                if (cached != null) {
                    drawImage(
                        image = cached,
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            (projection.centerOffsetX + tx * 256f).toInt(),
                            (projection.centerOffsetY + ty * 256f).toInt(),
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(256, 256),
                    )
                }
            }
        }
        for (track in tracks) drawPolyline(track.points, projection, androidx.compose.ui.graphics.Color(0xFF1565C0), 6f)
        for (route in routes) {
            drawPolyline(
                route.points.map { com.trailbook.core.model.TrackPoint(it.latitude, it.longitude, it.elevation, 0L) },
                projection, androidx.compose.ui.graphics.Color(0xFF6A1B9A), 6f,
            )
        }
        for (marker in markers) {
            val pos = projection.project(marker.latitude, marker.longitude)
            drawCircle(androidx.compose.ui.graphics.Color(0xFFD32F2F), radius = 10f, center = pos)
            drawCircle(androidx.compose.ui.graphics.Color.White, radius = 5f, center = pos)
        }
    }
    val png = com.trailbook.platform.ImageCodec.encodePng(bitmap)
        ?: return "PNG encoding failed on this platform"
    val saved = container.fileSharer.saveToDownloads("trailbook-map.png", png)
    return if (saved) "Map snapshot saved as trailbook-map.png" else "Snapshot could not be written"
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolyline(
    points: List<com.trailbook.core.model.TrackPoint>,
    projection: com.trailbook.app.ui.map.MapProjection,
    color: androidx.compose.ui.graphics.Color,
    strokeWidth: Float,
) {
    if (points.size < 2) return
    val path = androidx.compose.ui.graphics.Path()
    var started = false
    for (p in points) {
        val pos = projection.project(p.latitude, p.longitude)
        if (!started) {
            path.moveTo(pos.x, pos.y)
            started = true
        } else {
            path.lineTo(pos.x, pos.y)
        }
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth))
}
