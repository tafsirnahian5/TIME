package com.example.timeapp.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * How the app-lock PIN is stored (Phase 16). A 6-digit PIN has only a million values, so a
 * single salted SHA-256 (what Tier 8 stored) falls to brute force in well under a second from
 * a copied settings file. It's now PBKDF2-HMAC-SHA256 with [ITERATIONS] rounds, written as
 * `pbkdf2-sha256$<rounds>$<hex>` so the rounds can rise later. Old hashes still verify and are
 * upgraded on the next correct PIN. Compared in constant time. Pure; tested in PinHashTest.
 */
object PinHash {
    const val ITERATIONS = 120_000
    private const val PREFIX = "pbkdf2-sha256"
    /** PBKDF2 then an HMAC under a device-held key (PinPepper) — the preferred form. */
    private const val KS_PREFIX = "pbkdf2ks-sha256"

    fun newSalt(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /** [mac] is the device-held HMAC (PinPepper.mac); null (or failing) stores plain PBKDF2. */
    fun hash(pin: String, salt: String, iterations: Int = ITERATIONS, mac: ((ByteArray) -> ByteArray?)? = null): String {
        val derived = pbkdf2(pin, salt, iterations)
        val peppered = mac?.invoke(derived)
        return if (peppered != null) "$KS_PREFIX$$iterations$" + peppered.toHex() else "$PREFIX$$iterations$" + derived.toHex()
    }

    fun verify(pin: String, salt: String, stored: String, mac: ((ByteArray) -> ByteArray?)? = null): Boolean {
        val parts = stored.split('$')
        val rounds = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1_000..10_000_000 }
        val candidate = when {
            parts.size == 3 && parts[0] == KS_PREFIX -> {
                rounds ?: return false
                val peppered = mac?.invoke(pbkdf2(pin, salt, rounds)) ?: return false
                "$KS_PREFIX$$rounds$" + peppered.toHex()
            }
            parts.size == 3 && parts[0] == PREFIX -> hash(pin, salt, rounds ?: return false)
            parts.size == 1 -> legacy(pin, salt)
            else -> return false
        }
        return MessageDigest.isEqual(candidate.toByteArray(), stored.toByteArray())
    }

    /** True when [stored] isn't the strongest form this device can write — re-store it once the
     * PIN is known to be right. */
    fun needsUpgrade(stored: String, keystoreAvailable: Boolean = false): Boolean =
        !stored.startsWith("${if (keystoreAvailable) KS_PREFIX else PREFIX}$$ITERATIONS$")

    private fun legacy(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun pbkdf2(pin: String, salt: String, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
