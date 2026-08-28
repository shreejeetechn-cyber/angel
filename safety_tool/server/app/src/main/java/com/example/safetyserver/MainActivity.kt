package com.example.safetyserver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var connectionSummary: TextView
    private lateinit var clientStatus: TextView
    private lateinit var locationInfo: TextView
    private lateinit var history: TextView
    private lateinit var relayUrl: EditText
    private lateinit var token: EditText
    private lateinit var recordingsBox: LinearLayout
    private lateinit var recordingsCount: TextView

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
            updateConnectionSummary()
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

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Safety Viewer"
            textSize = 28f
            setTextColor(Color.rgb(20, 31, 45))
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Client status, location and encrypted recordings"
            textSize = 14f
            setTextColor(Color.rgb(91, 105, 120))
            setPadding(0, dp(4), 0, dp(18))
        })

        val overview = card().apply {
            addView(sectionTitle("CONNECTION OVERVIEW"))
            connectionSummary = TextView(this@MainActivity).apply {
                textSize = 16f
                setTextColor(Color.rgb(30, 45, 60))
                setPadding(0, dp(8), 0, dp(8))
            }
            addView(connectionSummary)
            status = TextView(this@MainActivity).apply {
                textSize = 13.5f
                setTextColor(Color.rgb(91, 105, 120))
            }
            addView(status)
        }
        root.addView(overview, cardParams())

        val connectionCard = card().apply {
            addView(sectionTitle("INTERNET / SIM RELAY"))
            addView(helper("Leave this empty for same-Wi-Fi LAN testing. For remote SIM/any-Wi-Fi use, enter the public HTTPS relay URL."))

            relayUrl = EditText(this@MainActivity).apply {
                hint = "https://relay.example.com"
                setText(prefs.getString("relay_url", ""))
                textSize = 16f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                background = fieldBackground()
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            addView(relayUrl, fieldParams())

            addView(TextView(this@MainActivity).apply {
                text = "Pair token"
                textSize = 13f
                setTextColor(Color.rgb(91, 105, 120))
                setPadding(0, dp(14), 0, dp(6))
            })
            token = EditText(this@MainActivity).apply {
                hint = "Pair token"
                setText(prefs.getString("pair_token", "DEMO-PAIR-2026"))
                textSize = 16f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                background = fieldBackground()
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            addView(token, fieldParams())

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            row.addView(actionButton("Sync Internet", Color.rgb(31, 111, 235)) {
                prefs.edit()
                    .putString("relay_url", relayUrl.text.toString().trim())
                    .putString("pair_token", token.text.toString())
                    .apply()
                syncInternet(true)
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })

            row.addView(actionButton("Start LAN", Color.rgb(42, 142, 85)) {
                requestAndStartLocal()
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
            addView(row)
        }
        root.addView(connectionCard, cardParams())

        val clientCard = card().apply {
            addView(sectionTitle("CLIENT HEALTH"))
            clientStatus = TextView(this@MainActivity).apply {
                textSize = 15f
                setTextColor(Color.rgb(30, 45, 60))
                setPadding(0, dp(8), 0, 0)
                text = "No client heartbeat received yet"
            }
            addView(clientStatus)
        }
        root.addView(clientCard, cardParams())

        val locationCard = card().apply {
            addView(sectionTitle("CLIENT LOCATION"))
            locationInfo = TextView(this@MainActivity).apply {
                textSize = 15.5f
                setTextColor(Color.rgb(30, 45, 60))
                setPadding(0, dp(8), 0, dp(10))
                text = "Waiting for first location..."
            }
            addView(locationInfo)

            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(outlineButton("Open Map") { openMap() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
            row.addView(outlineButton("History") {
                historyVisible = !historyVisible
                refreshLocation()
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
            addView(row)

            history = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(Color.rgb(75, 91, 107))
                visibility = View.GONE
                setPadding(0, dp(12), 0, 0)
            }
            addView(history)
        }
        root.addView(locationCard, cardParams())

        val recordingsCard = card().apply {
            val titleRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            titleRow.addView(sectionTitle("RECORDINGS"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            recordingsCount = TextView(this@MainActivity).apply {
                text = "0"
                textSize = 13f
                setTextColor(Color.rgb(91, 105, 120))
                setTypeface(typeface, Typeface.BOLD)
            }
            titleRow.addView(recordingsCount)
            addView(titleRow)

            addView(helper("Tap a recording to decrypt and play it on this Viewer phone."))
            addView(outlineButton("Refresh now") {
                refreshAudio(); refreshLocation(); refreshClientStatus(); syncInternet(true)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

            recordingsBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, 0)
            }
            addView(recordingsBox)
        }
        root.addView(recordingsCard, cardParams())

        root.addView(TextView(this).apply {
            text = "LAN works only when both phones can reach each other on the same local network. Internet mode requires a configured HTTPS relay."
            textSize = 12.5f
            setTextColor(Color.rgb(100, 113, 128))
            setPadding(dp(4), dp(2), dp(4), 0)
        })

        setContentView(scroll)
        updateConnectionSummary()
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
        status.text = "LAN receiver is active"
        updateConnectionSummary()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9) requestAndStartLocal()
    }

    private fun syncInternet(force: Boolean) {
        if (relayUrl.text.toString().trim().isBlank()) {
            if (force) status.text = "Internet relay is not configured; LAN mode remains available"
            updateConnectionSummary()
            return
        }
        if (!syncBusy.compareAndSet(false, true)) return
        status.text = "Syncing Internet relay..."
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
            .putString("relay_url", relayUrl.text.toString().trim())
            .putString("pair_token", token.text.toString())
            .apply()
        io.submit {
            val result = RelayClient.sync(this)
            runOnUiThread {
                status.text = result
                refreshAudio()
                refreshLocation()
                refreshClientStatus()
                updateConnectionSummary()
            }
            syncBusy.set(false)
        }
    }

    private fun updateConnectionSummary() {
        if (!::connectionSummary.isInitialized || !::relayUrl.isInitialized) return
        val relayConfigured = relayUrl.text.toString().trim().startsWith("https://", true)
        connectionSummary.text = "LAN  ${localIp()}:8080\nInternet relay  ${if (relayConfigured) "Configured" else "Not configured"}"
    }

    private fun refreshClientStatus() {
        val value = getSharedPreferences("cfg", MODE_PRIVATE).getString("remote_status", "") ?: ""
        clientStatus.text = if (value.isBlank()) "No Internet-relay heartbeat received yet. LAN audio/location can still arrive directly." else value
    }

    private fun refreshAudio() {
        val dir = File(filesDir, "audio").apply { mkdirs() }
        files = dir.listFiles { f -> f.extension == "enc" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        recordingsCount.text = "${files.size} saved"
        recordingsBox.removeAllViews()

        if (files.isEmpty()) {
            recordingsBox.addView(TextView(this).apply {
                text = "No recordings received yet"
                textSize = 14f
                setTextColor(Color.rgb(100, 113, 128))
                setPadding(dp(4), dp(10), dp(4), dp(10))
            })
            return
        }

        files.forEach { enc ->
            val meta = File(dir, "${enc.nameWithoutExtension}.json")
            val label = if (meta.exists()) {
                try {
                    val j = JSONObject(meta.readText())
                    val started = prettyTime(j.optString("started_at", ""))
                    "$started\n${enc.length() / 1024} KB encrypted audio"
                } catch (_: Exception) { "${enc.nameWithoutExtension.take(8)}\n${enc.length() / 1024} KB encrypted audio" }
            } else "${enc.nameWithoutExtension.take(8)}\n${enc.length() / 1024} KB encrypted audio"

            recordingsBox.addView(TextView(this).apply {
                text = label
                textSize = 14.5f
                setTextColor(Color.rgb(30, 45, 60))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = listItemBackground()
                isClickable = true
                isFocusable = true
                setOnClickListener { play(enc) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            })
        }
    }

    private fun refreshLocation() {
        val f = File(filesDir, "locations.jsonl")
        if (!f.exists() || f.length() == 0L) {
            locationInfo.text = "Waiting for first location..."
            history.visibility = View.GONE
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
            locationInfo.text = "Last update  $stamp\n${String.format(Locale.US, "%.6f", latestLat)}, ${String.format(Locale.US, "%.6f", latestLon)}\nAccuracy  $accText  •  $provider"
        } catch (e: Exception) {
            locationInfo.text = "Location parse error: ${e.message}"
        }

        if (historyVisible) {
            history.visibility = View.VISIBLE
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
        } else history.visibility = View.GONE
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
            status.text = "Playing recording"
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

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = cardBackground(Color.WHITE)
        elevation = dp(2).toFloat()
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 12.5f
        setTextColor(Color.rgb(91, 105, 120))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun helper(value: String) = TextView(this).apply {
        text = value
        textSize = 13.5f
        setTextColor(Color.rgb(75, 91, 107))
        setPadding(0, dp(8), 0, dp(12))
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = buttonBackground(color)
        setOnClickListener { action() }
    }

    private fun outlineButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14.5f
        setTextColor(Color.rgb(31, 111, 235))
        background = outlinedButtonBackground(Color.rgb(31, 111, 235))
        setOnClickListener { action() }
    }

    private fun cardParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(14)
    }

    private fun fieldParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))

    private fun cardBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.rgb(226, 232, 238))
    }

    private fun fieldBackground() = GradientDrawable().apply {
        setColor(Color.rgb(250, 252, 254))
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), Color.rgb(207, 216, 225))
    }

    private fun buttonBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }

    private fun outlinedButtonBackground(color: Int) = GradientDrawable().apply {
        setColor(Color.WHITE)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), color)
    }

    private fun listItemBackground() = GradientDrawable().apply {
        setColor(Color.rgb(249, 251, 253))
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), Color.rgb(226, 232, 238))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        io.shutdownNow()
        try { player?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
