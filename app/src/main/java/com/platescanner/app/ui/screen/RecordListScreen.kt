package com.platescanner.app.ui.screen

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.platescanner.app.R
import com.platescanner.app.data.PlateRecord
import com.platescanner.app.ui.records.RecordListViewModel
import com.platescanner.app.ui.theme.PlateScannerTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Record list screen with thumbnail rendering, full-screen image preview
 * (tap to zoom, pinch / drag to pan, tap-outside to close), and a FAB that
 * triggers Excel export via [RecordListViewModel.export].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    onBack: () -> Unit,
    viewModel: RecordListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()

    // Full-screen preview state. Null = no preview shown.
    var previewRecord by remember { mutableStateOf<PlateRecord?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Launch the share chooser whenever the VM emits a fresh intent.
    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { intent ->
            context.startActivity(intent)
        }
    }
    // Surface export status as both a Snackbar (in-app) and a Toast
    // (so it survives a navigation away).
    LaunchedEffect(viewModel) {
        viewModel.exportStatus.collect { status ->
            when (status) {
                is RecordListViewModel.ExportStatus.InProgress -> {
                    snackbarHostState.showSnackbar("正在导出 Excel…")
                }
                is RecordListViewModel.ExportStatus.Success -> {
                    val msg = "已导出到 ${status.path}"
                    snackbarHostState.showSnackbar(msg)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                is RecordListViewModel.ExportStatus.Failure -> {
                    snackbarHostState.showSnackbar("导出失败: ${status.message}")
                    Toast.makeText(context, "导出失败: ${status.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.records_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (records.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.export() },
                    icon = {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = stringResource(R.string.records_export),
                        )
                    },
                    text = { Text(stringResource(R.string.records_export)) },
                )
            }
        },
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.records_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    RecordRow(
                        record = record,
                        onThumbnailClick = { previewRecord = record },
                    )
                }
            }
        }

        // Full-screen preview dialog. Drawn at the Scaffold's content level
        // (sibling of the LazyColumn) so it covers the FAB + top bar.
        previewRecord?.let { record ->
            ImagePreviewDialog(
                record = record,
                onDismiss = { previewRecord = null },
            )
        }
    }
}

@Composable
private fun RecordRow(
    record: PlateRecord,
    onThumbnailClick: () -> Unit,
) {
    val bmp = remember(record.thumbnailPath) {
        record.thumbnailPath?.let { path ->
            runCatching {
                val file = File(path)
                if (file.exists()) android.graphics.BitmapFactory.decodeFile(path) else null
            }.getOrNull()
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(record.id) {
                        // Change 4: tap the thumbnail to open the
                        // full-screen preview. We swallow the gesture here
                        // so it doesn't bubble up to the row's click (the
                        // row has no click handler today, but the future
                        // "open detail" gesture should not be ambiguous).
                        detectTapGestures(onTap = { onThumbnailClick() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = "—",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = record.plate,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = formatTimestamp(record.capturedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                record.confidence?.let { conf ->
                    Text(
                        text = "置信度 %.2f".format(conf),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Full-screen image preview. Black background, image centred, supports
 * pinch-zoom + two-finger pan via [detectTransformGestures], and dismisses
 * on a single-tap.
 *
 * Implementation notes:
 *  - The bitmap is decoded from disk *once* via [remember]; if the file
 *    is gone (e.g. user cleared cache) we fall back to a placeholder.
 *  - Scale state lives in plain [remember] mutable floats; pinch-zoom
 *    multiplies the current scale, and double-tap resets to 1f.
 *  - The [graphicsLayer] modifier applies scale + translation in pixel
 *    space. We keep translation unconstrained and reset on dismiss; the
 *    preview is short-lived so the user won't drift off-screen.
 */
@Composable
private fun ImagePreviewDialog(
    record: PlateRecord,
    onDismiss: () -> Unit,
) {
    // Decode off the main thread would be nicer but Compose's remember is
    // only safe on the main thread, and these JPEGs are ~10-30 KB.
    val bmp = remember(record.thumbnailPath) {
        record.thumbnailPath?.let { path ->
            runCatching {
                val file = File(path)
                if (file.exists()) android.graphics.BitmapFactory.decodeFile(path) else null
            }.getOrNull()
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Outside-tap dismisses. The inner Image consumes the pinch
            // / drag gestures, so the tap detector only fires for taps
            // on the black scrim area.
            .pointerInput(record.id) {
                detectTapGestures(
                    onTap = { onDismiss() },
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = record.plate,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(record.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Compose 1.5+: zoom is the multiplicative factor
                            // (e.g. 1.1f means "make 10% bigger this frame").
                            // We clamp the scale to a sane range so a
                            // two-finger twitch can't blow it up to 100x.
                            scale = (scale * zoom).coerceIn(0.5f, 6f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        } else {
            Text(
                text = "图片已丢失",
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        // Top-right close button. Drawn last so it stays on top of the
        // (possibly zoomed) image.
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White,
            )
        }

        // Bottom caption: plate + timestamp. Helps the user confirm which
        // record they're looking at when scrolling through many rows.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = record.plate,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = Color.White,
            )
            Text(
                text = formatTimestamp(record.capturedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
private fun formatTimestamp(ts: Long): String = dateFmt.format(Date(ts))

@Preview(showBackground = true)
@Composable
private fun RecordListScreenPreview() {
    PlateScannerTheme {
        RecordListScreen(onBack = {})
    }
}
