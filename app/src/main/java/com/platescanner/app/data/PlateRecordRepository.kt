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

    /**
     * v0.7 "横屏多车" batch insert. Inserts each [record] in [records]
     * under the same dedup rules as [insertIfFresh] — i.e. plates that
     * have been seen in the last [DEDUP_WINDOW_MS] are silently dropped.
     *
     * Same [thumbnailBytes] (the wide-shot frame) is shared across all
     * records because there is one frame per batch. The thumbnail is
     * saved once and the same path is attached to every record, so the
     * 识别记录 list shows the same preview image for all 2-3 plates
     * from the same shot (which is the desired behaviour — the user
     * can see "ah, I shot this lane together").
     *
     * @return the list of plates that were actually inserted (deduped
     *         ones are excluded). Order matches [records] (deduped
     *         entries are simply absent).
     */
    suspend fun insertManyIfFresh(
        records: List<PlateRecord>,
        thumbnailBytes: ByteArray?,
    ): List<String> {
        if (records.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val savedPath: String? = thumbnailBytes?.let {
            // Save the shared thumbnail once. We use the timestamp as the
            // file prefix and append "_multi" so it's distinguishable from
            // single-shot thumbnails in the file system.
            try {
                val dir = File(context.filesDir, THUMB_DIR).apply { mkdirs() }
                val file = File(dir, "${now}_multi.jpg")
                FileOutputStream(file).use { out ->
                    out.write(it)
                    out.flush()
                }
                file.absolutePath
            } catch (t: Throwable) {
                Log.w(TAG, "insertManyIfFresh: shared thumbnail save failed", t)
                null
            }
        }

        val inserted = mutableListOf<String>()
        // Single canonicalisation pass + dedup decision per record. We do
        // this OUTSIDE the synchronized block of the loop body so we don't
        // hold the lock across IO (the inserts themselves are also off the
        // lock, but we want the dedup *decision* to be atomic).
        val toPersist = synchronized(dedupMutex) {
            records.mapNotNull { record ->
                val canonical = canonicalise(record.plate)
                val last = recentPlates[canonical]
                if (last != null && now - last < DEDUP_WINDOW_MS) {
                    Log.d(TAG, "insertMany: dedup $canonical (${now - last} ms ago)")
                    return@mapNotNull null
                }
                recentPlates[canonical] = now
                record.copy(
                    plate = canonical,
                    capturedAt = if (record.capturedAt > 0) record.capturedAt else now,
                    thumbnailPath = savedPath ?: record.thumbnailPath,
                )
            }
        }
        if (toPersist.isEmpty()) return emptyList()
        // dao.insert returns Long (rowid) per element; we just need the
        // plates that went through. Run as one transaction to keep the
        // history page consistent (all 3 plates appear together).
        dao.insertAll(toPersist)
        toPersist.forEach { inserted.add(it.plate) }
        return inserted
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
