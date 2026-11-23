package com.aurawallet.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    /** Новіші зверху; id як другий ключ, якщо час однаковий. */
    @Query("SELECT * FROM history ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    fun observeOne(id: Long): Flow<HistoryEntity?>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: Long): HistoryEntity?

    @Query("SELECT * FROM history WHERE confirmed = 1 AND status = 'DONE' ORDER BY created_at DESC, id DESC")
    fun observeConfirmed(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Update
    suspend fun update(entity: HistoryEntity)

    /** Атомарно переводить запис зі статусу [from] у [to]; повертає кількість змінених рядків (0 — хтось уже змінив). */
    @Query("UPDATE history SET status = :to WHERE id = :id AND status = :from")
    suspend fun transition(id: Long, from: String, to: String): Int

    /** Після примусового закриття застосунку «надсилається» повертається в «очікує». */
    @Query("UPDATE history SET status = 'PENDING' WHERE status = 'SENDING'")
    suspend fun resetStuckSending(): Int

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}
