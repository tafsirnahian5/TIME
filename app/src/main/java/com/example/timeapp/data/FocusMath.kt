package com.example.timeapp.data

import java.time.Duration
import java.time.Instant

/** How the one work timer is being run. Stored by name. */
enum class FocusMode { STOPWATCH, COUNTDOWN, POMODORO }

/** [IDLE]: nothing started. [WORK]: a work phase, running or paused — only a *running* work
 * phase has an open work session. [BREAK]: a rest countdown; never work time. */
enum class FocusPhase { IDLE, WORK, BREAK }

/** How a work session was run, kept on the session for history. [TASK] is a plain Start on a
 * task outside Focus. Stored by name. */
enum class SessionMode { TASK, STOPWATCH, COUNTDOWN, POMODORO }

/**
 * A reading of the clocks: wall time, `SystemClock.elapsedRealtime()` and which boot this is.
 * The PDF's timing rule: durations come from the monotonic clock within one boot (immune to
 * the user changing the time); wall time is for history, and across a reboot the old
 * monotonic anchor means nothing.
 */
data class Clocks(val wall: Instant, val elapsed: Long, val boot: Int?)

/**
 * The one work timer — the PDF's TimerRuntime singleton. The time itself lives in work
 * sessions; this says how the timer is being run: mode, phase, target, the current running
 * stretch's anchors, and what ran before it in this phase ([accumulatedMs]).
 */
data class FocusRuntime(
    val mode: FocusMode = FocusMode.STOPWATCH,
    val phase: FocusPhase = FocusPhase.IDLE,
    val running: Boolean = false,
    /** The task this run is on; null for a Focus run with no task. */
    val entryId: Long? = null,
    /** Length of the current countdown or Pomodoro phase; null for the stopwatch. */
    val targetMs: Long? = null,
    /** Time run in this phase before the current running stretch. */
    val accumulatedMs: Long = 0,
    val stretchStartedAt: Instant? = null,
    val stretchAnchor: Long? = null,
    val bootCount: Int? = null,
    /** Completed Pomodoro work phases in this run. */
    val cycles: Int = 0,
    /** Lap marks, as offsets into this run. */
    val laps: List<Long> = emptyList(),
    /** A countdown ran out and is waiting for Continue / Take a break / Finish. */
    val awaitingChoice: Boolean = false,
    val countdownMinutes: Int = 30,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesPerLongBreak: Int = 4,
    val autoStart: Boolean = false,
)

/** The current running stretch so far: monotonic within the same boot, wall clock only when
 * the anchor is missing (a session started before anchors were kept). */
fun FocusRuntime.stretchMs(c: Clocks): Long {
    if (!running) return 0
    val ms = when {
        stretchAnchor != null && bootCount != null && bootCount == c.boot -> c.elapsed - stretchAnchor
        stretchStartedAt != null -> Duration.between(stretchStartedAt, c.wall).toMillis()
        else -> 0
    }
    return ms.coerceAtLeast(0)
}

/** Time run in this phase (or this stopwatch run), paused stretches included. */
fun FocusRuntime.elapsedMs(c: Clocks): Long = accumulatedMs + stretchMs(c)

/** What's left of a countdown or Pomodoro phase; null for the stopwatch. */
fun FocusRuntime.remainingMs(c: Clocks): Long? = targetMs?.let { (it - elapsedMs(c)).coerceAtLeast(0) }

/** When a running countdown / phase will end, on the wall clock — what its alarm is set for. */
fun FocusRuntime.finishAt(c: Clocks): Instant? =
    if (running && targetMs != null) c.wall.plusMillis(remainingMs(c) ?: 0) else null

/**
 * The moment a running countdown / phase runs out, even when that's already past. Unlike
 * [finishAt] it isn't clamped to now, so a late alarm (Doze, a phone that was off) still ends
 * work when the time ran out rather than when the alarm finally arrived.
 */
fun FocusRuntime.dueAt(c: Clocks): Instant? =
    if (running && targetMs != null) c.wall.plusMillis(targetMs - elapsedMs(c)) else null

/** The length of the break after a completed work phase: long every [FocusRuntime.cyclesPerLongBreak]. */
fun FocusRuntime.breakAfter(completedCycles: Int): Int =
    if (cyclesPerLongBreak > 0 && completedCycles > 0 && completedCycles % cyclesPerLongBreak == 0) longBreakMinutes else breakMinutes

/** A fresh work phase's target for [mode]. */
fun FocusRuntime.workTargetMs(mode: FocusMode = this.mode): Long? = when (mode) {
    FocusMode.STOPWATCH -> null
    FocusMode.COUNTDOWN -> countdownMinutes * 60_000L
    FocusMode.POMODORO -> workMinutes * 60_000L
}

/** A session running longer than this is flagged for review when it closes — the PDF's
 * "a forgotten End day is flagged for review". */
val LONG_SESSION: Duration = Duration.ofHours(8)

/** Wall clock and monotonic clock disagreeing by more than this means the time was changed. */
val CLOCK_TOLERANCE: Duration = Duration.ofMinutes(2)

/**
 * Where a running stretch that began at [startedAt] ends now, and whether that end is
 * uncertain. Within the same boot the length comes from the monotonic clock — so changing
 * the phone's time can't stretch or shrink work — and a wall clock that disagrees by more than
 * [CLOCK_TOLERANCE] marks it uncertain (the PDF: "if clock changes make the result uncertain,
 * show Needs review; do not invent elapsed work"). Without a same-boot anchor it's the wall
 * clock, also uncertain.
 */
fun stretchEnd(startedAt: Instant, anchor: Long?, anchorBoot: Int?, c: Clocks): Pair<Instant, Boolean> {
    if (anchor == null || anchorBoot == null || anchorBoot != c.boot) {
        return maxOf(startedAt, c.wall) to (anchor != null)
    }
    val end = startedAt.plusMillis((c.elapsed - anchor).coerceAtLeast(0))
    val drift = Duration.between(end, c.wall).abs()
    return end to (drift > CLOCK_TOLERANCE)
}

fun FocusRuntime.serializeLaps(): String = laps.joinToString(",")

fun parseLaps(raw: String?): List<Long> = raw?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

/**
 * Why a session can't take these times, or null when it can. [others] are the other sessions'
 * spans (an open one reaching to [now]). Overlap is refused: the same minute would count as
 * work twice.
 */
fun sessionEditProblem(
    start: Instant,
    end: Instant,
    others: List<Pair<Instant, Instant>>,
    now: Instant,
    what: String = "session",
): String? = when {
    !end.isAfter(start) -> L("It has to end after it starts.")
    end.isAfter(now.plusSeconds(60)) -> L("It can't end in the future.")
    others.any { (s, e) -> s.isBefore(end) && e.isAfter(start) } -> L("That overlaps another %1\$s — the time would count twice.", what)
    else -> null
}

/** One work session as history shows it. [title] is null for Focus with no task. */
data class SessionRecord(
    val id: Long,
    val entryId: Long?,
    val title: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val mode: SessionMode,
    val note: String?,
    val needsReview: Boolean,
) {
    val durationMs: Long? get() = endedAt?.let { Duration.between(startedAt, it).toMillis() }
}

/** A named timer setup. */
data class TimerPreset(val id: Long, val name: String, val mode: FocusMode, val minutes: Int, val breakMinutes: Int?)
