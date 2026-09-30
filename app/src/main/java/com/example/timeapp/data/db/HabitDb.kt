package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.timeapp.data.Habit
import com.example.timeapp.data.HabitCheck
import com.example.timeapp.data.HabitMark
import com.example.timeapp.data.HabitSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/** One habit. The schedule is stored in its compact text form (see [HabitSchedule.serialize])
 * and the reminder time as minutes after midnight, so neither needs a type converter. */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val schedule: String,
    val startedOn: LocalDate,
    val categoryId: Long? = null,
    val remindMinuteOfDay: Int? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** A measurable habit's target (see [com.example.timeapp.data.HabitTarget]). */
    val targetAmount: Double? = null,
    val targetUnit: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val targetAtMost: Boolean = false,
    val timerMinutes: Int? = null,
)

/** One marked day. The composite key is the "at most one mark per habit per day" rule;
 * an unmarked day has no row at all. Deleting a habit deletes its history with it. */
@Entity(
    tableName = "habit_checks",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [
        ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class HabitCheckEntity(
    val habitId: Long,
    val date: LocalDate,
    val mark: HabitMark,
    /** A measurable habit's recorded amount. */
    val value: Double? = null,
    /** A short note on the day (the "day notes" of Loop and friends). */
    val note: String? = null,
)

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    schedule = HabitSchedule.parse(schedule),
    startedOn = startedOn,
    categoryId = categoryId,
    remindAt = remindMinuteOfDay?.let { LocalTime.of(it / 60, it % 60) },
    archived = archived,
    sortOrder = sortOrder,
    target = targetAmount?.let { com.example.timeapp.data.HabitTarget(it, targetUnit.orEmpty(), targetAtMost) },
    timerMinutes = timerMinutes,
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    schedule = schedule.serialize(),
    startedOn = startedOn,
    categoryId = categoryId,
    remindMinuteOfDay = remindAt?.let { it.hour * 60 + it.minute },
    archived = archived,
    sortOrder = sortOrder,
    targetAmount = target?.amount,
    targetUnit = target?.unit,
    targetAtMost = target?.atMost ?: false,
    timerMinutes = timerMinutes,
)

fun HabitCheckEntity.toDomain(): HabitCheck = HabitCheck(habitId, date, mark, value, note)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits")
    suspend fun nextSortOrder(): Int

    /** Every mark of every habit. Small by nature — one short row per habit per marked day. */
    @Query("SELECT * FROM habit_checks")
    fun observeAllChecks(): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId")
    suspend fun checksFor(habitId: Long): List<HabitCheckEntity>

    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId AND date = :date")
    suspend fun checkOn(habitId: Long, date: LocalDate): HabitCheckEntity?

    @Upsert
    suspend fun upsertCheck(check: HabitCheckEntity)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCheck(habitId: Long, date: LocalDate)
}
