package com.example.timeapp.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected backups (Phase 16; the PDF's release 1.5): the ordinary backup zip,
 * sealed so it can be restored on any device that knows the password — and on none that
 * doesn't. Pure JVM crypto, tested in BackupCryptoTest.
 *
 * - **Key**: PBKDF2-HMAC-SHA256 over the password with a random 16-byte salt and
 *   [ITERATIONS] rounds (stored in the header, so a later build can raise it and still read
 *   old files) → a 256-bit AES key. The password is never stored anywhere.
 * - **Data**: AES-256-GCM in [CHUNK]-sized pieces, so a backup with many receipts never has to
 *   sit whole in memory. Each chunk's nonce is a random 8-byte prefix plus its index, and its
 *   associated data is the whole header plus the index and a last-chunk flag — so chunks can't
 *   be reordered, dropped or cut off the end without the tag failing.
 * - **Wrong password** fails the first chunk's tag: [WrongPasswordException], raised before a
 *   single byte of plaintext is handed out, so a restore changes nothing.
 *
 * Layout: `TIMEENC1` · version (1 byte) · iterations (int) · salt (16) · nonce prefix (8) ·
 * then repeated `length (int) · ciphertext`, the last one flagged in its associated data.
 */
object BackupCrypto {
    private val MAGIC = "TIMEENC1".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    const val ITERATIONS = 310_000
    const val CHUNK = 1 shl 20
    private const val TAG_BITS = 128
    const val MIN_PASSWORD_LENGTH = 8

    class WrongPasswordException : Exception("Wrong password, or the file is damaged")
    class NotEncryptedException : Exception("Not a password-protected T!ME backup")

    /** True when [head] (a file's first bytes) starts like a protected backup. */
    fun isEncrypted(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    val headerSize: Int get() = MAGIC.size + 1 + 4 + 16 + 8

    fun encrypt(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        iterations: Int = ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ) {
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(8).also(random::nextBytes)
        val header = ByteBuffer.allocate(headerSize).put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(prefix).array()
        val key = deriveKey(password, salt, iterations)
        val out = DataOutputStream(output)
        out.write(header)
        // Read one chunk ahead so the last one can be flagged as last.
        var current = readChunk(input)
        var index = 0L
        while (true) {
            val next = if (current.size == CHUNK) readChunk(input) else ByteArray(0)
            val last = next.isEmpty()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, index)))
            cipher.updateAAD(aad(header, index, last))
            val sealed = cipher.doFinal(current)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (last) break
            current = next
            index++
        }
        out.flush()
    }

    /** Writes the plaintext to [output] chunk by chunk, each only after its tag checks out. */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        val data = DataInputStream(input.buffered())
        val header = ByteArray(headerSize)
        try {
            data.readFully(header)
        } catch (e: EOFException) {
            throw NotEncryptedException()
        }
        if (!isEncrypted(header)) throw NotEncryptedException()
        val buf = ByteBuffer.wrap(header, MAGIC.size, headerSize - MAGIC.size)
        val version = buf.get()
        if (version != VERSION) throw WrongPasswordException()
        val iterations = buf.int
        if (iterations !in 1_000..10_000_000) throw WrongPasswordException()
        val salt = ByteArray(16).also { buf.get(it) }
        val prefix = ByteArray(8).also { buf.get(it) }
        val key = deriveKey(password, salt, iterations)
        var index = 0L
        while (true) {
            val length = try { data.readInt() } catch (e: EOFException) { throw WrongPasswordException() }
            if (length !in (TAG_BITS / 8)..(CHUNK + TAG_BITS / 8)) throw WrongPasswordException()
            val sealed = ByteArray(length)
            try { data.readFully(sealed) } catch (e: EOFException) { throw WrongPasswordException() }
            // Is anything after this chunk? Only a genuinely last chunk may say it's last.
            data.mark(1)
            val last = data.read() == -1
            if (!last) data.reset()
            val plain = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(prefix, index)))
                cipher.updateAAD(aad(header, index, last))
                cipher.doFinal(sealed)
            } catch (e: AEADBadTagException) {
                throw WrongPasswordException()
            }
            output.write(plain)
            if (last) break
            index++
        }
        output.flush()
    }

    private fun readChunk(input: InputStream): ByteArray {
        val buf = ByteArray(CHUNK)
        var filled = 0
        while (filled < CHUNK) {
            val n = input.read(buf, filled, CHUNK - filled)
            if (n < 0) break
            filled += n
        }
        return if (filled == CHUNK) buf else buf.copyOf(filled)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, index: Long): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(index.toInt()).array()

    private fun aad(header: ByteArray, index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 9).put(header).putLong(index).put(if (last) 1 else 0).array()
}
