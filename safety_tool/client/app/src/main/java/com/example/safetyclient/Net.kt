package com.example.safetyclient

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object Net {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private fun base(ctx: Context): String? {
        var value = prefs(ctx).getString("endpoint", "")?.trim().orEmpty()
        if (value.isBlank()) {
            val old = prefs(ctx).getString("server_ip", "")?.trim().orEmpty()
            if (old.isNotBlank()) value = "http://$old:8080"
        }
        if (value.isBlank()) return null
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value:8080"
        return value.trimEnd('/')
    }

    private fun isRelay(base: String) = base.startsWith("https://", ignoreCase = true)
    private fun token(ctx: Context) = prefs(ctx).getString("pair_token", "DEMO-PAIR-2026") ?: "DEMO-PAIR-2026"

    private fun deviceId(ctx: Context): String {
        val p = prefs(ctx)
        val existing = p.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        p.edit().putString("device_id", id).apply()
        return id
    }

    private fun common(ctx: Context, c: HttpURLConnection) {
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
        c.setRequestProperty("X-Device-Id", deviceId(ctx))
    }

    fun sendAudio(ctx: Context, enc: File, meta: File): Boolean {
        val b = base(ctx) ?: return false
        val path = if (isRelay(b)) "/api/v1/client/audio" else "/audio"
        return try {
            val c = (URL(b + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                common(ctx, this)
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("X-Meta", Base64.encodeToString(meta.readBytes(), Base64.NO_WRAP))
                setFixedLengthStreamingMode(enc.length())
            }
            c.outputStream.use { out -> enc.inputStream().use { it.copyTo(out) } }
            val ok = c.responseCode in 200..299
            if (!ok) prefs(ctx).edit().putString("net_error", "Audio HTTP ${c.responseCode}").apply()
            c.disconnect()
            ok
        } catch (e: Exception) {
            prefs(ctx).edit().putString("net_error", "${e.javaClass.simpleName}: ${e.message ?: "network error"}").apply()
            false
        }
    }

    fun sendLocation(ctx: Context, json: String): Boolean {
        val b = base(ctx) ?: return false
        val path = if (isRelay(b)) "/api/v1/client/location" else "/location"
        return try {
            val bytes = json.toByteArray()
            val c = (URL(b + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                common(ctx, this)
                setRequestProperty("Content-Type", "application/json")
                setFixedLengthStreamingMode(bytes.size)
            }
            c.outputStream.use { it.write(bytes) }
            val ok = c.responseCode in 200..299
            if (!ok) prefs(ctx).edit().putString("net_error", "Location HTTP ${c.responseCode}").apply()
            c.disconnect()
            ok
        } catch (e: Exception) {
            prefs(ctx).edit().putString("net_error", "${e.javaClass.simpleName}: ${e.message ?: "network error"}").apply()
            false
        }
    }

    fun sendHeartbeat(ctx: Context, battery: Int, network: String, pendingAudio: Int, pendingLocations: Int, recording: Boolean): Boolean {
        val b = base(ctx) ?: return false
        return try {
            val body = JSONObject().apply {
                put("ts", java.time.Instant.now().toString())
                put("battery_pct", battery)
                put("network", network)
                put("pending_audio", pendingAudio)
                put("pending_locations", pendingLocations)
                put("recording", recording)
            }.toString().toByteArray()
            val path = if (isRelay(b)) "/api/v1/client/heartbeat" else "/heartbeat"
            val c = (URL(b + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                common(ctx, this)
                setRequestProperty("Content-Type", "application/json")
                setFixedLengthStreamingMode(body.size)
            }
            c.outputStream.use { it.write(body) }
            val ok = c.responseCode in 200..299
            if (!ok) prefs(ctx).edit().putString("net_error", "Heartbeat HTTP ${c.responseCode}").apply()
            c.disconnect()
            ok
        } catch (e: Exception) {
            prefs(ctx).edit().putString("net_error", "Heartbeat ${e.javaClass.simpleName}: ${e.message ?: "network error"}").apply()
            false
        }
    }
}
