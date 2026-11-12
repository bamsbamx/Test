package com.trailbook.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.trailbook.core.model.Marker
import com.trailbook.core.model.Route
import com.trailbook.core.model.Track
import com.trailbook.core.model.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapCanvasState(
    val cache: TileCache,
) {
    var camera by mutableStateOf(MapCamera())
    var style by mutableStateOf(com.trailbook.core.prefs.MapStyle.STANDARD)
    val tiles = mutableStateMapOf<String, ImageBitmap>()

    fun project(width: Float, height: Float) = MapProjection(camera, width, height)

    fun moveTo(lat: Double, lon: Double, zoom: Double? = null) {
        camera = MapCamera(lat, lon, zoom ?: camera.zoom)
    }
}

@Composable
fun MapCanvas(
    state: MapCanvasState,
    tracks: List<Track>,
    routes: List<Route>,
    markers: List<Marker>,
    locationPoint: TrackPoint?,
    elevationGrid: ElevationGrid?,
    selectedPoint: TrackPoint?,
    onMapTap: ((lat: Double, lon: Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val canvasSizeState = remember { mutableStateOf(IntSize.Zero) }
    val requestedTiles = remember { mutableStateMapOf<String, Boolean>() }
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    Canvas(modifier = modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    if (onMapTap != null && size != IntSize.Zero) {
                        val projection = state.project(size.width.toFloat(), size.height.toFloat())
                        val (lat, lon) = projection.unproject(offset.x, offset.y)
                        onMapTap(lat, lon)
                    }
                },
            )
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                val projection = state.project(size.width.toFloat(), size.height.toFloat())
                val newZoom = (state.camera.zoom + if (gestureZoom > 1f) 0.25 else -0.25).coerceIn(2.0, 19.0)
                val center = projection.unproject(size.width / 2f - pan.x, size.height / 2f - pan.y)
                state.camera = MapCamera(center.first, center.second, newZoom)
            }
        }
    ) {
        canvasSizeState.value = IntSize(size.width.toInt(), size.height.toInt())
        val projection = state.project(size.width, size.height)
        drawRect(Color(0xFFE8E4DC))

        val z = projection.camera.zoomInt
        val tileX0 = kotlin.math.floor((0f - projection.centerOffsetX) / 256f).toInt()
        val tileX1 = kotlin.math.ceil((size.width - projection.centerOffsetX) / 256f).toInt()
        val tileY0 = kotlin.math.floor((0f - projection.centerOffsetY) / 256f).toInt()
        val tileY1 = kotlin.math.ceil((size.height - projection.centerOffsetY) / 256f).toInt()
        for (tx in tileX0..tileX1) {
            for (ty in tileY0..tileY1) {
                val key = "$z/$tx/$ty"
                val bitmap = state.tiles[key]
                if (bitmap != null) {
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(
                            (projection.centerOffsetX + tx * 256f).toInt(),
                            (projection.centerOffsetY + ty * 256f).toInt(),
                        ),
                        dstSize = IntSize(256, 256),
                    )
                } else if (requestedTiles[key] != true) {
                    requestedTiles[key] = true
                    val txCapture = tx
                    val tyCapture = ty
                    scope.launch {
                        val loaded = state.cache.tile(state.style, z, txCapture, tyCapture)
                        if (loaded != null) state.tiles[key] = loaded
                        else requestedTiles.remove(key)
                    }
                }
            }
        }

        elevationGrid?.let { grid -> drawElevationGrid(grid, projection) }

        for (track in tracks) {
            drawTrackLine(track.points, projection, Color(0xFF1565C0), 4f)
        }
        for (route in routes) {
            drawTrackLine(
                route.points.map { TrackPoint(it.latitude, it.longitude, it.elevation, 0L) },
                projection, Color(0xFF6A1B9A), 4f,
            )
        }
        for (marker in markers) {
            val pos = projection.project(marker.latitude, marker.longitude)
            if (pos.x < -50 || pos.y < -50 || pos.x > size.width + 50 || pos.y > size.height + 50) continue
            drawCircle(Color(0xFFD32F2F), radius = 8f, center = pos)
            drawCircle(Color.White, radius = 4f, center = pos)
        }
        selectedPoint?.let { point ->
            val pos = projection.project(point.latitude, point.longitude)
            drawCircle(Color(0xFFFFA000), radius = 10f, center = pos)
            drawCircle(Color.White, radius = 5f, center = pos)
        }
        locationPoint?.let { point ->
            val pos = projection.project(point.latitude, point.longitude)
            drawCircle(Color(0xFF1976D2).copy(alpha = 0.25f), radius = 24f, center = pos)
            drawCircle(Color(0xFF1976D2), radius = 9f, center = pos)
            drawCircle(Color.White, radius = 4f, center = pos)
        }
    }
}

private fun DrawScope.drawTrackLine(
    points: List<TrackPoint>,
    projection: MapProjection,
    color: Color,
    strokeWidth: Float,
) {
    if (points.size < 2) return
    val path = Path()
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
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawElevationGrid(
    grid: ElevationGrid,
    projection: MapProjection,
) {
    val cellW = (grid.cellSizeMeters / projection.metersPerPixel(grid.centerLat)).toFloat()
    val cellH = cellW
    val minE = grid.minElevation
    val maxE = grid.maxElevation
    val range = (maxE - minE).coerceAtLeast(1.0)
    for (row in grid.values.indices) {
        for (col in grid.values[row].indices) {
            val elevation = grid.values[row][col] ?: continue
            val lat = grid.topLat - row * grid.latStep
            val lon = grid.leftLon + col * grid.lonStep
            val pos = projection.project(lat, lon)
            val color = when (grid.mode) {
                "elevation" -> elevationColor((elevation - minE) / range)
                "slope" -> slopeColor(grid.slopeAt(row, col))
                "aspect" -> aspectColor(grid.aspectAt(row, col))
                "flat" -> if (grid.slopeAt(row, col) < 5.0) Color(0xFF4CAF50).copy(alpha = 0.45f) else Color.Transparent
                "minmax" -> when {
                    elevation >= maxE - range * 0.05 -> Color(0xFFE53935).copy(alpha = 0.5f)
                    elevation <= minE + range * 0.05 -> Color(0xFF1E88E5).copy(alpha = 0.5f)
                    else -> Color.Transparent
                }
                else -> Color.Transparent
            }
            if (color != Color.Transparent) {
                drawRect(color, topLeft = pos, size = Size(cellW, cellH))
            }
        }
    }
}

private fun elevationColor(t: Double): Color = when {
    t < 0.2 -> Color(0xFF81C784).copy(alpha = 0.45f)
    t < 0.4 -> Color(0xFFFFD54F).copy(alpha = 0.45f)
    t < 0.6 -> Color(0xFFFFB74D).copy(alpha = 0.45f)
    t < 0.8 -> Color(0xFFE57373).copy(alpha = 0.45f)
    else -> Color(0xFFBDBDBD).copy(alpha = 0.55f)
}

private fun slopeColor(deg: Double): Color = when {
    deg < 10 -> Color(0xFF66BB6A).copy(alpha = 0.5f)
    deg < 20 -> Color(0xFFFFEE58).copy(alpha = 0.5f)
    deg < 30 -> Color(0xFFFFA726).copy(alpha = 0.5f)
    deg < 40 -> Color(0xFFEF5350).copy(alpha = 0.5f)
    else -> Color(0xFF8E24AA).copy(alpha = 0.55f)
}

private fun aspectColor(deg: Double): Color = when {
    deg < 45 || deg >= 315 -> Color(0xFFEF5350).copy(alpha = 0.5f)
    deg < 135 -> Color(0xFF66BB6A).copy(alpha = 0.5f)
    deg < 225 -> Color(0xFF42A5F5).copy(alpha = 0.5f)
    else -> Color(0xFFFFCA28).copy(alpha = 0.5f)
}

class ElevationGrid(
    val centerLat: Double,
    val centerLon: Double,
    val topLat: Double,
    val leftLon: Double,
    val latStep: Double,
    val lonStep: Double,
    val cellSizeMeters: Double,
    val values: List<List<Double?>>,
    val mode: String,
) {
    val minElevation: Double get() = values.flatten().filterNotNull().minOrNull() ?: 0.0
    val maxElevation: Double get() = values.flatten().filterNotNull().maxOrNull() ?: 0.0

    fun slopeAt(row: Int, col: Int): Double {
        val left = values.getOrNull(row)?.getOrNull(col - 1) ?: return 0.0
        val right = values.getOrNull(row)?.getOrNull(col + 1) ?: return 0.0
        val up = values.getOrNull(row - 1)?.getOrNull(col) ?: return 0.0
        val down = values.getOrNull(row + 1)?.getOrNull(col) ?: return 0.0
        val dzdx = (right - left) / (2.0 * cellSizeMeters)
        val dzdy = (down - up) / (2.0 * cellSizeMeters)
        return Math.toDegrees(kotlin.math.atan(kotlin.math.sqrt(dzdx * dzdx + dzdy * dzdy)))
    }

    fun aspectAt(row: Int, col: Int): Double {
        val left = values.getOrNull(row)?.getOrNull(col - 1) ?: return 0.0
        val right = values.getOrNull(row)?.getOrNull(col + 1) ?: return 0.0
        val up = values.getOrNull(row - 1)?.getOrNull(col) ?: return 0.0
        val down = values.getOrNull(row + 1)?.getOrNull(col) ?: return 0.0
        val dzdx = (right - left) / (2.0 * cellSizeMeters)
        val dzdy = (down - up) / (2.0 * cellSizeMeters)
        var aspect = Math.toDegrees(kotlin.math.atan2(-dzdx, dzdy))
        if (aspect < 0) aspect += 360.0
        return aspect
    }
}
