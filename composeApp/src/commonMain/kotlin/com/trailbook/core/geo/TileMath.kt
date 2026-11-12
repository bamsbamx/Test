package com.trailbook.core.geo

import kotlin.math.*

object TileMath {
    const val TILE_SIZE = 256

    fun lonToTileX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * (1 shl zoom)

    fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        return (1.0 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    fun tileXToLon(x: Double, zoom: Int): Double = x / (1 shl zoom) * 360.0 - 180.0

    fun tileYToLat(y: Double, zoom: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl zoom)
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }

    fun metersPerPixel(lat: Double, zoom: Int): Double =
        156543.03392 * cos(Math.toRadians(lat)) / (1 shl zoom)

    fun zoomForBounds(widthPx: Double, heightPx: Double, box: BoundingBox, paddingPx: Double = 32.0): Int {
        var zoom = 20
        while (zoom > 2) {
            val x1 = lonToTileX(box.minLon, zoom) * TILE_SIZE
            val x2 = lonToTileX(box.maxLon, zoom) * TILE_SIZE
            val y1 = latToTileY(box.maxLat, zoom) * TILE_SIZE
            val y2 = latToTileY(box.minLat, zoom) * TILE_SIZE
            if (abs(x2 - x1) <= widthPx - paddingPx * 2 && abs(y2 - y1) <= heightPx - paddingPx * 2) return zoom
            zoom--
        }
        return zoom
    }
}
