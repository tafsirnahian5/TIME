package com.example.timeapp

import com.example.timeapp.data.rampVolume
import com.example.timeapp.data.spokenAlert
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSoundTest {
    @Test
    fun `no fade-in means full volume at once`() {
        assertEquals(0.8f, rampVolume(0, 0.8f, 0), 1e-6f)
    }

    @Test
    fun `fade-in starts audible, rises linearly, then holds`() {
        val ramp = 30_000L
        assertEquals(0.08f, rampVolume(0, 0.8f, ramp), 1e-6f)
        assertEquals(0.44f, rampVolume(15_000, 0.8f, ramp), 1e-6f)
        assertEquals(0.8f, rampVolume(30_000, 0.8f, ramp), 1e-6f)
        assertEquals(0.8f, rampVolume(90_000, 0.8f, ramp), 1e-6f)
    }

    @Test
    fun `volume is never outside 0 to 1`() {
        assertEquals(1f, rampVolume(60_000, 1.7f, 30_000), 1e-6f)
        assertEquals(0f, rampVolume(-5, -1f, 30_000), 1e-6f)
    }

    /** A locked phone with content hidden never reads a task's name to the room. */
    @Test
    fun `spoken alert names nothing when details are hidden`() {
        assertEquals("Time's up on Essay", spokenAlert("Time's up on Essay", hideDetails = false))
        assertEquals("Your timer is done.", spokenAlert("Time's up on Essay", hideDetails = true))
        assertEquals("Your timer is done.", spokenAlert("", hideDetails = false))
    }
}
