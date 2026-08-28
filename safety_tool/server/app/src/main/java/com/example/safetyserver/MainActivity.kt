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
import android.os.StatFs
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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
    private val bg = Color.rgb(5, 13, 21)
    private val surface = Color.rgb(12, 25, 37)
    private val surface2 = Color.rgb(15, 31, 45)
    private val border = Color.rgb(38, 57, 75)
    private val text = Color.rgb(239, 246, 252)
    private val muted = Color.rgb(145, 164, 183)
    private val green = Color.rgb(48, 221, 103)
    private val blue = Color.rgb(52, 143, 255)
    private val red = Color.rgb(255, 83, 91)
    private val amber = Color.rgb(255, 174, 66)

    private lateinit var status: TextView
    private lateinit var signalLan: TextView
    private lateinit var signalRelay: TextView
    private lateinit var signalSync: TextView
    private lateinit var lanMini: TextView
    private lateinit var recordingsMini: TextView
    private lateinit var locationMini: TextView
    private lateinit var clientStatus: TextView
    private lateinit var locationInfo: TextView
    private lateinit var history: TextView
    private lateinit var systemOverview: TextView
    private lateinit var relayUrl: EditText
    private lateinit var token: EditText
    private lateinit var storageInfo: TextView
    private lateinit var recordingsBox: LinearLayout
    private lateinit var recordingsCount: TextView

    private lateinit var homeScroll: ScrollView
    private lateinit var recordingsScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var homeTab: Button
    private lateinit var recordingsTab: Button
    private lateinit var settingsTab: Button

    private var files: List<File> = emptyList()
    private var player: MediaPlayer? = null
    private var latestLat: Double? = null
    private var latestLon: Double? = null
    private var latestLocationStamp: String = ""
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
            updateSignals()
            updateSystemOverview()
            val now = System.currentTimeMillis()
            if (now - lastRemotePoll >= 30_000L) {
                lastRemotePoll = now
                syncInternet(false)
            }
            handler.postDelayed(this, 3_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        root.addView(buildHeader())
        root.addView(buildSignalStrip())

        val host = FrameLayout(this)
        homeScroll = screenScroll(buildHome())
        recordingsScroll = screenScroll(buildRecordings())
        settingsScroll = screenScroll(buildSettings(prefs))
        host.addView(homeScroll)
        host.addView(recordingsScroll)
        host.addView(settingsScroll)
        root.addView(host, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildBottomNav())

        setContentView(root)
        showTab(0)
        requestAndStartLocal()
        refreshAudio()
        refreshLocation()
        refreshClientStatus()
        updateSignals()
        updateSystemOverview()
    }

    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            addView(TextView(this@MainActivity).apply {
                text = "☰"
                textSize = 22f
                setTextColor(muted)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(34), dp(38)))
            addView(TextView(this@MainActivity).apply {
                text = "▣"
                textSize = 23f
                setTextColor(green)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(34), dp(38)).apply { marginStart = dp(4) })
            addView(TextView(this@MainActivity).apply {
                text = "Safety Server Viewer"
                textSize = 19f
                setTextColor(text)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
            addView(TextView(this@MainActivity).apply {
                text = "v0.6c"
                textSize = 11f
                setTextColor(muted)
                gravity = Gravity.CENTER
                background = rounded(surface2, 10, border)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
        }
    }

    private fun buildSignalStrip(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setBackgroundColor(Color.rgb(7, 18, 28))
        }
        signalLan = signal("● LAN", green)
        signalRelay = signal("● RELAY", muted)
        signalSync = signal("↻ AUTO 3s", blue)
        strip.addView(signalLan, LinearLayout.LayoutParams(0, dp(30), 1f))
        strip.addView(signalRelay, LinearLayout.LayoutParams(0, dp(30), 1f))
        strip.addView(signalSync, LinearLayout.LayoutParams(0, dp(30), 1f))
        return strip
    }

    private fun buildHome(): LinearLayout {
        val p = panel()

        val miniRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lanMini = miniBlock("LAN / RELAY", "Starting…")
        recordingsMini = miniBlock("RECORDINGS", "0 saved")
        locationMini = miniBlock("LOCATION", "Waiting…")
        miniRow.addView(lanMini, miniParams(0, 5))
        miniRow.addView(recordingsMini, miniParams(5, 5))
        miniRow.addView(locationMini, miniParams(5, 0))
        p.addView(miniRow, sectionParams())

        val clientCard = card().apply {
            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(sectionTitle("SELECTED CLIENT"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(TextView(this@MainActivity).apply {
                text = "ACTIVE VIEW"
                textSize = 10.5f
                setTextColor(green)
                setTypeface(typeface, Typeface.BOLD)
                background = rounded(Color.rgb(9, 48, 30), 9, Color.rgb(25, 94, 58))
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
            addView(top)
            clientStatus = TextView(this@MainActivity).apply {
                text = "Waiting for client data…"
                textSize = 14f
                setTextColor(text)
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(0f, 1.08f)
            }
            addView(clientStatus)
        }
        p.addView(clientCard, cardParams())

        val locationCard = card().apply {
            val rowTitle = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            rowTitle.addView(sectionTitle("LATEST LOCATION"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            rowTitle.addView(TextView(this@MainActivity).apply {
                text = "● GPS"
                textSize = 10.5f
                setTextColor(green)
            })
            addView(rowTitle)

            locationInfo = TextView(this@MainActivity).apply {
                text = "Waiting for first location…"
                textSize = 14f
                setTextColor(text)
                setPadding(0, dp(8), 0, dp(9))
            }
            addView(locationInfo)

            val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(compactButton("MAP", green) { openMap() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
            actions.addView(compactButton("SYNC", blue) { syncInternet(true) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
            addView(actions)

            history = TextView(this@MainActivity).apply {
                visibility = View.GONE
                textSize = 12.5f
                setTextColor(muted)
                setPadding(0, dp(9), 0, 0)
            }
            addView(history)

            addView(TextView(this@MainActivity).apply {
                text = "Location history"
                textSize = 12f
                setTextColor(blue)
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, 0)
                isClickable = true
                setOnClickListener {
                    historyVisible = !historyVisible
                    refreshLocation()
                }
            })
        }
        p.addView(locationCard, cardParams())

        val systemCard = card().apply {
            addView(sectionTitle("SYSTEM OVERVIEW"))
            systemOverview = TextView(this@MainActivity).apply {
                textSize = 12.8f
                setTextColor(text)
                setPadding(0, dp(8), 0, 0)
                setLineSpacing(0f, 1.15f)
            }
            addView(systemOverview)
        }
        p.addView(systemCard, cardParams())

        status = TextView(this).apply {
            text = "Starting LAN receiver…"
            textSize = 11.5f
            setTextColor(muted)
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        p.addView(status)
        return p
    }

    private fun buildRecordings(): LinearLayout {
        val p = panel()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(this).apply {
            text = "Recordings"
            textSize = 20f
            setTextColor(text)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        recordingsCount = TextView(this).apply {
            text = "0"
            textSize = 11.5f
            setTextColor(green)
            background = rounded(Color.rgb(9, 48, 30), 10, Color.rgb(25, 94, 58))
            setPadding(dp(9), dp(5), dp(9), dp(5))
        }
        head.addView(recordingsCount)
        p.addView(head, sectionParams())

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filterRow.addView(filterChip("ALL", true), LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        filterRow.addView(filterChip("NEWEST", false), LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        filterRow.addView(filterChip("ENCRYPTED", false), LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginStart = dp(4) })
        p.addView(filterRow, sectionParams())

        recordingsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        p.addView(recordingsBox)

        p.addView(compactButton("REFRESH RECORDINGS", blue) {
            refreshAudio(); syncInternet(true)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(5); bottomMargin = dp(10) })
        return p
    }

    private fun buildSettings(prefs: android.content.SharedPreferences): LinearLayout {
        val p = panel()

        p.addView(settingsTitle("CONNECTION"))
        val connectionCard = card().apply {
            addView(fieldLabel("Relay URL"))
            relayUrl = EditText(this@MainActivity).apply {
                hint = "https://your-relay.example"
                setHintTextColor(Color.rgb(92, 112, 132))
                setTextColor(text)
                setText(prefs.getString("relay_url", ""))
                textSize = 13.5f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                background = fieldBackground()
                setPadding(dp(11), dp(8), dp(11), dp(8))
            }
            addView(relayUrl, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(43)))

            addView(fieldLabel("Pair token").apply { setPadding(0, dp(10), 0, dp(5)) })
            token = EditText(this@MainActivity).apply {
                hint = "Pair token"
                setHintTextColor(Color.rgb(92, 112, 132))
                setTextColor(text)
                setText(prefs.getString("pair_token", "DEMO-PAIR-2026"))
                textSize = 13.5f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
                background = fieldBackground()
                setPadding(dp(11), dp(8), dp(11), dp(8))
            }
            addView(token, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(43)))

            val buttons = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            buttons.addView(compactButton("START LAN", green) { requestAndStartLocal() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
            buttons.addView(compactButton("SAVE + SYNC", blue) {
                prefs.edit()
                    .putString("relay_url", relayUrl.text.toString().trim())
                    .putString("pair_token", token.text.toString())
                    .apply()
                syncInternet(true)
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
            addView(buttons)
        }
        p.addView(connectionCard, cardParams())

        p.addView(settingsTitle("PREFERENCES"))
        p.addView(infoRow("Auto Refresh", "Every 3 seconds", "ON", green), cardParams())
        p.addView(infoRow("Remote Poll", "Internet relay check", "30s", blue), cardParams())

        p.addView(settingsTitle("SECURITY"))
        p.addView(infoRow("Encrypted Storage", "Client audio remains encrypted until Viewer playback", "SECURE", green), cardParams())
        p.addView(infoRow("Viewer Access", "Pair token required for relay access", "PAIRED", blue), cardParams())

        p.addView(settingsTitle("STORAGE"))
        val storageCard = card().apply {
            storageInfo = TextView(this@MainActivity).apply {
                textSize = 13f
                setTextColor(text)
            }
            addView(storageInfo)
        }
        p.addView(storageCard, cardParams())

        p.addView(TextView(this).apply {
            text = "SIM / different-Wi-Fi mode requires a reachable HTTPS relay backend. Google Drive backup is not enabled in this build."
            textSize = 11.5f
            setTextColor(muted)
            setPadding(dp(4), 0, dp(4), dp(10))
        })
        return p
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(7))
            setBackgroundColor(Color.rgb(7, 17, 26))
        }
        homeTab = navButton("⌂\nHome") { showTab(0) }
        recordingsTab = navButton("≋\nRecordings") { showTab(1) }
        settingsTab = navButton("⚙\nSettings") { showTab(2) }
        nav.addView(homeTab, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(recordingsTab, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(settingsTab, LinearLayout.LayoutParams(0, dp(54), 1f))
        return nav
    }

    private fun showTab(index: Int) {
        homeScroll.visibility = if (index == 0) View.VISIBLE else View.GONE
        recordingsScroll.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsScroll.visibility = if (index == 2) View.VISIBLE else View.GONE
        styleNav(homeTab, index == 0)
        styleNav(recordingsTab, index == 1)
        styleNav(settingsTab, index == 2)
        if (index == 1) refreshAudio()
        if (index == 0) {
            refreshLocation()
            refreshClientStatus()
            updateSignals()
            updateSystemOverview()
        }
        if (index == 2) updateStorageInfo()
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
        if (::status.isInitialized) status.text = "LAN receiver active at ${localIp()}:8080"
        updateSignals()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            requestAndStartLocal()
        }
    }

    private fun syncInternet(force: Boolean) {
        if (!::relayUrl.isInitialized) return
        val value = relayUrl.text.toString().trim()
        if (value.isBlank()) {
            if (force && ::status.isInitialized) status.text = "Relay not configured • LAN mode still available"
            updateSignals()
            return
        }
        if (!value.startsWith("https://", ignoreCase = true)) {
            if (force && ::status.isInitialized) status.text = "Relay URL must start with https://"
            return
        }
        if (!syncBusy.compareAndSet(false, true)) return
        if (::status.isInitialized) status.text = "Syncing relay…"
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
            .putString("relay_url", value)
            .putString("pair_token", token.text.toString())
            .apply()
        io.submit {
            val result = RelayClient.sync(this)
            val ok = result.contains("OK", ignoreCase = true)
            if (ok) {
                getSharedPreferences("cfg", MODE_PRIVATE).edit()
                    .putLong("viewer_last_sync_ms", System.currentTimeMillis())
                    .apply()
            }
            runOnUiThread {
                if (::status.isInitialized) status.text = result
                refreshAudio()
                refreshLocation()
                refreshClientStatus()
                updateSignals()
                updateSystemOverview()
                updateStorageInfo()
            }
            syncBusy.set(false)
        }
    }

    private fun updateSignals() {
        if (!::signalLan.isInitialized) return
        val ip = localIp()
        signalLan.text = "● LAN $ip"
        signalLan.setTextColor(if (ip != "unknown") green else amber)

        val relay = if (::relayUrl.isInitialized) relayUrl.text.toString().trim() else getSharedPreferences("cfg", MODE_PRIVATE).getString("relay_url", "").orEmpty()
        val relayOk = relay.startsWith("https://", ignoreCase = true)
        signalRelay.text = if (relayOk) "● RELAY READY" else "● RELAY OFF"
        signalRelay.setTextColor(if (relayOk) green else muted)

        val last = getSharedPreferences("cfg", MODE_PRIVATE).getLong("viewer_last_sync_ms", 0L)
        signalSync.text = if (last > 0L) "↻ ${shortTime(last)}" else "↻ AUTO 3s"
        signalSync.setTextColor(blue)

        if (::lanMini.isInitialized) {
            lanMini.text = "LAN / RELAY\n${if (ip == "unknown") "No LAN" else "Connected"}\n${if (relayOk) "Relay ready" else "$ip:8080"}"
        }
    }

    private fun refreshClientStatus() {
        if (!::clientStatus.isInitialized) return
        val remote = getSharedPreferences("cfg", MODE_PRIVATE).getString("remote_status", "").orEmpty()
        clientStatus.text = if (remote.isNotBlank()) {
            remote.replace("Client:", "Status:")
        } else {
            val loc = if (latestLocationStamp.isBlank()) "No location received" else "Last location: $latestLocationStamp"
            "LAN receiver active\n$loc\nInternet heartbeat: not received"
        }
    }

    private fun refreshAudio() {
        val dir = File(filesDir, "audio").apply { mkdirs() }
        files = dir.listFiles { f -> f.extension == "enc" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (::recordingsCount.isInitialized) recordingsCount.text = "${files.size} saved"
        if (::recordingsMini.isInitialized) recordingsMini.text = "RECORDINGS\n${files.size}\nEncrypted"
        if (!::recordingsBox.isInitialized) return

        recordingsBox.removeAllViews()
        if (files.isEmpty()) {
            recordingsBox.addView(TextView(this).apply {
                text = "No recordings received yet"
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(24), dp(6), dp(24))
            })
            return
        }

        files.forEach { enc ->
            val meta = File(dir, "${enc.nameWithoutExtension}.json")
            val started = try {
                if (meta.exists()) prettyTime(JSONObject(meta.readText()).optString("started_at", "")) else "unknown time"
            } catch (_: Exception) { "unknown time" }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(8), dp(8))
                background = rounded(surface, 12, border)
                isClickable = true
                setOnClickListener { play(enc) }
            }
            row.addView(TextView(this).apply {
                text = "▶"
                textSize = 17f
                setTextColor(green)
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(7, 47, 29), 20, Color.rgb(25, 111, 62))
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
            row.addView(TextView(this).apply {
                text = "$started\n${enc.length() / 1024} KB • encrypted audio"
                textSize = 12.8f
                setTextColor(text)
                setPadding(dp(10), 0, dp(4), 0)
                maxLines = 2
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "⋮"
                textSize = 20f
                setTextColor(muted)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(26), dp(38)))
            recordingsBox.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) })
        }
    }

    private fun refreshLocation() {
        if (!::locationInfo.isInitialized) return
        val f = File(filesDir, "locations.jsonl")
        if (!f.exists() || f.length() == 0L) {
            locationInfo.text = "Waiting for first location…"
            if (::locationMini.isInitialized) locationMini.text = "LOCATION\nWaiting\nNo fix"
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
            latestLocationStamp = prettyTime(j.optString("ts", ""))
            val accText = if (accuracy >= 0) "±${String.format(Locale.US, "%.0f", accuracy)}m" else "accuracy ?"
            locationInfo.text = "${String.format(Locale.US, "%.5f", latestLat)}, ${String.format(Locale.US, "%.5f", latestLon)}\n$latestLocationStamp • $accText • $provider"
            if (::locationMini.isInitialized) locationMini.text = "LOCATION\n${String.format(Locale.US, "%.3f", latestLat)}\n$accText"
        } catch (e: Exception) {
            locationInfo.text = "Location error: ${e.message}"
        }

        if (historyVisible) {
            history.visibility = View.VISIBLE
            history.text = lines.takeLast(8).asReversed().mapNotNull { line ->
                try {
                    val j = JSONObject(line)
                    val t = prettyTime(j.optString("ts", ""))
                    val lat = j.getDouble("lat")
                    val lon = j.getDouble("lon")
                    "$t  ${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)}"
                } catch (_: Exception) { null }
            }.joinToString("\n")
        } else history.visibility = View.GONE
    }

    private fun updateSystemOverview() {
        if (!::systemOverview.isInitialized) return
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val relay = prefs.getString("relay_url", "").orEmpty().startsWith("https://", true)
        val last = prefs.getLong("viewer_last_sync_ms", 0L)
        val storage = storageSummary()
        systemOverview.text =
            "Device uptime   ${deviceUptime()}        Relay   ${if (relay) "Connected" else "Not configured"}\n" +
            "Last sync       ${if (last > 0) shortTime(last) else "Never"}        Recordings   ${files.size}\n" +
            "Storage         ${storage.first} / ${storage.second}        Alerts   ${if (status.text.toString().contains("error", true)) "1" else "0"}"
    }

    private fun updateStorageInfo() {
        if (!::storageInfo.isInitialized) return
        val s = storageSummary()
        storageInfo.text = "Internal app storage\nUsed ${s.first} of ${s.second}\nEncrypted recordings: ${files.size}\nBackup: not configured"
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
                prepare()
                start()
            }
            if (::status.isInitialized) status.text = "Playing ${enc.nameWithoutExtension.take(8)}"
        } catch (e: Exception) {
            if (::status.isInitialized) status.text = "Playback error: ${e.message}"
        }
    }

    private fun prettyTime(raw: String): String = try {
        val z = Instant.parse(raw).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("dd MMM, hh:mm:ss a").format(z)
    } catch (_: Exception) { raw.ifBlank { "unknown" } }

    private fun shortTime(ms: Long): String = try {
        val z = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("hh:mm a").format(z)
    } catch (_: Exception) { "Synced" }

    private fun deviceUptime(): String {
        val total = SystemClock.elapsedRealtime() / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        return "${h}h ${m}m"
    }

    private fun storageSummary(): Pair<String, String> = try {
        val stat = StatFs(filesDir.absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free
        Pair(formatBytes(used), formatBytes(total))
    } catch (_: Exception) { Pair("?", "?") }

    private fun formatBytes(v: Long): String {
        val gb = v / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%.0f MB", v / (1024.0 * 1024.0))
    }

    private fun localIp(): String = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress ?: "unknown"
    } catch (_: Exception) { "unknown" }

    private fun screenScroll(content: View) = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(bg)
        addView(content)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(4))
        setBackgroundColor(bg)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(11), dp(10), dp(11), dp(10))
        background = rounded(surface, 12, border)
    }

    private fun miniBlock(title: String, value: String) = TextView(this).apply {
        text = "$title\n$value"
        textSize = 11.5f
        setTextColor(text)
        setLineSpacing(0f, 1.08f)
        setPadding(dp(9), dp(8), dp(9), dp(8))
        background = rounded(surface, 11, border)
        minHeight = dp(78)
    }

    private fun miniParams(start: Int, end: Int) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = dp(start)
        marginEnd = dp(end)
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 10.8f
        setTextColor(muted)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun settingsTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 11.5f
        setTextColor(blue)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(2), dp(2), 0, dp(6))
    }

    private fun fieldLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 11.5f
        setTextColor(muted)
        setPadding(0, 0, 0, dp(5))
    }

    private fun signal(label: String, color: Int) = TextView(this).apply {
        text = label
        textSize = 10.5f
        setTextColor(color)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        maxLines = 1
    }

    private fun compactButton(label: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color)
        background = rounded(Color.rgb(8, 22, 34), 10, color)
        setOnClickListener { action() }
        minHeight = 0
        minWidth = 0
        setPadding(dp(6), 0, dp(6), 0)
    }

    private fun filterChip(label: String, selected: Boolean) = TextView(this).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (selected) green else muted)
        background = rounded(if (selected) Color.rgb(7, 42, 27) else surface, 10, if (selected) green else border)
    }

    private fun infoRow(title: String, subtitle: String, badge: String, badgeColor: Int): LinearLayout {
        return card().apply {
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this@MainActivity).apply {
                text = "$title\n$subtitle"
                textSize = 12.5f
                setTextColor(text)
                maxLines = 2
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this@MainActivity).apply {
                text = badge
                textSize = 10.5f
                setTextColor(badgeColor)
                setTypeface(typeface, Typeface.BOLD)
                background = rounded(Color.rgb(8, 26, 37), 9, badgeColor)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            })
            addView(row)
        }
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11.5f
        setTextColor(muted)
        gravity = Gravity.CENTER
        background = rounded(Color.TRANSPARENT, 10, Color.TRANSPARENT)
        minHeight = 0
        minWidth = 0
        setPadding(0, 0, 0, 0)
        setOnClickListener { action() }
    }

    private fun styleNav(button: Button, active: Boolean) {
        button.setTextColor(if (active) green else muted)
        button.setTypeface(button.typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
        button.background = rounded(if (active) Color.rgb(8, 34, 25) else Color.TRANSPARENT, 10, Color.TRANSPARENT)
    }

    private fun fieldBackground() = rounded(Color.rgb(8, 21, 32), 9, border)

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun sectionParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(8)
    }

    private fun cardParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        io.shutdownNow()
        try { player?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
