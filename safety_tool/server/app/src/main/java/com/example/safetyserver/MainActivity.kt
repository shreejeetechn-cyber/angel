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
import android.widget.ArrayAdapter
import android.widget.Button
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

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var locationInfo: TextView
    private lateinit var history: TextView
    private lateinit var list: ListView
    private var files: List<File> = emptyList()
    private var player: MediaPlayer? = null
    private var latestLat: Double? = null
    private var latestLon: Double? = null
    private var historyVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private val refreshTask = object : Runnable {
        override fun run() {
            refreshAudio()
            refreshLocation()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        status = TextView(this).apply { textSize = 19f }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "START SERVER"
            setOnClickListener { requestAndStart() }
        })

        locationInfo = TextView(this).apply {
            textSize = 17f
            setPadding(0, 18, 0, 10)
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
            textSize = 14f
            visibility = TextView.GONE
            setPadding(0, 8, 0, 16)
        }
        root.addView(history)

        root.addView(TextView(this).apply {
            text = "RECORDINGS"
            textSize = 17f
            setPadding(0, 12, 0, 6)
        })

        root.addView(Button(this).apply {
            text = "REFRESH"
            setOnClickListener { refreshAudio(); refreshLocation() }
        })

        list = ListView(this)
        list.setOnItemClickListener { _, _, p, _ -> play(files[p]) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        status.text = "Phone 2 IP: ${localIp()}  Port: 8080"
        requestAndStart()
        refreshAudio()
        refreshLocation()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    private fun requestAndStart() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
            return
        }
        val i = Intent(this, ServerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        status.text = "Server active: ${localIp()}:8080"
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (r == 9) requestAndStart()
    }

    private fun refreshAudio() {
        val d = File(filesDir, "audio").apply { mkdirs() }
        files = d.listFiles { f -> f.extension == "enc" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, files.map {
            val m = File(d, "${it.nameWithoutExtension}.json")
            if (m.exists()) "${it.nameWithoutExtension.take(8)}  •  ${it.length() / 1024} KB" else it.name
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
            history.text = lines.takeLast(20).asReversed().mapNotNull { line ->
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
        } else {
            history.visibility = TextView.GONE
        }
    }

    private fun openMap() {
        val lat = latestLat
        val lon = latestLon
        if (lat == null || lon == null) {
            status.text = "No client location received yet"
            return
        }
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Client)")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            status.text = "No map app available"
        }
    }

    private fun prettyTime(raw: String): String {
        return try {
            val z = Instant.parse(raw).atZone(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a").format(z)
        } catch (_: Exception) { raw.ifBlank { "unknown" } }
    }

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
                prepare()
                start()
            }
            status.text = "Playing ${enc.nameWithoutExtension.take(8)}"
        } catch (e: Exception) {
            status.text = "Playback error: ${e.message}"
        }
    }

    private fun localIp(): String {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
                ?.hostAddress ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        try { player?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
