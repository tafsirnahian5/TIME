package com.example.timeapp

import com.example.timeapp.data.DisplayFormats
import com.example.timeapp.data.SETTINGS_INDEX
import com.example.timeapp.data.SettingsPage
import com.example.timeapp.data.adaptPattern
import com.example.timeapp.data.searchSettings
import com.example.timeapp.data.toTaka
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayFormatsTest {
    @After fun reset() { DisplayFormats.use24h = true; DisplayFormats.monthFirst = false; DisplayFormats.currencyLabel = "Tk" }

    @Test fun dayFirst24hIsUnchanged() {
        listOf("EEE d MMM", "HH:mm", "d MMM yyyy", "EEEE d MMM", "EEE d MMM · HH:mm").forEach {
            assertEquals(it, adaptPattern(it, use24h = true, monthFirst = false))
        }
        // Patterns written month-first become day-first too, so the choice is consistent.
        assertEquals("d MMM yyyy", adaptPattern("MMM d, yyyy", true, false))
        assertEquals("d MMM", adaptPattern("MMM d", true, false))
    }

    @Test fun monthFirst() {
        assertEquals("EEE MMM d", adaptPattern("EEE d MMM", true, true))
        assertEquals("MMM d, yyyy", adaptPattern("d MMM yyyy", true, true))
        assertEquals("MMMM yyyy", adaptPattern("MMMM yyyy", true, true))
        assertEquals("MMM yyyy", adaptPattern("MMM yyyy", true, true))
        assertEquals("EEE", adaptPattern("EEE", true, true))
    }

    @Test fun twelveHour() {
        assertEquals("EEE d MMM · h:mm a", adaptPattern("EEE d MMM · HH:mm", false, false))
        assertEquals("h:mm a", adaptPattern("H:mm", false, false))
    }

    @Test fun currencyLabelOnlyChangesTheLook() {
        assertEquals("Tk 1,200.50", 120050L.toTaka())
        DisplayFormats.currencyLabel = "৳"
        assertEquals("৳1,200.50", 120050L.toTaka())
        assertEquals("-৳400", (-40000L).toTaka())
    }

    @Test fun settingsSearchRanksTitlesFirstAndKnowsSynonyms() {
        val quiet = searchSettings("quiet")
        assertEquals("Quiet hours", quiet.first().title)
        assertTrue(quiet.any { it.title == "Ring like an alarm" })
        assertEquals(SettingsPage.APPEARANCE, searchSettings("amoled").single().page)
        assertEquals(emptyList<Any>(), searchSettings("   "))
        // Every page has at least one entry, so nothing is unreachable by search.
        assertEquals(SettingsPage.entries.toSet(), SETTINGS_INDEX.map { it.page }.toSet())
    }
}
