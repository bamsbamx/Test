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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailbook.app.AppContainer
import com.trailbook.app.SystemTime
import com.trailbook.core.format.UnitFormatter
import com.trailbook.core.model.WeatherCodes
import com.trailbook.core.model.WeatherLocation
import com.trailbook.core.weather.WeatherService
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

@Composable
fun WeatherScreen(container: AppContainer) {
    val settings by container.settings.collectAsState()
    val formatter = UnitFormatter(settings.units)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }
    var altText by remember { mutableStateOf("") }
    var forecast by remember { mutableStateOf<com.trailbook.core.model.WeatherForecast?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Weather", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = latText, onValueChange = { latText = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = lonText, onValueChange = { lonText = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = altText, onValueChange = { altText = it }, label = { Text("Alt m") }, modifier = Modifier.weight(1f))
        }
        Text("Forecast model", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((id, label) in WeatherService.MODELS.take(4)) {
                FilterChip(
                    selected = settings.weather.modelId == id,
                    onClick = { container.updateSettings { it.copy(weather = it.weather.copy(modelId = id)) } },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Button(onClick = {
            val lat = latText.toDoubleOrNull()
            val lon = lonText.toDoubleOrNull()
            val alt = altText.toDoubleOrNull()
            if (lat == null || lon == null) {
                error = "Enter valid latitude and longitude"
                return@Button
            }
            loading = true
            error = null
            scope.launch {
                try {
                    forecast = container.weatherService.forecast(
                        WeatherLocation(lat, lon, alt), settings.weather.modelId
                    )
                } catch (e: Exception) {
                    error = "Forecast unavailable: ${e.message}"
                }
                loading = false
            }
        }) { Text(if (loading) "Loading..." else "Get forecast") }

        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        val f = forecast
        if (f != null) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Hourly") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Daily") })
            }
            Text(
                "Model: ${f.model} - ${WeatherCodes.description(f.current?.weatherCode ?: 0)}" +
                    (f.moonPhase?.let { " - Moon: ${it.name} (${it.illuminationPercent}%)" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            if (tab == 0) {
                LazyColumn {
                    items(f.hourly.take(48)) { hour ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(formatClock(hour.time), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                            Text(WeatherCodes.description(hour.weatherCode), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                            Text(formatter.temperature(hour.temperatureC), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${com.trailbook.core.format.NumFormat.fixed(hour.precipitationMm, 1)} mm", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(hour.freezingLevelM?.let { "${com.trailbook.core.format.NumFormat.fixed(it, 0)}m" } ?: "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(hour.cloudBaseM?.let { "${com.trailbook.core.format.NumFormat.fixed(it, 0)}m" } ?: "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                LazyColumn {
                    items(f.daily) { day ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(formatDay(day.time), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${WeatherCodes.description(day.weatherCode)} - ${formatter.temperature(day.tempMinC)} to ${formatter.temperature(day.tempMaxC)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Precip ${com.trailbook.core.format.NumFormat.fixed(day.precipitationMm, 1)} mm" +
                                        (day.precipitationProbability?.let { " (${it}%)" } ?: "") +
                                        " - Snow ${com.trailbook.core.format.NumFormat.fixed(day.snowfallCm, 1)} cm" +
                                        " - Wind max ${com.trailbook.core.format.NumFormat.fixed(day.windSpeedMaxKmh, 0)} km/h" +
                                        (day.uvIndexMax?.let { " - UV ${com.trailbook.core.format.NumFormat.fixed(it, 1)}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (day.sunrise != null && day.sunset != null) {
                                    Text(
                                        "Sunrise ${formatClock(day.sunrise)} - Sunset ${formatClock(day.sunset)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatDay(epochMillis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
    val date = instant.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
    return date.toString()
}
