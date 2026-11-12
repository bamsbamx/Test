package com.trailbook.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: Long,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val accuracyM: Double? = null,
    val heartRate: Int? = null,
) {
    fun toGeoPoint() = com.trailbook.core.geo.GeoPoint(latitude, longitude)
}

enum class ActivityType(val id: String, val label: String) {
    HIKING("hiking", "Hiking"),
    WALKING("walking", "Walking"),
    RUNNING("running", "Running"),
    TRAIL_RUNNING("trail_running", "Trail running"),
    CYCLING("cycling", "Cycling"),
    MOUNTAIN_BIKING("mountain_biking", "Mountain biking"),
    MOUNTAINEERING("mountaineering", "Mountaineering"),
    SKI_TOURING("ski_touring", "Ski touring"),
    SKIING("skiing", "Skiing"),
    SNOWSHOEING("snowshoeing", "Snowshoeing"),
    CLIMBING("climbing", "Climbing"),
    SAILING("sailing", "Sailing"),
    KAYAKING("kayaking", "Kayaking"),
    DRIVING("driving", "Driving"),
    OTHER("other", "Other");

    companion object {
        fun byId(id: String?): ActivityType = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

@Serializable
data class TrackStats(
    val distanceMeters: Double,
    val totalTimeSeconds: Double,
    val movingTimeSeconds: Double,
    val maxSpeedMps: Double,
    val avgSpeedMps: Double,
    val avgMovingSpeedMps: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val minElevation: Double?,
    val maxElevation: Double?,
    val startedAt: Long,
    val endedAt: Long,
    val pointCount: Int,
)

@Serializable
data class Track(
    val id: String,
    val name: String,
    val activityType: ActivityType = ActivityType.HIKING,
    val points: List<TrackPoint> = emptyList(),
    val description: String? = null,
    val notes: String? = null,
    val difficulty: String? = null,
    val surface: String? = null,
    val createdAt: Long,
    val modifiedAt: Long = createdAt,
    val source: String? = null,
    val stats: TrackStats? = null,
    val syncedAt: Long? = null,
    val remoteId: String? = null,
) {
    val geoPoints: List<com.trailbook.core.geo.GeoPoint>
        get() = points.map { it.toGeoPoint() }
}

@Serializable
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val name: String? = null,
    val instruction: String? = null,
    val distanceFromStartM: Double? = null,
) {
    fun toGeoPoint() = com.trailbook.core.geo.GeoPoint(latitude, longitude)
}

@Serializable
data class Route(
    val id: String,
    val name: String,
    val profile: RoutingProfile = RoutingProfile.HIKING,
    val points: List<RoutePoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val elevationGain: Double = 0.0,
    val elevationLoss: Double = 0.0,
    val createdAt: Long,
    val modifiedAt: Long = createdAt,
    val fromTrackId: String? = null,
    val syncedAt: Long? = null,
    val remoteId: String? = null,
)

enum class RoutingProfile(val id: String, val label: String) {
    HIKING("hiking", "Hiking"),
    RUNNING("running", "Running"),
    CYCLING("cycling", "Cycling"),
    ROAD_CYCLING("road_cycling", "Road cycling"),
    MOUNTAIN_BIKING("mountain_biking", "Mountain biking"),
    WALKING("walking", "Walking");

    companion object {
        fun byId(id: String?): RoutingProfile = entries.firstOrNull { it.id == id } ?: HIKING
    }
}
