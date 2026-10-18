package app.olauncher.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import app.olauncher.BuildConfig
import app.olauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Weather(val temperature: Int, val code: Int)

private fun fetchText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

// Current weather from Open-Meteo for the device location, else the configured fallback
suspend fun fetchWeather(context: Context): Weather? = withContext(Dispatchers.IO) {
    val (latitude, longitude) = lastKnownLocation(context)
        ?: parseLatLon(BuildConfig.WEATHER_FALLBACK)
        ?: return@withContext null
    runCatching {
        val current = JSONObject(
            fetchText("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code")
        ).getJSONObject("current")
        Weather(current.getDouble("temperature_2m").roundToInt(), current.getInt("weather_code"))
    }.getOrNull()
}

// shortcut: uses the last fix the system already has, no own location request; after a reboot it can be missing, then the fallback is used
@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (granted != PackageManager.PERMISSION_GRANTED) return null
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
    return locationManager.getProviders(true)
        .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { it.latitude to it.longitude }
}

fun parseLatLon(value: String): Pair<Double, Double>? =
    value.split(",").map { it.trim().toDoubleOrNull() }
        .takeIf { it.size == 2 && it.all { coordinate -> coordinate != null } }
        ?.let { it[0]!! to it[1]!! }

// WMO weather codes as used by Open-Meteo
@DrawableRes
fun weatherIconRes(code: Int): Int? = when (code) {
    0 -> R.drawable.ic_weather_clear
    1, 2 -> R.drawable.ic_weather_partly_cloudy
    3 -> R.drawable.ic_weather_cloudy
    45, 48 -> R.drawable.ic_weather_fog
    in 51..57 -> R.drawable.ic_weather_drizzle
    in 61..67, in 80..82 -> R.drawable.ic_weather_rain
    in 71..77, 85, 86 -> R.drawable.ic_weather_snow
    in 95..99 -> R.drawable.ic_weather_thunderstorm
    else -> null
}

// True when the newest heartbeat of every monitor on the Uptime Kuma status page is "up"; unreachable counts as not ok
suspend fun fetchLabOk(statusPageUrl: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val apiUrl = statusPageUrl.replace("/status/", "/api/status-page/heartbeat/")
        val heartbeats = JSONObject(fetchText(apiUrl)).getJSONObject("heartbeatList")
        val latest = heartbeats.keys().asSequence().mapNotNull { id ->
            val beats = heartbeats.getJSONArray(id)
            if (beats.length() > 0) beats.getJSONObject(beats.length() - 1).getInt("status") else null
        }.toList()
        latest.isNotEmpty() && latest.all { it == 1 }
    }.getOrDefault(false)
}
