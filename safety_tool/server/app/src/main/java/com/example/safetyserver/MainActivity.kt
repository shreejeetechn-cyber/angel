package com.example.safetyserver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.net.NetworkInterface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var clientStatus: TextView
    private lateinit var locationInfo: TextView
    private lateinit var history: TextView
    private lateinit var relayUrl: EditText
    private lateinit var token: EditText
    private lateinit var list: ListView

    private var files: List<File> = emptyList()
    private var player: MediaPlayer? = null
    private var latestLat: Double? = null
    private var latestLon: Double? = null
    private var historyVisible = false
    private var lastRemotePoll = 0L
    private val syncBusy = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshAudio()
            refreshLocation()
            refreshClientStatus()
            val now = System.currentTimeMillis()
            if (now - lastRemotePoll >= 30000L) {
                lastRemotePoll = now
                syncInternet(false)
            }
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 44, 32, 28)
        }

        root.addView(TextView(this).apply { text = "Safety Viewer v0.4"; textSize = 24f })
        status = TextView(this).apply { textSize = 15f; setPadding(0, 8, 0, 10) }
        root.addView(status)

        relayUrl = EditText(this).apply {
            hint = "HTTPS relay URL (remote mode)"
            setText(prefs.getString("relay_url", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(relayUrl)

        token = EditText(this).apply {
            hint = "Pair token"
            setText(prefs.getString("pair_token", "DEMO-PAIR-2026"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(token)

        root.addView(Button(this).apply {
            text = "SAVE / SYNC INTERNET"
            setOnClickListener {
                prefs.edit()
                    .putString("relay_url", relayUrl.text.toString().trim())
                    .putString("pair_token", token.text.toString())
                    .apply()
                syncInternet(true)
            }
        })

        root.addView(Button(this).apply {
            text = "START LAN SERVER"
            setOnClickListener { requestAndStartLocal() }
        })

        clientStatus = TextView(this).apply {
            textSize = 15f
            setPadding(0, 12, 0, 10)
            text = "Client status: waiting for internet sync"
        }
        root.addView(clientStatus)

        locationInfo = TextView(this).apply {
            textSize = 16f
            setPadding(0, 10, 0, 8)
            text = "CLIENT LOCATION\nWaiting for first location..."
        }
        root.addView(locationInfo)

        root.addView(Button(this).apply {
            text = "OPEN MAP"
            setOnClickListener { openMap() }
        })
        root.addView(Button(this).apply {
            text = "LOCATION HISTORY"
            setOnClickListener {
                historyVisible = !historyVisible
                refreshLocation()
            }
        })

        history = TextView(this).apply {
            textSize = 13f
            visibility = TextView.GONE
            setPadding(0, 8, 0, 12)
        }
        root.addView(history)

        root.addView(TextView(this).apply {
            text = "RECORDINGS"
            textSize = 17f
            setPadding(0, 10, 0, 4)
        })

        root.addView(Button(this).apply {
            text = "REFRESH"
            setOnClickListener {
                refreshAudio(); refreshLocation(); refreshClientStatus(); syncInternet(true)
            }
        })

        list = ListView(this)
        list.setOnItemClickListener { _, _, position, _ -> play(files[position]) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        status.text = "LAN: ${localIp()}:8080 • Internet relay: ${if (relayUrl.text.isNullOrBlank()) "not configured" else "configured"}"
        requestAndStartLocal()
        refreshAudio()
        refreshLocation()
        refreshClientStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    private fun requestAndStartLocal() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
            return
        }
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("pair_token", token.text.toString()).apply()
        val i = Intent(this, ServerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        status.text = "LAN server active: ${localIp()}:8080"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9) requestAndStartLocal()
    }

    private fun syncInternet(force: Boolean) {
        if (relayUrl.text.toString().trim().isBlank()) {
            if (force) status.text = "Internet relay URL not configured; LAN mode still available"
            return
        }
        if (!syncBusy.compareAndSet(false, true)) return
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
            .putString("relay_url", relayUrl.text.toString().trim())
            .putString("pair_token", token.text.toString())
            .apply()
        io.submit {
            val result = RelayClient.sync(this)
            runOnUiThread {
                status.text = result + "\nLAN: ${localIp()}:8080"
                refreshAudio()
                refreshLocation()
                refreshClientStatus()
            }
            syncBusy.set(false)
        }
    }

    private fun refreshClientStatus() {
        val value = getSharedPreferences("cfg", MODE_PRIVATE).getString("remote_status", "") ?: ""
        clientStatus.text = if (value.isBlank()) "Client status: no relay heartbeat received yet" else value
    }

    private fun refreshAudio() {
        val dir = File(filesDir, "audio").apply { mkdirs() }
        files = dir.listFiles { f -> f.extension == "enc" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files.map {
            val meta = File(dir, "${it.nameWithoutExtension}.json")
            if (meta.exists()) {
                try {
                    val j = JSONObject(meta.readText())
                    val started = prettyTime(j.optString("started_at", ""))
                    "$started • ${it.length() / 1024} KB"
                } catch (_: Exception) { "${it.nameWithoutExtension.take(8)} • ${it.length() / 1024} KB" }
            } else it.name
        })
    }

    private fun refreshLocation() {
        val f = File(filesDir, "locations.jsonl")
        if (!f.exists() || f.length() == 0L) {
            locationInfo.text = "CLIENT LOCATION\nWaiting for first location..."
            history.visibility = TextView.GONE
            return
        }
        val lines = try { f.readLines().filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
        if (lines.isEmpty()) return
        try {
            val j = JSONObject(lines.last())
            latestLat = j.getDouble("lat")
            latestLon = j.getDouble("lon")
            val accuracy = j.optDouble("accuracy_m", -1.0)
            val provider = j.optString("provider", "")
            val stamp = prettyTime(j.optString("ts", ""))
            val accText = if (accuracy >= 0) String.format(Locale.US, "%.0f m", accuracy) else "unknown"
            locationInfo.text = "CLIENT LOCATION\nLast update: $stamp\nLatitude: ${String.format(Locale.US, "%.6f", latestLat)}\nLongitude: ${String.format(Locale.US, "%.6f", latestLon)}\nAccuracy: $accText\nProvider: $provider"
        } catch (e: Exception) {
            locationInfo.text = "CLIENT LOCATION\nLocation parse error: ${e.message}"
        }

        if (historyVisible) {
            history.visibility = TextView.VISIBLE
            history.text = lines.takeLast(30).asReversed().mapNotNull { line ->
                try {
                    val j = JSONObject(line)
                    val t = prettyTime(j.optString("ts", ""))
                    val lat = j.getDouble("lat")
                    val lon = j.getDouble("lon")
                    val a = j.optDouble("accuracy_m", -1.0)
                    val aText = if (a >= 0) " ±${String.format(Locale.US, "%.0f", a)}m" else ""
                    "$t  ${String.format(Locale.US, "%.5f", lat)}, ${String.format(Locale.US, "%.5f", lon)}$aText"
                } catch (_: Exception) { null }
            }.joinToString("\n")
        } else history.visibility = TextView.GONE
    }

    private fun openMap() {
        val lat = latestLat
        val lon = latestLon
        if (lat == null || lon == null) {
            status.text = "No client location received yet"
            return
        }
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Client)")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { status.text = "No map app available" }
    }

    private fun prettyTime(raw: String): String = try {
        val z = Instant.parse(raw).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a").format(z)
    } catch (_: Exception) { raw.ifBlank { "unknown" } }

    private fun play(enc: File) {
        try { player?.release() } catch (_: Exception) {}
        try {
            val tmp = File(cacheDir, "play_${System.currentTimeMillis()}.ogg")
            Decryptor.decrypt(this, enc, File(enc.parentFile, "${enc.nameWithoutExtension}.json"), tmp)
            player = MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                setOnCompletionListener {
                    try { tmp.delete() } catch (_: Exception) {}
                    status.text = "Playback complete"
                }
                prepare(); start()
            }
            status.text = "Playing ${enc.nameWithoutExtension.take(8)}"
        } catch (e: Exception) {
            status.text = "Playback error: ${e.message}"
        }
    }

    private fun localIp(): String = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress ?: "unknown"
    } catch (_: Exception) { "unknown" }

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        io.shutdownNow()
        try { player?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
