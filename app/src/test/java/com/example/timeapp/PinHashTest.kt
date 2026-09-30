package com.example.timeapp

import com.example.timeapp.data.PinHash
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PinHashTest {
    @Test fun rightPinVerifiesWrongOneDoesNot() {
        val salt = PinHash.newSalt()
        val stored = PinHash.hash("123456", salt)
        assertTrue(stored.startsWith("pbkdf2-sha256$"))
        assertTrue(PinHash.verify("123456", salt, stored))
        assertFalse(PinHash.verify("123457", salt, stored))
        assertFalse(PinHash.needsUpgrade(stored))
    }

    @Test fun oldSha256HashesStillOpenAndAskForAnUpgrade() {
        val salt = "abcdef"
        val legacy = MessageDigest.getInstance("SHA-256").digest((salt + "654321").toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(PinHash.verify("654321", salt, legacy))
        assertFalse(PinHash.verify("000000", salt, legacy))
        assertTrue(PinHash.needsUpgrade(legacy))
    }

    @Test fun malformedStoredValuesNeverVerify() {
        assertFalse(PinHash.verify("123456", "s", "pbkdf2-sha256$0$00"))
        assertFalse(PinHash.verify("123456", "s", "pbkdf2-sha256\$abc\$00"))
        assertFalse(PinHash.verify("123456", "s", ""))
    }

    private val deviceKey = "device-secret".toByteArray()
    private val mac: (ByteArray) -> ByteArray? = { data ->
        javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(deviceKey, "HmacSHA256")) }.doFinal(data)
    }

    @Test fun keystoreFormNeedsTheDeviceKey() {
        val salt = PinHash.newSalt()
        val stored = PinHash.hash("123456", salt, mac = mac)
        assertTrue(stored.startsWith("pbkdf2ks-sha256$"))
        assertTrue(PinHash.verify("123456", salt, stored, mac))
        assertFalse(PinHash.verify("123456", salt, stored, mac = null))
        assertFalse(PinHash.verify("123456", salt, stored, mac = { null }))
        assertFalse(PinHash.needsUpgrade(stored, keystoreAvailable = true))
        // A plain PBKDF2 verifier is upgraded once a Keystore is there.
        assertTrue(PinHash.needsUpgrade(PinHash.hash("123456", salt), keystoreAvailable = true))
    }

    @Test fun aFailingKeystoreFallsBackToPlainPbkdf2() {
        val stored = PinHash.hash("123456", "s", mac = { null })
        assertTrue(stored.startsWith("pbkdf2-sha256$"))
        assertTrue(PinHash.verify("123456", "s", stored, mac = { null }))
    }
}
