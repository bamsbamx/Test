package com.trailbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.core.format.UnitFormatter
import com.trailbook.core.routing.GuidanceEvent
import com.trailbook.core.routing.RouteFollower
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun NavigationScreen(container: AppContainer, routeId: String, onBack: () -> Unit) {
    val settings by container.settings.collectAsState()
    val formatter = UnitFormatter(settings.units)
    val scope = rememberCoroutineScope()
    val route = remember { container.routeRepository.getById(routeId) }
    var follower by remember { mutableStateOf<RouteFollower?>(null) }
    var followState by remember { mutableStateOf<com.trailbook.core.routing.FollowState?>(null) }
    var guidance by remember { mutableStateOf<String?>(null) }
    var locationJob by remember { mutableStateOf<Job?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    if (route == null || route.points.size < 2) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("This route has fewer than two points and cannot be followed.")
        }
        return
    }

    DisposableEffect(route.id) {
        onDispose { locationJob?.cancel() }
    }

    fun followUpdates() {
        locationJob?.cancel()
        locationJob = scope.launch {
            val status = container.permissionManager.request(container.locationProvider.permissionType())
            if (status != com.trailbook.platform.PermissionStatus.GRANTED) {
                locationError = "Location permission ${status.name.lowercase()}. Guidance needs a GPS position."
                return@launch
            }
            locationError = null
            container.locationProvider.updates(1500).collect { update ->
                when (update) {
                    is com.trailbook.platform.LocationUpdate.Success -> {
                        val f = follower ?: return@collect
                        val (state, events) = f.onLocation(update.point)
                        followState = state
                        for (event in events) {
                            guidance = when (event) {
                                GuidanceEvent.OFF_ROUTE -> "Off route! Deviation ${formatter.shortDistance(state.deviationMeters)}"
                                GuidanceEvent.BACK_ON_ROUTE -> "Back on route"
                                GuidanceEvent.WAYPOINT_REACHED -> "Waypoint reached"
                                GuidanceEvent.ROUTE_COMPLETED -> "Route completed"
                                GuidanceEvent.PROXIMITY_ALERT -> "Proximity alert"
                            }
                            if (event == GuidanceEvent.OFF_ROUTE || event == GuidanceEvent.ROUTE_COMPLETED) {
                                if (settings.navigation.guidanceSound) container.feedbackPlayer.playSound()
                                if (settings.navigation.guidanceVibration) container.feedbackPlayer.vibrate()
                            }
                        }
                    }
                    is com.trailbook.platform.LocationUpdate.Denied -> locationError = update.message
                    is com.trailbook.platform.LocationUpdate.Unavailable -> locationError = update.message
                    is com.trailbook.platform.LocationUpdate.Failed -> locationError = update.message
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(route.name, style = MaterialTheme.typography.titleLarge)
        }
        val active = follower != null
        val state = followState
        if (!active) {
            Text(
                "${route.profile.label} - ${formatter.longDistance(route.distanceMeters)} - ${route.points.size} points",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                val f = RouteFollower(
                    route.points,
                    settings.navigation.offRouteThresholdMeters,
                    settings.navigation.proximityAlertMeters,
                )
                follower = f
                followState = f.start()
                guidance = null
                followUpdates()
            }) { Text("Start following") }
        } else {
            if (state != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when {
                                state.completed -> "Route completed"
                                state.offRoute -> "Off route"
                                else -> "Following route"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                state.completed -> MaterialTheme.colorScheme.primary
                                state.offRoute -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (route.distanceMeters > 0) {
                                    (state.progressMeters / route.distanceMeters).toFloat().coerceIn(0f, 1f)
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Progress: ${formatter.longDistance(state.progressMeters)}", style = MaterialTheme.typography.bodySmall)
                        Text("Remaining: ${formatter.longDistance(state.remainingMeters)}", style = MaterialTheme.typography.bodySmall)
                        state.nextPoint?.let { next ->
                            Text(
                                "Next: ${next.name ?: "waypoint"} at ${state.nextPointDistanceMeters?.let { formatter.shortDistance(it) }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            state.bearingToNextDeg?.let {
                                Text("Bearing: ${com.trailbook.core.format.NumFormat.fixed(it, 0)}\u00b0", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (guidance != null) {
                Text(guidance!!, color = MaterialTheme.colorScheme.tertiary)
            }
            if (locationError != null) {
                Text(locationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButtonCompat {
                follower?.stop()
                follower = null
                followState = null
                locationJob?.cancel()
                guidance = "Guidance stopped"
            }
        }
    }
}

@Composable
private fun TextButtonCompat(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { Text("Stop guidance") }
}
