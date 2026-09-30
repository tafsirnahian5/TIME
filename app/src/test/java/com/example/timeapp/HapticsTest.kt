package com.example.timeapp

import com.example.timeapp.data.EFFECT_CLICK
import com.example.timeapp.data.EFFECT_DOUBLE_CLICK
import com.example.timeapp.data.EFFECT_HEAVY_CLICK
import com.example.timeapp.data.EFFECT_TICK
import com.example.timeapp.data.HapticEffect
import com.example.timeapp.data.HapticEvent
import com.example.timeapp.data.HapticLevel
import com.example.timeapp.data.hapticEffectFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsTest {
    @Test
    fun `off means nothing for any event`() {
        HapticEvent.entries.forEach { assertNull(hapticEffectFor(HapticLevel.OFF, it)) }
    }

    @Test
    fun `levels get firmer, never softer`() {
        assertEquals(HapticEffect.Predefined(EFFECT_TICK), hapticEffectFor(HapticLevel.LIGHT, HapticEvent.CONFIRM))
        assertEquals(HapticEffect.Predefined(EFFECT_CLICK), hapticEffectFor(HapticLevel.MEDIUM, HapticEvent.CONFIRM))
        assertEquals(HapticEffect.Predefined(EFFECT_HEAVY_CLICK), hapticEffectFor(HapticLevel.STRONG, HapticEvent.CONFIRM))
    }

    @Test
    fun `a milestone stands out from an ordinary tick at every level`() {
        HapticLevel.entries.filter { it != HapticLevel.OFF }.forEach { level ->
            assertTrue(hapticEffectFor(level, HapticEvent.MILESTONE) != hapticEffectFor(level, HapticEvent.TICK))
        }
        assertEquals(HapticEffect.Predefined(EFFECT_DOUBLE_CLICK), hapticEffectFor(HapticLevel.MEDIUM, HapticEvent.MILESTONE))
    }
}
