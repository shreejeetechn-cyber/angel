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
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SafetyService:Service(), LocationListener {
    private val running=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var rec:Recorder
    private lateinit var lm:LocationManager
    private val locFile by lazy { File(filesDir,"locations.jsonl") }
    private fun status(s:String){ getSharedPreferences("cfg",MODE_PRIVATE).edit().putString("status",s).apply() }

    override fun onCreate(){
        super.onCreate()
        rec=Recorder(this)
        lm=getSystemService(LOCATION_SERVICE) as LocationManager
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("safety","Safety service",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)}
        )
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(running.compareAndSet(false,true)){
            val n=Notification.Builder(this,"safety").setContentTitle("Safety Client").setContentText("Safety service active").setSmallIcon(android.R.drawable.ic_lock_idle_lock).setOngoing(true).build()
            if(Build.VERSION.SDK_INT>=30) startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(7,n)
            status("Service active; starting recorder...")
            startLocations()
            worker.submit{loop()}
        }
        return START_STICKY
    }

    private fun startLocations(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){status("Location permission missing");return}
        try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,30000L,0f,this)}catch(e:Exception){status("GPS error: ${e.javaClass.simpleName}")}
        try{lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,30000L,0f,this)}catch(_:Exception){}
    }

    private fun loop(){
        var started=0L
        var upload=0L
        while(running.get()){
            val now=System.currentTimeMillis()
            val inWindow=!LocalTime.now().isBefore(LocalTime.of(6,0))
            if(inWindow){
                if(!rec.active()){
                    try{rec.start();started=now;status("Recording active; next test chunk in 2 min")}
                    catch(e:Exception){status("RECORD ERROR: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}")}
                } else if(now-started>=2*60*1000L){
                    status("Closing 2-min chunk...")
                    try{rec.stop()}catch(e:Exception){status("STOP ERROR: ${e.javaClass.simpleName}")}
                    flush()
                    try{rec.start();started=System.currentTimeMillis();status("Recording active; previous chunk upload attempted")}
                    catch(e:Exception){status("RECORD ERROR: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}")}
                }
            } else if(rec.active()) rec.stop()
            if(now-upload>=30000L){flush();upload=now}
            try{Thread.sleep(3000)}catch(_:Exception){break}
        }
        if(rec.active())rec.stop()
    }

    private fun flush(){
        val dir=File(filesDir,"pending")
        var audioSent=0
        dir.listFiles{f->f.extension=="json"}?.sortedBy{it.lastModified()}?.forEach { m ->
            val e=File(dir,"${m.nameWithoutExtension}.enc")
            if(!e.exists()){m.delete();return@forEach}
            if(Net.sendAudio(this,e,m)){e.delete();m.delete();audioSent++} else { status("Recording active; server upload pending"); return }
        }
        if(locFile.exists()){
            val lines=locFile.readLines().filter{it.isNotBlank()}
            var sent=0
            for(l in lines){if(!Net.sendLocation(this,l))break;sent++}
            if(sent>0)locFile.writeText(lines.drop(sent).joinToString("\n",postfix=if(sent<lines.size)"\n" else ""))
            if(audioSent>0 || sent>0) status("Upload OK: audio chunks=$audioSent, locations=$sent")
        } else if(audioSent>0) status("Upload OK: audio chunks=$audioSent")
    }

    override fun onLocationChanged(l:Location){
        locFile.appendText(JSONObject().apply{put("ts",Instant.now().toString());put("lat",l.latitude);put("lon",l.longitude);put("accuracy_m",l.accuracy.toDouble());put("provider",l.provider?:"")}.toString()+"\n")
    }
    override fun onDestroy(){running.set(false);try{lm.removeUpdates(this)}catch(_:Exception){};worker.shutdownNow();status("Stopped");super.onDestroy()}
    override fun onBind(i:Intent?):IBinder?=null
}
