package com.example.safetyclient

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val requestCode = 41
    private lateinit var ip: EditText
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val p = getSharedPreferences("cfg", MODE_PRIVATE)
            status.text = "STATUS: " + (p.getString("status", "Not started") ?: "Not started")
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36,56,36,36) }
        root.addView(TextView(this).apply { text="Safety Client Test v0.3"; textSize=24f })
        root.addView(TextView(this).apply { text="Phone 2 का server IP डालें। Test build में 2-minute encrypted audio chunks बनेंगे."; textSize=15f; setPadding(0,16,0,12) })
        ip = EditText(this).apply { hint="Server phone IP"; setText(prefs.getString("server_ip", "")) }
        root.addView(ip)
        status = TextView(this).apply { textSize=17f; setPadding(0,20,0,20) }
        root.addView(status)
        root.addView(Button(this).apply { text="START"; setOnClickListener { prefs.edit().putString("server_ip", ip.text.toString().trim()).putString("status","Checking permissions...").apply(); requestAndStart() } })
        root.addView(Button(this).apply { text="STOP"; setOnClickListener { stopService(Intent(this@MainActivity, SafetyService::class.java)); prefs.edit().putString("status","Stopped").apply() } })
        setContentView(root)
    }

    override fun onResume(){ super.onResume(); handler.post(refresh) }
    override fun onPause(){ handler.removeCallbacks(refresh); super.onPause() }

    private fun requestAndStart() {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        val missing = p.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startNow() else requestPermissions(missing.toTypedArray(), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startNow()
        else getSharedPreferences("cfg",MODE_PRIVATE).edit().putString("status","Permission denied").apply()
    }

    private fun startNow() {
        getSharedPreferences("cfg",MODE_PRIVATE).edit().putString("status","Starting service...").apply()
        val i = Intent(this, SafetyService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }
}
