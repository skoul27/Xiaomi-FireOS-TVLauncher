package com.shivam.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Where the weather is for. */
@Immutable
class WeatherPlace(val latitude: Double, val longitude: Double)

/**
 * Set to fixed coordinates to skip the IP lookup below, so the only request is to Open-Meteo.
 * Null finds the place from the TV's public IP.
 */
val WeatherLocation: WeatherPlace? = null

/**
 * The place from ipwho.is (free, no account), which sees the TV's public IP and answers with the
 * city it maps to. Looked up at most once a day per launcher process; kept in memory only.
 */
private object IpPlace {
    private val lock = Mutex()
    private var place: WeatherPlace? = null
    private var foundAt = 0L

    suspend fun get(): WeatherPlace? = lock.withLock {
        val now = System.currentTimeMillis()
        if (place == null || now - foundAt > 24 * 60 * 60 * 1000L) {
            runCatching { lookUp() }.getOrNull()?.let {
                place = it
                foundAt = now
            }
        }
        place
    }

    private fun lookUp(): WeatherPlace? {
        val json = JSONObject(httpGet(URL("https://ipwho.is/?fields=success,latitude,longitude")))
        if (!json.optBoolean("success")) return null
        return WeatherPlace(json.getDouble("latitude"), json.getDouble("longitude"))
    }
}

/** What the weather icon draws. */
enum class WeatherKind { Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Thunder }

@Immutable
class Weather(val temperature: Int, val summary: String, val kind: WeatherKind, val isDay: Boolean)

/**
 * The current weather from Open-Meteo (free, no account), refreshed every 30 minutes; null until
 * the first answer, and whenever the network or the services are unavailable. Retries sooner
 * while it has nothing to show, since the TV may still be joining Wi-Fi after a boot.
 */
@Composable
fun rememberWeather(): Weather? = produceState<Weather?>(null) {
    while (true) {
        value = withContext(Dispatchers.IO) {
            val place = WeatherLocation ?: IpPlace.get()
            place?.let { runCatching { fetchWeather(it) }.getOrNull() }
        } ?: value
        delay(if (value == null) 60 * 1000L else 30 * 60 * 1000L)
    }
}.value

private fun httpGet(url: URL): String {
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun fetchWeather(place: WeatherPlace): Weather {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code,is_day",
    )
    val current = JSONObject(httpGet(url)).getJSONObject("current")
    val code = current.getInt("weather_code")
    return Weather(
        temperature = current.getDouble("temperature_2m").roundToInt(),
        summary = describe(code),
        kind = kindOf(code),
        isDay = current.optInt("is_day", 1) == 1,
    )
}

private fun kindOf(code: Int): WeatherKind = when (code) {
    0, 1 -> WeatherKind.Clear
    2 -> WeatherKind.PartlyCloudy
    45, 48 -> WeatherKind.Fog
    in 51..67, in 80..82 -> WeatherKind.Rain
    in 71..77, 85, 86 -> WeatherKind.Snow
    in 95..99 -> WeatherKind.Thunder
    else -> WeatherKind.Cloudy
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
