package com.platescanner.app.export

import com.platescanner.app.data.PlateRecord

/**
 * Contract for exporting plate records to xlsx. Track 2 supplies a
 * real implementation backed by Apache POI.
 */
interface ExcelExporter {
    /**
     * Write [records] to a workbook at [outputPath] (absolute path on device FS).
     *
     * @return true on success, false otherwise. Implementations should
     *   also wrap any IO error and rethrow as a [java.io.IOException]
     *   for callers to surface via the UI.
     */
    suspend fun export(records: List<PlateRecord>, outputPath: String): Boolean
}
