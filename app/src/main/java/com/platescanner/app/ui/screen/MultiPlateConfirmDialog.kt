package com.platescanner.app.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.platescanner.app.R
import com.platescanner.app.domain.PlateCandidate
import com.platescanner.app.ui.scanner.ScannerViewModel
import kotlinx.coroutines.delay

/**
 * v0.7 wide-shot confirmation grid. Shows all the plates the model
 * returned from a single 横屏 capture, lets the user pick which ones
 * to commit (default: ALL ticked), and confirms with one bottom button.
 *
 * Layout:
 *   - Top: header "识别到 N 辆车" + 30s countdown bar
 *   - Middle: LazyColumn of plate cards (1 per row, max ~3 rows)
 *     - Each card: plate number (big monospace) + confidence + ✓/✗ toggle
 *     - Default state: all plates checked (✓)
 *     - Tapping the card flips between ✓ (will commit) and ✗ (will skip)
 *   - Bottom: "确认全部" button (commits all checked) + "跳过" (commits none)
 *
 * Auto-confirm after 30s behaves like the v0.6 single-plate dialog:
 * all currently-checked plates are committed, the dialog closes.
 */
@Composable
fun MultiPlateConfirmDialog(
    candidates: List<PlateCandidate>,
    frameBytes: ByteArray?,
    openedAtMs: Long,
    onConfirm: (selected: List<PlateCandidate>) -> Unit,
    onSkip: () -> Unit,
) {
    // Decode the wide-shot JPEG to a Bitmap (shared thumbnail for the grid).
    val thumbnail = remember(frameBytes) {
        frameBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .getOrNull()
        }
    }

    // Selection state: a Set of plate strings the user has ticked. Default
    // is ALL candidates — the user un-ticks the ones they don't want.
    var selected by remember(candidates) {
        mutableStateOf(candidates.map { it.plate }.toSet())
    }

    // 4Hz countdown tick (matches v0.6 PlateConfirmDialog cadence).
    var nowMs by remember(openedAtMs) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(openedAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(250L)
        }
    }
    val total = ScannerViewModel.CONFIRM_AUTO_AFTER_MS.toFloat()
    val elapsed = (nowMs - openedAtMs).coerceAtLeast(0L).toFloat()
    val remainingMs = (total - elapsed).coerceAtLeast(0f)
    val progress = (remainingMs / total).coerceIn(0f, 1f)
    val remainingSeconds = (remainingMs / 1000f).coerceAtLeast(0f)
    // Auto-confirm when the timer runs out. We commit whatever's
    // currently selected (which by default is all).
    LaunchedEffect(openedAtMs) {
        delay(ScannerViewModel.CONFIRM_AUTO_AFTER_MS)
        onConfirm(candidates.filter { it.plate in selected })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header — count + auto-confirm countdown.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(
                            R.string.scanner_multi_grid_title,
                            candidates.size,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.scanner_multi_grid_select_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Wide-shot thumbnail (shared preview, ~16:9 to match the
                // actual landscape capture). Showing it gives the user
                // visual context for "which lane did I just shoot".
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        )
                    } else {
                        Text(text = "—", color = Color.White.copy(alpha = 0.6f))
                    }
                }

                // Plate cards — one per row, max 3 in practice.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((candidates.size.coerceAtMost(3) * 64).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates, key = { it.plate }) { cand ->
                        PlateRow(
                            candidate = cand,
                            selected = cand.plate in selected,
                            onToggle = {
                                selected = if (cand.plate in selected) {
                                    selected - cand.plate
                                } else {
                                    selected + cand.plate
                                }
                            },
                        )
                    }
                }

                // Countdown bar.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "%.0fs 后自动确认".format(remainingSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Action row.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.scanner_multi_grid_skip))
                    }
                    Button(
                        onClick = {
                            onConfirm(candidates.filter { it.plate in selected })
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.scanner_multi_grid_confirm_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlateRow(
    candidate: PlateCandidate,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val row = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = row),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ✓/✗ indicator on the left.
            Icon(
                imageVector = if (selected) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            // Plate + confidence.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.plate,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                candidate.confidence?.let {
                    Text(
                        text = "置信度 %.2f".format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
