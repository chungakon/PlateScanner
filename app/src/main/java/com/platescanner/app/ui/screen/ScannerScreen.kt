package com.platescanner.app.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.platescanner.app.R
import com.platescanner.app.data.SettingsRepository
import com.platescanner.app.ui.scanner.ScannerViewModel
import com.platescanner.app.ui.theme.PlateScannerTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

/**
 * Real scanner screen. Hosts the CameraX [PreviewView] in an [AndroidView],
 * wires the lifecycle-bound camera, and renders a floating HUD with the
 * latest recognised candidates and recent records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    // Watch the API-key field so we can surface a "请先配置 key" hint when
    // it's blank. The DataStore Flow is the source of truth — if the user
    // saves a new key in Settings and comes back here, the banner goes away
    // without us having to manually re-fetch.
    val settingsRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java,
        ).settingsRepository()
    }
    val apiKey by settingsRepository.apiKeyFlow.collectAsState(initial = "")
    val apiKeyMissing = apiKey.isBlank()

    // v0.7:硬件/手势返回键也走"先退出横屏,再返回"逻辑
    val isMultiModeForBack = uiState.captureMode ==
        com.platescanner.app.camera.CameraController.Mode.MULTI
    BackHandler(enabled = isMultiModeForBack) {
        viewModel.exitMultiPlateMode()
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    // Re-check on resume (handles a user granting the permission via Settings).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            // Reserved for future toast / snackbar plumbing.
            when (event) {
                is ScannerViewModel.Event.Recognized -> { /* haptic handled in VM */ }
                is ScannerViewModel.Event.Error -> { /* log only */ }
            }
        }
    }

    // Change 3: 30s auto-confirm. The dialog closes itself with ✓ after
    // CONFIRM_AUTO_AFTER_MS unless the user has already tapped a button.
    // The key is `pendingOpenedAtMs` so re-opening the dialog for a new
    // candidate re-arms the timer.
    LaunchedEffect(uiState.pendingOpenedAtMs) {
        if (uiState.pendingOpenedAtMs > 0L) {
            delay(ScannerViewModel.CONFIRM_AUTO_AFTER_MS)
            viewModel.confirmPending()
        }
    }

    // v0.7: orientation flips when the user toggles the multi-plate mode.
    // The activity-level request is the only reliable way to keep the
    // camera preview landscape (PreviewView doesn't auto-rotate on its
    // own — the user has to physically rotate the device, and that
    // triggers a config change which we must handle gracefully).
    val activity = LocalContext.current as? Activity
    LaunchedEffect(uiState.captureMode) {
        val target = when (uiState.captureMode) {
            com.platescanner.app.camera.CameraController.Mode.MULTI ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            com.platescanner.app.camera.CameraController.Mode.SINGLE ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        activity?.requestedOrientation = target
    }

    // Block-by-dialog state for the "missing API key" prompt. The dialog is
    // non-blocking on the camera preview — we just show a banner above the
    // HUD if the user dismissed it once. The user can always re-trigger via
    // the settings cog in the top bar.
    var showMissingKeyDialog by remember { mutableStateOf(apiKeyMissing) }
    LaunchedEffect(apiKeyMissing) {
        if (apiKeyMissing) showMissingKeyDialog = true
    }

    if (showMissingKeyDialog && apiKeyMissing) {
        AlertDialog(
            onDismissRequest = { showMissingKeyDialog = false },
            title = { Text(stringResource(R.string.scanner_missing_key_title)) },
            text = { Text(stringResource(R.string.scanner_missing_key_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showMissingKeyDialog = false
                    onOpenSettings()
                }) {
                    Text(stringResource(R.string.scanner_missing_key_go_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMissingKeyDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            // v0.7 横屏优化:横屏时把 TopAppBar 改成透明 + 紧凑,让相机预览
            // 占据尽可能多的横向空间。竖屏时保持原样(白底 + 标题)。
            val isMultiMode = uiState.captureMode ==
                com.platescanner.app.camera.CameraController.Mode.MULTI
            // v0.7:横屏时按返回 → 先切回竖屏,再返回上一页
            // (避免直接回到首页还是横屏状态)
            val backHandler: () -> Unit = {
                if (isMultiMode) {
                    viewModel.exitMultiPlateMode()
                } else {
                    onBack()
                }
            }
            TopAppBar(
                title = {
                    if (isMultiMode) {
                        // 横屏时把标题藏起来,只留功能按钮,最大化相机空间
                    } else {
                        Text(stringResource(R.string.scanner_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = backHandler) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = if (isMultiMode) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    // v0.7 mode toggle. Switching here rotates the screen
                    // and re-binds the camera at the new resolution.
                    IconButton(onClick = {
                        if (isMultiMode) {
                            viewModel.exitMultiPlateMode()
                        } else {
                            viewModel.enterMultiPlateMode()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.CropLandscape,
                            contentDescription = stringResource(
                                if (isMultiMode) R.string.scanner_multi_mode_exit
                                else R.string.scanner_multi_mode_enter
                            ),
                            // Highlight when active so the user knows
                            // they're in 横屏 mode.
                            tint = if (isMultiMode) {
                                // 横屏时 active 状态用亮色(背景已透明,需要对比)
                                Color(0xFF00E5FF)
                            } else if (isMultiMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = if (isMultiMode) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = if (isMultiMode) {
                    // 横屏:背景透明,让相机预览占满
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    )
                } else {
                    // 竖屏:原样
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors()
                },
                windowInsets = if (isMultiMode) {
                    // 横屏:不要 status bar insets,节省垂直空间
                    androidx.compose.foundation.layout.WindowInsets(0)
                } else {
                    androidx.compose.material3.TopAppBarDefaults.windowInsets
                },
            )
        },
    ) { padding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        if (hasPermission) {
            // Tap-to-capture camera preview.
            // The whole preview area is clickable — the user taps anywhere on
            // the camera feed to capture one frame, which is then sent to M3
            // for recognition. While a capture round is in flight the
            // overlay dims the preview and shows a spinner; new taps are
            // ignored via the [UiState.isCapturing] debounce.
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                viewModel = viewModel,
                uiState = uiState,
                onTapToCapture = {
                    if (uiState.captureMode ==
                        com.platescanner.app.camera.CameraController.Mode.MULTI) {
                        viewModel.captureMulti()
                    } else {
                        viewModel.captureNow()
                    }
                },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (apiKeyMissing) {
                    MissingKeyBanner(onOpenSettings = {
                        showMissingKeyDialog = false
                        onOpenSettings()
                    })
                }
                ScannerHud(
                    uiState = uiState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
                PermissionRationale(
                    modifier = Modifier.align(Alignment.Center),
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                )
            }

            // Change 3: confirmation dialog. Sits as the last child of the
            // outer Box so it draws on top of the camera preview. The
            // scrim is half-transparent (Color.Black @ 0.55f) so the
            // preview is still partially visible behind the dialog — the
            // operator can keep an eye on the gate while confirming.
            val pending = uiState.pendingConfirmation
            if (pending != null) {
                PlateConfirmDialog(
                    plate = pending.plate,
                    confidence = pending.confidence,
                    frameBytes = uiState.pendingFrameBytes,
                    openedAtMs = uiState.pendingOpenedAtMs,
                    onConfirm = { viewModel.confirmPending() },
                    onSkip = { viewModel.skipPending() },
                )
            }

            // v0.7 wide-shot grid. Mutually exclusive with
            // [pendingConfirmation] — the v0.7 ViewModel keeps them in
            // separate state fields and only one can ever be non-null at
            // a time (see ScannerViewModel.processFrame).
            val multiPending = uiState.pendingMultiConfirm
            if (multiPending.isNotEmpty()) {
                MultiPlateConfirmDialog(
                    candidates = multiPending,
                    frameBytes = uiState.pendingMultiFrameBytes,
                    openedAtMs = uiState.pendingMultiOpenedAtMs,
                    onConfirm = { selected ->
                        viewModel.confirmMultiSelected(selected)
                    },
                    onSkip = { viewModel.skipMulti() },
                )
            }
        }
    }
}

@Composable
private fun MissingKeyBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.scanner_missing_key_banner),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings_title))
            }
        }
    }
}

/**
 * The post-recognition confirmation dialog. Sits on top of the camera
 * preview with a half-transparent scrim. Shows:
 *  - The recognised plate (big, monospace).
 *  - A 200x150 thumbnail of the captured frame.
 *  - A 30s countdown bar (auto-confirm).
 *  - Two buttons: ✓ confirm + persist, ✗ skip + discard.
 *
 * The dialog does NOT take focus away from the rest of the app — it
 * dismisses on ✓ / ✗ / back press and on the auto-confirm timer firing.
 */
@Composable
private fun PlateConfirmDialog(
    plate: String,
    confidence: Float?,
    frameBytes: ByteArray?,
    openedAtMs: Long,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    // Decode the JPEG bytes to a Bitmap *once* per dialog. We deliberately
    // don't use remember(frameBytes) because ByteArray equality is reference
    // equality — keeping the bitmap across the entire dialog lifetime is
    // safe (it only lives as long as the dialog does).
    val thumbnail = remember(frameBytes) {
        frameBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                .getOrNull()
        }
    }

    // Tick the countdown at 4Hz. We use a 250ms step so the bar visibly
    // animates without doing a recomposition storm.
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
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
                // Plate number, big and monospace.
                Text(
                    text = plate,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                confidence?.let {
                    Text(
                        text = "置信度 %.2f".format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 200x150 thumbnail. We pin the size (instead of letting
                // Compose size to content) so the dialog doesn't jump when
                // the bitmap finishes decoding.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
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
                            modifier = Modifier
                                .size(width = 200.dp, height = 150.dp),
                        )
                    } else {
                        Text(
                            text = "—",
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                // Countdown progress bar.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
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

                // ✓ / ✗ buttons.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = "跳过")
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = "确认")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    viewModel: ScannerViewModel,
    uiState: ScannerViewModel.UiState,
    onTapToCapture: () -> Unit,
) {
    // Hold a single AndroidView-bound PreviewView across recompositions.
    val holder = remember { PreviewViewHolder() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Whole preview area is clickable → fire a single capture.
            // While a capture is in flight the same gesture is a no-op
            // (debounced by [UiState.isCapturing]).
            .pointerInput(uiState.isCapturing) {
                if (!uiState.isCapturing) {
                    detectTapGestures(onTap = { onTapToCapture() })
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { holder.view = it }
            },
            update = { /* intentionally empty — start happens in DisposableEffect below */ },
            onRelease = {
                viewModel.stopScanning()
                holder.view = null
            },
        )

        // Tap-to-capture hint + capturing-in-flight overlay.
        // Sits on top of the preview (last child of the Box → drawn last).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (uiState.isCapturing) Color.Black.copy(alpha = 0.45f)
                    else Color.Transparent,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (uiState.isCapturing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(bottom = 96.dp)
                        .background(
                            Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(24.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "识别中…",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                // Idle hint — the whole area is tappable. Text differs
                // between SINGLE and MULTI mode so the user always knows
                // which mode they're in.
                val isMulti = uiState.captureMode ==
                    com.platescanner.app.camera.CameraController.Mode.MULTI
                val hintText = androidx.compose.ui.res.stringResource(
                    if (isMulti) R.string.scanner_multi_hint
                    else R.string.scanner_single_hint,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(bottom = 96.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = hintText,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    // Start the camera once the PreviewView is attached to the window.
    // Doing this here (instead of in `update`) avoids re-binding on every
    // recomposition and ensures the surface is alive when CameraX asks for it.
    DisposableEffect(lifecycleOwner) {
        val view = holder.view
        if (view != null) {
            if (view.isAttachedToWindow) {
                viewModel.startScanning(lifecycleOwner, view)
            } else {
                val listener = object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) {
                        v.removeOnAttachStateChangeListener(this)
                        viewModel.startScanning(lifecycleOwner, view)
                    }
                    override fun onViewDetachedFromWindow(v: android.view.View) = Unit
                }
                view.addOnAttachStateChangeListener(listener)
            }
        }
        onDispose { viewModel.stopScanning() }
    }
}

/**
 * Draws a single bbox + label on top of the camera preview. The bbox is
 * normalized (0..1) in **source image coordinates** (the JPEG we sent to M3).
 * To map it onto the preview we need:
 *
 *  1. The view's actual on-screen size in px (passed via [previewSize]).
 *  2. The source image's aspect ratio, which we infer from
 *     `latestFrameWidth / latestFrameHeight` (the JPEG dimensions we sent).
 *
 * PreviewView scaleType is FILL_CENTER → the image is uniformly scaled by
 * `max(viewW/srcW, viewH/srcH)` so it covers the view, then centered. The
 * overflow on the longer axis is cropped equally on both sides. The math:
 *
 * ```
 * scale  = max(viewW / srcW, viewH / srcH)
 * dispW  = srcW * scale
 * dispH  = srcH * scale
 * offX   = (viewW - dispW) / 2
 * offY   = (viewH - dispH) / 2
 * viewX  = normLeft  * srcW * scale + offX
 * viewY  = normTop   * srcH * scale + offY
 * ```
 *
 * If [uiState.latestBbox] is null or the source size is unknown, the
 * overlay draws nothing — the user still gets the HUD text.
 */
@Composable
private fun BboxOverlay(
    previewSize: IntSize,
    uiState: ScannerViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    val bbox = uiState.latestBbox
    val srcW = uiState.latestFrameWidth
    val srcH = uiState.latestFrameHeight
    val plate = uiState.latestPlate

    if (bbox == null || srcW == null || srcH == null || srcW <= 0 || srcH <= 0) {
        return
    }
    if (previewSize.width <= 0 || previewSize.height <= 0) {
        return
    }

    val viewW = previewSize.width.toFloat()
    val viewH = previewSize.height.toFloat()
    val scale = maxOf(viewW / srcW.toFloat(), viewH / srcH.toFloat())
    val dispW = srcW.toFloat() * scale
    val dispH = srcH.toFloat() * scale
    val offX = (viewW - dispW) / 2f
    val offY = (viewH - dispH) / 2f

    // Project normalized bbox corners into the Canvas's coordinate space
    // (which is the same as the preview view's local px space — Compose
    // Canvas inside a fillMaxSize Box uses the Box's px size).
    val left = bbox.left * srcW.toFloat() * scale + offX
    val top = bbox.top * srcH.toFloat() * scale + offY
    val right = bbox.right * srcW.toFloat() * scale + offX
    val bottom = bbox.bottom * srcH.toFloat() * scale + offY

    val confidence = uiState.lastCandidates
        .firstOrNull { it.plate == plate }
        ?.confidence
    val label = buildString {
        append(plate.orEmpty())
        if (confidence != null) {
            append("  ")
            append("%.2f".format(confidence))
        }
    }

    val labelBg = Color(0xCC000000)
    val labelText = Color.White
    val labelGapFromBoxPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    val cornerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
    val strokePx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    val halfStroke = strokePx / 2f
    val boxColor = Color(0xFF00E5FF) // cyan — looks like a real viewfinder

    Canvas(modifier = modifier) {
        // Camera viewfinder corner-bracket style instead of a full rectangle.
        // Four corners, each L-shaped with a 40px arm length.

        // Top-left corner
        drawLine(boxColor, Offset(left - halfStroke, top), Offset(left + cornerPx, top), strokeWidth = strokePx)
        drawLine(boxColor, Offset(left, top - halfStroke), Offset(left, top + cornerPx), strokeWidth = strokePx)

        // Top-right corner
        drawLine(boxColor, Offset(right - cornerPx, top), Offset(right + halfStroke, top), strokeWidth = strokePx)
        drawLine(boxColor, Offset(right, top - halfStroke), Offset(right, top + cornerPx), strokeWidth = strokePx)

        // Bottom-left corner
        drawLine(boxColor, Offset(left - halfStroke, bottom), Offset(left + cornerPx, bottom), strokeWidth = strokePx)
        drawLine(boxColor, Offset(left, bottom - cornerPx), Offset(left, bottom + halfStroke), strokeWidth = strokePx)

        // Bottom-right corner
        drawLine(boxColor, Offset(right - cornerPx, bottom), Offset(right + halfStroke, bottom), strokeWidth = strokePx)
        drawLine(boxColor, Offset(right, bottom - cornerPx), Offset(right, bottom + halfStroke), strokeWidth = strokePx)
    }

    // Place the label as a sibling Text on top of the canvas, anchored just
    // above the top edge of the box. We compute the offset in raw px (the
    // Canvas's coord space) and use Modifier.offset (which is layout-time,
    // not draw-time) to position it.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelFontSize = 14.sp
    val labelFontSizePx = with(density) { labelFontSize.toPx() }
    val labelPadVPx = with(density) { 2.dp.toPx() }
    val labelPadHPx = with(density) { 6.dp.toPx() }
    // Rough label width: monospace-ish. Real shaping happens in Text layout.
    val labelWidthPx = label.length * labelFontSizePx * 0.62f + labelPadHPx * 2f
    val labelHeightPx = labelFontSizePx + labelPadVPx * 2f

    val rawLabelX = left
    val rawLabelY = top - labelGapFromBoxPx - labelHeightPx
    val clampedLabelX = rawLabelX.coerceIn(
        0f,
        (viewW - labelWidthPx).coerceAtLeast(0f),
    )
    val clampedLabelY = rawLabelY.coerceAtLeast(0f)

    Text(
        text = label,
        color = labelText,
        style = TextStyle(
            fontSize = labelFontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    x = clampedLabelX.toInt(),
                    y = clampedLabelY.toInt(),
                )
            }
            .background(labelBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Holds a single PreviewView reference for the lifetime of the composable. */
private class PreviewViewHolder {
    var view: PreviewView? = null
}

@Composable
private fun ScannerHud(
    uiState: ScannerViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.55f),
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = uiState.statusMessage
                        ?: if (uiState.scanning) stringResource(R.string.scanner_recognizing)
                        else stringResource(R.string.scanner_idle),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
                uiState.lastCandidates.firstOrNull()?.let { cand ->
                    Text(
                        text = buildString {
                            append(cand.plate)
                            cand.confidence?.let { append("  "); append("%.2f".format(it)) }
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
        if (uiState.recentRecords.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.scanner_recent_label),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    uiState.recentRecords.take(3).forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = r.plate,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = r.confidence?.let { "%.2f".format(it) }.orEmpty(),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(
    modifier: Modifier = Modifier,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.scanner_permission_required),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) {
            Text(stringResource(R.string.scanner_grant_permission))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSettings) {
            Text(stringResource(R.string.action_open_settings))
        }
    }
}

/**
 * Entry point for grabbing the [SettingsRepository] from a non-Hilt context
 * (here, the [ScannerScreen] composable). Hilt-built singletons are always
 * accessible via this indirection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}

@Preview(showBackground = true)
@Composable
private fun ScannerScreenPreview() {
    PlateScannerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Text(
                text = "Scanner preview not available in IDE",
                color = Color.White,
            )
        }
    }
}