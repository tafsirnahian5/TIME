package com.example.timeapp.data

import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV export for the two things people actually take elsewhere: the money ledger and
 * tracked time. Amounts are written as exact decimal taka (never a float), and dates as
 * plain ISO local dates, so a spreadsheet reads them without guessing.
 *
 * This is a convenience format, not a backup — it cannot be imported back and deliberately
 * omits ids and relationships. `BackupManager` remains the way to move the whole app.
 */
object CsvExport {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun writeTransactions(db: TimeDatabase, out: OutputStream) = withContext(Dispatchers.IO) {
        val wallets = db.walletDao().observeAll().first().associate { it.id to it.name }
        val categories = db.categoryDao().all().associate { it.id to it.name }
        val rows = db.moneyRecordDao().observeHistory().first().map { it.toDomain() }
        out.bufferedWriter().use { writer ->
            writer.appendLine(row("Date", "Type", "Title", "Category", "Wallet", "Amount (Tk)", "Note"))
            rows.sortedBy { it.settledAt }.forEach { record ->
                writer.appendLine(
                    row(
                        record.settledAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(dateFormat) ?: "",
                        when (record.direction) {
                            Direction.INCOME -> "Income"
                            Direction.COST -> "Expense"
                            Direction.TRANSFER -> "Transfer"
                            Direction.REFUND -> "Refund"
                            Direction.LOAN_OUT -> "Loan out"
                            Direction.LOAN_IN -> "Loan in"
                        },
                        record.title,
                        record.categoryId?.let { categories[it] } ?: "",
                        record.walletId?.let { wallets[it] } ?: "",
                        record.paidMinor.toTakaInput(),
                        record.note.orEmpty(),
                    ),
                )
            }
        }
    }

    suspend fun writeTimeSessions(db: TimeDatabase, out: OutputStream) = withContext(Dispatchers.IO) {
        val entries = db.entryDao().observeAll().first().associateBy { it.id }
        val categories = db.categoryDao().all().associate { it.id to it.name }
        val sessions = db.workSessionDao().sessionsInRange(java.time.Instant.EPOCH, java.time.Instant.now()).first()
        out.bufferedWriter().use { writer ->
            writer.appendLine(row("Date", "Task", "Category", "Start", "End", "Minutes"))
            sessions.sortedBy { it.startedAt }.forEach { session ->
                val entry = entries[session.entryId]
                val start = session.startedAt.atZone(ZoneId.systemDefault())
                val end = session.endedAt?.atZone(ZoneId.systemDefault())
                val minutes = if (end == null) 0 else (session.endedAt!!.toEpochMilli() - session.startedAt.toEpochMilli()) / 60_000
                writer.appendLine(
                    row(
                        start.toLocalDate().format(dateFormat),
                        entry?.title ?: if (session.entryId == null) "Focus (no task)" else "(deleted task)",
                        entry?.categoryId?.let { categories[it] } ?: entry?.categoryName ?: "",
                        start.toLocalTime().withSecond(0).withNano(0).toString(),
                        end?.toLocalTime()?.withSecond(0)?.withNano(0)?.toString() ?: "",
                        minutes.toString(),
                    ),
                )
            }
        }
    }

    private fun row(vararg cells: String): String = cells.joinToString(",") { escape(it) }

    /** RFC 4180 quoting: anything containing a comma, quote or newline is quoted, and inner
     * quotes are doubled — a note with a comma must not shift every later column. */
    private fun escape(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }
}
