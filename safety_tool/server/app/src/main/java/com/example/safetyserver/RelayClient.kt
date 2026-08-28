package com.example.safetyserver

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RelayClient {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun base(ctx: Context): String? {
        val value = prefs(ctx).getString("relay_url", "")?.trim().orEmpty().trimEnd('/')
        return if (value.startsWith("https://", ignoreCase = true)) value else null
    }
    private fun token(ctx: Context) = prefs(ctx).getString("pair_token", "DEMO-PAIR-2026") ?: "DEMO-PAIR-2026"

    private fun open(ctx: Context, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer ${token(ctx)}")
        }

    fun sync(ctx: Context): String {
        val b = base(ctx) ?: return "Internet relay not configured"
        return try {
            val recordings = getJsonArray(ctx, "$b/api/v1/viewer/recordings")
            val audioDir = File(ctx.filesDir, "audio").apply { mkdirs() }
            var downloaded = 0
            for (i in 0 until recordings.length()) {
                val item = recordings.getJSONObject(i)
                val id = item.getString("id")
                val enc = File(audioDir, "$id.enc")
                val meta = File(audioDir, "$id.json")
                if (!enc.exists() || !meta.exists()) {
                    if (downloadRecording(ctx, "$b/api/v1/viewer/audio/$id", enc, meta)) downloaded++
                }
            }

            val lastTs = prefs(ctx).getString("remote_last_location_ts", "") ?: ""
            val locationsUrl = "$b/api/v1/viewer/locations?after=${URLEncoder.encode(lastTs, "UTF-8")}&limit=200"
            val locations = getJsonArray(ctx, locationsUrl)
            var newestTs = lastTs
            if (locations.length() > 0) {
                val localFile = File(ctx.filesDir, "locations.jsonl")
                for (i in 0 until locations.length()) {
                    val j = locations.getJSONObject(i)
                    localFile.appendText(j.toString() + "\n")
                    val ts = j.optString("ts", "")
                    if (ts > newestTs) newestTs = ts
                }
                prefs(ctx).edit().putString("remote_last_location_ts", newestTs).apply()
            }

            val status = getJsonObject(ctx, "$b/api/v1/viewer/status")
            prefs(ctx).edit().putString("remote_status", formatStatus(status)).apply()
            "Internet sync OK • recordings=$downloaded • locations=${locations.length()}"
        } catch (e: Exception) {
            "Internet sync error: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
        }
    }

    private fun getJsonArray(ctx: Context, url: String): JSONArray {
        val c = open(ctx, url).apply { requestMethod = "GET" }
        val code = c.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        return JSONArray(text)
    }

    private fun getJsonObject(ctx: Context, url: String): JSONObject {
        val c = open(ctx, url).apply { requestMethod = "GET" }
        val code = c.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        return JSONObject(text)
    }

    private fun downloadRecording(ctx: Context, url: String, enc: File, meta: File): Boolean {
        val c = open(ctx, url).apply { requestMethod = "GET" }
        if (c.responseCode !in 200..299) { c.disconnect(); return false }
        val metaHeader = c.getHeaderField("X-Meta") ?: run { c.disconnect(); return false }
        val tmp = File(enc.parentFile, enc.name + ".tmp")
        c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        meta.writeBytes(Base64.decode(metaHeader, Base64.DEFAULT))
        if (enc.exists()) enc.delete()
        tmp.renameTo(enc)
        c.disconnect()
        return true
    }

    private fun formatStatus(j: JSONObject): String {
        val seen = j.optString("last_seen", "unknown")
        val battery = j.optInt("battery_pct", -1)
        val network = j.optString("network", "unknown")
        val pendingAudio = j.optInt("pending_audio", 0)
        val pendingLocations = j.optInt("pending_locations", 0)
        val recording = j.optBoolean("recording", false)
        val online = j.optBoolean("online", false)
        return "Client: ${if (online) "ONLINE" else "OFFLINE"}\nLast contact: $seen\nBattery: ${if (battery >= 0) "$battery%" else "unknown"}\nNetwork: $network\nSafety session: ${if (recording) "active" else "inactive"}\nClient pending: audio=$pendingAudio location=$pendingLocations"
    }
}
