package com.example.timeapp

import com.example.timeapp.data.isPartialTakaInput
import com.example.timeapp.data.parseTakaToMinor
import com.example.timeapp.data.toTaka
import com.example.timeapp.data.toTakaInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Money is integer poisha end to end. These pin the exact behaviour that replaced the old
 * `text.toLongOrNull() ?: 0L * 100`, under which "1200.50" silently saved as Tk 0.
 */
class MoneyInputTest {

    @Test
    fun `whole taka parses to poisha`() {
        assertEquals(120000L, parseTakaToMinor("1200"))
        assertEquals(0L, parseTakaToMinor("0"))
    }

    @Test
    fun `two decimal places parse exactly`() {
        assertEquals(120050L, parseTakaToMinor("1200.50"))
        assertEquals(120005L, parseTakaToMinor("1200.05"))
    }

    @Test
    fun `one decimal place means tenths, not hundredths`() {
        // "1200.5" is Tk 1200 and 50 poisha — not 5 poisha.
        assertEquals(120050L, parseTakaToMinor("1200.5"))
    }

    /** The PDF's own worked example: Tk 0.10 + Tk 0.20 must be exactly Tk 0.30. */
    @Test
    fun `no floating point drift`() {
        val sum = parseTakaToMinor("0.10")!! + parseTakaToMinor("0.20")!!
        assertEquals(30L, sum)
        assertEquals("Tk 0.30", sum.toTaka())
    }

    @Test
    fun `more than two decimals is rejected rather than rounded`() {
        assertNull(parseTakaToMinor("1200.505"))
    }

    @Test
    fun `junk and blanks are rejected, never coerced to zero`() {
        assertNull(parseTakaToMinor(""))
        assertNull(parseTakaToMinor("   "))
        assertNull(parseTakaToMinor("abc"))
        assertNull(parseTakaToMinor("12.3.4"))
        assertNull(parseTakaToMinor("12a"))
    }

    @Test
    fun `overflowing magnitude is rejected`() {
        assertNull(parseTakaToMinor("9223372036854775808")) // Long.MAX_VALUE + 1
        assertNull(parseTakaToMinor("99999999999999999999"))
        // The subtle one: whole * 100 still fits here, but adding the poisha overflows —
        // a plain magnitude check would let this through as a negative number.
        assertNull(parseTakaToMinor("92233720368547758.99"))
    }

    @Test
    fun `commas in pasted input are tolerated`() {
        assertEquals(120050L, parseTakaToMinor("1,200.50"))
    }

    @Test
    fun `negative amounts round-trip`() {
        assertEquals(-120050L, parseTakaToMinor("-1200.50"))
        assertEquals("-Tk 1,200.50", (-120050L).toTaka())
    }

    @Test
    fun `display keeps whole amounts clean and shows poisha when present`() {
        assertEquals("Tk 1,200", 120000L.toTaka())
        assertEquals("Tk 1,200.50", 120050L.toTaka())
        assertEquals("Tk 0.05", 5L.toTaka())
    }

    @Test
    fun `edit-field formatting round-trips through the parser`() {
        listOf(0L, 5L, 50L, 120000L, 120050L).forEach { minor ->
            assertEquals(minor, parseTakaToMinor(minor.toTakaInput()))
        }
    }

    @Test
    fun `partial input stays typeable`() {
        assertTrue(isPartialTakaInput(""))
        assertTrue(isPartialTakaInput("12"))
        assertTrue(isPartialTakaInput("12."))
        assertTrue(isPartialTakaInput("12.5"))
        assertTrue(isPartialTakaInput("12.50"))
        assertFalse(isPartialTakaInput("12.505"))
        assertFalse(isPartialTakaInput("12..5"))
        assertFalse(isPartialTakaInput("12a"))
    }
}
