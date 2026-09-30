package com.example.timeapp.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Importing transactions from a bank or spreadsheet CSV — the PDF's "CSV import with a
 * mapping preview and duplicate checks". Pure: parsing, mapping and duplicate detection are
 * all here and tested; the screen only shows the result and the repository only inserts it.
 *
 * Nothing is guessed silently. Every row either parses exactly or carries an [ImportRow.error]
 * saying why it will be skipped, and a likely duplicate is shown and left unticked rather
 * than dropped.
 */
object CsvImport {

    /** RFC 4180: quoted fields, doubled quotes, commas and newlines inside quotes, CRLF. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val src = text.removePrefix("﻿") // a UTF-8 BOM from Excel
        while (i < src.length) {
            val c = src[i]
            when {
                inQuotes && c == '"' && src.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && src.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row.toList())
                    row.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotBlank() }) rows.add(row.toList())
        }
        return rows
    }

    /** Date layouts tried in order; ISO first so an unambiguous file never hits the others. */
    val dateFormats: List<String> = listOf(
        "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "d/M/yyyy", "d MMM yyyy", "dd MMM yyyy", "d-MMM-yyyy",
    )

    fun parseDate(raw: String, pattern: String): LocalDate? = try {
        LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    } catch (e: DateTimeParseException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * The first layout that reads **every** non-blank cell — so 03/04/2026 is never read
     * day-first in one row and month-first in the next. Null when none fits them all.
     */
    fun detectDateFormat(cells: List<String>): String? {
        val values = cells.map { it.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) return null
        return dateFormats.firstOrNull { pattern -> values.all { parseDate(it, pattern) != null } }
    }

    /**
     * An amount cell → signed poisha, exactly, or null. Accepts "Tk"/"৳"/"BDT", thousands
     * separators, a leading minus or accounting brackets "(1,200.00)" for negatives. Anything
     * with more than two decimals is rejected, never rounded.
     */
    fun parseAmount(raw: String): Long? {
        var s = raw.trim()
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        s = s.replace("BDT", "", ignoreCase = true).replace("Tk", "", ignoreCase = true).replace("৳", "")
            .replace(",", "").replace(" ", "").trim()
        if (s.startsWith("-")) { negative = !negative; s = s.substring(1) }
        if (s.startsWith("+")) s = s.substring(1)
        val value = parseTakaToMinor(s) ?: return null
        if (value < 0) return null
        return if (negative) -value else value
    }

    /** A type cell → direction. Transfers and refunds can't be imported (they need a second
     * wallet or an original expense), so they're reported, not mis-filed as income. */
    fun parseType(raw: String): TypeCell = when (raw.trim().lowercase()) {
        "expense", "expenses", "debit", "dr", "cost", "spent", "spend", "out", "withdrawal", "payment", "purchase" -> TypeCell.Of(Direction.COST)
        "income", "credit", "cr", "deposit", "in", "received", "salary" -> TypeCell.Of(Direction.INCOME)
        "transfer" -> TypeCell.Unsupported(L("Transfers need two wallets — record them in the app."))
        "refund" -> TypeCell.Unsupported(L("Refunds need their original expense — record them from History."))
        "loan out", "loan in", "loan" -> TypeCell.Unsupported(L("Loan movements belong to a loan — record them in Money › Loans & dues."))
        else -> TypeCell.Unknown
    }

    sealed class TypeCell {
        data class Of(val direction: Direction) : TypeCell()
        data class Unsupported(val reason: String) : TypeCell()
        data object Unknown : TypeCell()
    }

    /** How to read direction when there's no type column. */
    enum class SignRule { SIGNED, ALL_EXPENSES, ALL_INCOME }

    /** Which column holds what; -1 means "not in this file". */
    data class Mapping(
        val date: Int,
        val amount: Int,
        val title: Int,
        val type: Int = -1,
        val category: Int = -1,
        val note: Int = -1,
        val signRule: SignRule = SignRule.SIGNED,
        val dateFormat: String,
    )

    /** Best-effort starting mapping from header names; the user can change every column. */
    fun guessMapping(header: List<String>, body: List<List<String>>): Mapping {
        fun find(vararg names: String) = header.indexOfFirst { h -> names.any { h.trim().equals(it, ignoreCase = true) } }
        fun findContains(vararg names: String) = header.indexOfFirst { h -> names.any { h.contains(it, ignoreCase = true) } }
        val date = find("date", "transaction date", "posting date").takeIf { it >= 0 } ?: findContains("date").coerceAtLeast(0)
        val amount = find("amount", "amount (tk)", "value").takeIf { it >= 0 } ?: findContains("amount").let { if (it >= 0) it else 1.coerceAtMost(header.lastIndex) }
        val title = find("title", "description", "payee", "details", "narration", "name").let { if (it >= 0) it else 2.coerceAtMost(header.lastIndex) }
        val type = find("type", "direction", "dr/cr", "debit/credit")
        return Mapping(
            date = date,
            amount = amount,
            title = title,
            type = type,
            category = find("category"),
            note = find("note", "notes", "memo", "remarks"),
            dateFormat = detectDateFormat(body.map { it.getOrElse(date) { "" } }) ?: dateFormats.first(),
        )
    }

    /** One CSV line after mapping. [error] non-null → it will be skipped, with that reason. */
    data class ImportRow(
        val line: Int,
        val date: LocalDate?,
        val direction: Direction?,
        val amountMinor: Long?,
        val title: String,
        val categoryName: String?,
        val note: String?,
        val error: String? = null,
        val duplicate: Boolean = false,
    ) {
        val importable: Boolean get() = error == null && date != null && direction != null && amountMinor != null
    }

    /** A ledger row as the duplicate check sees it: same day, direction, amount and title. */
    data class Fingerprint(val date: LocalDate, val direction: Direction, val amountMinor: Long, val title: String)

    fun fingerprintOf(date: LocalDate, direction: Direction, amountMinor: Long, title: String) =
        Fingerprint(date, direction, amountMinor, title.trim().lowercase())

    /**
     * Maps [body] rows (header already removed; [firstLine] is the file line of the first one)
     * and flags duplicates — of a transaction already in the ledger ([existing]), or of an
     * earlier row in the same file.
     */
    fun buildRows(body: List<List<String>>, mapping: Mapping, existing: Set<Fingerprint>, firstLine: Int = 2): List<ImportRow> {
        val seen = mutableSetOf<Fingerprint>()
        return body.mapIndexed { index, cells ->
            val line = firstLine + index
            fun cell(i: Int) = if (i < 0) "" else cells.getOrElse(i) { "" }.trim()
            val title = cell(mapping.title)
            val date = parseDate(cell(mapping.date), mapping.dateFormat)
            val signed = parseAmount(cell(mapping.amount))
            var error: String? = when {
                date == null -> L("Date \"%1\$s\" doesn't match %2\$s", cell(mapping.date), mapping.dateFormat)
                signed == null -> L("Amount \"%1\$s\" isn't an exact taka amount", cell(mapping.amount))
                signed == 0L -> L("Amount is zero")
                title.isEmpty() -> L("No title")
                else -> null
            }
            var direction: Direction? = null
            if (error == null && signed != null) {
                if (mapping.type >= 0) {
                    when (val t = parseType(cell(mapping.type))) {
                        is TypeCell.Of -> direction = t.direction
                        is TypeCell.Unsupported -> error = t.reason
                        TypeCell.Unknown -> error = L("Type \"%1\$s\" isn't expense or income", cell(mapping.type))
                    }
                } else {
                    direction = when (mapping.signRule) {
                        SignRule.SIGNED -> if (signed < 0) Direction.COST else Direction.INCOME
                        SignRule.ALL_EXPENSES -> Direction.COST
                        SignRule.ALL_INCOME -> Direction.INCOME
                    }
                }
            }
            val amount = signed?.let { kotlin.math.abs(it) }
            var duplicate = false
            if (error == null && date != null && direction != null && amount != null) {
                val fp = fingerprintOf(date, direction, amount, title)
                duplicate = fp in existing || !seen.add(fp)
            }
            ImportRow(
                line = line,
                date = date,
                direction = direction,
                amountMinor = amount,
                title = title,
                categoryName = cell(mapping.category).ifEmpty { null },
                note = cell(mapping.note).ifEmpty { null },
                error = error,
                duplicate = duplicate,
            )
        }
    }
}
