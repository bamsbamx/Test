package com.trailbook.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val name: String? = null,
)

@Serializable
data class HourlyWeather(
    val time: Long,
    val temperatureC: Double,
    val apparentTemperatureC: Double? = null,
    val precipitationMm: Double = 0.0,
    val precipitationProbability: Int? = null,
    val snowfallCm: Double = 0.0,
    val windSpeedKmh: Double = 0.0,
    val windGustsKmh: Double? = null,
    val windDirectionDeg: Double? = null,
    val humidityPercent: Int? = null,
    val pressureHpa: Double? = null,
    val cloudCoverPercent: Int? = null,
    val weatherCode: Int = 0,
    val freezingLevelM: Double? = null,
    val cloudBaseM: Double? = null,
)

@Serializable
data class DailyWeather(
    val time: Long,
    val tempMaxC: Double,
    val tempMinC: Double,
    val precipitationMm: Double = 0.0,
    val precipitationProbability: Int? = null,
    val snowfallCm: Double = 0.0,
    val windSpeedMaxKmh: Double = 0.0,
    val weatherCode: Int = 0,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    val uvIndexMax: Double? = null,
)

@Serializable
data class WeatherForecast(
    val location: WeatherLocation,
    val model: String,
    val fetchedAt: Long,
    val current: HourlyWeather? = null,
    val hourly: List<HourlyWeather> = emptyList(),
    val daily: List<DailyWeather> = emptyList(),
    val moonPhase: MoonPhase? = null,
)

@Serializable
data class MoonPhase(val name: String, val illuminationPercent: Int, val ageDays: Double)

object WeatherCodes {
    fun description(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snowfall"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }
}

object MoonPhaseCalculator {
    fun compute(epochMillis: Long): MoonPhase {
        val synodic = 29.530588853
        val knownNewMoon = 2451550.09766
        val jd = epochMillis / 86400000.0 + 2440587.5
        var age = (jd - knownNewMoon) % synodic
        if (age < 0) age += synodic
        val fraction = age / synodic
        val illum = ((1 - kotlin.math.cos(2 * Math.PI * fraction)) / 2 * 100).toInt()
        val name = when {
            age < 1.84566 || age > 27.68493 -> "New moon"
            age < 7.38265 -> "Waxing crescent"
            age < 9.22821 -> "First quarter"
            age < 14.77256 -> "Waxing gibbous"
            age < 16.61096 -> "Full moon"
            age < 22.24827 -> "Waning gibbous"
            age < 24.09383 -> "Last quarter"
            else -> "Waning crescent"
        }
        return MoonPhase(name, illum, age)
    }
}
