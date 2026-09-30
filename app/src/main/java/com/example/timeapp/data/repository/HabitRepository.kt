package com.example.timeapp.data.repository

import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitCheck
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.WorkCategory
import com.example.timeapp.data.db.HabitCheckEntity
import com.example.timeapp.data.db.TimeDatabase
import com.example.timeapp.data.db.toDomain
import com.example.timeapp.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HabitRepository(private val db: TimeDatabase) {
    private val dao get() = db.habitDao()

    /** Habits with their category resolved at read time — a rename or recolour shows up on
     * every habit at once, as it does for tasks. */
    fun observeHabits(): Flow<List<Habit>> =
        combine(dao.observeAll(), db.categoryDao().observeAll()) { habits, categories ->
            val byId = categories.associateBy { it.id }
            habits.map { entity ->
                val domain = entity.toDomain()
                val resolved = entity.categoryId?.let { byId[it] }
                if (resolved == null) domain else domain.copy(category = WorkCategory(resolved.name, resolved.colorArgb, resolved.icon))
            }
        }

    fun observeChecks(): Flow<List<HabitCheck>> = dao.observeAllChecks().map { list -> list.map { it.toDomain() } }

    suspend fun getHabit(id: Long): Habit? = dao.getById(id)?.toDomain()

    /** Running timed-habit timers: habit id → when it rings. */
    fun observeHabitTimers(): Flow<Map<Long, java.time.Instant>> = db.utilityTimerDao().observeAll().map { list ->
        list.mapNotNull { t -> val h = t.habitId; val e = t.endsAt; if (h != null && e != null) h to e else null }.toMap()
    }

    /**
     * Starts a timed habit's timer (Phase 12): a utility timer, so it never counts as work time,
     * linked to the habit so ringing checks the day off. One timer per habit, reused. Returns
     * the timer's id and when it rings.
     */
    suspend fun startTimer(habit: Habit, now: java.time.Instant = java.time.Instant.now()): Pair<Long, java.time.Instant>? {
        val minutes = habit.timerMinutes ?: return null
        val ends = now.plusSeconds(minutes * 60L)
        val tdao = db.utilityTimerDao()
        val existing = tdao.forHabit(habit.id)
        val row = (existing ?: com.example.timeapp.data.db.UtilityTimerEntity(label = habit.name, durationMs = minutes * 60_000L, remainingMs = minutes * 60_000L, habitId = habit.id))
            .copy(label = habit.name, durationMs = minutes * 60_000L, endsAt = ends, remainingMs = minutes * 60_000L)
        val id = if (existing != null) { tdao.update(row); row.id } else tdao.insert(row)
        return id to ends
    }

    suspend fun marksFor(habitId: Long): Map<LocalDate, HabitMark> =
        dao.checksFor(habitId).associate { it.date to it.mark }

    suspend fun add(habit: Habit): Long = dao.insert(habit.toEntity().copy(id = 0, sortOrder = dao.nextSortOrder()))

    suspend fun update(habit: Habit) = dao.update(habit.toEntity())

    /** Writes a new order: [idsInOrder] top to bottom (manual reorder). */
    suspend fun setOrder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { i, id -> dao.getById(id)?.let { dao.update(it.copy(sortOrder = i)) } }
    }

    /** Deletes the habit and, by foreign-key cascade, every day it was marked. */
    suspend fun delete(id: Long) = dao.delete(id)

    /** Sets or clears one day's mark. Null removes the row — an unmarked day has none. */
    suspend fun setMark(habitId: Long, date: LocalDate, mark: HabitMark?) {
        if (mark == null) {
            dao.deleteCheck(habitId, date)
        } else {
            // Changing the mark keeps the day's amount and note.
            val existing = dao.checkOn(habitId, date)
            dao.upsertCheck(HabitCheckEntity(habitId, date, mark, existing?.value, existing?.note))
        }
    }

    /**
     * The day editor's save: an amount (measurable habits — the mark follows the target), a
     * rest day, or a plain done; plus a note. With neither a mark nor an amount the day is
     * cleared, note included.
     */
    suspend fun setDay(habitId: Long, date: LocalDate, mark: HabitMark?, value: Double?, note: String?) {
        val habit = dao.getById(habitId)?.toDomain() ?: return
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        val finalMark = when {
            mark == HabitMark.SKIPPED -> HabitMark.SKIPPED
            value != null && habit.target != null -> habit.target.markFor(value)
            else -> mark
        }
        if (finalMark == null) dao.deleteCheck(habitId, date)
        else dao.upsertCheck(HabitCheckEntity(habitId, date, finalMark, value.takeIf { finalMark != HabitMark.SKIPPED }, cleanNote))
    }
}
