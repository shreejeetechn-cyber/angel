package com.example.safetyserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ServerService:Service(){
    private val running=AtomicBoolean(false); private val pool=Executors.newCachedThreadPool(); private var ss:ServerSocket?=null; private val token="Bearer DEMO-PAIR-2026"
    override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("server","Safety server",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})}
    override fun onStartCommand(i:Intent?,f:Int,s:Int):Int{ if(running.compareAndSet(false,true)){ val n=Notification.Builder(this,"server").setContentTitle("Safety Server").setContentText("Receiving encrypted safety data").setSmallIcon(android.R.drawable.ic_menu_upload).setOngoing(true).build(); if(Build.VERSION.SDK_INT>=34)startForeground(8,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(8,n); pool.submit{serve()} };return START_STICKY }
    private fun serve(){try{ss=ServerSocket(8080);while(running.get()){val sock=ss!!.accept();pool.submit{handle(sock)}}}catch(_:Exception){} }
    private fun handle(sock:Socket){sock.use{s->try{val input=BufferedInputStream(s.getInputStream());val out=BufferedOutputStream(s.getOutputStream());val request=readLine(input)?:return;val parts=request.split(" ");if(parts.size<2){respond(out,400,"bad");return};val path=parts[1];val headers=mutableMapOf<String,String>();while(true){val line=readLine(input)?:break;if(line.isEmpty())break;val k=line.substringBefore(':').trim().lowercase();val v=line.substringAfter(':',"").trim();headers[k]=v};if(headers["authorization"]!=token){respond(out,401,"unauthorized");return};val len=headers["content-length"]?.toIntOrNull()?:0;when(path){"/audio"->{val metaB64=headers["x-meta"]?:"";val meta=String(Base64.decode(metaB64,Base64.DEFAULT));val id=org.json.JSONObject(meta).getString("id");val d=File(filesDir,"audio").apply{mkdirs()};File(d,"$id.json").writeText(meta);File(d,"$id.enc").outputStream().use{fo->copyN(input,fo,len)};respond(out,200,"ok")};"/location"->{val b=ByteArray(len);readFully(input,b);File(filesDir,"locations.jsonl").appendText(String(b)+"\n");respond(out,200,"ok")};else->respond(out,404,"not found")}}catch(_:Exception){}}}
    private fun readLine(input:BufferedInputStream):String?{val b=StringBuilder();while(true){val c=input.read();if(c<0)return if(b.isEmpty())null else b.toString();if(c==10)break;if(c!=13)b.append(c.toChar())};return b.toString()}
    private fun readFully(input:BufferedInputStream,b:ByteArray){var o=0;while(o<b.size){val n=input.read(b,o,b.size-o);if(n<0)break;o+=n}}
    private fun copyN(input:BufferedInputStream,out:java.io.OutputStream,len:Int){val b=ByteArray(16384);var left=len;while(left>0){val n=input.read(b,0,minOf(b.size,left));if(n<0)break;out.write(b,0,n);left-=n}}
    private fun respond(out:BufferedOutputStream,code:Int,msg:String){val body=msg.toByteArray();out.write("HTTP/1.1 $code OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());out.write(body);out.flush()}
    override fun onDestroy(){running.set(false);try{ss?.close()}catch(_:Exception){};pool.shutdownNow();super.onDestroy()}
    override fun onBind(i:Intent?):IBinder?=null
}
