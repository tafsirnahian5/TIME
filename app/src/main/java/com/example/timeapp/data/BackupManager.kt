package com.example.timeapp.data

import android.content.Context
import android.net.Uri
import com.example.timeapp.BuildConfig
import com.example.timeapp.data.db.TimeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val FORMAT_VERSION = 1
private const val INTERNAL_SAFETY_BACKUP_NAME = "pre_restore_backup.zip"

data class RestorePreview(
    val createdAtEpochMs: Long,
    val appVersionName: String,
    val formatVersion: Int,
    val recordCounts: Map<String, Int>,
)

sealed class RestoreResult {
    data object Success : RestoreResult()
    data object CorruptZip : RestoreResult()
    data object MissingManifest : RestoreResult()
    data object ChecksumMismatch : RestoreResult()
    data class UnsupportedFormatVersion(val found: Int, val supported: Int) : RestoreResult()
    /** The database inside is from a schema this build can't open: newer than it (opening
     * would crash) or older than any migration (opening would wipe it). */
    data class UnsupportedSchema(val found: Int, val newer: Boolean) : RestoreResult()
    /** A password-protected backup and no password yet: ask for it. */
    data object NeedsPassword : RestoreResult()
    /** The password didn't open it (or the file is damaged). Nothing was changed. */
    data object WrongPassword : RestoreResult()
}

/** What opening a backup to look inside it found. */
sealed class PreviewOutcome {
    data class Ok(val preview: RestorePreview, val encrypted: Boolean) : PreviewOutcome()
    data object NeedsPassword : PreviewOutcome()
    data object WrongPassword : PreviewOutcome()
    data object Corrupt : PreviewOutcome()
}

/**
 * Real SAF-backed backup/restore of the whole Room DB plus an allowlisted subset of
 * settings, zipped together. PIN/lock keys are never included — see
 * [AppSettingsDataStore.restoreAllowedSettings]. Restoring requires a process restart
 * afterward (see [TimeDatabase.clearInstanceForRestore]'s kdoc for why).
 */
class BackupManager(
    private val context: Context,
    private val database: TimeDatabase,
    private val settingsStore: AppSettingsDataStore,
) {
    /** Shared with the CSV exporters so they don't each need their own ContentResolver. */
    fun openOutputStream(uri: Uri): java.io.OutputStream? = context.contentResolver.openOutputStream(uri)

    /**
     * Writes a backup to [destUri]. With a [password] the zip is sealed by [BackupCrypto]
     * (built in the cache first, then encrypted into the destination, then deleted) — the
     * password itself is never stored.
     */
    suspend fun createBackup(destUri: Uri, password: CharArray? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (password == null) {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    writeBackupZip(out)
                } ?: return@withContext false
            } else {
                val plain = File(context.cacheDir, "backup_plain.zip")
                try {
                    plain.outputStream().use { writeBackupZip(it) }
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        plain.inputStream().use { BackupCrypto.encrypt(it, out, password) }
                    } ?: return@withContext false
                } finally {
                    plain.delete()
                }
            }
            // The PDF: "Verify the readable result before reporting success." Automatic backups
            // already did; a manual one is now re-read from where it was written too.
            if (!verifyBackup(destUri, password)) return@withContext false
            settingsStore.setLastBackupEpochMs(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            false
        }
    }

    /** No picker, app-internal — the automatic pre-restore safety copy. */
    private suspend fun createInternalSafetyBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            File(context.filesDir, INTERNAL_SAFETY_BACKUP_NAME).outputStream().use { writeBackupZip(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun writeBackupZip(out: java.io.OutputStream) {
        val dbFile = snapshotDatabase()
        try {
            writeBackupZip(out, dbFile)
        } finally {
            dbFile.delete()
        }
    }

    /**
     * A consistent copy of the database to back up. It used to checkpoint and then read the live
     * file twice (once for the checksum, once into the zip): a write — a timer checkpoint, a
     * reminder, the widget — folded into the file between the two made a backup whose checksum
     * didn't match, which restore then refuses. `VACUUM INTO` (Android 11+) snapshots inside one
     * read transaction; older versions copy after a checkpoint and retry until SQLite's own check
     * passes. Either way the checksum and the zipped bytes are the same file.
     */
    private fun snapshotDatabase(): File {
        val snap = File(context.cacheDir, "backup_snapshot.db")
        snap.delete()
        val db = database.openHelper.writableDatabase
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            db.execSQL("VACUUM INTO ?", arrayOf<Any>(snap.path))
            return snap
        }
        val live = context.getDatabasePath(TimeDatabase.FILE_NAME)
        repeat(3) {
            checkpointWal()
            live.copyTo(snap, overwrite = true)
            if (quickCheck(snap)) return snap
        }
        snap.delete()
        throw IllegalStateException("database copy kept changing")
    }

    private fun quickCheck(file: File): Boolean = runCatching {
        android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
        }
    }.getOrDefault(false)

    private suspend fun writeBackupZip(out: java.io.OutputStream, dbFile: File) {
        val manifest = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("createdAtEpochMs", System.currentTimeMillis())
            put("appVersionCode", BuildConfig.VERSION_CODE)
            put("appVersionName", BuildConfig.VERSION_NAME)
            put("dbChecksumSha256", sha256(dbFile))
            put("recordCounts", JSONObject(recordCounts() as Map<*, *>))
        }
        val settingsJson = buildSettingsJson()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("settings.json"))
            zip.write(settingsJson.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(TimeDatabase.FILE_NAME))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            // Receipts travel as bytes, not references (the PDF).
            File(context.filesDir, com.example.timeapp.data.Attachments.DIR).listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry(com.example.timeapp.data.Attachments.DIR + "/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Forces a WAL checkpoint so `time.db` alone is self-consistent — the sidecar
     * `-wal`/`-shm` files are then safe to leave out of the backup entirely. */
    private fun checkpointWal() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
    }

    private suspend fun recordCounts(): Map<String, Int> = mapOf(
        "tasks" to database.entryDao().count(),
        "moneyRecords" to database.moneyRecordDao().count(),
        "wallets" to database.walletDao().count(),
        "ideas" to database.ideaDao().count(),
        "bookmarks" to database.bookmarkDao().count(),
    )

    private suspend fun buildSettingsJson(): JSONObject {
        val s = settingsStore.settings.first()
        return JSONObject().apply {
            put("colorway", s.colorway.name)
            put("theme_mode", s.themeMode)
            put("user_name", s.userName)
            put("user_avatar_uri", s.userAvatarUri ?: JSONObject.NULL)
            put("user_title", s.userTitle)
            put("work_start", s.workStart)
            put("work_end", s.workEnd)
            put("timezone", s.timezone)
            put("currency", s.currency)
            put("rest_days", s.restDays.joinToString(","))
            put("notify_work_channel", s.notifyWorkChannel)
            put("notify_schedule_channel", s.notifyScheduleChannel)
            put("notify_money_channel", s.notifyMoneyChannel)
            put("notify_maintenance_channel", s.notifyMaintenanceChannel)
            put("notify_redact_content", s.notifyRedactContent)
            // Only a budget the user actually set: writing the untouched default made a restore
            // turn it into a "set" budget, and budget alerts started counting against it.
            put("monthly_budget_set", s.monthlyBudgetSet)
            if (s.monthlyBudgetSet) put("monthly_budget_minor", s.monthlyBudgetMinor)
            put("include_overdue_carryover", s.includeOverdueCarryover)
            put("focus_break_reminder_minutes", s.focusBreakReminderMinutes)
            put("week_starts_on", s.weekStartsOn)
            put("quiet_hours_enabled", s.quietHoursEnabled)
            put("timer_alarm_style", s.timerAlarmStyle)
            put("budget_alerts", s.budgetAlertsEnabled)
            put("quiet_start", s.quietStart)
            put("quiet_end", s.quietEnd)
            put("default_calendar_view", s.defaultCalendarView)
            put("board_sort", s.boardSort)
            put("app_language", s.appLanguage)
            put("money_widget_amounts", s.moneyWidgetAmounts)
            put("clock_format", s.clockFormat)
            put("date_order", s.dateOrder)
            put("currency_label", s.currencyLabel)
            put("dynamic_color", s.dynamicColor)
            put("pure_black", s.pureBlack)
            put("haptic_level", s.hapticLevel.name)
            put("reduce_celebrations", s.reduceCelebrations)
            put("saved_filters", s.savedFilters)
        }
    }

    /** The chosen file as a plain zip in the cache: copied as-is, or decrypted when protected. */
    private sealed class Opened {
        class Zip(val file: File, val encrypted: Boolean) : Opened()
        data object NeedsPassword : Opened()
        data object WrongPassword : Opened()
        data object Unreadable : Opened()
    }

    private fun open(sourceUri: Uri, password: CharArray?): Opened {
        val target = File(context.cacheDir, "restore_source.zip")
        target.delete()
        return try {
            val input = context.contentResolver.openInputStream(sourceUri)?.buffered() ?: return Opened.Unreadable
            input.use { stream ->
                stream.mark(64)
                val head = ByteArray(BackupCrypto.headerSize)
                val read = stream.readNBytesCompat(head)
                stream.reset()
                val encrypted = BackupCrypto.isEncrypted(head.copyOf(read))
                if (encrypted && password == null) return Opened.NeedsPassword
                target.outputStream().use { out ->
                    if (encrypted) BackupCrypto.decrypt(stream, out, password!!) else stream.copyTo(out)
                }
                Opened.Zip(target, encrypted)
            }
        } catch (e: BackupCrypto.WrongPasswordException) {
            target.delete()
            Opened.WrongPassword
        } catch (e: Exception) {
            target.delete()
            Opened.Unreadable
        }
    }

    private fun java.io.InputStream.readNBytesCompat(buf: ByteArray): Int {
        var filled = 0
        while (filled < buf.size) {
            val n = read(buf, filled, buf.size - filled)
            if (n < 0) break
            filled += n
        }
        return filled
    }

    suspend fun previewRestore(sourceUri: Uri, password: CharArray? = null): PreviewOutcome = withContext(Dispatchers.IO) {
        val opened = open(sourceUri, password)
        val zipFile = when (opened) {
            is Opened.Zip -> opened.file
            Opened.NeedsPassword -> return@withContext PreviewOutcome.NeedsPassword
            Opened.WrongPassword -> return@withContext PreviewOutcome.WrongPassword
            Opened.Unreadable -> return@withContext PreviewOutcome.Corrupt
        }
        try {
            zipFile.inputStream().use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "manifest.json") {
                            val json = JSONObject(zip.readBytes().decodeToString())
                            val countsJson = json.optJSONObject("recordCounts") ?: JSONObject()
                            val counts = countsJson.keys().asSequence().associateWith { countsJson.getInt(it) }
                            return@withContext PreviewOutcome.Ok(
                                RestorePreview(
                                    createdAtEpochMs = json.optLong("createdAtEpochMs"),
                                    appVersionName = json.optString("appVersionName", "?"),
                                    formatVersion = json.optInt("formatVersion", -1),
                                    recordCounts = counts,
                                ),
                                opened.encrypted,
                            )
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            PreviewOutcome.Corrupt
        } catch (e: Exception) {
            PreviewOutcome.Corrupt
        } finally {
            zipFile.delete()
        }
    }

    /**
     * Re-reads a backup just written and checks it would restore: it opens (with [password] when
     * protected), has a manifest, and its database matches the manifest's checksum. Automatic
     * backups delete older copies only after the new one passes this.
     */
    suspend fun verifyBackup(uri: Uri, password: CharArray?): Boolean = withContext(Dispatchers.IO) {
        val zipFile = (open(uri, password) as? Opened.Zip)?.file ?: return@withContext false
        try {
            var expected: String? = null
            val db = File(context.cacheDir, "verify_temp.db")
            zipFile.inputStream().use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "manifest.json" -> expected = JSONObject(zip.readBytes().decodeToString()).optString("dbChecksumSha256").ifBlank { null }
                            TimeDatabase.FILE_NAME -> db.outputStream().use { zip.copyTo(it) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            val ok = expected != null && db.exists() && sha256(db) == expected
            db.delete()
            ok
        } catch (e: Exception) {
            false
        } finally {
            zipFile.delete()
        }
    }

    suspend fun restoreBackup(sourceUri: Uri, password: CharArray? = null): RestoreResult = withContext(Dispatchers.IO) {
        // Decrypt (or copy) first: a wrong password stops here, before anything is touched.
        val zipFile = when (val opened = open(sourceUri, password)) {
            is Opened.Zip -> opened.file
            Opened.NeedsPassword -> return@withContext RestoreResult.NeedsPassword
            Opened.WrongPassword -> return@withContext RestoreResult.WrongPassword
            Opened.Unreadable -> return@withContext RestoreResult.CorruptZip
        }
        try {
            restoreFromZip(zipFile)
        } finally {
            zipFile.delete()
        }
    }

    /**
     * Stops everything that was running in a backup's database: open work sessions end at their
     * last checkpoint (flagged for review, as after a reboot), their tasks go from Doing to
     * Paused, the Focus runtime stops, and utility timers are paused with what was left at the
     * snapshot. Nothing is counted past what the backup itself knew.
     */
    private fun pauseRunningTimers(dbFile: File, snapshotAt: Long?) {
        android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("UPDATE work_sessions SET endedAt = MAX(startedAt, COALESCE(checkpointAt, startedAt)), needsReview = 1 WHERE endedAt IS NULL")
                db.execSQL("UPDATE entries SET status = 'PAUSED' WHERE status = 'DOING'")
                db.execSQL("UPDATE focus_runtime SET running = 0, stretchStartedAt = NULL, stretchAnchor = NULL WHERE running = 1")
                if (snapshotAt != null) {
                    db.execSQL("UPDATE utility_timers SET remainingMs = MAX(0, endsAt - ?), endsAt = NULL WHERE endsAt IS NOT NULL", arrayOf<Any>(snapshotAt))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private suspend fun restoreFromZip(zipFile: File): RestoreResult {
        val tempDbFile = File(context.cacheDir, "restore_temp.db")
        // Receipts are staged apart and only swapped in once the database is accepted.
        val staging = File(context.cacheDir, "restore_attachments").apply { deleteRecursively(); mkdirs() }
        var manifestChecksum: String? = null
        var formatVersion: Int? = null
        var settingsJsonText: String? = null
        var snapshotAt: Long? = null
        try {
            val opened = zipFile.inputStream().use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "manifest.json" -> {
                                val json = JSONObject(zip.readBytes().decodeToString())
                                manifestChecksum = json.optString("dbChecksumSha256").ifBlank { null }
                                formatVersion = json.optInt("formatVersion", -1)
                                snapshotAt = json.optLong("createdAtEpochMs").takeIf { it > 0 }
                            }
                            "settings.json" -> settingsJsonText = zip.readBytes().decodeToString()
                            TimeDatabase.FILE_NAME -> tempDbFile.outputStream().use { out -> zip.copyTo(out) }
                            else -> com.example.timeapp.data.Attachments.storedNameFromZip(entry.name)?.let { safe ->
                                // Only names the app itself makes; a bounded copy, so a crafted
                                // entry can't expand without limit.
                                File(staging, safe).outputStream().use { out ->
                                    val buf = ByteArray(64 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val n = zip.read(buf)
                                        if (n < 0) break
                                        total += n
                                        if (total > com.example.timeapp.data.Attachments.MAX_BYTES) throw IllegalStateException("oversized entry")
                                        out.write(buf, 0, n)
                                    }
                                }
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
                true
            }
            if (!opened) return RestoreResult.CorruptZip
        } catch (e: Exception) {
            return RestoreResult.CorruptZip
        }

        val version = formatVersion ?: return RestoreResult.MissingManifest
        if (version > FORMAT_VERSION) return RestoreResult.UnsupportedFormatVersion(version, FORMAT_VERSION)
        if (!tempDbFile.exists()) return RestoreResult.CorruptZip

        val actualChecksum = sha256(tempDbFile)
        if (manifestChecksum != null && manifestChecksum != actualChecksum) {
            tempDbFile.delete()
            return RestoreResult.ChecksumMismatch
        }

        // Checked before anything is replaced: the current data stays untouched on refusal.
        val schema = schemaVersionOf(tempDbFile)
        if (schema == null || schema > com.example.timeapp.data.db.DB_VERSION || schema < com.example.timeapp.data.db.OLDEST_MIGRATABLE_VERSION) {
            tempDbFile.delete()
            return if (schema == null) RestoreResult.CorruptZip
            else RestoreResult.UnsupportedSchema(schema, newer = schema > com.example.timeapp.data.db.DB_VERSION)
        }

        // The PDF: "Restore from backup always pauses active timers at the backup snapshot." Done
        // on the staged copy, before it replaces anything, so a restored running session can't
        // keep counting the time between the backup and now.
        runCatching { pauseRunningTimers(tempDbFile, snapshotAt) }

        createInternalSafetyBackup()

        settingsJsonText?.let { settingsStore.restoreAllowedSettings(JSONObject(it)) }

        TimeDatabase.clearInstanceForRestore()
        val dbFile = context.getDatabasePath(TimeDatabase.FILE_NAME)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        tempDbFile.copyTo(dbFile, overwrite = true)
        tempDbFile.delete()
        val attachmentsDir = File(context.filesDir, com.example.timeapp.data.Attachments.DIR)
        attachmentsDir.deleteRecursively()
        attachmentsDir.mkdirs()
        staging.listFiles()?.forEach { it.copyTo(File(attachmentsDir, it.name), overwrite = true) }
        staging.deleteRecursively()

        return RestoreResult.Success
    }
}

/** SQLite's `user_version`, which Room sets to the schema version. Null if unreadable. */
private fun schemaVersionOf(file: File): Int? = try {
    android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { it.version }
} catch (e: Exception) {
    null
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read = input.read(buffer)
        while (read >= 0) {
            digest.update(buffer, 0, read)
            read = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
