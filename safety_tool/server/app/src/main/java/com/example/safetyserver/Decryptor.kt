package com.example.safetyserver

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

object Decryptor {
    fun decrypt(ctx:Context, enc:File, meta:File, out:File){
        val pem=ctx.assets.open("server_private_key.pem").bufferedReader().use{it.readText()}
        val raw=pem.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replace("\\s".toRegex(),"")
        val priv=KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(raw,Base64.DEFAULT)))
        val j=JSONObject(meta.readText())
        val wrapped=Base64.decode(j.getString("wrapped_key_b64"),Base64.DEFAULT)
        val iv=Base64.decode(j.getString("iv_b64"),Base64.DEFAULT)
        val rsa=Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsa.init(Cipher.DECRYPT_MODE,priv,OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT))
        val key=SecretKeySpec(rsa.doFinal(wrapped),"AES")
        val aes=Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,iv))
        CipherInputStream(FileInputStream(enc),aes).use{input->FileOutputStream(out).use{input.copyTo(it)}}
    }
}
