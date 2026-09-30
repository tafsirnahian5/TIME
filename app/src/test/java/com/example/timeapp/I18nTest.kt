package com.example.timeapp

import com.example.timeapp.data.BnStrings
import com.example.timeapp.data.L
import com.example.timeapp.data.Lang
import com.example.timeapp.data.adaptPattern
import com.example.timeapp.data.displayPattern
import com.example.timeapp.data.toTaka
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class I18nTest {
    @After fun english() { Lang.bangla = false }

    @Test fun englishIsTheKeyAndPassesThrough() {
        assertEquals("Settings", L("Settings"))
        assertEquals("3 settings match", L("%1\$s settings match", 3))
    }

    @Test fun banglaTranslatesAndLocalisesNumbersButNotUserText() {
        Lang.bangla = true
        assertEquals("সেটিংস", L("Settings"))
        assertEquals("৩টি সেটিং মিলেছে", L("%1\$s settings match", 3))
        // A user's own title is never altered, digits included.
        assertEquals("“Call 3 people” শেষ", L("“%1\$s” is done", "Call 3 people"))
        // Missing from the catalog: English, not a crash.
        assertEquals("Not in the catalog %1\$s".replace("%1\$s", "x"), L("Not in the catalog %1\$s", "x"))
    }

    @Test fun amountsAndDatesInBangla() {
        Lang.bangla = true
        assertEquals("Tk ১,২০০.৫০", 120050L.toTaka())
        val date = LocalDate.of(2026, 9, 28)
        val shown = date.format(displayPattern("d MMM yyyy"))
        assertTrue(shown, shown.contains("২৮") && shown.contains("২০২৬"))
    }

    /** Every Bangla entry formats with arguments for each of its slots — a broken slot would
     * otherwise only surface on the screen that shows it. */
    @Test fun everyEntryFormats() {
        Lang.bangla = true
        BnStrings.map.forEach { (english, bangla) ->
            val slots = Regex("%(\\d+)\\\$s").findAll(english).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0
            if (slots > 0) {
                val args = Array<Any?>(slots) { "x${it + 1}" }
                val out = L(english, *args)
                (1..slots).forEach { n -> assertTrue("slot $n lost in: $bangla", out.contains("x$n")) }
            } else {
                assertEquals(bangla, L(english))
            }
        }
    }

    @Test fun patternsAreUntouchedByLanguage() {
        assertEquals("EEE d MMM", adaptPattern("EEE d MMM", use24h = true, monthFirst = false))
    }
}
