package com.example.safetyserver

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : Activity() {
    private lateinit var status:TextView; private lateinit var list:ListView; private var files:List<File> = emptyList(); private var player:MediaPlayer?=null
    override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,48,32,32)}; status=TextView(this).apply{textSize=20f}; root.addView(status); root.addView(Button(this).apply{text="START SERVER";setOnClickListener{requestAndStart()}}); root.addView(Button(this).apply{text="REFRESH";setOnClickListener{refresh()}}); list=ListView(this); list.setOnItemClickListener{_,_,p,_->play(files[p])}; root.addView(list,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root); status.text="Phone 2 IP: ${localIp()}  Port: 8080"; requestAndStart(); refresh() }
    private fun requestAndStart(){ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),9);return}; val i=Intent(this,ServerService::class.java); if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i); status.text="Server active: ${localIp()}:8080" }
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==9)requestAndStart()}
    private fun refresh(){ val d=File(filesDir,"audio").apply{mkdirs()}; files=d.listFiles{f->f.extension=="enc"}?.sortedByDescending{it.lastModified()}?: emptyList(); list.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,files.map{ val m=File(d,"${it.nameWithoutExtension}.json"); if(m.exists()) "${it.nameWithoutExtension.take(8)}  •  ${it.length()/1024} KB" else it.name }) }
    private fun play(enc:File){ try{player?.release()}catch(_:Exception){}; try{ val tmp=File(cacheDir,"play_${System.currentTimeMillis()}.ogg"); Decryptor.decrypt(this,enc,File(enc.parentFile,"${enc.nameWithoutExtension}.json"),tmp); player=MediaPlayer().apply{setDataSource(tmp.absolutePath);setOnCompletionListener{try{tmp.delete()}catch(_:Exception){};status.text="Playback complete"};prepare();start()}; status.text="Playing ${enc.nameWithoutExtension.take(8)}" }catch(e:Exception){status.text="Playback error: ${e.message}"} }
    private fun localIp():String{ return try{ Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap{Collections.list(it.inetAddresses)}.firstOrNull{!it.isLoopbackAddress && it.hostAddress?.contains(':')==false}?.hostAddress?:"unknown" }catch(_:Exception){"unknown"} }
    override fun onDestroy(){try{player?.release()}catch(_:Exception){};super.onDestroy()}
}
