package com.trailbook.app.ui.map

import androidx.compose.ui.graphics.ImageBitmap
import com.trailbook.core.geo.TileMath
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport
import com.trailbook.core.prefs.MapStyle
import com.trailbook.platform.AppFileSystem
import com.trailbook.platform.ImageCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TileCache(
    private val transport: HttpTransport,
    private val fileSystem: AppFileSystem,
    private val diskCacheDir: String,
) {
    private val memory = LinkedHashMap<String, ImageBitmap>()
    private val inFlight = mutableMapOf<String, Boolean>()
    private val mutex = Mutex()

    suspend fun tile(style: MapStyle, zoom: Int, x: Int, y: Int): ImageBitmap? {
        val key = "${style.id}/$zoom/$x/$y"
        mutex.withLock {
            val cached = memory[key]
            if (cached != null) return cached
            if (inFlight[key] == true) return null
            inFlight[key] = true
        }
        try {
            val diskPath = "$diskCacheDir/${style.id}_${zoom}_${x}_$y.png"
            val disk = fileSystem.readBytes(diskPath)
            if (disk != null) {
                val bitmap = ImageCodec.decode(disk)
                if (bitmap != null) {
                    remember(key, bitmap)
                    return bitmap
                }
            }
            if (zoom > style.maxZoom || x < 0 || y < 0 || x >= (1 shl zoom) || y >= (1 shl zoom)) return null
            val url = style.urlPattern
                .replace("{z}", zoom.toString())
                .replace("{x}", x.toString())
                .replace("{y}", y.toString())
            val response = try {
                transport.execute(HttpRequest(url, headers = mapOf("User-Agent" to "TrailBook/1.0")))
            } catch (e: Exception) {
                return null
            }
            if (!response.isSuccessful) return null
            val bitmap = ImageCodec.decode(response.body) ?: return null
            try {
                fileSystem.writeBytes(diskPath, response.body)
            } catch (e: Exception) {
            }
            remember(key, bitmap)
            return bitmap
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }

    @Synchronized
    private fun remember(key: String, bitmap: ImageBitmap) {
        memory[key] = bitmap
        while (memory.size > 256) {
            val eldest = memory.keys.firstOrNull() ?: break
            memory.remove(eldest)
        }
    }
}

data class MapCamera(
    val centerLat: Double = 47.3769,
    val centerLon: Double = 8.5417,
    val zoom: Double = 12.0,
) {
    val zoomInt: Int get() = zoom.toInt().coerceIn(2, 20)
}

data class MapProjection(
    val camera: MapCamera,
    val widthPx: Float,
    val heightPx: Float,
) {
    private val z = camera.zoomInt
    private val centerTileX = TileMath.lonToTileX(camera.centerLon, z)
    private val centerTileY = TileMath.latToTileY(camera.centerLat, z)
    private val scale = (1 shl z) * 256f

    val centerOffsetX = widthPx / 2f - (centerTileX * 256f).toFloat()
    val centerOffsetY = heightPx / 2f - (centerTileY * 256f).toFloat()

    fun project(lat: Double, lon: Double): androidx.compose.ui.geometry.Offset {
        val tx = (TileMath.lonToTileX(lon, z) * 256f).toFloat() + centerOffsetX
        val ty = (TileMath.latToTileY(lat, z) * 256f).toFloat() + centerOffsetY
        return androidx.compose.ui.geometry.Offset(tx, ty)
    }

    fun unproject(x: Float, y: Float): Pair<Double, Double> {
        val tileX = ((x - centerOffsetX) / 256f).toDouble()
        val tileY = ((y - centerOffsetY) / 256f).toDouble()
        return TileMath.tileXToLon(tileX, z) to TileMath.tileYToLat(tileY, z)
    }

    fun metersPerPixel(lat: Double): Double = TileMath.metersPerPixel(lat, z)
}
