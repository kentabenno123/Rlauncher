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

// lab: every monitor except Gemma is up; gemma: the Gemma monitor is up, null when none is configured
data class HomelabStatus(val lab: Boolean, val gemma: Boolean?)

// Uptime Kuma status 1 means "up"
fun homelabStatus(latestStatusById: Map<String, Int>, gemmaMonitorId: String?, gemmaConfigured: Boolean): HomelabStatus {
    val others = latestStatusById.filterKeys { it != gemmaMonitorId }.values
    return HomelabStatus(
        lab = others.isNotEmpty() && others.all { it == 1 },
        gemma = if (gemmaConfigured) latestStatusById[gemmaMonitorId] == 1 else null,
    )
}

// Reads the Uptime Kuma status page; when it can't be reached, everything counts as not ok
suspend fun fetchHomelabStatus(statusPageUrl: String, gemmaMonitorName: String): HomelabStatus =
    withContext(Dispatchers.IO) {
        val gemmaConfigured = gemmaMonitorName.isNotEmpty()
        runCatching {
            val heartbeats = JSONObject(fetchText(statusPageUrl.replace("/status/", "/api/status-page/heartbeat/")))
                .getJSONObject("heartbeatList")
            val latest = heartbeats.keys().asSequence().mapNotNull { id ->
                val beats = heartbeats.getJSONArray(id)
                if (beats.length() > 0) id to beats.getJSONObject(beats.length() - 1).getInt("status") else null
            }.toMap()
            val gemmaId = if (gemmaConfigured) findMonitorId(statusPageUrl, gemmaMonitorName) else null
            homelabStatus(latest, gemmaId, gemmaConfigured)
        }.getOrDefault(HomelabStatus(lab = false, gemma = if (gemmaConfigured) false else null))
    }

private fun findMonitorId(statusPageUrl: String, name: String): String? {
    val groups = JSONObject(fetchText(statusPageUrl.replace("/status/", "/api/status-page/"))).getJSONArray("publicGroupList")
    for (g in 0 until groups.length()) {
        val monitors = groups.getJSONObject(g).getJSONArray("monitorList")
        for (m in 0 until monitors.length()) {
            val monitor = monitors.getJSONObject(m)
            if (monitor.getString("name").equals(name, ignoreCase = true)) return monitor.getInt("id").toString()
        }
    }
    return null
}
