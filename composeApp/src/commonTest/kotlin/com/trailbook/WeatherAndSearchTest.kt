package com.trailbook

import com.trailbook.core.model.MoonPhaseCalculator
import com.trailbook.core.model.WeatherLocation
import com.trailbook.core.search.GeocodingService
import com.trailbook.helpers.FakeTransport
import com.trailbook.core.search.RecentSearchStore
import com.trailbook.core.weather.WeatherService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeatherAndSearchTest {

    private val weatherJson = """
    {
      "hourly": {
        "time": ["2024-06-01T00:00", "2024-06-01T01:00"],
        "temperature_2m": [12.5, 11.0],
        "apparent_temperature": [11.0, 10.0],
        "precipitation": [0.0, 1.5],
        "precipitation_probability": [10, 80],
        "snowfall": [0.0, 0.0],
        "weather_code": [0, 61],
        "cloud_cover": [20, 90],
        "pressure_msl": [1013.0, 1010.0],
        "wind_speed_10m": [5.0, 8.0],
        "relative_humidity_2m": [60, 85],
        "freezing_level_height": [3000.0, 2800.0],
        "cloud_base_height": [1500.0, 900.0]
      },
      "daily": {
        "time": ["2024-06-01", "2024-06-02"],
        "weather_code": [3, 61],
        "temperature_2m_max": [18.0, 15.0],
        "temperature_2m_min": [8.0, 7.0],
        "precipitation_sum": [1.5, 12.0],
        "precipitation_probability_max": [80, 95],
        "snowfall_sum": [0.0, 0.0],
        "wind_speed_10m_max": [20.0, 30.0],
        "sunrise": ["2024-06-01T05:31", "2024-06-02T05:30"],
        "sunset": ["2024-06-01T21:15", "2024-06-02T21:16"],
        "uv_index_max": [6.0, 3.0]
      }
    }
    """.trimIndent()

    @Test
    fun weatherParseExtractsHourlyAndDaily() {
        val location = WeatherLocation(47.3769, 8.5417, altitude = 500.0, name = "Zurich")
        val forecast = WeatherService(FakeTransport.offline()) { 0L }.parse(location, "best_match", weatherJson, 1_717_200_000_000L)
        assertEquals(2, forecast.hourly.size)
        assertEquals(12.5, forecast.hourly[0].temperatureC, 0.001)
        assertEquals(80, forecast.hourly[1].precipitationProbability)
        assertEquals(3000.0, forecast.hourly[0].freezingLevelM!!, 0.001)
        assertEquals(1500.0, forecast.hourly[0].cloudBaseM!!, 0.001)
        assertEquals(2, forecast.daily.size)
        assertEquals(18.0, forecast.daily[0].tempMaxC, 0.001)
        assertEquals("Zurich", forecast.location.name)
        assertEquals("best_match", forecast.model)
        assertNotNull(forecast.moonPhase)
    }

    @Test
    fun moonPhaseIsBounded() {
        val phase = MoonPhaseCalculator.compute(1_717_200_000_000L)
        assertTrue(phase.illuminationPercent in 0..100)
        assertTrue(phase.ageDays in 0.0..29.6)
        assertTrue(phase.name.isNotBlank())
    }

    @Test
    fun geocodingParseReadsNominatimResponse() {
        val json = """
        [
          {"lat": "47.3769", "lon": "8.5417", "name": "Zurich", "display_name": "Zurich, Switzerland", "type": "city"},
          {"lat": "91", "lon": "0", "display_name": "invalid"}
        ]
        """.trimIndent()
        val results = GeocodingService(FakeTransport.offline()).parse(json)
        assertEquals(1, results.size)
        assertEquals("Zurich", results[0].name)
        assertEquals(47.3769, results[0].latitude, 1e-6)
    }

    @Test
    fun urlEncodeEscapesSpecials() {
        assertEquals("a%20b%26c", GeocodingService(FakeTransport.offline()).urlEncode("a b&c"))
    }

    @Test
    fun recentSearchesDeduplicateAndCap() {
        val store = RecentSearchStore(maxEntries = 3)
        store.record("Zurich", 1L)
        store.record("Bern", 2L)
        store.record("Zurich", 3L)
        store.record("Basel", 4L)
        store.record("Geneva", 5L)
        val all = store.all()
        assertEquals(3, all.size)
        assertEquals("Geneva", all.first().query)
        assertTrue(all.none { it.query == "Bern" })
        store.clear()
        assertTrue(store.all().isEmpty())
    }
}
