package com.example.safetyclient

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec

class Recorder(private val context: Context) {
    data class A(val id:String,val start:String,val enc:File,val meta:File,val rec:MediaRecorder,val fd:ParcelFileDescriptor,val future:Future<*>,val iv:ByteArray,val wrapped:ByteArray)
    private val io = Executors.newCachedThreadPool()
    private var a:A? = null

    fun active() = a != null
    fun start() {
        if (a != null) return
        val dir = File(context.filesDir,"pending").apply{mkdirs()}
        val id=UUID.randomUUID().toString(); val enc=File(dir,"$id.enc"); val meta=File(dir,"$id.json")
        val key=Crypto.newAes(); val iv=Crypto.iv(); val wrapped=Crypto.wrap(context,key)
        val pipe=ParcelFileDescriptor.createPipe(); val rfd=pipe[0]; val wfd=pipe[1]
        val f=io.submit {
            ParcelFileDescriptor.AutoCloseInputStream(rfd).use { input ->
                FileOutputStream(enc).use { out ->
                    val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv))
                    CipherOutputStream(out,c).use { input.copyTo(it) }
                }
            }
        }
        val rec = if (Build.VERSION.SDK_INT>=31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioChannels(1); setAudioSamplingRate(16000); setAudioEncodingBitRate(8000)
            setOutputFile(wfd.fileDescriptor); prepare(); start()
        }
        a=A(id,Instant.now().toString(),enc,meta,rec,wfd,f,iv,wrapped)
    }

    fun stop() {
        val x=a?:return; a=null
        try{x.rec.stop()}catch(_:Exception){}; try{x.rec.release()}catch(_:Exception){}; try{x.fd.close()}catch(_:Exception){}
        try{x.future.get(20,TimeUnit.SECONDS)}catch(_:Exception){}
        if (!x.enc.exists() || x.enc.length()==0L) { x.enc.delete(); return }
        x.meta.writeText(JSONObject().apply {
            put("id",x.id); put("started_at",x.start); put("ended_at",Instant.now().toString()); put("codec","opus"); put("container","ogg")
            put("bitrate_bps",8000); put("sample_rate_hz",16000); put("channels",1)
            put("iv_b64",Base64.encodeToString(x.iv,Base64.NO_WRAP)); put("wrapped_key_b64",Base64.encodeToString(x.wrapped,Base64.NO_WRAP)); put("encrypted_bytes",x.enc.length())
        }.toString())
    }
}
