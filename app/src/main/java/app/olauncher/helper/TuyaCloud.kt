package app.olauncher.helper

import app.olauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// A smart plug button: short label shown under the circle and the Tuya device id
data class Plug(val label: String, val id: String)

// "Pal:deviceId,Bet:deviceId" → plugs in display order; broken entries are skipped
fun parsePlugs(value: String): List<Plug> =
    value.split(",").mapNotNull { entry ->
        val parts = entry.split(":").map { it.trim() }
        if (parts.size == 2 && parts.all { it.isNotEmpty() }) Plug(parts[0], parts[1]) else null
    }

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

// Tuya OpenAPI request signature (HMAC-SHA256, empty nonce, no signed headers)
fun tuyaSign(clientId: String, secret: String, accessToken: String, timestamp: String, method: String, path: String, body: String): String {
    val bodyHash = MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).toHex()
    val stringToSign = "$method\n$bodyHash\n\n$path"
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
    return mac.doFinal((clientId + accessToken + timestamp + stringToSign).toByteArray()).toHex().uppercase()
}

// Switches plugs through the Tuya cloud; the plugs stay in their own (guest) network
object TuyaCloud {
    private val baseUrl get() = "https://openapi.tuya${BuildConfig.TUYA_REGION}.com"
    private var accessToken = ""
    private var tokenExpiresAt = 0L

    val configured get() = BuildConfig.TUYA_ACCESS_ID.isNotEmpty() && BuildConfig.TUYA_ACCESS_SECRET.isNotEmpty()

    private fun request(method: String, path: String, body: String = "", token: String = accessToken): JSONObject {
        val timestamp = System.currentTimeMillis().toString()
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("client_id", BuildConfig.TUYA_ACCESS_ID)
            connection.setRequestProperty("t", timestamp)
            connection.setRequestProperty("sign_method", "HMAC-SHA256")
            connection.setRequestProperty(
                "sign",
                tuyaSign(BuildConfig.TUYA_ACCESS_ID, BuildConfig.TUYA_ACCESS_SECRET, token, timestamp, method, path, body)
            )
            if (token.isNotEmpty()) connection.setRequestProperty("access_token", token)
            if (body.isNotEmpty()) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (!json.optBoolean("success")) throw IOException("Tuya: ${json.optString("msg")}")
            return json
        } finally {
            connection.disconnect()
        }
    }

    @Synchronized
    private fun ensureToken() {
        if (accessToken.isNotEmpty() && System.currentTimeMillis() < tokenExpiresAt) return
        val result = request("GET", "/v1.0/token?grant_type=1", token = "").getJSONObject("result")
        accessToken = result.getString("access_token")
        tokenExpiresAt = System.currentTimeMillis() + (result.getLong("expire_time") - 60) * 1000
    }

    private fun <T> withToken(block: () -> T): T? =
        runCatching {
            ensureToken()
            block()
        }.onFailure { accessToken = "" }.getOrNull() // a rejected token is fetched again next time

    // Device id → switched on; null when the cloud can't be reached
    suspend fun fetchStates(ids: List<String>): Map<String, Boolean>? = withContext(Dispatchers.IO) {
        withToken {
            val result = request("GET", "/v1.0/iot-03/devices/status?device_ids=${ids.joinToString(",")}").getJSONArray("result")
            (0 until result.length()).map { result.getJSONObject(it) }.associate { device ->
                val status = device.getJSONArray("status")
                val on = (0 until status.length()).map { status.getJSONObject(it) }
                    .any { it.getString("code") == "switch_1" && it.optBoolean("value") }
                device.getString("id") to on
            }
        }
    }

    suspend fun setSwitch(id: String, on: Boolean): Boolean = withContext(Dispatchers.IO) {
        withToken {
            request("POST", "/v1.0/iot-03/devices/$id/commands", """{"commands":[{"code":"switch_1","value":$on}]}""")
        } != null
    }
}
