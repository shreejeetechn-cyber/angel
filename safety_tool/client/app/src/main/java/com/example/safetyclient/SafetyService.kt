package com.example.safetyclient

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SafetyService : Service(), LocationListener {
    private val running = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var recorder: Recorder
    private lateinit var locationManager: LocationManager
    private val locationQueue by lazy { File(filesDir, "locations.jsonl") }
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private fun status(value: String) { prefs.edit().putString("status", value).apply() }

    override fun onCreate() {
        super.onCreate()
        recorder = Recorder(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("safety", "Safety session", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.compareAndSet(false, true)) {
            val notification = Notification.Builder(this, "safety")
                .setContentTitle("Safety Session active")
                .setContentText("Microphone and location sharing are active")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(7, notification)
            startLocationUpdates()
            worker.submit { loop() }
        }
        return START_NOT_STICKY
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status("Location permission missing")
            return
        }
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000L, 0f, this) }
        catch (e: Exception) { status("GPS error: ${e.javaClass.simpleName}") }
        try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000L, 0f, this) }
        catch (_: Exception) {}
    }

    private fun loop() {
        var chunkStartedAt = 0L
        var nextFlushAt = 0L
        var nextHeartbeatAt = 0L
        while (running.get()) {
            val now = System.currentTimeMillis()
            if (!recorder.active()) {
                try {
                    recorder.start()
                    chunkStartedAt = now
                    updateStatus("Recording active • 10-minute encrypted chunks")
                } catch (e: Exception) {
                    status("RECORD ERROR: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
                }
            } else if (now - chunkStartedAt >= 10 * 60 * 1000L) {
                try { recorder.stop() } catch (_: Exception) {}
                flushQueues()
                try {
                    recorder.start()
                    chunkStartedAt = System.currentTimeMillis()
                    updateStatus("Recording active • previous chunk queued/synced")
                } catch (e: Exception) {
                    status("RECORD ERROR: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
                }
            }

            if (now >= nextFlushAt) {
                flushQueues()
                nextFlushAt = now + 60000L
            }
            if (now >= nextHeartbeatAt) {
                sendHeartbeat()
                nextHeartbeatAt = now + 60000L
            }
            try { Thread.sleep(5000L) } catch (_: Exception) { break }
        }
        if (recorder.active()) try { recorder.stop() } catch (_: Exception) {}
    }

    private fun pendingAudioCount(): Int =
        File(filesDir, "pending").listFiles { f -> f.extension == "json" }?.size ?: 0

    private fun pendingLocationCount(): Int = if (!locationQueue.exists()) 0 else try {
        locationQueue.readLines().count { it.isNotBlank() }
    } catch (_: Exception) { 0 }

    private fun updateStatus(prefix: String) {
        val last = prefs.getString("last_sync", "never") ?: "never"
        status("$prefix\nPending audio: ${pendingAudioCount()}\nPending location: ${pendingLocationCount()}\nNetwork: ${networkType()}\nLast sync: $last")
    }

    private fun flushQueues() {
        val pending = File(filesDir, "pending")
        var audioSent = 0
        val metadataFiles = pending.listFiles { f -> f.extension == "json" }?.sortedBy { it.lastModified() } ?: emptyList()
        for (meta in metadataFiles) {
            val enc = File(pending, "${meta.nameWithoutExtension}.enc")
            if (!enc.exists()) { meta.delete(); continue }
            if (Net.sendAudio(this, enc, meta)) {
                enc.delete(); meta.delete(); audioSent++
            } else break
        }

        var locationsSent = 0
        if (locationQueue.exists()) {
            val lines = try { locationQueue.readLines().filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
            for (line in lines) {
                if (!Net.sendLocation(this, line)) break
                locationsSent++
            }
            if (locationsSent > 0) {
                locationQueue.writeText(lines.drop(locationsSent).joinToString("\n", postfix = if (locationsSent < lines.size) "\n" else ""))
            }
        }

        if (audioSent > 0 || locationsSent > 0) {
            val stamp = LocalDateTime.now().toString().replace('T', ' ').take(19)
            prefs.edit().putString("last_sync", stamp).apply()
        }
        updateStatus(if (audioSent > 0 || locationsSent > 0) "Upload OK • audio=$audioSent location=$locationsSent" else "Safety Session active")
    }

    private fun sendHeartbeat() {
        Net.sendHeartbeat(
            this,
            batteryPercent(),
            networkType(),
            pendingAudioCount(),
            pendingLocationCount(),
            recorder.active()
        )
    }

    private fun batteryPercent(): Int = try {
        (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) { -1 }

    private fun networkType(): String = try {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "offline"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "offline"
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    } catch (_: Exception) { "unknown" }

    override fun onLocationChanged(location: Location) {
        locationQueue.appendText(JSONObject().apply {
            put("ts", Instant.now().toString())
            put("lat", location.latitude)
            put("lon", location.longitude)
            put("accuracy_m", location.accuracy.toDouble())
            put("provider", location.provider ?: "")
        }.toString() + "\n")
    }

    override fun onDestroy() {
        running.set(false)
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        worker.shutdownNow()
        status("Stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
