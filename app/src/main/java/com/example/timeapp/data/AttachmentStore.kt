package com.example.timeapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.timeapp.data.db.AttachmentEntity
import com.example.timeapp.data.db.TimeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Copies a picked file into the app's own attachments folder (never a reference to the
 * original, which could move or vanish) after checking its type and size, and records it.
 */
class AttachmentStore(private val context: Context, private val db: TimeDatabase) {
    val dir: File get() = File(context.filesDir, Attachments.DIR).apply { mkdirs() }

    fun file(a: AttachmentEntity) = File(dir, a.fileName)

    /** Attaches [uri] to ledger row [recordId]. Returns the problem, or null when attached. */
    suspend fun attach(recordId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        var displayName: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                displayName = c.getString(0)
                size = if (c.isNull(1)) null else c.getLong(1)
            }
        }
        Attachments.problem(mime, size)?.let { return@withContext it }
        val name = UUID.randomUUID().toString() + "." + Attachments.ALLOWED.getValue(mime!!)
        val target = File(dir, name)
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        written += n
                        // The declared size can be missing or wrong; the copy enforces the limit.
                        if (written > Attachments.MAX_BYTES) throw TooLarge()
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            } ?: return@withContext L("That file couldn't be read.")
        } catch (e: TooLarge) {
            target.delete()
            return@withContext L("That file is over 10 MB.")
        } catch (e: Exception) {
            target.delete()
            return@withContext L("That file couldn't be read.")
        }
        if (written == 0L) { target.delete(); return@withContext L("That file is empty.") }
        db.attachmentDao().insert(
            AttachmentEntity(
                recordId = recordId, fileName = name, mime = mime, sizeBytes = written,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) }, addedAt = Instant.now(), displayName = displayName,
            ),
        )
        null
    }

    suspend fun remove(a: AttachmentEntity) = withContext(Dispatchers.IO) {
        db.attachmentDao().delete(a.id)
        file(a).delete()
    }

    /** Files no row points at any more (their transaction was deleted) — run at app start, so an
     * Undo within the session can still bring a receipt back. */
    suspend fun cleanOrphans() = withContext(Dispatchers.IO) {
        val known = db.attachmentDao().allFileNames().toSet()
        dir.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
    }

    private class TooLarge : Exception()
}
