package com.example.timeapp

import com.example.timeapp.data.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {
    // Few rounds keep the test fast; the real default is BackupCrypto.ITERATIONS.
    private val rounds = 2_000

    private fun seal(plain: ByteArray, pw: String): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(plain), it, pw.toCharArray(), rounds) }.toByteArray()

    private fun open(sealed: ByteArray, pw: String): ByteArray =
        ByteArrayOutputStream().also { BackupCrypto.decrypt(ByteArrayInputStream(sealed), it, pw.toCharArray()) }.toByteArray()

    @Test fun roundTripsAcrossSeveralChunksAndEmpty() {
        listOf(0, 1, BackupCrypto.CHUNK, BackupCrypto.CHUNK + 7, 3 * BackupCrypto.CHUNK - 1).forEach { size ->
            val plain = Random(size).nextBytes(size)
            val sealed = seal(plain, "correct horse")
            assertTrue(BackupCrypto.isEncrypted(sealed))
            assertArrayEquals(plain, open(sealed, "correct horse"))
        }
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun wrongPasswordFails() { open(seal("zip bytes".toByteArray(), "right one"), "wrong one") }

    @Test fun wrongPasswordHandsOutNothing() {
        val out = ByteArrayOutputStream()
        runCatching { BackupCrypto.decrypt(ByteArrayInputStream(seal(ByteArray(10_000) { 1 }, "a password")), out, "another".toCharArray()) }
        assertTrue(out.size() == 0)
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun truncationIsCaught() {
        // Drop the whole last chunk: what remains ends on a chunk that was sealed as not-last.
        val plain = Random(1).nextBytes(BackupCrypto.CHUNK + 100)
        val sealed = seal(plain, "a password")
        val lastChunkBytes = 4 + 100 + 16
        open(sealed.copyOf(sealed.size - lastChunkBytes), "a password")
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun tamperingIsCaught() {
        val sealed = seal("important".toByteArray(), "a password")
        sealed[sealed.size - 3] = (sealed[sealed.size - 3].toInt() xor 1).toByte()
        open(sealed, "a password")
    }

    @Test fun samePasswordTwiceStillDiffers() {
        val plain = "same".toByteArray()
        assertFalse(seal(plain, "pw123456").contentEquals(seal(plain, "pw123456")))
    }

    @Test(expected = BackupCrypto.NotEncryptedException::class)
    fun aPlainZipIsNotMistakenForOne() { open("PK\u0003\u0004 plain zip".toByteArray(), "x") }
}
