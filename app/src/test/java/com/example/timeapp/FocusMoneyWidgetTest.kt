package com.example.timeapp

import com.example.timeapp.data.Clocks
import com.example.timeapp.data.Direction
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.FocusRuntime
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.PlanStatus
import com.example.timeapp.data.widget.FocusWidgetAction
import com.example.timeapp.data.widget.focusWidgetContent
import com.example.timeapp.data.widget.moneyWidgetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FocusMoneyWidgetTest {
    private val t0 = Instant.parse("2026-09-28T09:00:00Z")
    private val c = Clocks(t0.plusSeconds(600), 1_600_000, 3)
    private val running = FocusRuntime(
        mode = FocusMode.COUNTDOWN, phase = FocusPhase.WORK, running = true, entryId = 1,
        targetMs = 25 * 60_000L, stretchStartedAt = t0, stretchAnchor = 1_000_000, bootCount = 3,
    )

    @Test fun runningCountdownCountsDownWithPauseAndFinish() {
        val w = focusWidgetContent(running, "Case study", null, locked = false, c)
        assertEquals("FOCUS · COUNTDOWN", w.heading)
        assertEquals("Case study", w.title)
        assertTrue(w.clock!!.countDown && w.clock!!.running)
        assertEquals(15 * 60_000L, w.clock!!.valueMs)
        assertEquals(FocusWidgetAction.PAUSE to FocusWidgetAction.FINISH, w.primary to w.secondary)
    }

    @Test fun lockedNamesNothingAndHasNoControls() {
        val w = focusWidgetContent(running, "Case study", "Other", locked = true, c)
        assertEquals("Timer running", w.title)
        assertNull(w.primary); assertNull(w.secondary)
        assertFalse(w.title.contains("Case"))
    }

    @Test fun idleOffersThePausedTaskByNameOnlyWhenUnlocked() {
        assertEquals("Nothing running · Invoice is paused", focusWidgetContent(FocusRuntime(), null, "Invoice", false, c).title)
        assertEquals("Nothing running", focusWidgetContent(FocusRuntime(), null, "Invoice", true, c).title)
    }

    @Test fun pomodoroBreakOffersSkip() {
        val w = focusWidgetContent(running.copy(mode = FocusMode.POMODORO, phase = FocusPhase.BREAK), null, null, false, c)
        assertEquals("Not work time", w.title)
        assertEquals(FocusWidgetAction.SKIP, w.secondary)
    }

    private val today = LocalDate.of(2026, 9, 28)
    private fun plan(id: Long, due: LocalDate, amount: Long = 100_00, paid: Long = 0) =
        MoneyRecord(id, Direction.COST, "p$id", amountMinor = amount, paidMinor = paid, dueOn = due, status = PlanStatus.PLANNED)

    @Test fun moneyHidesTheBalanceUnlessShownAndCountsWhenLocked() {
        val plans = listOf(plan(1, today.minusDays(2)), plan(2, today.plusDays(7)), plan(3, today.plusDays(8)), plan(4, today, paid = 100_00))
        val hidden = moneyWidgetContent(5_000_00, plans, showAmounts = false, locked = false, today)
        assertTrue(hidden.balance.contains("••••"))
        assertEquals("2 bills due in 7 days", hidden.line)
        val shown = moneyWidgetContent(5_000_00, plans, showAmounts = true, locked = false, today)
        assertEquals("Tk 5,000", shown.balance)
        assertEquals("2 bills due in 7 days · Tk 200", shown.line)
        val locked = moneyWidgetContent(5_000_00, plans, showAmounts = true, locked = true, today)
        assertFalse(locked.balance.contains("5,000"))
        assertEquals("2 bills due in 7 days", locked.line)
    }
}
