package com.trailbook.core.weather

import com.trailbook.core.model.DailyWeather
import com.trailbook.core.model.HourlyWeather
import com.trailbook.core.model.MoonPhaseCalculator
import com.trailbook.core.model.WeatherForecast
import com.trailbook.core.model.WeatherLocation
import com.trailbook.core.net.HttpRequest
import com.trailbook.core.net.HttpTransport
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.math.roundToInt

class WeatherService(private val transport: HttpTransport, private val now: () -> Long) {

    suspend fun forecast(location: WeatherLocation, model: String = "best_match"): WeatherForecast {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?")
            append("latitude=").append(Num.fmt(location.latitude))
            append("&longitude=").append(Num.fmt(location.longitude))
            location.altitude?.let { append("&altitude=").append(Num.fmt(it, 0)) }
            append("&current=temperature_2m,apparent_temperature,precipitation,snowfall,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_gusts_10m,wind_direction_10m,relative_humidity_2m")
            append("&hourly=temperature_2m,apparent_temperature,precipitation,precipitation_probability,snowfall,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_gusts_10m,wind_direction_10m,relative_humidity_2m,freezing_level_height,cloud_base_height")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,snowfall_sum,wind_speed_10m_max,sunrise,sunset,uv_index_max")
            append("&timezone=auto")
            append("&models=").append(model)
        }
        val response = transport.execute(HttpRequest(url))
        if (!response.isSuccessful) throw com.trailbook.core.net.NetworkUnavailableException("Weather service error: ${response.status}")
        return parse(location, model, response.text(), now())
    }

    fun parse(location: WeatherLocation, model: String, json: String, nowMillis: Long): WeatherForecast {
        val root = Json.parseToJsonElement(json).jsonObject
        val hourlyObj = root["hourly"] as? JsonObject ?: JsonObject(emptyMap())
        val dailyObj = root["daily"] as? JsonObject ?: JsonObject(emptyMap())
        val currentObj = root["current"] as? JsonObject

        val hourlyTimes = hourlyObj.longArray("time")
        val hourly = hourlyTimes.indices.map { i ->
            HourlyWeather(
                time = hourlyTimes[i],
                temperatureC = hourlyObj.doubleAt("temperature_2m", i) ?: 0.0,
                apparentTemperatureC = hourlyObj.doubleAt("apparent_temperature", i),
                precipitationMm = hourlyObj.doubleAt("precipitation", i) ?: 0.0,
                precipitationProbability = hourlyObj.intAt("precipitation_probability", i),
                snowfallCm = hourlyObj.doubleAt("snowfall", i) ?: 0.0,
                windSpeedKmh = hourlyObj.doubleAt("wind_speed_10m", i) ?: 0.0,
                windGustsKmh = hourlyObj.doubleAt("wind_gusts_10m", i),
                windDirectionDeg = hourlyObj.doubleAt("wind_direction_10m", i),
                humidityPercent = hourlyObj.intAt("relative_humidity_2m", i),
                pressureHpa = hourlyObj.doubleAt("pressure_msl", i),
                cloudCoverPercent = hourlyObj.intAt("cloud_cover", i),
                weatherCode = hourlyObj.intAt("weather_code", i) ?: 0,
                freezingLevelM = hourlyObj.doubleAt("freezing_level_height", i),
                cloudBaseM = hourlyObj.doubleAt("cloud_base_height", i)
                    ?: estimateCloudBase(hourlyObj.doubleAt("temperature_2m", i) ?: 0.0, hourlyObj.intAt("cloud_cover", i) ?: 0),
            )
        }

        val dailyTimes = dailyObj.longArray("time")
        val daily = dailyTimes.indices.map { i ->
            DailyWeather(
                time = dailyTimes[i],
                tempMaxC = dailyObj.doubleAt("temperature_2m_max", i) ?: 0.0,
                tempMinC = dailyObj.doubleAt("temperature_2m_min", i) ?: 0.0,
                precipitationMm = dailyObj.doubleAt("precipitation_sum", i) ?: 0.0,
                precipitationProbability = dailyObj.intAt("precipitation_probability_max", i),
                snowfallCm = dailyObj.doubleAt("snowfall_sum", i) ?: 0.0,
                windSpeedMaxKmh = dailyObj.doubleAt("wind_speed_10m_max", i) ?: 0.0,
                weatherCode = dailyObj.intAt("weather_code", i) ?: 0,
                sunrise = parseIsoLocal(dailyObj.stringAt("sunrise", i)),
                sunset = parseIsoLocal(dailyObj.stringAt("sunset", i)),
                uvIndexMax = dailyObj.doubleAt("uv_index_max", i),
            )
        }

        val current = currentObj?.let { c ->
            fun d(key: String): Double? = c[key]?.let { el ->
                try { el.jsonPrimitive.double } catch (e: Exception) { null }
            }
            fun i(key: String): Int? = c[key]?.let { el ->
                try { el.jsonPrimitive.int } catch (e: Exception) { null }
            }
            HourlyWeather(
                time = parseIsoLocal(c["time"]?.jsonPrimitive?.content) ?: nowMillis,
                temperatureC = d("temperature_2m") ?: 0.0,
                apparentTemperatureC = d("apparent_temperature"),
                precipitationMm = d("precipitation") ?: 0.0,
                snowfallCm = d("snowfall") ?: 0.0,
                windSpeedKmh = d("wind_speed_10m") ?: 0.0,
                windGustsKmh = d("wind_gusts_10m"),
                windDirectionDeg = d("wind_direction_10m"),
                humidityPercent = i("relative_humidity_2m"),
                pressureHpa = d("pressure_msl"),
                cloudCoverPercent = i("cloud_cover"),
                weatherCode = i("weather_code") ?: 0,
            )
        }

        return WeatherForecast(
            location = location,
            model = model,
            fetchedAt = nowMillis,
            current = current,
            hourly = hourly,
            daily = daily,
            moonPhase = MoonPhaseCalculator.compute(nowMillis),
        )
    }

    private fun estimateCloudBase(tempC: Double, cloudCoverPercent: Int): Double? {
        if (cloudCoverPercent < 10) return null
        val dewPoint = tempC - (100.0 - cloudCoverPercent) / 5.0
        val spread = (tempC - dewPoint).coerceAtLeast(0.0)
        return (spread / 8.0 * 1000.0).roundToInt().toDouble()
    }

    private fun parseIsoLocal(s: String?): Long? = parseIsoLocalUtc(s)

    private object Num {
        fun fmt(v: Double, decimals: Int = 4): String = com.trailbook.core.format.NumFormat.fixed(v, decimals)
    }

    companion object {
        val MODELS = listOf(
            "best_match" to "Best match",
            "ecmwf_ifs025" to "ECMWF IFS",
            "gfs_seamless" to "GFS",
            "icon_seamless" to "DWD ICON",
            "meteofrance_seamless" to "Météo-France",
            "ukmo_seamless" to "UK Met Office",
        )

        fun freezingLevelFromLapseRate(tempC: Double, altitudeM: Double): Double =
            altitudeM + (tempC / 0.0065).coerceAtLeast(0.0)
    }
}

private fun JsonObject.longArray(key: String): List<Long> =
    (this[key] as? JsonArray)?.mapNotNull { el ->
        val prim = el as? kotlinx.serialization.json.JsonPrimitive ?: return@mapNotNull null
        if (prim.isString) parseIsoLocalUtc(prim.content) else try { prim.long } catch (e: Exception) { null }
    } ?: emptyList()

internal fun parseIsoLocalUtc(s: String?): Long? {
    if (s == null) return null
    return try {
        val datePart = s.substringBefore("T")
        val timePart = s.substringAfter("T", "00:00").substringBefore("+")
        val parts = datePart.split("-")
        val timeParts = timePart.split(":")
        if (parts.size < 3) return null
        kotlinx.datetime.LocalDateTime(
            parts[0].toInt(), parts[1].toInt(), parts[2].toInt(),
            (timeParts.getOrElse(0) { "0" }).toInt(), (timeParts.getOrElse(1) { "0" }).toInt()
        ).toInstant(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds()
    } catch (e: Exception) {
        null
    }
}

private fun JsonObject.doubleAt(key: String, index: Int): Double? =
    (this[key] as? JsonArray)?.getOrNull(index)?.let { el ->
        try { el.jsonPrimitive.double } catch (e: Exception) { null }
    }

private fun JsonObject.intAt(key: String, index: Int): Int? =
    (this[key] as? JsonArray)?.getOrNull(index)?.let { el ->
        try { el.jsonPrimitive.int } catch (e: Exception) { null }
    }

private fun JsonObject.stringAt(key: String, index: Int): String? =
    (this[key] as? JsonArray)?.getOrNull(index)?.let { el ->
        try { el.jsonPrimitive.content } catch (e: Exception) { null }
    }
