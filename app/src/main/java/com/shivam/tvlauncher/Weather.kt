package com.shivam.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Where the weather is for. Fixed coordinates, so no location lookup ever leaves the TV. */
@Immutable
class WeatherPlace(val latitude: Double, val longitude: Double)

/** Set to this TV's city to turn the weather line on; null keeps the launcher offline. */
val WeatherLocation: WeatherPlace? = null

@Immutable
class Weather(val temperature: Int, val summary: String)

/**
 * The current weather from Open-Meteo (free, no account), refreshed every 30 minutes; null until
 * the first answer, and whenever the network or the service is unavailable.
 */
@Composable
fun rememberWeather(): Weather? = produceState<Weather?>(null) {
    val place = WeatherLocation ?: return@produceState
    while (true) {
        value = withContext(Dispatchers.IO) { runCatching { fetchWeather(place) }.getOrNull() } ?: value
        delay(30 * 60 * 1000L)
    }
}.value

private fun fetchWeather(place: WeatherPlace): Weather {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code",
    )
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val current = JSONObject(body).getJSONObject("current")
        return Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            summary = describe(current.getInt("weather_code")),
        )
    } finally {
        connection.disconnect()
    }
}

/** WMO weather codes, as Open-Meteo reports them, in a few words. */
private fun describe(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    85, 86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> ""
}
