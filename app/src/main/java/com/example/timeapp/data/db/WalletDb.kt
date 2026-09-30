package com.example.timeapp.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.example.timeapp.data.Wallet
import com.example.timeapp.data.WalletKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: WalletKind,
    val openingBalanceMinor: Long,
    val openingDate: LocalDate,
    val archivedAt: java.time.Instant? = null,
)

fun WalletEntity.toDomain(): Wallet = Wallet(id, name, kind, openingBalanceMinor, openingDate, archivedAt)

fun Wallet.toEntity(): WalletEntity = WalletEntity(id, name, kind, openingBalanceMinor, openingDate, archivedAt)

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY id")
    fun observeAll(): Flow<List<WalletEntity>>

    @Insert
    suspend fun insert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int
}
