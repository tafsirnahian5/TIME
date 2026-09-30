package com.example.timeapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a reminder fires, as the user chose it (Phase 11: "per-reminder custom time"). Kept on
 * the reminder row so a repeat occurrence gets the same, and a timezone change moves a
 * date-anchored one to the same wall-clock time instead of resetting it. Pure; tested in
 * ReminderTimingTest.
 */
sealed class ReminderTiming {
    /** [dayOffset] days from the due date (0 = on the day, -1 = the day before), at [time]. */
    data class OnDay(val dayOffset: Int, val time: LocalTime) : ReminderTiming()

    /** [minutes] before a block starts (0 = as it starts). */
    data class BeforeStart(val minutes: Int) : ReminderTiming()

    /** The moment, or null when there's nothing to anchor on. */
    fun at(dueOn: LocalDate?, start: Instant?, zone: ZoneId = ZoneId.systemDefault()): Instant? = when (this) {
        is OnDay -> dueOn?.plusDays(dayOffset.toLong())?.atTime(time)?.atZone(zone)?.toInstant()
        is BeforeStart -> start?.minusSeconds(minutes * 60L)
    }

    fun label(): String = when (this) {
        is OnDay -> {
            val t = time.format(displayPattern("HH:mm"))
            when (dayOffset) {
                0 -> L("%1\$s on the day", t)
                -1 -> L("%1\$s the day before", t)
                else -> L("%1\$s, %2\$s days before", t, -dayOffset)
            }
        }
        is BeforeStart -> if (minutes == 0) L("As it starts") else if (minutes % 60 == 0) L("%1\$s h before", (minutes / 60)) else L("%1\$s min before", minutes)
    }

    companion object {
        val TASK_DEFAULT = OnDay(0, LocalTime.of(9, 0))
        val PLAN_DEFAULT = OnDay(-1, LocalTime.of(9, 0))
        val EVENT_DEFAULT = BeforeStart(0)

        /** Rebuilds a stored timing from the reminder row's columns. */
        fun from(dayOffset: Int?, localTime: String?, minutesBefore: Int?): ReminderTiming? = when {
            minutesBefore != null -> BeforeStart(minutesBefore)
            dayOffset != null && localTime != null -> runCatching { OnDay(dayOffset, LocalTime.parse(localTime)) }.getOrNull()
            else -> null
        }
    }
}
