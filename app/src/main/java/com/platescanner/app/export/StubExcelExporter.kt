package com.platescanner.app.export

import android.util.Log
import com.platescanner.app.data.PlateRecord

/**
 * Placeholder exporter. Returns false to signal "not yet implemented".
 * Track 2 wires Apache POI to write an .xlsx workbook.
 */
class StubExcelExporter : ExcelExporter {
    override suspend fun export(records: List<PlateRecord>, outputPath: String): Boolean {
        Log.d(TAG, "export(${records.size} records) -> $outputPath — stub")
        return false
    }

    private companion object {
        const val TAG = "StubExcelExporter"
    }
}
