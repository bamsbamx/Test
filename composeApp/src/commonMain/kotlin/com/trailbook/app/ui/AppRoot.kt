package com.trailbook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.trailbook.app.AppContainer
import com.trailbook.app.createAppContainer

enum class AppScreen { MAP, RECORD, TRACKS, ROUTES, MARKERS, WEATHER, SENSORS, SETTINGS, ACCOUNT, MORE }

@Composable
fun App() {
    val container = remember { createAppContainer() }
    val settings by container.settings.collectAsState()
    TrailBookTheme(themeMode = settings.themeMode) {
        AppRoot(container)
    }
}

@Composable
fun AppRoot(container: AppContainer) {
    var screen by remember { mutableStateOf(AppScreen.MAP) }
    var selectedTrackId by remember { mutableStateOf<String?>(null) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }
    val settings by container.settings.collectAsState()

    Scaffold(
        bottomBar = {
            BottomNav(screen = screen, onSelect = { destination ->
                selectedTrackId = null
                selectedRouteId = null
                screen = destination
            })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.MAP -> MapScreen(container)
                AppScreen.RECORD -> RecordScreen(container)
                AppScreen.TRACKS -> {
                    val trackId = selectedTrackId
                    if (trackId != null) {
                        TrackDetailScreen(
                            container = container,
                            trackId = trackId,
                            onBack = { selectedTrackId = null },
                            onFollow = { selectedRouteId = null; screen = AppScreen.ROUTES },
                        )
                    } else {
                        TracksScreen(container, onOpenTrack = { selectedTrackId = it })
                    }
                }
                AppScreen.ROUTES -> {
                    val routeId = selectedRouteId
                    if (routeId != null) {
                        NavigationScreen(container, routeId, onBack = { selectedRouteId = null })
                    } else {
                        RoutesScreen(container, onFollowRoute = { selectedRouteId = it })
                    }
                }
                AppScreen.MARKERS -> MarkersScreen(container)
                AppScreen.WEATHER -> WeatherScreen(container)
                AppScreen.SENSORS -> SensorsScreen(container)
                AppScreen.SETTINGS -> SettingsScreen(container)
                AppScreen.ACCOUNT -> AccountScreen(container)
                AppScreen.MORE -> MoreScreen(container, onNavigate = { screen = it })
            }
        }
    }
}

@Composable
private fun BottomNav(screen: AppScreen, onSelect: (AppScreen) -> Unit) {
    val items = listOf(
        AppScreen.MAP to "Map",
        AppScreen.RECORD to "Record",
        AppScreen.TRACKS to "Tracks",
        AppScreen.ROUTES to "Routes",
        AppScreen.MORE to "More",
    )
    NavigationBar {
        for ((destination, label) in items) {
            NavigationBarItem(
                selected = screen == destination || (destination == AppScreen.MORE && screen in moreScreens),
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = when (destination) {
                            AppScreen.MAP -> Icons.Filled.Map
                            AppScreen.RECORD -> Icons.Filled.MyLocation
                            AppScreen.TRACKS -> Icons.Filled.Timeline
                            AppScreen.ROUTES -> Icons.Filled.Route
                            else -> Icons.Filled.MoreHoriz
                        },
                        contentDescription = label,
                    )
                },
                label = { Text(label) },
            )
        }
    }
}

private val moreScreens = setOf(
    AppScreen.MARKERS, AppScreen.WEATHER, AppScreen.SENSORS, AppScreen.SETTINGS, AppScreen.ACCOUNT
)

@Composable
fun MoreScreen(container: AppContainer, onNavigate: (AppScreen) -> Unit) {
    val rows = listOf(
        Triple(AppScreen.MARKERS, "Markers", "Marker lists, icons, and map collection"),
        Triple(AppScreen.WEATHER, "Weather", "Hourly and daily forecasts, freezing level, moon"),
        Triple(AppScreen.SENSORS, "Sensors", "Compass, pressure, tilt, magnetic field"),
        Triple(AppScreen.ACCOUNT, "Account", "Sign in, sync, Suunto"),
        Triple(AppScreen.SETTINGS, "Settings", "Units, map, recording, charts, purchases, about"),
    )
    Column(Modifier.fillMaxSize()) {
        for ((destination, title, subtitle) in rows) {
            androidx.compose.material3.ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(subtitle) },
                leadingContent = {
                    Icon(
                        when (destination) {
                            AppScreen.MARKERS -> Icons.Filled.Place
                            AppScreen.WEATHER -> Icons.Filled.Map
                            AppScreen.SENSORS -> Icons.Filled.MyLocation
                            AppScreen.ACCOUNT -> Icons.Filled.Place
                            else -> Icons.Filled.Settings
                        },
                        contentDescription = title,
                    )
                },
                modifier = Modifier.fillMaxWidth().clickable { onNavigate(destination) },
            )
        }
    }
}
