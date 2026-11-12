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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.trailbook.core.format.UnitFormatter
import com.trailbook.core.geo.GeoPoint
import com.trailbook.core.model.Route
import com.trailbook.core.model.RoutePoint
import com.trailbook.core.model.RoutingProfile
import com.trailbook.core.routing.RoutingService
import com.trailbook.core.routing.RoutingUnavailableException
import kotlinx.coroutines.launch

@Composable
fun RoutesScreen(container: AppContainer, onFollowRoute: (String) -> Unit) {
    val settings by container.settings.collectAsState()
    val formatter = UnitFormatter(settings.units)
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var planning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val routes = remember(version) { container.routeRepository.getAll() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Routes", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { planning = !planning }) { Text(if (planning) "Close planner" else "Plan route") }
        }
        if (planning) {
            RoutePlanner(container, onSaved = {
                planning = false
                version++
                message = "Route saved"
            })
        }
        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary)
        if (routes.isEmpty()) {
            Text("No routes yet. Plan one with the routing service, or use an existing track as a route.")
        } else {
            LazyColumn {
                items(routes, key = { it.id }) { route ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onFollowRoute(route.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(route.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${route.profile.label} - ${formatter.longDistance(route.distanceMeters)} - ${route.points.size} points" +
                                    if (route.elevationGain > 0) " - up ${formatter.elevation(route.elevationGain)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row {
                                TextButton(onClick = {
                                    val track = com.trailbook.core.model.Track(
                                        id = com.trailbook.app.Ids.next(),
                                        name = route.name,
                                        activityType = ActivityTypeFor(route.profile),
                                        points = route.points.map {
                                            com.trailbook.core.model.TrackPoint(it.latitude, it.longitude, it.elevation, 0L)
                                        },
                                        createdAt = SystemTime.now(),
                                        source = "route",
                                        stats = com.trailbook.core.stats.StatisticsEngine.compute(
                                            route.points.map {
                                                com.trailbook.core.model.TrackPoint(it.latitude, it.longitude, it.elevation, 0L)
                                            }
                                        ),
                                    )
                                    container.trackRepository.save(track)
                                    container.routeRepository.delete(route.id)
                                    version++
                                    message = "Route saved as track \"${track.name}\""
                                }) { Text("Save as track") }
                                TextButton(onClick = {
                                    container.routeRepository.delete(route.id)
                                    version++
                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ActivityTypeFor(profile: RoutingProfile): com.trailbook.core.model.ActivityType = when (profile) {
    RoutingProfile.HIKING -> com.trailbook.core.model.ActivityType.HIKING
    RoutingProfile.WALKING -> com.trailbook.core.model.ActivityType.WALKING
    RoutingProfile.RUNNING -> com.trailbook.core.model.ActivityType.RUNNING
    RoutingProfile.CYCLING, RoutingProfile.ROAD_CYCLING, RoutingProfile.MOUNTAIN_BIKING -> com.trailbook.core.model.ActivityType.CYCLING
}

@Composable
private fun RoutePlanner(container: AppContainer, onSaved: () -> Unit) {
    val settings by container.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var waypoints by remember { mutableStateOf(listOf<GeoPoint>()) }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf(settings.routingProfile) }
    var planningState by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Plan a route", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Route name") }, modifier = Modifier.fillMaxWidth())
        Text("Profile", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (p in RoutingProfile.entries) {
                FilterChip(selected = profile == p, onClick = { profile = p }, label = { Text(p.label, style = MaterialTheme.typography.labelSmall) })
            }
        }
        Text("Waypoints (${waypoints.size})", style = MaterialTheme.typography.labelMedium)
        for ((index, wp) in waypoints.withIndex()) {
            Text(
                "${index + 1}. ${com.trailbook.core.format.NumFormat.fixed(wp.latitude, 5)}, ${com.trailbook.core.format.NumFormat.fixed(wp.longitude, 5)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = latText, onValueChange = { latText = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = lonText, onValueChange = { lonText = it }, label = { Text("Lon") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = {
                val lat = latText.toDoubleOrNull()
                val lon = lonText.toDoubleOrNull()
                val point = if (lat != null && lon != null) GeoPoint.tryCreate(lat, lon) else null
                if (point != null) {
                    waypoints = waypoints + point
                    latText = ""
                    lonText = ""
                } else {
                    planningState = "Invalid coordinates"
                }
            }) { Text("Add point") }
            OutlinedButton(onClick = {
                if (waypoints.isNotEmpty()) waypoints = waypoints.dropLast(1)
            }) { Text("Remove last") }
        }
        Button(onClick = {
            scope.launch {
                planningState = "Contacting routing service..."
                try {
                    val result = container.routingService.route(waypoints, profile)
                    container.routeRepository.save(
                        Route(
                            id = com.trailbook.app.Ids.next(),
                            name = name.ifBlank { "Route " + com.trailbook.core.format.NumFormat.fixed(result.distanceMeters / 1000.0, 1) + " km" },
                            profile = profile,
                            points = result.points,
                            distanceMeters = result.distanceMeters,
                            elevationGain = result.elevationGain,
                            elevationLoss = result.elevationLoss,
                            createdAt = SystemTime.now(),
                        )
                    )
                    onSaved()
                } catch (e: RoutingUnavailableException) {
                    planningState = "Routing unavailable: ${e.message}. You can still save a straight-line route."
                } catch (e: Exception) {
                    planningState = "Routing failed: ${e.message}"
                }
            }
        }) { Text("Route with OSRM and save") }
        OutlinedButton(onClick = {
            if (waypoints.size < 2) {
                planningState = "Add at least two waypoints"
                return@OutlinedButton
            }
            val points = waypoints.mapIndexed { i, p ->
                RoutePoint(p.latitude, p.longitude, null, "Point ${i + 1}")
            }
            container.routeRepository.save(
                Route(
                    id = com.trailbook.app.Ids.next(),
                    name = name.ifBlank { "Manual route" },
                    profile = profile,
                    points = points,
                    distanceMeters = RoutingService.distanceOf(points),
                    createdAt = SystemTime.now(),
                )
            )
            onSaved()
        }) { Text("Save straight-line route (offline)") }
        if (planningState != null) Text(planningState!!, style = MaterialTheme.typography.bodySmall)
    }
}
