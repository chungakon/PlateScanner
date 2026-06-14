package com.platescanner.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.platescanner.app.data.PlateRecord
import org.apache.poi.ss.usermodel.Drawing
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Apache POI 5.x-backed [ExcelExporter] that writes a one-sheet `.xlsx`
 * workbook with the following columns:
 *
 *   1. 车牌号
 *   2. 捕获时间 (`yyyy-MM-dd HH:mm:ss`)
 *   3. 置信度 (`0.00..1.00`, blank if null)
 *   4. 缩略图 (80×60 px, JPEG/PNG)
 *
 * The header row is bolded; column widths are tuned for typical plate text
 * + the 80×60 thumbnail.
 *
 * The workbook is written to [outputPath]; the parent directory must already
 * exist (callers should `File(outputPath).parentFile.mkdirs()`).
 */
class PoiExcelExporter : ExcelExporter {

    override suspend fun export(records: List<PlateRecord>, outputPath: String): Boolean {
        Log.i("ExcelExporter", "export() called: ${records.size} records → $outputPath")
        return runCatching {
            val workbook = XSSFWorkbook()
            try {
                val sheet: XSSFSheet = workbook.createSheet("Plates")
                val drawing: XSSFDrawing = sheet.createDrawingPatriarch() as XSSFDrawing

                val headerFont: Font = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 12.toShort()
                }
                val headerStyle: XSSFCellStyle = workbook.createCellStyle().apply {
                    setFont(headerFont)
                }

                val dateFmt = SimpleDateFormat(DATE_PATTERN, Locale.US)

                // ---- Header row ----
                val header: XSSFRow = sheet.createRow(0)
                HEADERS.forEachIndexed { idx, label ->
                    val cell = header.createCell(idx) as XSSFCell
                    cell.setCellValue(label)
                    cell.cellStyle = headerStyle
                }

                // ---- Data rows ----
                records.forEachIndexed { rowIdx, record ->
                    val row = sheet.createRow(rowIdx + 1)
                    // 0: plate
                    row.createCell(0).setCellValue(record.plate)
                    // 1: capturedAt
                    row.createCell(1).setCellValue(dateFmt.format(Date(record.capturedAt)))
                    // 2: confidence
                    val confCell = row.createCell(2)
                    if (record.confidence != null) {
                        confCell.setCellValue(record.confidence.toDouble())
                    }
                    // 3: thumbnail
                    val thumbPath = record.thumbnailPath
                    if (!thumbPath.isNullOrBlank()) {
                        Log.d("ExcelExporter", "row $rowIdx attaching thumb: $thumbPath")
                        attachThumbnail(workbook, drawing, rowIdx, thumbPath)
                    }
                }

                // ---- Column widths (in characters × 256) ----
                sheet.setColumnWidth(0, 20 * 256)   // plate
                sheet.setColumnWidth(1, 22 * 256)   // capturedAt
                sheet.setColumnWidth(2, 12 * 256)   // confidence
                sheet.setColumnWidth(3, 18 * 256)   // thumbnail column

                // ---- Row heights so the thumbnail fits ----
                records.indices.forEach { i ->
                    sheet.getRow(i + 1)?.heightInPoints = THUMB_HEIGHT_PT
                }

                Log.i("ExcelExporter", "writing workbook to file…")
                FileOutputStream(outputPath).use { fos ->
                    workbook.write(fos)
                    fos.flush()
                }
                Log.i("ExcelExporter", "SUCCESS: $outputPath")
                true
            } finally {
                workbook.close()
            }
        }.getOrElse { t ->
            Log.e("ExcelExporter", "EXPORT FAILED: ${t.javaClass.simpleName}: ${t.message}")
            Log.e("ExcelExporter", "Stack trace:", t)
            false
        }
    }

    private fun attachThumbnail(
        workbook: XSSFWorkbook,
        drawing: Drawing<*>,
        rowIdx: Int,
        thumbPath: String,
    ) {
        val bytes = java.io.File(thumbPath).takeIf { it.exists() && it.length() > 0 }
            ?.let { java.io.FileInputStream(it).use { input -> input.readBytes() } }
            ?: return
        val bitmap: Bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(bytes)) ?: return
        // Downscale to THUMB_WIDTH x THUMB_HEIGHT so the row stays compact.
        val scaled = if (bitmap.width > THUMB_WIDTH || bitmap.height > THUMB_HEIGHT) {
            Bitmap.createScaledBitmap(bitmap, THUMB_WIDTH, THUMB_HEIGHT, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }
        val pngBytes = java.io.ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        scaled.recycle()

        val pictureIdx = workbook.addPicture(pngBytes, XSSFWorkbook.PICTURE_TYPE_PNG)
        // XSSFDrawing has an overload returning XSSFClientAnchor (8-arg).
        @Suppress("UNCHECKED_CAST")
        val anchor = (drawing as XSSFDrawing).createAnchor(
            /* dx1 */ 0, /* dy1 */ 0,
            /* dx2 */ 0, /* dy2 */ 0,
            /* col1 */ 3, /* row1 */ rowIdx + 1,
            /* col2 */ 4, /* row2 */ rowIdx + 2,
        )
        @Suppress("UNCHECKED_CAST")
        val picture: XSSFPicture = drawing.createPicture(anchor, pictureIdx) as XSSFPicture
        // NOTE: do NOT call picture.resize(...) — that goes through
        // ImageUtils which references java.awt.Dimension, a JDK AWT class
        // that doesn't exist on Android (NoClassDefFoundError at runtime).
        // The 1×1-cell anchor above is good enough as a thumbnail size
        // — Excel / WPS still renders the picture at its native resolution
        // and the row height we set above (THUMB_HEIGHT_PT) gives it
        // enough vertical space.
    }

    companion object {
        private const val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"
        private val HEADERS = arrayOf("车牌号", "捕获时间", "置信度", "缩略图")
        private const val THUMB_WIDTH = 80
        private const val THUMB_HEIGHT = 60
        private const val THUMB_HEIGHT_PT = 48f // ~64 px @ 96 dpi
    }
}
