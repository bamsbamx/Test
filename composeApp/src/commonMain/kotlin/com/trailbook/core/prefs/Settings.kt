package com.trailbook.core.prefs

import kotlinx.serialization.Serializable

enum class UnitsSystem(val id: String, val label: String) {
    METRIC("metric", "Metric"),
    IMPERIAL("imperial", "Imperial");

    companion object { fun byId(id: String?): UnitsSystem = entries.firstOrNull { it.id == id } ?: METRIC }
}

enum class MapStyle(val id: String, val label: String, val urlPattern: String, val maxZoom: Int, val attribution: String) {
    STANDARD("standard", "Standard (OSM)",
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png", 19, "© OpenStreetMap contributors"),
    LIGHT("light", "Light",
        "https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png", 20, "© OpenStreetMap contributors, © CARTO"),
    DARK("dark", "Dark",
        "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png", 20, "© OpenStreetMap contributors, © CARTO"),
    TERRAIN("terrain", "Terrain",
        "https://tile.opentopomap.org/{z}/{x}/{y}.png", 17, "© OpenStreetMap contributors, SRTM | © OpenTopoMap"),
    SATELLITE("satellite", "Satellite",
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", 19,
        "© Esri, Maxar, Earthstar Geographics");

    companion object { fun byId(id: String?): MapStyle = entries.firstOrNull { it.id == id } ?: STANDARD }
}

enum class MapLayer(val id: String, val label: String) {
    ASPECT("aspect", "Aspect"),
    SLOPE_ANGLE("slope", "Slope angle"),
    AVALANCHE_RISK("avalanche", "Avalanche risk"),
    ELEVATION("elevation", "Elevation shading"),
    MIN_MAX_ELEVATION("minmax", "Min/max elevation"),
    FLAT_AREAS("flat", "Flat areas");

    companion object { fun byId(id: String?): MapLayer = entries.firstOrNull { it.id == id } ?: SLOPE_ANGLE }
}

@Serializable
data class RecordingSettings(
    val minAccuracyMeters: Double = 25.0,
    val minDistanceMeters: Double = 3.0,
    val minIntervalSeconds: Int = 2,
    val autoPauseEnabled: Boolean = false,
    val autoPauseSpeedKmh: Double = 1.0,
    val keepScreenOn: Boolean = true,
    val soundIndications: Boolean = true,
    val vibrationIndications: Boolean = true,
    val defaultActivityTypeId: String = "hiking",
    val backgroundRecording: Boolean = true,
)

@Serializable
data class ChartSettings(
    val smoothingWindow: Int = 5,
    val showSpeed: Boolean = true,
    val showElevation: Boolean = true,
    val showSlope: Boolean = false,
)

@Serializable
data class WeatherPreferences(
    val modelId: String = "best_match",
    val temperatureUnit: String = "celsius",
    val windSpeedUnit: String = "kmh",
    val precipitationUnit: String = "mm",
)

@Serializable
data class NavigationSettings(
    val offRouteThresholdMeters: Double = 40.0,
    val guidanceSound: Boolean = true,
    val guidanceVibration: Boolean = true,
    val proximityAlertMeters: Double = 50.0,
)

@Serializable
data class AppSettings(
    val units: UnitsSystem = UnitsSystem.METRIC,
    val mapStyleId: String = "standard",
    val enabledLayers: Set<String> = emptySet(),
    val showTracksOnMap: Boolean = true,
    val showMarkersOnMap: Boolean = true,
    val showRoutesOnMap: Boolean = true,
    val showUserLocation: Boolean = true,
    val showWeatherOnMap: Boolean = false,
    val coordinateFormatId: String = "dd",
    val recording: RecordingSettings = RecordingSettings(),
    val chart: ChartSettings = ChartSettings(),
    val weather: WeatherPreferences = WeatherPreferences(),
    val navigation: NavigationSettings = NavigationSettings(),
    val routingProfileId: String = "hiking",
    val defaultMarkerListId: String? = null,
    val recentSearches: List<String> = emptyList(),
    val fullVersionUnlocked: Boolean = false,
    val syncOnWifiOnly: Boolean = true,
    val themeMode: String = "system",
) {
    val mapStyle: MapStyle get() = MapStyle.byId(mapStyleId)
    val coordinateFormat: com.trailbook.core.geo.CoordinateDisplayFormat
        get() = com.trailbook.core.geo.CoordinateDisplayFormat.byId(coordinateFormatId)
    val routingProfile: com.trailbook.core.model.RoutingProfile
        get() = com.trailbook.core.model.RoutingProfile.byId(routingProfileId)
}
