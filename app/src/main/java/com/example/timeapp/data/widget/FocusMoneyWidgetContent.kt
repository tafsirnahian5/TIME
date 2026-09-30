package com.example.timeapp.data.widget

import com.example.timeapp.data.L

import com.example.timeapp.data.Clocks
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.FocusRuntime
import com.example.timeapp.data.MoneyRecord
import com.example.timeapp.data.elapsedMs
import com.example.timeapp.data.remainingMs
import com.example.timeapp.data.toTaka
import java.time.LocalDate

/** What a widget button does — the Focus notification's own actions. */
enum class FocusWidgetAction(val label: String) { PAUSE(L("Pause")), RESUME(L("Resume")), FINISH(L("Finish")), SKIP(L("Skip break")) }

/** The clock line: counting [countDown] or up, [running] or held at [valueMs]. */
data class WidgetClock(val countDown: Boolean, val running: Boolean, val valueMs: Long)

data class FocusWidgetContent(
    val heading: String,
    val title: String,
    val clock: WidgetClock?,
    val primary: FocusWidgetAction?,
    val secondary: FocusWidgetAction?,
)

/**
 * The Focus widget (Phase 15; canvas): the one work timer's state and its controls. Pure, so
 * tested in FocusMoneyWidgetTest. **Locked**: says a timer runs, never what for, and offers no
 * controls — a tap opens the app through the lock.
 */
fun focusWidgetContent(rt: FocusRuntime, taskTitle: String?, pausedTaskTitle: String?, locked: Boolean, c: Clocks): FocusWidgetContent {
    if (rt.phase == FocusPhase.IDLE) {
        return FocusWidgetContent(
            L("FOCUS"),
            if (locked || pausedTaskTitle == null) L("Nothing running") else L("Nothing running · %1\$s is paused", pausedTaskTitle),
            null, null, null,
        )
    }
    val modeLabel = when {
        rt.phase == FocusPhase.BREAK -> L("BREAK")
        rt.mode == FocusMode.STOPWATCH -> L("FOCUS · STOPWATCH")
        rt.mode == FocusMode.COUNTDOWN -> L("FOCUS · COUNTDOWN")
        else -> L("FOCUS · POMODORO")
    }
    if (rt.awaitingChoice) return FocusWidgetContent(modeLabel, L("Time's up — choose what's next in T!ME"), null, null, null)
    val remaining = rt.remainingMs(c)
    val clock = WidgetClock(countDown = remaining != null, running = rt.running, valueMs = remaining ?: rt.elapsedMs(c))
    if (locked) return FocusWidgetContent(modeLabel, if (rt.phase == FocusPhase.BREAK) L("Break running") else L("Timer running"), clock, null, null)
    val title = when {
        rt.phase == FocusPhase.BREAK -> L("Not work time")
        else -> taskTitle ?: L("Focus, no task")
    }
    return FocusWidgetContent(
        modeLabel, title, clock,
        primary = if (rt.running) FocusWidgetAction.PAUSE else FocusWidgetAction.RESUME,
        secondary = if (rt.phase == FocusPhase.BREAK && rt.mode == FocusMode.POMODORO) FocusWidgetAction.SKIP else FocusWidgetAction.FINISH,
    )
}

data class MoneyWidgetContent(val heading: String, val note: String, val balance: String, val line: String, val footer: String)

/**
 * The Money widget (Phase 15; canvas): the total across wallets, **hidden unless the user turned
 * amounts on** (Money settings), and bills due in the next 7 days (overdue included) as a count.
 * Locked: counts only, whatever the amounts setting says.
 */
fun moneyWidgetContent(balanceMinor: Long, openPlans: List<MoneyRecord>, showAmounts: Boolean, locked: Boolean, today: LocalDate): MoneyWidgetContent {
    val due = openPlans.filter { p -> p.remainingMinor > 0 && p.dueOn != null && !p.dueOn.isAfter(today.plusDays(7)) }
    val dueLine = when (due.size) {
        0 -> L("No bills due in the next 7 days")
        1 -> L("1 bill due in 7 days")
        else -> L("%1\$s bills due in 7 days", due.size)
    }
    if (locked) return MoneyWidgetContent(L("MONEY · LOCKED"), "", L("T!ME is locked"), dueLine, L("With the app lock on, widgets show counts only."))
    return if (showAmounts) {
        MoneyWidgetContent(L("MONEY"), L("All wallets"), balanceMinor.toTaka(), dueLine + (due.sumOf { it.remainingMinor }.takeIf { it > 0 }?.let { " · ${it.toTaka()}" } ?: ""), L("Tap to open T!ME."))
    } else {
        MoneyWidgetContent(L("MONEY"), L("Balances hidden"), "${com.example.timeapp.data.DisplayFormats.currencyPrefix}••••••", dueLine, L("Amounts show only if you turn them on in Money settings."))
    }
}
