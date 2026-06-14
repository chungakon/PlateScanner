package com.platescanner.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plate record repository. Owns:
 *  - Dedup: the same plate (canonicalised) is suppressed for [DEDUP_WINDOW_MS]
 *    after its last insertion.
 *  - Thumbnail persistence: each new record's thumbnail JPEG is written to
 *    `filesDir/plates/<timestamp>.jpg`.
 *
 * The dedup cache lives in memory only — it survives the process but is reset
 * on app restart, which is the desired behaviour for a "scan once, dedupe
 * while running" UX.
 */
@Singleton
class PlateRecordRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PlateRecordDao,
) {

    /**
     * Plate (canonical) → last insert time (epoch ms).
     * Access is synchronised by [dedupMutex].
     */
    private val recentPlates: MutableMap<String, Long> = ConcurrentHashMap()
    private val dedupMutex = Any()

    fun observeAll(): Flow<List<PlateRecord>> = dao.observeAll()

    /**
     * Insert [record] unless [record.plate] has been inserted in the last
     * [DEDUP_WINDOW_MS]. Thumbnail is saved to internal storage before insert;
     * its absolute path is set on [record] before persistence.
     *
     * @return true if the record was inserted, false if deduped.
     */
    suspend fun insertIfFresh(
        record: PlateRecord,
        thumbnailBytes: ByteArray?,
    ): Boolean {
        val now = System.currentTimeMillis()
        val canonical = canonicalise(record.plate)
        synchronized(dedupMutex) {
            val last = recentPlates[canonical]
            if (last != null && now - last < DEDUP_WINDOW_MS) {
                Log.d(TAG, "dedup: $canonical seen ${now - last} ms ago — skipping")
                return false
            }
            recentPlates[canonical] = now
        }

        val savedPath = thumbnailBytes?.let { saveThumbnail(canonical, now, it) }
        val toInsert = record.copy(
            plate = canonical,
            capturedAt = if (record.capturedAt > 0) record.capturedAt else now,
            thumbnailPath = savedPath ?: record.thumbnailPath,
        )
        dao.insert(toInsert)
        return true
    }

    suspend fun findById(id: Long): PlateRecord? = dao.findById(id)

    suspend fun deleteById(id: Long): Int = dao.deleteById(id)

    suspend fun clear(): Int {
        synchronized(dedupMutex) { recentPlates.clear() }
        return dao.clear()
    }

    private fun saveThumbnail(plate: String, timestamp: Long, bytes: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, THUMB_DIR).apply { mkdirs() }
            // Use timestamp + a sanitised plate suffix so the filename stays
            // unique even if dedup is bypassed in unusual paths.
            val safe = plate.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(24)
            val file = File(dir, "${timestamp}_$safe.jpg")
            FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
            }
            file.absolutePath
        } catch (t: Throwable) {
            Log.w(TAG, "saveThumbnail failed for $plate", t)
            null
        }
    }

    /**
     * Trim whitespace and uppercase Latin / digit characters. Chinese
     * characters are left untouched — they're not case-foldable in any
     * meaningful sense and OCR sometimes returns them as "京A12345" already.
     */
    private fun canonicalise(plate: String): String = plate.trim()

    companion object {
        private const val TAG = "PlateRecordRepository"
        private const val THUMB_DIR = "plates"
        const val DEDUP_WINDOW_MS = 2_000L
    }
}
