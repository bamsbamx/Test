package com.trailbook.core.elevation

import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport

class ElevationService(private val transport: HttpTransport) {

    suspend fun elevationAt(point: GeoPoint): Double? =
        elevationFor(listOf(point)).firstOrNull()

    suspend fun elevationFor(points: List<GeoPoint>): List<Double?> {
        if (points.isEmpty()) return emptyList()
        val coords = points.joinToString("|") { "${it.latitude},${it.longitude}" }
        val url = "https://api.open-meteo.com/v1/elevation?latitude=${coords.take(900)}"
        val response = transport.execute(HttpRequest(url))
        if (!response.isSuccessful) throw HttpTransport.HttpException(response.status, "Elevation request failed: ${response.status}")
        val text = response.text()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return List(points.size) { null as Double? }
        val inner = text.substring(start + 1, end)
        return inner.split(',').map { it.trim().toDoubleOrNull() }
    }
}
