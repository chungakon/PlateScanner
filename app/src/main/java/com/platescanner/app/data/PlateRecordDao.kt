package com.platescanner.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [PlateRecord]. Track 2 implements dedup logic
 * (e.g., last N chars of plate within a time window) at the repository
 * level — the DAO just exposes raw CRUD.
 */
@Dao
interface PlateRecordDao {

    @Query("SELECT * FROM plate_records ORDER BY captured_at DESC")
    fun observeAll(): Flow<List<PlateRecord>>

    @Query("SELECT * FROM plate_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PlateRecord?

    @Query("SELECT COUNT(*) FROM plate_records WHERE plate = :plate AND captured_at >= :since")
    suspend fun countRecent(plate: String, since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PlateRecord): Long

    @Query("DELETE FROM plate_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM plate_records")
    suspend fun clear(): Int
}
