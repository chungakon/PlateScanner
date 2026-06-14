package com.platescanner.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.platescanner.app.R
import com.platescanner.app.ui.theme.PlateScannerTheme

/**
 * Parking-lot inspired home background.
 *
 *  - Top half: blue-sky gradient (浅蓝 → 浅白), 模拟室外停车场的天空
 *  - Bottom half: faded asphalt (浅灰 → 浅白), 模拟水泥/柏油地面
 *  - Decorative:
 *    * Soft sun-glow circle in the upper-left
 *    * Faint yellow parking-space divider lines near the bottom
 *  - All decoration rendered via Compose Canvas (no asset file needed),
 *    so it scales to any screen and zero APK bloat.
 */
@Composable
private fun ParkingLotBackground(modifier: Modifier = Modifier) {
    val skyTop = Color(0xFFB3D9F2)       // 偏亮天蓝
    val skyBottom = Color(0xFFE8F1F8)    // 接近白的浅蓝
    val groundTop = Color(0xFFE8E8E8)    // 浅灰水泥
    val groundBottom = Color(0xFFF7F7F7) // 接近白
    val lineColor = Color(0xFFFFD54F)    // 黄色车位线
    val sunColor = Color(0xFFFFF59D)     // 太阳光晕

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(skyTop, skyBottom, groundTop, groundBottom),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY,
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. 太阳光晕(左上角, 软光)
            drawCircle(
                color = sunColor.copy(alpha = 0.45f),
                radius = size.minDimension * 0.35f,
                center = Offset(size.width * 0.18f, size.height * 0.06f),
            )
            drawCircle(
                color = sunColor.copy(alpha = 0.25f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.18f, size.height * 0.06f),
            )

            // 2. 车位线 — 在屏幕 ~80% 高度画 5 条短黄线
            val lineY = size.height * 0.82f
            val lineHeightPx = 6.dp.toPx()
            val lineWidthPx = 56.dp.toPx()
            val lineGapPx = 40.dp.toPx()
            val totalWidth = lineWidthPx * 5 + lineGapPx * 4
            val startX = (size.width - totalWidth) / 2f
            for (i in 0 until 5) {
                val x = startX + i * (lineWidthPx + lineGapPx)
                drawRect(
                    color = lineColor.copy(alpha = 0.65f),
                    topLeft = Offset(x, lineY),
                    size = Size(lineWidthPx, lineHeightPx),
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onStartScan: () -> Unit,
    onViewRecords: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Decorative parking-lot background — drawn behind everything.
            ParkingLotBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_start_scan))
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onViewRecords,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_view_records))
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_title))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PlateScannerTheme {
        HomeScreen(onStartScan = {}, onViewRecords = {}, onOpenSettings = {})
    }
}
