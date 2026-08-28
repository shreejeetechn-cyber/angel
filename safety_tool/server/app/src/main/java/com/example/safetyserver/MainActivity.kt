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
import android.view.Gravity
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
    private lateinit var homePanel: LinearLayout
    private lateinit var recordingsPanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var homeTab: Button
    private lateinit var recordingsTab: Button
    private lateinit var settingsTab: Button

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

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(244, 247, 250))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(16))
        }
        header.addView(TextView(this).apply {
            text = "Safety Viewer"
            textSize = 29f
            setTextColor(Color.rgb(17, 31, 46))
            setTypeface(typeface, Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Server dashboard • v0.6 UI"
            textSize = 14f
            setTextColor(Color.rgb(92, 108, 124))
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(header)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Color.WHITE, 14, Color.rgb(221, 228, 235))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        homeTab = navButton("Home") { showTab(0) }
        recordingsTab = navButton("Recordings") { showTab(1) }
        settingsTab = navButton("Settings") { showTab(2) }
        tabs.addView(homeTab, LinearLayout.LayoutParams(0, dp(46), 1f))
        tabs.addView(recordingsTab, LinearLayout.LayoutParams(0, dp(46), 1f))
        tabs.addView(settingsTab, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })

        homePanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        recordingsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settingsPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(homePanel)
        root.addView(recordingsPanel)
        root.addView(settingsPanel)

        buildHome(homePanel)
        buildRecordings(recordingsPanel)
        buildSettings(settingsPanel, prefs)

        setContentView(scroll)
        showTab(0)
        updateConnectionSummary()
        requestAndStartLocal()
        refreshAudio()
        refreshLocation()
        refreshClientStatus()
    }

    private fun buildHome(parent: LinearLayout) {
        val overview = card().apply {
            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(TextView(this@MainActivity).apply {
                text = "CONNECTION"
                textSize = 12f
                setTextColor(Color.rgb(91, 105, 120))
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(TextView(this@MainActivity).apply {
                text = "AUTO REFRESH"
                textSize = 11.5f
                setTextColor(Color.rgb(33, 129, 79))
                setTypeface(typeface, Typeface.BOLD)
                background = pillBackground(Color.rgb(232, 248, 239), Color.rgb(181, 225, 199))
                setPadding(dp(10), dp(5), dp(10), dp(5))
            })
            addView(top)

            connectionSummary = TextView(this@MainActivity).apply {
                textSize = 18f
                setTextColor(Color.rgb(25, 41, 58))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(8))
            }
            addView(connectionSummary)

            status = TextView(this@MainActivity).apply {
                text = "Starting LAN receiver..."
                textSize = 13.5f
                setTextColor(Color.rgb(91, 105, 120))
            }
            addView(status)
        }
        parent.addView(overview, cardParams())

        val health = card().apply {
            addView(sectionTitle("CLIENT HEALTH"))
            clientStatus = TextView(this@MainActivity).apply {
                text = "Waiting for client status..."
                textSize = 15.5f
                setTextColor(Color.rgb(29, 45, 61))
                setPadding(0, dp(12), 0, 0)
                setLineSpacing(0f, 1.12f)
            }
            addView(clientStatus)
        }
        parent.addView(health, cardParams())

        val locationCard = card().apply {
            val titleRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(sectionTitle("LATEST LOCATION"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            titleRow.addView(TextView(this@MainActivity).apply {
                text = "GPS"
                textSize = 11f
                setTextColor(Color.rgb(31, 111, 235))
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(titleRow)

            locationInfo = TextView(this@MainActivity).apply {
                text = "Waiting for first location..."
                textSize = 15.5f
                setTextColor(Color.rgb(29, 45, 61))
                setPadding(0, dp(12), 0, dp(14))
                setLineSpacing(0f, 1.12f)
            }
            addView(locationInfo)

            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(primaryButton("Open Map", Color.rgb(31, 111, 235)) { openMap() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
            row.addView(outlineButton("History") {
                historyVisible = !historyVisible
                refreshLocation()
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
            addView(row)

            history = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(Color.rgb(75, 91, 107))
                visibility = View.GONE
                setPadding(0, dp(14), 0, 0)
            }
            addView(history)
        }
        parent.addView(locationCard, cardParams())

        val actions = card().apply {
            addView(sectionTitle("QUICK ACTIONS"))
            addView(helper("Use Refresh for local data. Internet Sync also checks the configured relay."))
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(outlineButton("Refresh") {
                refreshAudio(); refreshLocation(); refreshClientStatus(); updateConnectionSummary()
                status.text = "Local dashboard refreshed"
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
            row.addView(primaryButton("Internet Sync", Color.rgb(31, 111, 235)) {
                syncInternet(true)
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
            addView(row)
        }
        parent.addView(actions, cardParams())
    }

    private fun buildRecordings(parent: LinearLayout) {
        val recordingsCard = card().apply {
            val titleRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(TextView(this@MainActivity).apply {
                text = "Recordings"
                textSize = 22f
                setTextColor(Color.rgb(20, 34, 50))
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            recordingsCount = TextView(this@MainActivity).apply {
                text = "0 saved"
                textSize = 12.5f
                setTextColor(Color.rgb(91, 105, 120))
                setTypeface(typeface, Typeface.BOLD)
                background = pillBackground(Color.rgb(241, 245, 249), Color.rgb(221, 228, 235))
                setPadding(dp(10), dp(6), dp(10), dp(6))
            }
            titleRow.addView(recordingsCount)
            addView(titleRow)

            addView(helper("Encrypted recordings received by this Viewer appear here. Tap any item to decrypt and play locally."))
            addView(outlineButton("Refresh recordings") {
                refreshAudio(); syncInternet(true)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

            recordingsBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, 0)
            }
            addView(recordingsBox)
        }
        parent.addView(recordingsCard, cardParams())
    }

    private fun buildSettings(parent: LinearLayout, prefs: android.content.SharedPreferences) {
        val lan = card().apply {
            addView(sectionTitle("LAN SERVER"))
            addView(TextView(this@MainActivity).apply {
                text = "Same Wi-Fi / hotspot mode"
                textSize = 18f
                setTextColor(Color.rgb(25, 41, 58))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(4))
            })
            addView(helper("Client destination should be http://THIS-PHONE-IP:8080 when both phones are on the same reachable local network."))
            addView(primaryButton("Start LAN Server", Color.rgb(42, 142, 85)) { requestAndStartLocal() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))
        }
        parent.addView(lan, cardParams())

        val relay = card().apply {
            addView(sectionTitle("INTERNET / SIM RELAY"))
            addView(TextView(this@MainActivity).apply {
                text = "Remote connection"
                textSize = 18f
                setTextColor(Color.rgb(25, 41, 58))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(4))
            })
            addView(helper("For SIM data or different Wi-Fi networks, enter your deployed public HTTPS relay URL."))

            addView(fieldLabel("Relay URL"))
            relayUrl = EditText(this@MainActivity).apply {
                hint = "https://your-relay.example"
                setText(prefs.getString("relay_url", ""))
                textSize = 15.5f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                background = fieldBackground()
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            addView(relayUrl, fieldParams())

            addView(fieldLabel("Pair token").apply { setPadding(0, dp(14), 0, dp(6)) })
            token = EditText(this@MainActivity).apply {
                hint = "Pair token"
                setText(prefs.getString("pair_token", "DEMO-PAIR-2026"))
                textSize = 15.5f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                background = fieldBackground()
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            addView(token, fieldParams())

            addView(primaryButton("Save & Sync Internet", Color.rgb(31, 111, 235)) {
                prefs.edit()
                    .putString("relay_url", relayUrl.text.toString().trim())
                    .putString("pair_token", token.text.toString())
                    .apply()
                syncInternet(true)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(14) })
        }
        parent.addView(relay, cardParams())

        parent.addView(TextView(this).apply {
            text = "LAN mode works locally. SIM / different-Wi-Fi mode requires a reachable HTTPS relay backend."
            textSize = 12.5f
            setTextColor(Color.rgb(100, 113, 128))
            setPadding(dp(4), 0, dp(4), dp(8))
        })
    }

    private fun showTab(index: Int) {
        homePanel.visibility = if (index == 0) View.VISIBLE else View.GONE
        recordingsPanel.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsPanel.visibility = if (index == 2) View.VISIBLE else View.GONE
        styleNavButton(homeTab, index == 0)
        styleNavButton(recordingsTab, index == 1)
        styleNavButton(settingsTab, index == 2)
        if (index == 1) refreshAudio()
        if (index == 0) {
            refreshLocation()
            refreshClientStatus()
            updateConnectionSummary()
        }
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
        if (::token.isInitialized) {
            getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("pair_token", token.text.toString()).apply()
        }
        val i = Intent(this, ServerService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        if (::status.isInitialized) status.text = "LAN receiver is active"
        updateConnectionSummary()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            requestAndStartLocal()
        }
    }

    private fun syncInternet(force: Boolean) {
        if (!::relayUrl.isInitialized) return
        if (relayUrl.text.toString().trim().isBlank()) {
            if (force && ::status.isInitialized) status.text = "Internet relay is not configured; LAN mode remains available"
            updateConnectionSummary()
            return
        }
        if (!syncBusy.compareAndSet(false, true)) return
        if (::status.isInitialized) status.text = "Syncing Internet relay..."
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
            .putString("relay_url", relayUrl.text.toString().trim())
            .putString("pair_token", token.text.toString())
            .apply()
        io.submit {
            val result = RelayClient.sync(this)
            runOnUiThread {
                if (::status.isInitialized) status.text = result
                refreshAudio()
                refreshLocation()
                refreshClientStatus()
                updateConnectionSummary()
            }
            syncBusy.set(false)
        }
    }

    private fun updateConnectionSummary() {
        if (!::connectionSummary.isInitialized) return
        val savedRelay = if (::relayUrl.isInitialized) relayUrl.text.toString().trim() else getSharedPreferences("cfg", MODE_PRIVATE).getString("relay_url", "")?.trim().orEmpty()
        val relayConfigured = savedRelay.startsWith("https://", true)
        connectionSummary.text = "LAN  ${localIp()}:8080\nRelay  ${if (relayConfigured) "configured" else "not configured"}"
    }

    private fun refreshClientStatus() {
        if (!::clientStatus.isInitialized) return
        val value = getSharedPreferences("cfg", MODE_PRIVATE).getString("remote_status", "") ?: ""
        clientStatus.text = if (value.isBlank()) {
            "No Internet-relay heartbeat yet.\nLAN audio and location can still arrive directly."
        } else value
    }

    private fun refreshAudio() {
        if (!::recordingsBox.isInitialized || !::recordingsCount.isInitialized) return
        val dir = File(filesDir, "audio").apply { mkdirs() }
        files = dir.listFiles { f -> f.extension == "enc" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        recordingsCount.text = "${files.size} saved"
        recordingsBox.removeAllViews()

        if (files.isEmpty()) {
            recordingsBox.addView(TextView(this).apply {
                text = "No recordings received yet"
                textSize = 14.5f
                setTextColor(Color.rgb(100, 113, 128))
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(24), dp(8), dp(24))
                background = dashedPlaceholder()
            })
            return
        }

        files.forEachIndexed { index, enc ->
            val meta = File(dir, "${enc.nameWithoutExtension}.json")
            val started = if (meta.exists()) {
                try {
                    val j = JSONObject(meta.readText())
                    prettyTime(j.optString("started_at", ""))
                } catch (_: Exception) { enc.nameWithoutExtension.take(8) }
            } else enc.nameWithoutExtension.take(8)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(13), dp(12), dp(13))
                background = listItemBackground()
                isClickable = true
                isFocusable = true
                setOnClickListener { play(enc) }
            }
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            copy.addView(TextView(this).apply {
                text = started
                textSize = 15f
                setTextColor(Color.rgb(29, 45, 61))
                setTypeface(typeface, Typeface.BOLD)
            })
            copy.addView(TextView(this).apply {
                text = "${enc.length() / 1024} KB • encrypted audio"
                textSize = 12.5f
                setTextColor(Color.rgb(91, 105, 120))
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "PLAY"
                textSize = 12f
                setTextColor(Color.rgb(31, 111, 235))
                setTypeface(typeface, Typeface.BOLD)
                background = pillBackground(Color.rgb(235, 243, 255), Color.rgb(190, 211, 244))
                setPadding(dp(11), dp(7), dp(11), dp(7))
            })
            recordingsBox.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (index < files.lastIndex) bottomMargin = dp(8)
            })
        }
    }

    private fun refreshLocation() {
        if (!::locationInfo.isInitialized || !::history.isInitialized) return
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
            locationInfo.text = "Updated  $stamp\n${String.format(Locale.US, "%.6f", latestLat)}, ${String.format(Locale.US, "%.6f", latestLon)}\nAccuracy  $accText  •  $provider"
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
            if (::status.isInitialized) status.text = "No client location received yet"
            return
        }
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Client)")
        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { if (::status.isInitialized) status.text = "No map app available" }
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
                    if (::status.isInitialized) status.text = "Playback complete"
                }
                prepare(); start()
            }
            if (::status.isInitialized) status.text = "Playing recording"
        } catch (e: Exception) {
            if (::status.isInitialized) status.text = "Playback error: ${e.message}"
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
        background = rounded(Color.WHITE, 16, Color.rgb(225, 231, 237))
        elevation = dp(2).toFloat()
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(Color.rgb(91, 105, 120))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun helper(value: String) = TextView(this).apply {
        text = value
        textSize = 13.5f
        setTextColor(Color.rgb(75, 91, 107))
        setPadding(0, dp(8), 0, dp(12))
        setLineSpacing(0f, 1.08f)
    }

    private fun fieldLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.rgb(75, 91, 107))
        setPadding(0, dp(6), 0, dp(6))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        minHeight = 0
        minWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { action() }
    }

    private fun styleNavButton(button: Button, active: Boolean) {
        button.setTextColor(if (active) Color.WHITE else Color.rgb(67, 84, 101))
        button.setTypeface(button.typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
        button.background = if (active) {
            rounded(Color.rgb(31, 111, 235), 11, Color.rgb(31, 111, 235))
        } else {
            rounded(Color.TRANSPARENT, 11, Color.TRANSPARENT)
        }
    }

    private fun primaryButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14.5f
        setTextColor(Color.WHITE)
        background = rounded(color, 12, color)
        setOnClickListener { action() }
    }

    private fun outlineButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14.5f
        setTextColor(Color.rgb(31, 111, 235))
        background = rounded(Color.WHITE, 12, Color.rgb(177, 198, 226))
        setOnClickListener { action() }
    }

    private fun cardParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(14)
    }

    private fun fieldParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50))

    private fun fieldBackground() = rounded(Color.rgb(249, 251, 253), 12, Color.rgb(207, 216, 225))

    private fun listItemBackground() = rounded(Color.rgb(249, 251, 253), 13, Color.rgb(226, 232, 238))

    private fun dashedPlaceholder() = rounded(Color.rgb(250, 252, 254), 13, Color.rgb(226, 232, 238))

    private fun pillBackground(fill: Int, stroke: Int) = rounded(fill, 100, stroke)

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        io.shutdownNow()
        try { player?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
