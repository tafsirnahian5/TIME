package com.example.timeapp.data.repository

import com.example.timeapp.data.L

import android.os.SystemClock
import androidx.room.withTransaction
import com.example.timeapp.data.Clocks
import com.example.timeapp.data.EntryStatus
import com.example.timeapp.data.FocusMode
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.FocusRuntime
import com.example.timeapp.data.LONG_SESSION
import com.example.timeapp.data.SessionMode
import com.example.timeapp.data.breakAfter
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.WorkSessionEntity
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import com.example.timeapp.data.elapsedMs
import com.example.timeapp.data.finishAt
import com.example.timeapp.data.dueAt
import com.example.timeapp.data.stretchEnd
import com.example.timeapp.data.workTargetMs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

/** The real clocks. [bootCount] is set once at process start (`Settings.Global.BOOT_COUNT`),
 * so a session knows whether its monotonic anchor still means anything. */
object TimerClock {
    @Volatile var bootCount: Int? = null
    fun now(): Clocks = Clocks(Instant.now(), SystemClock.elapsedRealtime(), bootCount)
}

/** How a timed phase ended — what the "time's up" notification says. */
sealed class PhaseEnd {
    /** A countdown ran out: work stopped at that moment and waits for a choice. */
    data object CountdownDone : PhaseEnd()
    /** A Pomodoro work phase ended; the break is [breakMinutes] and may have started itself. */
    data class FocusDone(val breakMinutes: Int, val breakStarted: Boolean) : PhaseEnd()
    /** A break ended; with auto-start the next work phase is already running. */
    data class BreakDone(val workStarted: Boolean) : PhaseEnd()
}

/**
 * The one work timer. Every way work starts, pauses, switches or stops — a task's Start, the
 * Focus stopwatch, a countdown, a Pomodoro phase, a notification action, a countdown's own
 * alarm — goes through here, and each change writes the session rows and the runtime row in
 * one transaction. So there is never more than one open session, and Focus, the task's status
 * and the reports can't disagree about what's running (the PDF: "enforce a single active work
 * timer through a singleton runtime record and a database transaction").
 *
 * Task statuses change through [EntryRepository.update], so every transition is logged.
 */
class WorkTimer(
    private val db: TimeDatabase,
    private val entries: EntryRepository,
    private val clocks: () -> Clocks = TimerClock::now,
) {
    private val sessionDao get() = db.workSessionDao()
    private val focusDao get() = db.focusDao()

    fun observe(): Flow<FocusRuntime> = focusDao.observeRuntime().map { it?.toDomain() ?: FocusRuntime() }

    suspend fun runtime(): FocusRuntime = focusDao.runtime()?.toDomain() ?: FocusRuntime()

    private suspend fun save(r: FocusRuntime) = focusDao.saveRuntime(r.toEntity())

    /** The monotonic reading that corresponds to wall instant [at] (≤ now) in this boot. */
    private fun anchorFor(at: Instant, c: Clocks): Long? =
        if (c.boot == null) null else c.elapsed - Duration.between(at, c.wall).toMillis().coerceAtLeast(0)

    /** Closes the open session, if any — at [at] when given, else where its own clock says —
     * and flags it when that end is uncertain or implausibly long. Returns the end. */
    private suspend fun closeOpen(c: Clocks, at: Instant? = null): Instant? {
        val open = sessionDao.getActive() ?: return null
        val (end, uncertain) = at?.let { maxOf(it, open.startedAt) to false }
            ?: stretchEnd(open.startedAt, open.anchorElapsed, open.bootCount, c)
        // Opened and closed in the same instant (a double tap, a switch straight back): it
        // recorded no time, so it isn't kept as a zero-length row in the history.
        if (!end.isAfter(open.startedAt)) {
            sessionDao.delete(open.id)
            return maxOf(end, open.startedAt)
        }
        val tooLong = Duration.between(open.startedAt, end) > LONG_SESSION
        sessionDao.update(open.copy(endedAt = end, checkpointAt = end, needsReview = open.needsReview || uncertain || tooLong))
        return end
    }

    /**
     * A command reads the clock before it waits for the database, so under a race (a
     * notification button and a widget at the same instant) the one that read first can commit
     * second, and its times would land before the other's: overlapping work (BreakItTest found
     * it). Inside the transaction, time never runs behind what's already stored. Only small lags
     * are raced reads; a bigger one is the phone's clock moving, which the monotonic maths and
     * Needs review already handle, so it's left alone.
     */
    private suspend fun ordered(c: Clocks): Clocks {
        val last = sessionDao.latestEdge() ?: return c
        val lag = last.toEpochMilli() - c.wall.toEpochMilli()
        return if (lag <= 0 || lag > RACE_WINDOW_MS) c else Clocks(last, c.elapsed + lag, c.boot)
    }

    private suspend fun open(entryId: Long?, mode: SessionMode, at: Instant, c: Clocks) {
        sessionDao.insert(
            WorkSessionEntity(
                entryId = entryId, startedAt = at, mode = mode,
                anchorElapsed = anchorFor(at, c), bootCount = c.boot, checkpointAt = at,
            ),
        )
    }

    /** Sets a task's status (logged). A timer never takes a finished task back out of Done. */
    private suspend fun setStatus(entryId: Long?, status: EntryStatus, at: Instant, note: String? = null, related: Long? = null) {
        val row = entryId?.let { db.entryDao().getById(it) } ?: return
        if (row.status == status || row.status == EntryStatus.DONE) return
        val task = row.toDomain()
        val resumeNote = when (status) {
            // Back at work: the "where I left off" note has done its job (the event keeps it).
            EntryStatus.DOING -> null
            EntryStatus.PAUSED -> note ?: task.resumeNote
            else -> task.resumeNote
        }
        entries.update(task.copy(status = status, resumeNote = resumeNote), note = note, relatedEntryId = related, at = at)
    }

    private fun FocusMode.sessionMode(): SessionMode = when (this) {
        FocusMode.STOPWATCH -> SessionMode.STOPWATCH
        FocusMode.COUNTDOWN -> SessionMode.COUNTDOWN
        FocusMode.POMODORO -> SessionMode.POMODORO
    }

    /** The timer with nothing started, keeping the mode and the user's lengths. */
    private fun FocusRuntime.idle() = FocusRuntime(
        mode = mode, countdownMinutes = countdownMinutes, workMinutes = workMinutes, breakMinutes = breakMinutes,
        longBreakMinutes = longBreakMinutes, cyclesPerLongBreak = cyclesPerLongBreak, autoStart = autoStart,
    )

    /** A new work phase for [mode]; a Pomodoro keeps counting its cycles across breaks. */
    private fun FocusRuntime.freshWork(mode: FocusMode, keepCycles: Boolean) = copy(
        mode = mode, phase = FocusPhase.WORK, running = false, targetMs = workTargetMs(mode), accumulatedMs = 0,
        stretchStartedAt = null, stretchAnchor = null, laps = emptyList(), awaitingChoice = false,
        cycles = if (keepCycles) cycles else 0,
    )

    private fun FocusRuntime.runningFrom(at: Instant, c: Clocks) =
        copy(running = true, stretchStartedAt = at, stretchAnchor = anchorFor(at, c), bootCount = c.boot, awaitingChoice = false)

    /**
     * Starts work on [entryId] (null: Focus with no task). Whatever else runs is closed at the
     * same instant the new session opens, so segments meet exactly; the task left is paused
     * with [resumeNote] and names the task switched to. Starting what already runs does
     * nothing (replay protection). A work phase in progress carries on with the new task —
     * switching doesn't restart a countdown or a Pomodoro; otherwise a new run starts in [mode].
     */
    @Suppress("NAME_SHADOWING")
    suspend fun start(entryId: Long?, mode: FocusMode = FocusMode.STOPWATCH, resumeNote: String? = null, c: Clocks = clocks()) =
        db.withTransaction {
            val c = ordered(c)
            val rt = runtime()
            val open = sessionDao.getActive()
            if (rt.running && rt.phase == FocusPhase.WORK && open != null && open.entryId == entryId) return@withTransaction
            val at = closeOpen(c) ?: c.wall
            val previous = open?.entryId ?: rt.entryId.takeIf { rt.phase == FocusPhase.WORK }
            if (previous != null && previous != entryId) setStatus(previous, EntryStatus.PAUSED, at, note = resumeNote, related = entryId)
            val inRun = rt.phase == FocusPhase.WORK && !rt.awaitingChoice
            val base = when {
                inRun -> rt.copy(accumulatedMs = rt.elapsedMs(c).coerceAtMost(rt.targetMs ?: Long.MAX_VALUE))
                rt.phase == FocusPhase.BREAK -> rt.freshWork(rt.mode, keepCycles = true)
                else -> rt.freshWork(mode, keepCycles = false)
            }
            open(entryId, base.mode.sessionMode(), at, c)
            setStatus(entryId, EntryStatus.DOING, at)
            save(base.copy(phase = FocusPhase.WORK, entryId = entryId).runningFrom(at, c))
        }

    /** Pauses whatever runs: work closes its session and pauses the task (with [resumeNote]);
     * a break just stops counting down. Phase and what's left of it are kept. */
    @Suppress("NAME_SHADOWING")
    suspend fun pause(resumeNote: String? = null, c: Clocks = clocks()) = db.withTransaction {
        val c = ordered(c)
        val rt = runtime()
        val open = sessionDao.getActive()
        if (!rt.running && open == null) return@withTransaction
        val at = closeOpen(c) ?: c.wall
        (open?.entryId ?: rt.entryId.takeIf { rt.phase == FocusPhase.WORK })?.let { setStatus(it, EntryStatus.PAUSED, at, note = resumeNote) }
        if (rt.running) save(rt.copy(running = false, accumulatedMs = rt.elapsedMs(c), stretchStartedAt = null, stretchAnchor = null))
    }

    /** Resumes a paused phase where it stopped. */
    @Suppress("NAME_SHADOWING")
    suspend fun resume(c: Clocks = clocks()) = db.withTransaction {
        val c = ordered(c)
        val rt = runtime()
        if (rt.running || rt.phase == FocusPhase.IDLE || rt.awaitingChoice) return@withTransaction
        if (rt.phase == FocusPhase.WORK) {
            closeOpen(c)
            open(rt.entryId, rt.mode.sessionMode(), c.wall, c)
            setStatus(rt.entryId, EntryStatus.DOING, c.wall)
        }
        save(rt.runningFrom(c.wall, c))
    }

    /**
     * Ends the run. The task is paused — finishing a session isn't finishing the task — unless
     * [completeTask], the PDF's "Stop and complete" in one step.
     */
    @Suppress("NAME_SHADOWING")
    suspend fun finish(completeTask: Boolean = false, c: Clocks = clocks()) = db.withTransaction {
        val c = ordered(c)
        val rt = runtime()
        val open = sessionDao.getActive()
        val at = closeOpen(c) ?: c.wall
        val task = open?.entryId ?: rt.entryId
        if (task != null) {
            if (completeTask) {
                db.entryDao().getById(task)?.let { entries.update(it.toDomain().copy(status = EntryStatus.DONE), at = at) }
            } else if (open != null || rt.phase == FocusPhase.WORK) {
                setStatus(task, EntryStatus.PAUSED, at)
            }
        }
        save(rt.idle())
    }

    /**
     * Stops the timer if it's running on [entryId], without touching the task's status — for
     * changes that move a task out of Doing themselves (a Board move, archive, completion).
     * Must be called inside the caller's transaction.
     */
    @Suppress("NAME_SHADOWING")
    internal suspend fun stopIfOn(entryId: Long, c: Clocks = clocks()): Instant? {
        val c = ordered(c)
        val rt = runtime()
        val open = sessionDao.getActiveForEntry(entryId)
        if (open == null && rt.entryId != entryId) return null
        val at = if (open != null) closeOpen(c) else null
        if (rt.entryId == entryId) save(rt.idle())
        return at
    }

    /** A lap mark at the stopwatch's current reading. */
    suspend fun lap(c: Clocks = clocks()) = db.withTransaction {
        val rt = runtime()
        if (rt.phase != FocusPhase.WORK) return@withTransaction
        save(rt.copy(laps = rt.laps + rt.elapsedMs(c)))
    }

    suspend fun clearLaps() = db.withTransaction { save(runtime().copy(laps = emptyList())) }

    /**
     * A countdown or Pomodoro phase ran out; [finishAt] is when it was due. Work stops at that
     * moment — not at delivery, which can be late — and the task is paused, never marked Done
     * (the PDF: "countdown expiry asks whether to continue, take a break or finish"). A
     * stale call — the phase was paused, extended or restarted since — does nothing.
     */
    suspend fun onPhaseExpired(finishAt: Instant, c: Clocks = clocks()): PhaseEnd? = db.withTransaction {
        val rt = runtime()
        if (!rt.running || rt.targetMs == null) return@withTransaction null
        val due = rt.dueAt(c) ?: return@withTransaction null
        if (due.isAfter(c.wall.plusSeconds(EXPIRY_SLACK_SECONDS)) || due.isAfter(finishAt.plusSeconds(EXPIRY_SLACK_SECONDS))) return@withTransaction null
        val end = minOf(due, c.wall)
        when (rt.phase) {
            FocusPhase.WORK -> {
                closeOpen(c, at = end)
                setStatus(rt.entryId, EntryStatus.PAUSED, end)
                if (rt.mode == FocusMode.POMODORO) {
                    val cycles = rt.cycles + 1
                    val breakMinutes = rt.breakAfter(cycles)
                    val next = rt.copy(
                        phase = FocusPhase.BREAK, cycles = cycles, targetMs = breakMinutes * 60_000L, accumulatedMs = 0,
                        running = false, stretchStartedAt = null, stretchAnchor = null,
                    )
                    save(if (rt.autoStart) next.runningFrom(end, c) else next)
                    PhaseEnd.FocusDone(breakMinutes, breakStarted = rt.autoStart)
                } else {
                    save(rt.copy(running = false, accumulatedMs = rt.targetMs, stretchStartedAt = null, stretchAnchor = null, awaitingChoice = true))
                    PhaseEnd.CountdownDone
                }
            }
            FocusPhase.BREAK -> {
                val work = rt.freshWork(rt.mode, keepCycles = true).copy(entryId = rt.entryId)
                if (rt.mode == FocusMode.POMODORO && rt.autoStart) {
                    open(rt.entryId, SessionMode.POMODORO, end, c)
                    setStatus(rt.entryId, EntryStatus.DOING, end)
                    save(work.runningFrom(end, c))
                    PhaseEnd.BreakDone(workStarted = true)
                } else {
                    save(work)
                    PhaseEnd.BreakDone(workStarted = false)
                }
            }
            FocusPhase.IDLE -> null
        }
    }

    /** "+N min": lengthens the current countdown or phase; after a countdown ran out it also
     * gets back to work (the PDF's "add time"). */
    suspend fun extend(extraMs: Long, c: Clocks = clocks()) = db.withTransaction {
        val rt = runtime()
        val target = rt.targetMs ?: return@withTransaction
        save(rt.copy(targetMs = target + extraMs, awaitingChoice = false))
        if (rt.awaitingChoice) resume(c)
    }

    /** After a countdown ran out: another one of the same length, straight away. */
    suspend fun continueRun(c: Clocks = clocks()) = db.withTransaction {
        val rt = runtime()
        if (rt.phase != FocusPhase.WORK) return@withTransaction
        save(rt.copy(accumulatedMs = 0, targetMs = rt.workTargetMs(), awaitingChoice = false, laps = emptyList()))
        resume(c)
    }

    /** Stops work and starts a break of [minutes] (default: the break length) — never work time. */
    suspend fun takeBreak(minutes: Int? = null, c: Clocks = clocks()) = db.withTransaction {
        val rt = runtime()
        val open = sessionDao.getActive()
        val at = closeOpen(c) ?: c.wall
        (open?.entryId ?: rt.entryId)?.let { setStatus(it, EntryStatus.PAUSED, at) }
        val length = (minutes ?: rt.breakMinutes).coerceAtLeast(1) * 60_000L
        save(
            rt.copy(phase = FocusPhase.BREAK, targetMs = length, accumulatedMs = 0, awaitingChoice = false, laps = emptyList())
                .runningFrom(at, c),
        )
    }

    /** Pomodoro "skip": ends this phase now and moves to the next, running if this one was. */
    suspend fun skipPhase(c: Clocks = clocks()) = db.withTransaction {
        val rt = runtime()
        when (rt.phase) {
            FocusPhase.WORK -> {
                val wasRunning = rt.running
                val at = closeOpen(c) ?: c.wall
                rt.entryId?.let { setStatus(it, EntryStatus.PAUSED, at) }
                val cycles = rt.cycles + 1
                val next = rt.copy(
                    phase = FocusPhase.BREAK, cycles = cycles, targetMs = rt.breakAfter(cycles) * 60_000L, accumulatedMs = 0,
                    running = false, stretchStartedAt = null, stretchAnchor = null, awaitingChoice = false,
                )
                save(if (wasRunning) next.runningFrom(at, c) else next)
            }
            FocusPhase.BREAK -> {
                val work = rt.freshWork(rt.mode, keepCycles = true).copy(entryId = rt.entryId)
                if (rt.running) {
                    open(rt.entryId, rt.mode.sessionMode(), c.wall, c)
                    setStatus(rt.entryId, EntryStatus.DOING, c.wall)
                    save(work.runningFrom(c.wall, c))
                } else {
                    save(work)
                }
            }
            FocusPhase.IDLE -> Unit
        }
    }

    /** Picks the mode for the next run; ignored while a run is in progress. */
    suspend fun setMode(mode: FocusMode) = db.withTransaction {
        val rt = runtime()
        if (rt.phase == FocusPhase.IDLE) save(rt.copy(mode = mode))
    }

    /** The user's lengths. An idle or unstarted phase picks them up at once; a started one
     * keeps its length until it ends. */
    suspend fun setLengths(
        countdownMinutes: Int? = null,
        workMinutes: Int? = null,
        breakMinutes: Int? = null,
        autoStart: Boolean? = null,
        longBreakMinutes: Int? = null,
    ) = db.withTransaction {
        val rt = runtime()
        var next = rt.copy(
            countdownMinutes = countdownMinutes?.coerceIn(1, MAX_MINUTES) ?: rt.countdownMinutes,
            workMinutes = workMinutes?.coerceIn(1, MAX_MINUTES) ?: rt.workMinutes,
            breakMinutes = breakMinutes?.coerceIn(1, MAX_MINUTES) ?: rt.breakMinutes,
            autoStart = autoStart ?: rt.autoStart,
            longBreakMinutes = longBreakMinutes?.coerceIn(1, MAX_MINUTES) ?: rt.longBreakMinutes,
        )
        val unstarted = rt.phase == FocusPhase.WORK && !rt.running && rt.accumulatedMs == 0L && !rt.awaitingChoice
        if (unstarted) next = next.copy(targetMs = next.workTargetMs())
        save(next)
    }

    /** Records "still running at this moment" on the open session — the point a session cut off
     * by a reboot is closed at, instead of guessing how long it went on. */
    suspend fun checkpoint(c: Clocks = clocks()) {
        val open = sessionDao.getActive() ?: return
        val (at, _) = stretchEnd(open.startedAt, open.anchorElapsed, open.bootCount, c)
        sessionDao.update(open.copy(checkpointAt = at))
    }

    /**
     * After a reboot the old monotonic anchors mean nothing, and the phone may have been off for
     * hours. A session left open is closed at its last checkpoint and flagged for review, its
     * task paused, and the timer paused — "default work sessions to Interrupted, retaining the
     * last trusted checkpoint and asking the user to confirm any uncertain gap" (the PDF).
     * Returns whether anything was recovered.
     */
    suspend fun recoverAfterReboot(c: Clocks = clocks()): Boolean = db.withTransaction {
        if (c.boot == null) return@withTransaction false
        val rt = runtime()
        val open = sessionDao.getActive()
        val staleSession = open != null && open.bootCount != null && open.bootCount != c.boot
        val staleRuntime = rt.running && rt.bootCount != null && rt.bootCount != c.boot
        if (!staleSession && !staleRuntime) return@withTransaction false
        var countedMs = 0L
        if (open != null && staleSession) {
            val end = maxOf(open.checkpointAt ?: open.startedAt, open.startedAt)
            sessionDao.update(open.copy(endedAt = end, needsReview = true))
            setStatus(open.entryId, EntryStatus.PAUSED, end)
            countedMs = Duration.between(open.startedAt, end).toMillis()
        }
        if (rt.running) {
            save(rt.copy(running = false, accumulatedMs = rt.accumulatedMs + countedMs, stretchStartedAt = null, stretchAnchor = null))
        }
        true
    }

    // ---- History: the PDF's "session notes and editable history" ----

    private fun records(source: Flow<List<WorkSessionEntity>>): Flow<List<com.example.timeapp.data.SessionRecord>> =
        kotlinx.coroutines.flow.combine(source, db.entryDao().observeAll()) { sessions, entries ->
            val titles = entries.associate { it.id to it.title }
            sessions.map {
                com.example.timeapp.data.SessionRecord(
                    it.id, it.entryId, it.entryId?.let { id -> titles[id] ?: L("(deleted task)") },
                    it.startedAt, it.endedAt, it.mode, it.note, it.needsReview,
                )
            }
        }

    /** Sessions started since [from] (and any still open), newest first. */
    fun observeSince(from: Instant) = records(sessionDao.observeSince(from))

    fun observeForEntry(entryId: Long) = records(sessionDao.observeForEntry(entryId))

    fun observeAnyEver() = sessionDao.observeAny()

    fun observeNeedingReview() = records(sessionDao.observeNeedingReview())

    fun observeCorrections(sessionId: Long) = focusDao.observeCorrections(sessionId)

    /**
     * Edits a closed session's times and note. Returns why not (see `sessionEditProblem`), or
     * null once saved. Changed times keep the originals and [reason] in `session_corrections`
     * (the PDF: "record manual corrections with their reason and original values"); saving
     * also clears Needs review — the user has looked at it. A running session's note can be
     * edited, its times can't.
     */
    suspend fun editSession(id: Long, start: Instant, end: Instant?, note: String?, reason: String, c: Clocks = clocks()): String? =
        db.withTransaction {
            val row = sessionDao.getById(id) ?: return@withTransaction L("That session no longer exists.")
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
            if (row.endedAt == null || end == null) {
                sessionDao.update(row.copy(note = cleanNote))
                return@withTransaction null
            }
            val timesChanged = start != row.startedAt || end != row.endedAt
            if (timesChanged) {
                val others = sessionDao.sessionsInRange(start.minusSeconds(1), end.plusSeconds(1)).first()
                    .filter { it.id != id }.map { it.startedAt to it.endedAt!! } +
                    listOfNotNull(sessionDao.getActive()?.takeIf { it.id != id }?.let { it.startedAt to c.wall })
                com.example.timeapp.data.sessionEditProblem(start, end, others, c.wall)?.let { return@withTransaction it }
                if (reason.isBlank()) return@withTransaction L("Say why the times changed — it's kept with the original.")
                focusDao.insertCorrection(
                    com.example.timeapp.data.db.SessionCorrectionEntity(
                        sessionId = id, at = c.wall, oldStartedAt = row.startedAt, oldEndedAt = row.endedAt,
                        newStartedAt = start, newEndedAt = end, reason = reason.trim(),
                    ),
                )
            }
            sessionDao.update(row.copy(startedAt = start, endedAt = end, note = cleanNote, needsReview = false, anchorElapsed = null, bootCount = null))
            null
        }

    /** "+5 min" on a finished session — the PDF's "add time" — as a recorded correction. */
    suspend fun addTime(id: Long, minutes: Int = 5): String? {
        val row = sessionDao.getById(id) ?: return L("That session no longer exists.")
        val end = row.endedAt ?: return L("It's still running.")
        return editSession(id, row.startedAt, end.plusSeconds(minutes * 60L), row.note, L("Added %1\$s min", minutes))
    }

    /** "Looks right": the user has checked a flagged session. */
    suspend fun confirmSession(id: Long) {
        sessionDao.getById(id)?.let { sessionDao.update(it.copy(needsReview = false)) }
    }

    /** Deletes a finished session (a running one is stopped with Finish, not deleted). */
    suspend fun deleteSession(id: Long) = db.withTransaction {
        val row = sessionDao.getById(id) ?: return@withTransaction
        if (row.endedAt != null) sessionDao.delete(id)
    }

    fun observePresets(): Flow<List<com.example.timeapp.data.TimerPreset>> = focusDao.observePresets().map { list ->
        list.map { com.example.timeapp.data.TimerPreset(it.id, it.name, it.mode, it.minutes, it.breakMinutes) }
    }

    suspend fun savePreset(name: String, mode: FocusMode, minutes: Int, breakMinutes: Int? = null) {
        focusDao.insertPreset(
            com.example.timeapp.data.db.TimerPresetEntity(name = name.trim(), mode = mode, minutes = minutes.coerceIn(1, MAX_MINUTES), breakMinutes = breakMinutes),
        )
    }

    suspend fun deletePreset(id: Long) = focusDao.deletePreset(id)

    /** Takes a preset's lengths for the next run (a started phase keeps its own). */
    suspend fun applyPreset(preset: com.example.timeapp.data.TimerPreset) = when (preset.mode) {
        FocusMode.COUNTDOWN -> setLengths(countdownMinutes = preset.minutes)
        FocusMode.POMODORO -> setLengths(workMinutes = preset.minutes, breakMinutes = preset.breakMinutes)
        FocusMode.STOPWATCH -> Unit
    }

    private companion object {
        /** An expiry alarm this early is a stale one; the phase's real alarm is still to come. */
        const val EXPIRY_SLACK_SECONDS = 5L
        const val MAX_MINUTES = 24 * 60
    }
}

/** How far behind the stored timeline a command's clock may be and still count as a race. */
private const val RACE_WINDOW_MS = 5_000L
