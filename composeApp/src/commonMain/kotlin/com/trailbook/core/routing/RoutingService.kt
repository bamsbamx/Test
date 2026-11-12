package com.trailbook.core.routing

import com.trailbook.core.elevation.ElevationService
import com.trailbook.core.geo.GeoMath
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.RoutePoint
import com.trailbook.core.model.RoutingProfile
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport
import kotlinx.serialization.json.*

data class RoutingResult(
    val points: List<RoutePoint>,
    val distanceMeters: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val provider: String,
)

class RoutingUnavailableException(message: String) : Exception(message)

class RoutingService(
    private val transport: HttpTransport,
    private val elevation: ElevationService? = null,
) {
    suspend fun route(waypoints: List<GeoPoint>, profile: RoutingProfile): RoutingResult {
        if (waypoints.size < 2) throw RoutingUnavailableException("Need at least two waypoints")
        val profileSlug = when (profile) {
            RoutingProfile.HIKING, RoutingProfile.WALKING, RoutingProfile.RUNNING -> "foot"
            RoutingProfile.CYCLING, RoutingProfile.ROAD_CYCLING, RoutingProfile.MOUNTAIN_BIKING -> "bike"
        }
        val coords = waypoints.joinToString(";") { "${it.longitude},${it.latitude}" }
        val url = "https://router.project-osrm.org/route/v1/$profileSlug/$coords?overview=full&geometries=geojson"
        val response = try {
            transport.execute(HttpRequest(url))
        } catch (e: Exception) {
            throw RoutingUnavailableException("Routing service unreachable: ${e.message}")
        }
        if (!response.isSuccessful) throw RoutingUnavailableException("Routing service error: ${response.status}")
        val parsed = parse(response.text())
        val withElevation = if (elevation != null && parsed.points.isNotEmpty()) {
            try {
                val els = elevation.elevationFor(parsed.points.take(100).map { it.toGeoPoint() })
                parsed.points.mapIndexed { i, rp -> rp.copy(elevation = els.getOrNull(i) ?: rp.elevation) }
            } catch (e: Exception) {
                parsed.points
            }
        } else {
            parsed.points
        }
        val gain = withElevation.mapNotNull { it.elevation }
        var up = 0.0; var down = 0.0
        for (i in 1 until gain.size) {
            val d = gain[i] - gain[i - 1]
            if (d > 0) up += d else down -= d
        }
        return RoutingResult(withElevation, parsed.distanceMeters, up, down, "OSRM")
    }

    internal fun parse(json: String): RoutingResult {
        val root = Json.parseToJsonElement(json).jsonObject
        val code = root["code"]?.jsonPrimitive?.contentOrNull
        if (code != "Ok") throw RoutingUnavailableException("Routing failed: $code")
        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw RoutingUnavailableException("No route found")
        val distance = route["distance"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val coords = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: JsonArray(emptyList())
        val points = coords.mapNotNull { c ->
            val a = c.jsonArray
            val lon = a.getOrNull(0)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = a.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val p = GeoPoint.tryCreate(lat, lon) ?: return@mapNotNull null
            RoutePoint(latitude = p.latitude, longitude = p.longitude)
        }
        return RoutingResult(points, distance, 0.0, 0.0, "OSRM")
    }

    companion object {
        fun routeFromTrack(points: List<com.trailbook.core.model.TrackPoint>, name: String): List<RoutePoint> =
            points.map { RoutePoint(it.latitude, it.longitude, it.elevation) }

        fun distanceOf(points: List<RoutePoint>): Double {
            var d = 0.0
            for (i in 1 until points.size) {
                d += GeoMath.haversineMeters(points[i - 1].toGeoPoint(), points[i].toGeoPoint())
            }
            return d
        }
    }
}
