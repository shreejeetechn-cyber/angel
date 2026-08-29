package com.example.safetyclient

import android.content.Context
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Net {
    private const val TOKEN="DEMO-PAIR-2026"
    private fun base(ctx:Context):String? { val ip=ctx.getSharedPreferences("cfg",Context.MODE_PRIVATE).getString("server_ip","")?.trim(); return if(ip.isNullOrEmpty()) null else "http://$ip:8080" }

    fun sendAudio(ctx:Context, enc:File, meta:File):Boolean {
        val b=base(ctx)?:return false
        return try {
            val c=(URL("$b/audio").openConnection() as HttpURLConnection).apply { requestMethod="POST"; doOutput=true; connectTimeout=5000; readTimeout=10000; setRequestProperty("Authorization","Bearer $TOKEN"); setRequestProperty("X-Meta",Base64.encodeToString(meta.readBytes(),Base64.NO_WRAP)); setFixedLengthStreamingMode(enc.length()) }
            c.outputStream.use { out -> enc.inputStream().use{it.copyTo(out)} }
            val ok=c.responseCode in 200..299; c.disconnect(); ok
        } catch(_:Exception){ false }
    }

    fun sendLocation(ctx:Context, json:String):Boolean {
        val b=base(ctx)?:return false
        return try {
            val bytes=json.toByteArray(); val c=(URL("$b/location").openConnection() as HttpURLConnection).apply { requestMethod="POST"; doOutput=true; connectTimeout=5000; readTimeout=10000; setRequestProperty("Authorization","Bearer $TOKEN"); setRequestProperty("Content-Type","application/json"); setFixedLengthStreamingMode(bytes.size) }
            c.outputStream.use{it.write(bytes)}; val ok=c.responseCode in 200..299; c.disconnect(); ok
        } catch(_:Exception){ false }
    }
}
