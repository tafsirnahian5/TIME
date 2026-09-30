package com.example.timeapp.data

import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceFreq { DAILY, WEEKLY, MONTHLY }

/** What a monthly repeat on the 29th–31st does in a month without that day (the PDF's
 * day-31 rule, now the user's choice). */
enum class MonthEndRule { LAST_VALID_DAY, SKIP_MONTH }

/**
 * A repeat rule attached to a task, event or planned cost.
 *
 * Occurrences are **separate records sharing a `seriesId`**, never one row that mutates —
 * so paying October's rent leaves November's untouched, and completing Tuesday's task does
 * not complete the series. Generation is bounded (see [RecurrenceExpander]); nothing is
 * pre-generated to infinity.
 *
 * Every occurrence carries a copy of the rule, so whichever row the expander treats as the
 * template sees the same thing. Series-wide state therefore lives here too:
 * - [remind]: each generated occurrence gets its own reminder, at the fixed per-kind time
 *   (09:00 on a task's due date, 09:00 the day before a bill, a block's own start). Without
 *   it only the first occurrence ever reminded.
 * - [skipped]: dates deliberately removed from the series ("delete only this"), so the
 *   expander never re-creates them — iCalendar's EXDATE, for the same reason.
 */
data class Recurrence(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val endOn: LocalDate? = null,
    /** WEEKLY only: the days it falls on. Empty means "the same weekday each time". */
    val weekdays: Set<DayOfWeek> = emptySet(),
    val remind: Boolean = false,
    val skipped: Set<LocalDate> = emptySet(),
    /** MONTHLY only. Default: the last valid day, so a bill never vanishes from February. */
    val monthEnd: MonthEndRule = MonthEndRule.LAST_VALID_DAY,
) {
    /**
     * The next date strictly after [date], or null once the rule has ended.
     *
     * Monthly keeps the day of [anchor] (the series' first occurrence): the 31st of January
     * repeats to 28/29 February and then back to the 31st of March. It used to step from the
     * previous occurrence, so after one short month every later one landed on the 28th.
     * [MonthEndRule.LAST_VALID_DAY] (default) clamps in a short month; [MonthEndRule.SKIP_MONTH]
     * leaves it out — iCalendar's own behaviour, used for imported rules.
     *
     * Weekly with [weekdays] walks to the next listed day in the same Monday-started week,
     * and past the last one jumps [interval] weeks ahead to the first listed day — so
     * "every 2 weeks on Mon, Thu" is Mon, Thu, (skip a week), Mon, Thu.
     */
    fun nextAfter(date: LocalDate, anchor: LocalDate? = null): LocalDate? {
        val step = interval.coerceAtLeast(1).toLong()
        val next = when (freq) {
            RecurrenceFreq.DAILY -> date.plusDays(step)
            RecurrenceFreq.WEEKLY -> if (weekdays.isEmpty()) date.plusWeeks(step) else nextListedWeekday(date, step)
            RecurrenceFreq.MONTHLY -> nextMonthly(date, (anchor ?: date).dayOfMonth, step)
        }
        return if (endOn != null && next.isAfter(endOn)) null else next
    }

    private fun nextMonthly(date: LocalDate, day: Int, step: Long): LocalDate {
        var month = java.time.YearMonth.from(date).plusMonths(step)
        if (monthEnd == MonthEndRule.SKIP_MONTH) {
            // Bounded: some month within four years always has the day (even Feb 29 yearly).
            var guard = 0
            while (day > month.lengthOfMonth() && guard++ < 48) month = month.plusMonths(step)
        }
        return month.atDay(minOf(day, month.lengthOfMonth()))
    }

    private fun nextListedWeekday(date: LocalDate, step: Long): LocalDate {
        val days = weekdays.sortedBy { it.value }
        days.firstOrNull { it.value > date.dayOfWeek.value }?.let {
            return date.plusDays((it.value - date.dayOfWeek.value).toLong())
        }
        val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return weekStart.plusWeeks(step).plusDays((days.first().value - 1).toLong())
    }

    /** "Every 2 weeks", "Monthly", "Weekly on Mon, Fri" — shown on forms and list rows. */
    fun label(): String {
        val unit = when (freq) {
            RecurrenceFreq.DAILY -> if (interval == 1) L("Daily") else "days"
            RecurrenceFreq.WEEKLY -> if (interval == 1) L("Weekly") else "weeks"
            RecurrenceFreq.MONTHLY -> if (interval == 1) L("Monthly") else "months"
        }
        val base = if (interval == 1) unit else L("Every %1\$s %2\$s", interval, unit)
        if (freq == RecurrenceFreq.MONTHLY && monthEnd == MonthEndRule.SKIP_MONTH) return L("%1\$s, skipping short months", base)
        if (freq != RecurrenceFreq.WEEKLY || weekdays.isEmpty()) return base
        return L("%1\$s on ", base) + weekdays.sortedBy { it.value }.joinToString(", ") { it.displayLabel() }
    }

    /**
     * Compact single-column form: `FREQ|interval|endOn|MON,FRI|R|2026-10-01,2026-11-01|SKIPMONTH`.
     * Everything after the interval is optional, so rows written before weekdays, reminders
     * and skipped dates existed (`MONTHLY|1|`) still parse — no schema change was needed.
     */
    fun serialize(): String = listOf(
        freq.name,
        interval.toString(),
        endOn?.toString() ?: "",
        weekdays.sortedBy { it.value }.joinToString(",") { it.name },
        if (remind) "R" else "",
        skipped.sorted().joinToString(","),
        if (monthEnd == MonthEndRule.SKIP_MONTH) "SKIPMONTH" else "",
    ).joinToString("|")

    companion object {
        fun parse(raw: String?): Recurrence? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("|")
            val freq = parts.getOrNull(0)?.let { runCatching { RecurrenceFreq.valueOf(it) }.getOrNull() } ?: return null
            val interval = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val endOn = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val weekdays = parts.getOrNull(3).orEmpty().split(",")
                .mapNotNull { name -> runCatching { DayOfWeek.valueOf(name) }.getOrNull() }
                .toSet()
            val remind = parts.getOrNull(4) == "R"
            val skipped = parts.getOrNull(5).orEmpty().split(",")
                .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                .toSet()
            val monthEnd = if (parts.getOrNull(6) == "SKIPMONTH") MonthEndRule.SKIP_MONTH else MonthEndRule.LAST_VALID_DAY
            return Recurrence(freq, interval, endOn, weekdays, remind, skipped, monthEnd)
        }
    }
}

/** "Mon", "Tue" … — display copy, the enum name is storage. */
fun DayOfWeek.shortLabel(): String = name.take(1) + name.substring(1, 3).lowercase()

/** The short name as shown — translated. [shortLabel] stays English: settings store it. */
fun DayOfWeek.displayLabel(): String = L(shortLabel())

/** A day chip's label: two letters in English ("Mo"), the whole short name in Bangla. */
fun DayOfWeek.chipLabel(): String = if (Lang.bangla) displayLabel() else shortLabel().take(2)

/** Same repeat *rule*, ignoring series bookkeeping (reminder flag, skipped dates) — what a
 * form compares to decide whether an edit changed how the series repeats. */
fun Recurrence?.sameRuleAs(other: Recurrence?): Boolean =
    this?.copy(remind = false, skipped = emptySet()) == other?.copy(remind = false, skipped = emptySet())

/** Which occurrences an edit or delete of one series row applies to. */
enum class SeriesScope { ONLY_THIS, THIS_AND_FUTURE }

/** One row of a series as [planSeriesSplit] sees it. [locked] means it carries history —
 * done/tracked work, or a plan with any payment — and so is never deleted by a series edit. */
data class SeriesOccurrence(val id: Long, val date: LocalDate?, val locked: Boolean)

/**
 * What "this and future" does to a series, split at [pivot]:
 * - [delete]: later occurrences with no history, removed so the expander regenerates them
 *   from the edited row (and so they pick up the new title, amount, rule and reminder).
 * - [moveToNew]: later occurrences that *do* have history. They are kept untouched but join
 *   the new series, so their dates count as taken and nothing is generated on top of them.
 * - [keepInOld]: earlier occurrences. The old series is ended the day before [pivot], so it
 *   never grows past the split again.
 */
data class SeriesSplit(
    val delete: List<Long>,
    val moveToNew: List<Long>,
    val keepInOld: List<Long>,
    val endOldOn: LocalDate?,
)

fun planSeriesSplit(occurrences: List<SeriesOccurrence>, pivot: SeriesOccurrence): SeriesSplit {
    val pivotDate = pivot.date
    val others = occurrences.filter { it.id != pivot.id }
    // An undated pivot has no "future" to speak of; treat every other row as earlier.
    val (later, earlier) = others.partition { o -> pivotDate != null && o.date != null && o.date.isAfter(pivotDate) }
    return SeriesSplit(
        delete = later.filterNot { it.locked }.map { it.id },
        moveToNew = later.filter { it.locked }.map { it.id },
        keepInOld = earlier.map { it.id },
        endOldOn = pivotDate?.minusDays(1),
    )
}
