package com.example.timeapp.data.repository

import com.example.timeapp.data.L

import androidx.room.withTransaction
import com.example.timeapp.data.FocusPhase
import com.example.timeapp.data.WorkdaySegment
import com.example.timeapp.data.WorkdaySegmentKind
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.WorkdaySegmentEntity
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.sessionEditProblem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * One calendar day's Start-day / break / End-day timeline — the PDF's workday: "Take break
 * closes [the on-duty interval] and pauses active work. Resume day opens the next on-duty
 * interval and asks whether to resume the previous task. Ending closes the day and pauses or
 * finishes the active session with confirmation." The work timer is the same one Focus and
 * tasks use, so a break really stops work time.
 */
class WorkdayRepository(private val db: TimeDatabase) {
    private val dao get() = db.workdaySegmentDao()
    private val timer get() = EntryRepository(db).timer

    fun observeForDate(date: LocalDate): Flow<List<WorkdaySegment>> =
        dao.observeForDate(date).map { list -> list.map { it.toDomain() } }

    /** A day started before [date] and never ended. */
    fun observeForgottenBefore(date: LocalDate): Flow<WorkdaySegment?> = dao.observeOpenBefore(date).map { it?.toDomain() }

    suspend fun startDay(date: LocalDate, now: Instant = Instant.now()) = openExclusive(date, WorkdaySegmentKind.ON_DUTY, now)

    /** A break pauses whatever work runs, at the same moment. */
    suspend fun takeBreak(date: LocalDate, now: Instant = Instant.now()) = db.withTransaction {
        timer.pause()
        openExclusive(date, WorkdaySegmentKind.BREAK, now)
    }

    /**
     * The task a break interrupted: the session that ended as the current break began —
     * what Resume day offers to pick up again. Null when no work was running then.
     */
    suspend fun taskInterruptedByBreak(date: LocalDate): Long? {
        val brk = dao.getActiveForDate(date)?.takeIf { it.kind == WorkdaySegmentKind.BREAK } ?: return null
        val last = db.workSessionDao().lastEndedBy(brk.startedAt.plusSeconds(2)) ?: return null
        val gap = Duration.between(last.endedAt!!, brk.startedAt).abs()
        return last.entryId?.takeIf { gap <= Duration.ofSeconds(5) }
    }

    /** Back on duty; with [resumeTaskId], that task's work starts again at the same moment. */
    suspend fun resumeDay(date: LocalDate, resumeTaskId: Long? = null, now: Instant = Instant.now()) = db.withTransaction {
        openExclusive(date, WorkdaySegmentKind.ON_DUTY, now)
        if (resumeTaskId != null) timer.start(resumeTaskId)
    }

    /** Whether ending the day needs to ask about running work. */
    suspend fun workIsRunning(): Boolean = timer.runtime().let { it.running && it.phase == FocusPhase.WORK }

    /** Ends the day. Running work is paused, or finished when [finishWork] — never left running
     * unseen after the day closes. */
    suspend fun endDay(date: LocalDate, finishWork: Boolean = false, now: Instant = Instant.now()) = db.withTransaction {
        if (workIsRunning()) { if (finishWork) timer.finish() else timer.pause() }
        dao.getActiveForDate(date)?.let { dao.update(it.copy(endedAt = now)) }
    }

    /** Closes a forgotten day at [at] (the user's answer to "when did it really end?"). */
    suspend fun closeForgotten(id: Long, at: Instant) = db.withTransaction {
        val seg = dao.getById(id) ?: return@withTransaction
        if (seg.endedAt == null) dao.update(seg.copy(endedAt = maxOf(at, seg.startedAt), correctionReason = seg.correctionReason ?: L("Day ended late")))
    }

    /**
     * Corrects a timeline interval. Refuses an end before the start, in the future, or
     * overlapping another interval of the day; the first correction keeps the originals, and
     * each keeps its reason. Returns the problem, or null when saved.
     */
    suspend fun correct(id: Long, start: Instant, end: Instant?, reason: String, now: Instant = Instant.now()): String? = db.withTransaction {
        val seg = dao.getById(id) ?: return@withTransaction L("That entry no longer exists.")
        if (reason.isBlank()) return@withTransaction L("Say why — it's kept with the original times.")
        val others = dao.forDate(seg.date).filter { it.id != id }.map { it.startedAt to (it.endedAt ?: now) }
        sessionEditProblem(start, end ?: now, others, now, what = L("part of the day"))?.let { return@withTransaction it }
        dao.update(
            seg.copy(
                startedAt = start,
                endedAt = end,
                originalStartedAt = seg.originalStartedAt ?: seg.startedAt,
                originalEndedAt = if (seg.originalStartedAt == null) seg.endedAt else seg.originalEndedAt,
                correctionReason = reason.trim(),
            ),
        )
        null
    }

    private suspend fun openExclusive(date: LocalDate, kind: WorkdaySegmentKind, now: Instant) {
        db.withTransaction {
            dao.getActiveForDate(date)?.let { dao.update(it.copy(endedAt = now)) }
            dao.insert(WorkdaySegmentEntity(date = date, kind = kind, startedAt = now))
        }
    }
}
