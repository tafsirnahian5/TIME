package com.example.timeapp

import com.example.timeapp.data.MAX_NOTE_CHARS
import com.example.timeapp.data.clampedNote
import com.example.timeapp.data.clampedTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TextLimitsTest {
    @Test fun shortTextIsUntouched() {
        val s = "hello"
        assertSame(s, s.clampedNote())
    }

    @Test fun longTextIsCutToTheLimit() {
        assertEquals(MAX_NOTE_CHARS, "x".repeat(MAX_NOTE_CHARS + 50).clampedNote().length)
    }

    @Test fun anEmojiIsNeverCutInHalf() {
        // "ab" then 🚀 (two chars): a cut at 3 would leave half of the rocket.
        assertEquals("ab", "ab🚀".clampedTo(3))
        assertEquals("ab🚀", "ab🚀c".clampedTo(4))
    }
}
