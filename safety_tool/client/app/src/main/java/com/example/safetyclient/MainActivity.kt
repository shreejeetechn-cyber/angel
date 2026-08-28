package com.example.safetyclient

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val requestCode = 41
    private lateinit var endpoint: EditText
    private lateinit var token: EditText
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val p = getSharedPreferences("cfg", MODE_PRIVATE)
            status.text = "STATUS\n" + (p.getString("status", "Not started") ?: "Not started")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val oldIp = prefs.getString("server_ip", "")?.trim().orEmpty()
        val savedEndpoint = prefs.getString("endpoint", "")?.trim().orEmpty().ifBlank {
            if (oldIp.isNotBlank()) "http://$oldIp:8080" else ""
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 52, 36, 36)
        }
        root.addView(TextView(this).apply { text = "Safety Client v0.4"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Visible, user-started Safety Session\nLAN: http://PHONE2-IP:8080\nInternet: https://YOUR-RELAY-DOMAIN"
            textSize = 14f
            setPadding(0, 14, 0, 10)
        })

        endpoint = EditText(this).apply {
            hint = "Destination URL"
            setText(savedEndpoint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(endpoint)

        token = EditText(this).apply {
            hint = "Pair token"
            setText(prefs.getString("pair_token", "DEMO-PAIR-2026"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(token)

        status = TextView(this).apply { textSize = 16f; setPadding(0, 18, 0, 18) }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "START SAFETY SESSION"
            setOnClickListener {
                prefs.edit()
                    .putString("endpoint", endpoint.text.toString().trim())
                    .putString("pair_token", token.text.toString())
                    .putString("status", "Checking permissions...")
                    .apply()
                requestAndStart()
            }
        })
        root.addView(Button(this).apply {
            text = "STOP SAFETY SESSION"
            setOnClickListener {
                stopService(Intent(this@MainActivity, SafetyService::class.java))
                prefs.edit().putString("status", "Stopped").apply()
            }
        })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }

    private fun requestAndStart() {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        val missing = p.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startNow() else requestPermissions(missing.toTypedArray(), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startNow()
        else getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("status", "Permission denied").apply()
    }

    private fun startNow() {
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("status", "Starting visible Safety Session...").apply()
        val i = Intent(this, SafetyService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }
}
