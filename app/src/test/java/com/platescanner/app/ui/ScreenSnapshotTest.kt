package com.platescanner.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.platescanner.app.R
import com.platescanner.app.data.PlateRecord
import com.platescanner.app.ui.screen.AboutScreen
import com.platescanner.app.ui.screen.HomeScreen
import com.platescanner.app.ui.theme.PlateScannerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Roborazzi-based snapshot tests — render every screen into a PNG on the JVM
 * so we can iterate on UI without booting an emulator / installing an APK.
 *
 * How to use:
 *   ./gradlew :app:recordRoborazziDebug     # writes PNGs to app/build/outputs/roborazzi/
 *   ./gradlew :app:verifyRoborazziDebug     # fails the build if PNGs diverge
 *
 * Implementation note: We use `createAndroidComposeRule<ComponentActivity>`
 * (NOT MainActivity — MainActivity is @AndroidEntryPoint and would need a
 * Hilt test rule), and we just call our standalone `HomeScreen` / `AboutScreen`
 * composables directly inside `composeTestRule.setContent { ... }`. The rule
 * gives us a plain `ComponentActivity` host that's safe to instantiate
 * without an Application or DI graph.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5, sdk = [34])
class ScreenSnapshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val roborazziRule = RoborazziRule()

    @Test
    fun homeScreen() {
        composeTestRule.setContent {
            PlateScannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        onStartScan = {},
                        onViewRecords = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("home_screen.png")
    }

    @Test
    fun aboutScreen() {
        composeTestRule.setContent {
            PlateScannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AboutScreen(onBack = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("about_screen.png")
    }

    @Test
    fun settingsTopBar() {
        composeTestRule.setContent {
            PlateScannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsTopBarStub(onBack = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("settings_topbar.png")
    }

    @Test
    fun recordListScreen() {
        composeTestRule.setContent {
            PlateScannerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RecordListStub(onBack = {}, records = sampleRecords())
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage("record_list_screen.png")
    }

    private fun sampleRecords(): List<PlateRecord> = listOf(
        PlateRecord(
            id = 1,
            plate = "粤TDH8884",
            capturedAt = System.currentTimeMillis() - 5_000,
            thumbnailPath = null,
            confidence = 0.95f,
        ),
        PlateRecord(
            id = 2,
            plate = "粤B12345",
            capturedAt = System.currentTimeMillis() - 65_000,
            thumbnailPath = null,
            confidence = 0.92f,
        ),
        PlateRecord(
            id = 3,
            plate = "京AD12345",
            capturedAt = System.currentTimeMillis() - 130_000,
            thumbnailPath = null,
            confidence = 0.88f,
        ),
        PlateRecord(
            id = 4,
            plate = "沪AF00001",
            capturedAt = System.currentTimeMillis() - 200_000,
            thumbnailPath = null,
            confidence = 0.85f,
        ),
    )
}

@Composable
private fun SettingsTopBarStub(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "API Key / Base URL / 模型名 (真实表单在设备上看更清晰)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RecordListStub(onBack: () -> Unit, records: List<PlateRecord>) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.records_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {},
                icon = {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                text = {
                    Text(stringResource(R.string.records_export))
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(records) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Text(
                            text = record.plate,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(Date(record.capturedAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "置信度 ${"%.2f".format(record.confidence ?: 0f)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
