package com.example.timeapp

import com.example.timeapp.data.db.UtilityTimerEntity
import com.example.timeapp.data.db.remainingAt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class UtilityTimerTest {
    private val now = Instant.parse("2026-09-28T10:00:00Z")

    @Test
    fun `a running timer counts down from its end instant and stops at zero`() {
        val t = UtilityTimerEntity(1, "Tea", 300_000, endsAt = now.plusSeconds(90), remainingMs = 300_000)
        assertEquals(90_000L, t.remainingAt(now))
        assertEquals(0L, t.remainingAt(now.plusSeconds(600)))
    }

    @Test
    fun `a paused timer keeps what was left`() {
        assertEquals(42_000L, UtilityTimerEntity(1, "Tea", 300_000, endsAt = null, remainingMs = 42_000).remainingAt(now))
    }
}
