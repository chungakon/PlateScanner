package com.platescanner.app.ui.records

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platescanner.app.data.PlateRecord
import com.platescanner.app.data.PlateRecordRepository
import com.platescanner.app.export.ExcelExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Drives the record-list screen. Exposes the persisted records (latest first)
 * and an [export] action that builds an .xlsx via [ExcelExporter] and emits a
 * share [Intent] through [shareEvents].
 */
@HiltViewModel
class RecordListViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: PlateRecordRepository,
    private val exporter: ExcelExporter,
) : ViewModel() {

    private val _records = MutableStateFlow<List<PlateRecord>>(emptyList())
    val records: StateFlow<List<PlateRecord>> = _records.asStateFlow()

    private val _shareEvents = MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val shareEvents: SharedFlow<Intent> = _shareEvents.asSharedFlow()

    private val _exportStatus = MutableSharedFlow<ExportStatus>(extraBufferCapacity = 4)
    val exportStatus: SharedFlow<ExportStatus> = _exportStatus.asSharedFlow()

    sealed interface ExportStatus {
        data object InProgress : ExportStatus
        data class Success(val path: String) : ExportStatus
        data class Failure(val message: String) : ExportStatus
    }

    init {
        viewModelScope.launch {
            repository.observeAll().collect { list ->
                _records.value = list
            }
        }
    }

    /**
     * Build the .xlsx, then emit a [Intent.ACTION_SEND] chooser ready to be
     * launched by the screen.
     */
    fun export() {
        if (_records.value.isEmpty()) {
            viewModelScope.launch {
                _exportStatus.emit(ExportStatus.Failure("没有记录可导出"))
            }
            return
        }
        viewModelScope.launch {
            _exportStatus.emit(ExportStatus.InProgress)
            val ok = withContext(Dispatchers.IO) {
                val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val outFile = File(exportDir, "plates_$ts.xlsx")
                val success = exporter.export(_records.value, outFile.absolutePath)
                if (success) outFile.absolutePath else null
            }
            if (ok == null) {
                _exportStatus.emit(ExportStatus.Failure("导出失败,详细请查看日志"))
                return@launch
            }
            try {
                val uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    File(ok),
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "分享 Excel 文件").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                _shareEvents.emit(chooser)
                _exportStatus.emit(ExportStatus.Success(ok))
            } catch (t: Throwable) {
                Timber.w(t, "export share intent build failed")
                _exportStatus.emit(ExportStatus.Failure(t.message ?: "分享 Intent 失败"))
            }
        }
    }
}
