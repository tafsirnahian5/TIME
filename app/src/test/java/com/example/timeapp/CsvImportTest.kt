package com.example.timeapp

import com.example.timeapp.data.CsvImport
import com.example.timeapp.data.CsvImport.SignRule
import com.example.timeapp.data.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvImportTest {

    @Test
    fun `parses quotes, embedded commas and newlines, CRLF and a BOM`() {
        val text = "﻿Date,Title,Amount\r\n2026-09-01,\"Rice, 5kg\",-450\r\n2026-09-02,\"Said \"\"hi\"\"\nsecond line\",100\r\n"
        val rows = CsvImport.parse(text)
        assertEquals(3, rows.size)
        assertEquals(listOf("Date", "Title", "Amount"), rows[0])
        assertEquals("Rice, 5kg", rows[1][1])
        assertEquals("Said \"hi\"\nsecond line", rows[2][1])
    }

    @Test
    fun `amounts parse exactly or not at all`() {
        assertEquals(120_050L, CsvImport.parseAmount("1,200.50"))
        assertEquals(-120_050L, CsvImport.parseAmount("-1200.50"))
        assertEquals(-120_000L, CsvImport.parseAmount("(1,200.00)"))
        assertEquals(50_000L, CsvImport.parseAmount("Tk 500"))
        assertEquals(50_000L, CsvImport.parseAmount("৳500"))
        assertNull(CsvImport.parseAmount("12.345")) // never rounded
        assertNull(CsvImport.parseAmount("abc"))
    }

    /** 03/04 must not be read day-first on one row and month-first on the next. */
    @Test
    fun `date format is the first that reads every cell`() {
        assertEquals("yyyy-MM-dd", CsvImport.detectDateFormat(listOf("2026-09-01", "2026-12-31")))
        assertEquals("dd/MM/yyyy", CsvImport.detectDateFormat(listOf("03/04/2026", "25/12/2026")))
        assertEquals("MM/dd/yyyy", CsvImport.detectDateFormat(listOf("03/04/2026", "12/25/2026")))
        assertEquals("d MMM yyyy", CsvImport.detectDateFormat(listOf("1 Sep 2026")))
        assertNull(CsvImport.detectDateFormat(listOf("yesterday")))
    }

    private val body = listOf(
        listOf("2026-09-01", "Groceries", "-1200.50"),
        listOf("2026-09-02", "Salary", "50000"),
        listOf("2026-09-02", "Salary", "50000"),
        listOf("not a date", "Bus", "-40"),
        listOf("2026-09-03", "Tea", "12.345"),
    )
    private val mapping = CsvImport.Mapping(date = 0, title = 1, amount = 2, dateFormat = "yyyy-MM-dd")

    @Test
    fun `signed amounts decide direction and bad rows carry a reason`() {
        val rows = CsvImport.buildRows(body, mapping, existing = emptySet())
        assertEquals(Direction.COST, rows[0].direction)
        assertEquals(120_050L, rows[0].amountMinor)
        assertEquals(Direction.INCOME, rows[1].direction)
        assertTrue(rows[3].error!!.startsWith("Date"))
        assertTrue(rows[4].error!!.startsWith("Amount"))
        assertEquals(5, rows[3].line)
    }

    @Test
    fun `duplicates are flagged within the file and against the ledger`() {
        val existing = setOf(CsvImport.fingerprintOf(LocalDate.of(2026, 9, 1), Direction.COST, 120_050, " groceries "))
        val rows = CsvImport.buildRows(body, mapping, existing)
        assertTrue(rows[0].duplicate)
        assertFalse(rows[1].duplicate)
        assertTrue(rows[2].duplicate) // repeat of the line above
    }

    @Test
    fun `a type column wins, and transfers or refunds are refused not misfiled`() {
        val typed = listOf(
            listOf("2026-09-01", "Rent", "15000", "Debit"),
            listOf("2026-09-01", "Move", "500", "Transfer"),
            listOf("2026-09-01", "Back", "20", "Refund"),
            listOf("2026-09-01", "??", "20", "whatever"),
        )
        val rows = CsvImport.buildRows(typed, mapping.copy(type = 3), emptySet())
        assertEquals(Direction.COST, rows[0].direction)
        assertTrue(rows[1].error!!.startsWith("Transfers"))
        assertTrue(rows[2].error!!.startsWith("Refunds"))
        assertTrue(rows[3].error!!.startsWith("Type"))
    }

    @Test
    fun `unsigned files can be read as all expenses`() {
        val rows = CsvImport.buildRows(listOf(listOf("2026-09-01", "Lunch", "250")), mapping.copy(signRule = SignRule.ALL_EXPENSES), emptySet())
        assertEquals(Direction.COST, rows[0].direction)
    }

    /** T!ME's own export (Date, Type, Title, Category, Wallet, Amount (Tk), Note) maps itself. */
    @Test
    fun `our own export's header is recognised`() {
        val header = listOf("Date", "Type", "Title", "Category", "Wallet", "Amount (Tk)", "Note")
        val m = CsvImport.guessMapping(header, listOf(listOf("2026-09-01", "Expense", "Lunch", "Food", "Cash", "250", "")))
        assertEquals(0, m.date); assertEquals(1, m.type); assertEquals(2, m.title)
        assertEquals(3, m.category); assertEquals(5, m.amount); assertEquals(6, m.note)
    }
}
