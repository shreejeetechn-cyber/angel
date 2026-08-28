package com.example.safetyclient

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

class MainActivity : Activity() {
    private val requestCode = 41
    private lateinit var endpoint: EditText
    private lateinit var token: EditText
    private lateinit var status: TextView
    private lateinit var statusCard: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val p = getSharedPreferences("cfg", MODE_PRIVATE)
            val value = p.getString("status", "Not started") ?: "Not started"
            status.text = value
            statusCard.background = cardBackground(
                when {
                    value.contains("record", true) || value.contains("upload ok", true) || value.contains("active", true) -> Color.rgb(232, 247, 238)
                    value.contains("error", true) || value.contains("denied", true) -> Color.rgb(255, 235, 235)
                    value.contains("pending", true) -> Color.rgb(255, 247, 225)
                    else -> Color.rgb(242, 245, 248)
                }
            )
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

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Safety Client"
            textSize = 28f
            setTextColor(Color.rgb(20, 31, 45))
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Secure audio + location sharing"
            textSize = 14f
            setTextColor(Color.rgb(91, 105, 120))
            setPadding(0, dp(4), 0, dp(18))
        })

        statusCard = card().apply {
            addView(sectionTitle("SESSION STATUS"))
            status = TextView(this@MainActivity).apply {
                textSize = 16f
                setTextColor(Color.rgb(30, 45, 60))
                setPadding(0, dp(8), 0, 0)
            }
            addView(status)
        }
        root.addView(statusCard, cardParams())

        val connectionCard = card().apply {
            addView(sectionTitle("CONNECTION"))
            addView(helper("Same Wi-Fi: http://PHONE2-IP:8080\nInternet/SIM: https://YOUR-RELAY-DOMAIN"))

            endpoint = EditText(this@MainActivity).apply {
                hint = "Destination URL"
                setText(savedEndpoint)
                textSize = 16f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                background = fieldBackground()
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            addView(endpoint, fieldParams())

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
        }
        root.addView(connectionCard, cardParams())

        val actionCard = card().apply {
            addView(sectionTitle("SAFETY SESSION"))
            addView(helper("Start once while this screen is visible. Android will show the required microphone/location indicators while the session is active."))

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            row.addView(Button(this@MainActivity).apply {
                text = "START"
                isAllCaps = false
                textSize = 16f
                setTextColor(Color.WHITE)
                background = buttonBackground(Color.rgb(31, 111, 235))
                setOnClickListener {
                    prefs.edit()
                        .putString("endpoint", endpoint.text.toString().trim())
                        .putString("pair_token", token.text.toString())
                        .putString("status", "Checking permissions...")
                        .apply()
                    requestAndStart()
                }
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })

            row.addView(Button(this@MainActivity).apply {
                text = "STOP"
                isAllCaps = false
                textSize = 16f
                setTextColor(Color.rgb(180, 48, 48))
                background = outlinedButtonBackground(Color.rgb(220, 76, 76))
                setOnClickListener {
                    stopService(Intent(this@MainActivity, SafetyService::class.java))
                    prefs.edit().putString("status", "Stopped").apply()
                }
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
            addView(row)
        }
        root.addView(actionCard, cardParams())

        root.addView(TextView(this).apply {
            text = "Audio is stored as encrypted chunks before upload. Keep the destination and pair token identical to the Viewer/Relay configuration."
            textSize = 12.5f
            setTextColor(Color.rgb(100, 113, 128))
            setPadding(dp(4), dp(4), dp(4), 0)
        })

        setContentView(scroll)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
