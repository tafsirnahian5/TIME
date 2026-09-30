package com.example.timeapp.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The rules behind automatic backups (Phase 17), pure so they're tested in AutoBackupTest:
 * when one is due, what the file is called, which old copies go, and when to remind someone who
 * backs up by hand. The worker (AutoBackupWorker) does the I/O around these.
 */
object AutoBackup {
    const val KEEP = 7
    const val PREFIX = "time-auto-"
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    enum class Frequency(val label: String, val every: Duration) {
        DAILY(L("Daily"), Duration.ofHours(20)),
        WEEKLY(L("Weekly"), Duration.ofDays(6).plusHours(20)),
    }

    /**
     * Due once [Frequency.every] has passed since the last one — a little under a day or week, so
     * a job that runs a bit early each day doesn't slip a whole period. Never before the first.
     */
    fun isDue(lastAt: Instant?, frequency: Frequency, now: Instant): Boolean =
        lastAt == null || !now.isBefore(lastAt.plus(frequency.every))

    /** `time-auto-20260928-091500.zip` (or `.timebackup` when protected); names sort by time. */
    fun fileName(now: Instant, zone: ZoneId, encrypted: Boolean): String =
        PREFIX + LocalDateTime.ofInstant(now, zone).format(stamp) + if (encrypted) ".timebackup" else ".zip"

    fun isOurs(name: String): Boolean =
        name.startsWith(PREFIX) && (name.endsWith(".zip") || name.endsWith(".timebackup"))

    /**
     * Which files to delete so [keep] remain — the oldest of T!ME's own automatic copies only;
     * anything else in the folder is never touched. Called only after a new copy has verified,
     * so a failing run can never leave fewer good backups than before.
     */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> {
        val ours = names.filter(::isOurs).sortedDescending()
        return if (ours.size <= keep) emptyList() else ours.drop(keep)
    }

    /**
     * Someone who backs up by hand gets a nudge when the last backup (either kind) is more than
     * [days] old — at most once every [days], and never with [days] = 0 (off).
     */
    fun reminderDue(lastBackupAt: Instant?, lastReminderAt: Instant?, days: Int, now: Instant, installedAt: Instant? = null): Boolean {
        if (days <= 0) return false
        val period = Duration.ofDays(days.toLong())
        val since = lastBackupAt ?: installedAt ?: return false
        if (now.isBefore(since.plus(period))) return false
        return lastReminderAt == null || !now.isBefore(lastReminderAt.plus(period))
    }
}
