package com.example.timeapp

import com.example.timeapp.data.Clocks
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.FocusRuntime
import com.example.timeapp.data.breakAfter
import com.example.timeapp.data.elapsedMs
import com.example.timeapp.data.dueAt
import com.example.timeapp.data.finishAt
import com.example.timeapp.data.parseLaps
import com.example.timeapp.data.remainingMs
import com.example.timeapp.data.serializeLaps
import com.example.timeapp.data.stretchEnd
import com.example.timeapp.data.workTargetMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FocusMathTest {
    private val t0 = Instant.parse("2026-09-28T09:00:00Z")
    private val minute = 60_000L
    private fun clocks(afterMs: Long, boot: Int = 7, wallSkewMs: Long = 0) =
        Clocks(t0.plusMillis(afterMs + wallSkewMs), 1_000_000 + afterMs, boot)
    private fun running(mode: FocusMode = FocusMode.COUNTDOWN, accumulated: Long = 0) = FocusRuntime(
        mode = mode, phase = FocusPhase.WORK, running = true, targetMs = 30 * minute, accumulatedMs = accumulated,
        stretchStartedAt = t0, stretchAnchor = 1_000_000, bootCount = 7,
    )

    /** A late alarm still ends a countdown when it ran out — finishAt would say "now". */
    @Test
    fun `due moment is not clamped to now when the alarm is late`() {
        val r = running()
        assertEquals(t0.plusMillis(30 * minute), r.dueAt(clocks(40 * minute)))
        assertEquals(t0.plusMillis(40 * minute), r.finishAt(clocks(40 * minute)))
        assertEquals(t0.plusMillis(30 * minute), r.dueAt(clocks(10 * minute)))
    }

    /** Changing the phone's time mid-session neither adds nor removes work. */
    @Test
    fun `elapsed time follows the monotonic clock, not the wall clock`() {
        val r = running()
        assertEquals(10 * minute, r.elapsedMs(clocks(10 * minute, wallSkewMs = 3_600_000)))
        assertEquals(10 * minute, r.elapsedMs(clocks(10 * minute, wallSkewMs = -3_600_000)))
    }

    @Test
    fun `a paused phase keeps what it ran, and the countdown what's left`() {
        val paused = running(accumulated = 12 * minute).copy(running = false, stretchStartedAt = null, stretchAnchor = null)
        assertEquals(12 * minute, paused.elapsedMs(clocks(99 * minute)))
        assertEquals(18 * minute, paused.remainingMs(clocks(99 * minute)))
        assertNull(paused.finishAt(clocks(0)))
    }

    @Test
    fun `finish is now plus what's left`() {
        val r = running(accumulated = 5 * minute)
        assertEquals(t0.plusMillis(25 * minute), r.finishAt(clocks(0)))
        assertNull(running(FocusMode.STOPWATCH).copy(targetMs = null).finishAt(clocks(0)))
    }

    /** Across a reboot the old monotonic anchor is meaningless: the wall clock is used, and
     * the result is marked uncertain rather than trusted. */
    @Test
    fun `a stretch across a reboot is uncertain`() {
        val (end, uncertain) = stretchEnd(t0, 1_000_000, 7, Clocks(t0.plusMillis(40 * minute), 5_000, 8))
        assertEquals(t0.plusMillis(40 * minute), end)
        assertTrue(uncertain)
    }

    @Test
    fun `a stretch in the same boot ends by the monotonic clock`() {
        val (end, uncertain) = stretchEnd(t0, 1_000_000, 7, clocks(20 * minute))
        assertEquals(t0.plusMillis(20 * minute), end)
        assertFalse(uncertain)
        // Someone moved the clock an hour forward: length stays 20 min, flagged for review.
        val (skewedEnd, skewed) = stretchEnd(t0, 1_000_000, 7, clocks(20 * minute, wallSkewMs = 3_600_000))
        assertEquals(t0.plusMillis(20 * minute), skewedEnd)
        assertTrue(skewed)
    }

    @Test
    fun `every fourth break is long`() {
        val r = FocusRuntime(breakMinutes = 5, longBreakMinutes = 15, cyclesPerLongBreak = 4)
        assertEquals(listOf(5, 5, 5, 15, 5), (1..5).map { r.breakAfter(it) })
    }

    @Test
    fun `targets per mode`() {
        val r = FocusRuntime(countdownMinutes = 45, workMinutes = 25)
        assertNull(r.workTargetMs(FocusMode.STOPWATCH))
        assertEquals(45 * minute, r.workTargetMs(FocusMode.COUNTDOWN))
        assertEquals(25 * minute, r.workTargetMs(FocusMode.POMODORO))
    }

    @Test
    fun `laps round trip`() {
        val r = FocusRuntime(laps = listOf(61_000, 125_500))
        assertEquals(listOf(61_000L, 125_500L), parseLaps(r.serializeLaps()))
        assertEquals(emptyList<Long>(), parseLaps(""))
    }

    @Test
    fun `a session edit must end after it starts, not in the future, and not overlap`() {
        val now = t0.plusSeconds(3 * 3600)
        val other = t0.plusSeconds(3600) to t0.plusSeconds(5400)
        assertEquals("It has to end after it starts.", com.example.timeapp.data.sessionEditProblem(t0, t0, emptyList(), now))
        assertEquals("It can't end in the future.", com.example.timeapp.data.sessionEditProblem(t0, now.plusSeconds(600), emptyList(), now))
        assertTrue(com.example.timeapp.data.sessionEditProblem(t0, t0.plusSeconds(3700), listOf(other), now)!!.contains("overlaps"))
        // Touching end to start is fine: segments that meet don't double-count.
        assertNull(com.example.timeapp.data.sessionEditProblem(t0, t0.plusSeconds(3600), listOf(other), now))
    }
}
