package com.example.timeapp.data.widget

import com.example.timeapp.data.L

import com.example.timeapp.data.displayPattern

import com.example.timeapp.data.withBlocksExpanded
import com.example.timeapp.data.Entry
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.showsToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the Today widget shows: a heading, up to [MAX_LINES] lines, and a footer. */
data class WidgetContent(val heading: String, val lines: List<String>, val footer: String)

const val MAX_LINES = 4

/**
 * The Today widget's text — pure, tested in TodayWidgetTest. With the app lock on it shows
 * **counts only**, never a task or habit name (the PDF: "a locked … widget never exposes
 * protected content"); tapping still goes through the lock screen.
 */
fun todayWidgetContent(
    entries: List<Entry>,
    habits: List<Habit>,
    habitMarks: Map<Long, Map<LocalDate, HabitMark>>,
    locked: Boolean,
    now: Instant,
    zone: ZoneId,
): WidgetContent {
    val today = now.atZone(zone).toLocalDate()
    val heading = L("Today · ") + today.format(displayPattern("EEE d MMM"))
    // Archived tasks are out of every default view, the widget included.
    val entries = entries.filter { it.archivedAt == null }
    val current = entries.firstOrNull { it.status == EntryStatus.DOING } ?: entries.firstOrNull { it.status == EntryStatus.PAUSED }
    val upcoming = entries.withBlocksExpanded()
        .filter { it.startAt != null && it.startAt.isAfter(now) && it.startAt.atZone(zone).toLocalDate() == today && it.id != current?.id }
        .sortedBy { it.startAt }
    val dueToday = entries.filter { it.startAt == null && it.dueOn == today && it.status != EntryStatus.DONE && it.id != current?.id }
    val habitsLeft = habits.filter { h ->
        val marks = habitMarks[h.id].orEmpty()
        h.showsToday(marks, today) && marks[today] == null
    }

    if (locked) {
        val parts = listOfNotNull(
            (upcoming.size + dueToday.size).takeIf { it > 0 }?.let { if (it == 1) L("1 thing scheduled or due") else L("%1\$s things scheduled or due", it) },
            habitsLeft.size.takeIf { it > 0 }?.let { if (it == 1) L("1 habit to check in") else L("%1\$s habits to check in", it) },
        )
        return WidgetContent(heading, parts.ifEmpty { listOf(L("Nothing left for today")) }, L("Locked — open T!ME to see details"))
    }

    val time = displayPattern("HH:mm")
    val lines = buildList {
        current?.let { add((if (it.status == EntryStatus.DOING) L("● Now: ") else L("‖ Paused: ")) + it.title) }
        upcoming.take(2).forEach { add("${it.startAt!!.atZone(zone).format(time)}  ${it.title}") }
        dueToday.take(2).forEach { add(L("Due  %1\$s", it.title)) }
        if (habitsLeft.isNotEmpty()) add(L("Habits: ") + habitsLeft.joinToString(", ") { it.name })
    }
    val shown = lines.take(MAX_LINES)
    val hidden = lines.size - shown.size
    return WidgetContent(
        heading,
        shown.ifEmpty { listOf(L("Nothing left for today")) },
        if (hidden > 0) L("+%1\$s more in T!ME", hidden) else L("Tap to open"),
    )
}
