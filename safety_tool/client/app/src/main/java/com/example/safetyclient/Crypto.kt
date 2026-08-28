package com.example.safetyclient

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object Crypto {
    fun newAes(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    fun iv(): ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }
    fun wrap(context: Context, key: SecretKey): ByteArray {
        val pem = context.assets.open("server_public_key.pem").bufferedReader().use { it.readText() }
        val raw = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replace("\\s".toRegex(), "")
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(raw, Base64.DEFAULT)))
        return Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.ENCRYPT_MODE, pub, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
            doFinal(key.encoded)
        }
    }
}
