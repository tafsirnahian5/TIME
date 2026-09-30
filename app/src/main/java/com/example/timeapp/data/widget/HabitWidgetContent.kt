package com.example.timeapp.data.widget

import com.example.timeapp.data.L

import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.showsToday
import java.time.LocalDate

/** One widget row. [checkable]: a tap checks it off; otherwise (done, measurable, timed) a
 * tap opens the habit in the app. */
data class HabitWidgetRow(val habitId: Long, val text: String, val checkable: Boolean)

data class HabitWidgetContent(val heading: String, val rows: List<HabitWidgetRow>, val footer: String)

/**
 * The habit widget's text (Phase 12): today's habits, up to four, with one-tap check-ins. With
 * the app lock on it shows counts only — never a habit's name (the PDF: a locked widget never
 * exposes protected content). Pure; tested in HabitWidgetTest.
 */
fun habitWidgetContent(habits: List<Habit>, marks: Map<Long, Map<LocalDate, HabitMark>>, locked: Boolean, today: LocalDate): HabitWidgetContent {
    val due = habits.filter { it.showsToday(marks[it.id].orEmpty(), today) }
    val done = due.count { marks[it.id].orEmpty()[today] == HabitMark.DONE }
    val footer = if (due.isEmpty()) L("Nothing due today") else L("%1\$s of %2\$s done today", done, due.size)
    if (locked) return HabitWidgetContent(L("Habits"), emptyList(), L("%1\$s · open T!ME to check in", footer))
    val rows = due.sortedBy { marks[it.id].orEmpty()[today] == HabitMark.DONE }.take(4).map { h ->
        val isDone = marks[h.id].orEmpty()[today] == HabitMark.DONE
        HabitWidgetRow(h.id, (if (isDone) "✓  " else "○  ") + h.name, checkable = !isDone && h.target == null && h.timerMinutes == null)
    }
    return HabitWidgetContent(L("Habits today"), rows, footer)
}
