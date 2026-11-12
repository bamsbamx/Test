package com.trailbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.trailbook.core.geo.CoordinateDisplayFormat
import com.trailbook.core.prefs.MapStyle
import com.trailbook.core.prefs.UnitsSystem
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var suuntoUser by remember { mutableStateOf(container.suuntoService.storedUsername() ?: "") }
    var suuntoPassword by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Units", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (units in UnitsSystem.entries) {
                FilterChip(
                    selected = settings.units == units,
                    onClick = { container.updateSettings { it.copy(units = units) } },
                    label = { Text(units.label) },
                )
            }
        }

        Text("Theme", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (mode in listOf("system" to "System", "light" to "Light", "dark" to "Dark")) {
                FilterChip(
                    selected = settings.themeMode == mode.first,
                    onClick = { container.updateSettings { it.copy(themeMode = mode.first) } },
                    label = { Text(mode.second) },
                )
            }
        }

        Text("Map", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (style in MapStyle.entries) {
                FilterChip(
                    selected = settings.mapStyleId == style.id,
                    onClick = { container.updateSettings { it.copy(mapStyleId = style.id) } },
                    label = { Text(style.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Text("Coordinate format", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (format in CoordinateDisplayFormat.entries) {
                FilterChip(
                    selected = settings.coordinateFormatId == format.id,
                    onClick = { container.updateSettings { it.copy(coordinateFormatId = format.id) } },
                    label = { Text(format.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        ToggleRow("Show tracks on map", settings.showTracksOnMap) { v -> container.updateSettings { it.copy(showTracksOnMap = v) } }
        ToggleRow("Show routes on map", settings.showRoutesOnMap) { v -> container.updateSettings { it.copy(showRoutesOnMap = v) } }
        ToggleRow("Show markers on map", settings.showMarkersOnMap) { v -> container.updateSettings { it.copy(showMarkersOnMap = v) } }

        Text("Recording", style = MaterialTheme.typography.titleSmall)
        ToggleRow("Background recording", settings.recording.backgroundRecording) { v ->
            container.updateSettings { it.copy(recording = it.recording.copy(backgroundRecording = v)) }
        }
        ToggleRow("Sound indications", settings.recording.soundIndications) { v ->
            container.updateSettings { it.copy(recording = it.recording.copy(soundIndications = v)) }
        }
        ToggleRow("Vibration indications", settings.recording.vibrationIndications) { v ->
            container.updateSettings { it.copy(recording = it.recording.copy(vibrationIndications = v)) }
        }

        Text("Charts", style = MaterialTheme.typography.titleSmall)
        ToggleRow("Show elevation chart", settings.chart.showElevation) { v ->
            container.updateSettings { it.copy(chart = it.chart.copy(showElevation = v)) }
        }
        ToggleRow("Show speed chart", settings.chart.showSpeed) { v ->
            container.updateSettings { it.copy(chart = it.chart.copy(showSpeed = v)) }
        }

        Text("Navigation", style = MaterialTheme.typography.titleSmall)
        ToggleRow("Guidance sound", settings.navigation.guidanceSound) { v ->
            container.updateSettings { it.copy(navigation = it.navigation.copy(guidanceSound = v)) }
        }
        ToggleRow("Guidance vibration", settings.navigation.guidanceVibration) { v ->
            container.updateSettings { it.copy(navigation = it.navigation.copy(guidanceVibration = v)) }
        }

        Text("Full version", style = MaterialTheme.typography.titleSmall)
        Text(
            if (settings.fullVersionUnlocked) "Full version is unlocked." else "Free version. Unlock the full version for unlimited sync.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    when (val result = container.billing.purchase()) {
                        is com.trailbook.core.sync.PurchaseResult.Unlocked ->
                            message = if (result.testMode) "Purchased (test mode: no store credentials configured)" else "Purchased"
                        is com.trailbook.core.sync.PurchaseResult.Failed -> message = result.message
                    }
                }
            }) { Text("Purchase") }
            OutlinedButton(onClick = {
                scope.launch {
                    when (val result = container.billing.restore()) {
                        is com.trailbook.core.sync.PurchaseResult.Unlocked ->
                            message = if (result.testMode) "Restored (test mode: no store credentials configured)" else "Restored"
                        is com.trailbook.core.sync.PurchaseResult.Failed -> message = result.message
                    }
                }
            }) { Text("Restore") }
        }

        Text("Suunto", style = MaterialTheme.typography.titleSmall)
        val suuntoConnected = container.suuntoService.hasCredentials()
        Text(
            if (suuntoConnected) "Connected as ${container.suuntoService.storedUsername()}"
            else "Not connected. Suunto Cloud API requires OAuth client credentials that are not bundled with the app.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = suuntoUser, onValueChange = { suuntoUser = it }, label = { Text("Suunto username") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = suuntoPassword, onValueChange = { suuntoPassword = it }, label = { Text("Password") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    when (val result = container.suuntoService.connect(suuntoUser, suuntoPassword)) {
                        is com.trailbook.core.sync.SuuntoResult.Connected -> message = "Suunto connected"
                        com.trailbook.core.sync.SuuntoResult.NotConfigured -> message = "Enter username and password"
                        is com.trailbook.core.sync.SuuntoResult.Failed -> message = result.message
                    }
                }
            }) { Text("Connect") }
            if (suuntoConnected) {
                OutlinedButton(onClick = {
                    container.suuntoService.disconnect()
                    suuntoUser = ""
                    message = "Suunto disconnected"
                }) { Text("Disconnect") }
            }
        }

        Text("About", style = MaterialTheme.typography.titleSmall)
        Text("TrailBook 1.0.0 - offline-first outdoor tracking built with Kotlin and Compose Multiplatform.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkButton("Licenses", "https://www.openstreetmap.org/copyright") { message = it }
            LinkButton("Privacy", "https://www.openstreetmap.org/privacy") { message = it }
            LinkButton("Terms", "https://www.openstreetmap.org/copyright") { message = it }
            LinkButton("Contact", "https://www.openstreetmap.org/contact") { message = it }
        }
        Text(
            "Map data " + com.trailbook.core.prefs.MapStyle.STANDARD.attribution,
            style = MaterialTheme.typography.bodySmall,
        )

        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkButton(label: String, url: String, onMessage: (String) -> Unit) {
    TextButton(onClick = { onMessage(label + ": " + url) }) { Text(label) }
}
