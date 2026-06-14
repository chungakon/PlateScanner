package com.platescanner.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database. Track 2 may add a TypeConverter for Instant / LocalDateTime
 * if richer temporal types are needed.
 */
@Database(
    entities = [PlateRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class PlateScannerDatabase : RoomDatabase() {
    abstract fun plateRecordDao(): PlateRecordDao

    companion object {
        const val DB_NAME = "plate_scanner.db"
    }
}
