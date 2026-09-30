package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Loan
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** A loan's who and when; its money is in `money_records` rows with `loanId` (see `Loans.kt`). */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val lent: Boolean,
    val startedOn: LocalDate,
    val dueOn: LocalDate? = null,
    val note: String? = null,
    val closedAt: Instant? = null,
)

fun LoanEntity.toDomain() = Loan(id, person, lent, startedOn, dueOn, note, closedAt)
fun Loan.toEntity() = LoanEntity(id, person, lent, startedOn, dueOn, note, closedAt)

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY closedAt IS NOT NULL, startedOn DESC, id DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: Long): LoanEntity?

    @Insert
    suspend fun insert(loan: LoanEntity): Long

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun delete(id: Long)
}
