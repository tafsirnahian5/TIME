package com.example.timeapp.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * The PDF's "Keystore-protected secret for the stored verifier": an HMAC-SHA256 key created in
 * the Android Keystore on first use and never exportable. The PIN verifier is
 * HMAC(key, PBKDF2(PIN)), so a copied settings file can't be brute-forced anywhere but on this
 * phone, through this key. Device-bound by design — PIN verifiers are excluded from backups
 * anyway. If the Keystore is unavailable, [mac] returns null and PinHash falls back to plain
 * PBKDF2 rather than locking the owner out.
 */
object PinPepper {
    private const val ALIAS = "time_pin_pepper"

    fun mac(data: ByteArray): ByteArray? = runCatching {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key())
        mac.doFinal(data)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }
}
