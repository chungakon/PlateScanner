package com.platescanner.app.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.platescanner.app.camera.CameraController.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * CameraX-backed implementation of [CameraController].
 *
 * Pipeline (tap-to-capture):
 *  1. CameraSelector.DEFAULT_BACK_CAMERA → [Preview] only.
 *  2. User taps the preview → [takePicture] enqueues a one-shot
 *     [ImageAnalysis] use case and a [OneShotAnalyzer].
 *  3. The next frame is converted YUV → RGB Bitmap via
 *     `ImageProxy.toBitmap()`, downscaled so the long edge is
 *     [MAX_EDGE_PX], then JPEG-compressed at quality [JPEG_QUALITY].
 *  4. JPEG bytes + final width/height pushed to the listener on the
 *     camera thread; the listener is responsible for any further
 *     offloading.
 *
 * Why not use the camera2 `takePicture` API directly? It needs
 * `CameraDevice` and a custom `CaptureSession` which is more code than the
 * ImageAnalysis-on-demand trick below. The result is the same: a single
 * JPEG frame at the moment the user taps.
 */
class CameraXController(
    private val maxEdgePx: Int = MAX_EDGE_PX,
) : CameraController {

    /**
     * v0.7 wide-shot resolution cap. Higher than [MAX_EDGE_PX] so small
     * plates in a landscape frame stay readable. 1500px is the sweet
     * spot: a plate that occupies 8% of the 1920px width is ~150px
     * wide, well above the model's ~30px minimum, and the JPEG payload
     * is still under 200KB at quality 60.
     */
    private val multiModeMaxEdgePx: Int = MULTI_MAX_EDGE_PX

    @Volatile
    private var listener: ((ByteArray, Int, Int) -> Unit)? = null

    @Volatile
    private var lifecycleOwner: LifecycleOwner? = null

    @Volatile
    private var previewView: PreviewView? = null

    @Volatile
    private var provider: ProcessCameraProvider? = null

    /**
     * Whether the camera is currently in "armed" mode — i.e. an
     * ImageAnalysis use case is bound and ready to fire on the next frame.
     * A capture cycle is: arm → wait for frame → fire → disarm.
     */
    @Volatile
    private var armed: Boolean = false

    /**
     * v0.7 capture mode. v0.6 always uses [Mode.SINGLE] (lower resolution,
     * 500px long edge). When the user enters the "横屏多车" mode, the UI
     * calls [switchToMultiPlateMode] which flips this to [Mode.MULTI] and
     * re-binds the camera at a higher resolution so small plates in a
     * wide frame stay above the model's minimum pixel threshold.
     */
    @Volatile
    private var mode: Mode = Mode.SINGLE

    private val cameraExecutor: java.util.concurrent.ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "CameraXController-Analysis").apply { isDaemon = true }
        }

    private val analysisDispatcher = cameraExecutor.asCoroutineDispatcher()

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + analysisDispatcher)

    private var imageAnalysis: ImageAnalysis? = null

    private var bindJob: Job? = null

    /**
     * Serializes concurrent [takePicture] calls so we don't double-bind /
     * double-unbind ImageAnalysis under burst tapping.
     */
    private val captureLock = Mutex()

    override fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
    }

    override fun start() {
        val owner = lifecycleOwner
        val surface = previewView
        if (owner == null) {
            Log.w(TAG, "start() called before bindToLifecycle; ignoring")
            return
        }
        if (surface == null) {
            Log.w(TAG, "start() called without previewView; ignoring")
            return
        }
        if (bindJob?.isActive == true) {
            Log.d(TAG, "start() called while a previous bind is in flight; ignoring")
            return
        }
        val ownerContext = surface.context
        bindJob = scope.launch {
            try {
                // ProcessCameraProvider.getInstance() is thread-safe.
                val cameraProvider = withContext(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(ownerContext).get(5, TimeUnit.SECONDS)
                }
                provider = cameraProvider

                // CRITICAL: PreviewView.getSurfaceProvider() and
                // cameraProvider.bindToLifecycle() MUST be called on the main
                // thread. Doing them on the cameraExecutor thread throws
                // `IllegalStateException: Not in application's main thread`
                // and silently kills the preview (results in a black screen).
                withContext(Dispatchers.Main.immediate) {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(surface.surfaceProvider)
                    }
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    // Preview only — ImageAnalysis is added on demand per
                    // [takePicture] call.
                    cameraProvider.bindToLifecycle(owner, selector, preview)
                    Log.d(TAG, "camera bound (preview-only)")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "failed to bind camera", t)
            }
        }
    }

    override fun stop() {
        try {
            provider?.unbindAll()
        } catch (t: Throwable) {
            Log.w(TAG, "stop: unbindAll failed", t)
        }
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        armed = false
        provider = null
        Log.d(TAG, "camera stopped")
    }

    /**
     * User tapped the screen — arm an ImageAnalysis use case so the **next**
     * frame the camera delivers becomes a single JPEG delivered to the
     * listener. Re-tapping while a capture is already in flight is safe —
     * we coalesce so a burst-tap only produces one frame.
     */
    override fun takePicture() {
        if (armed) {
            Log.d(TAG, "takePicture: already armed, ignoring duplicate tap")
            return
        }
        val owner = lifecycleOwner ?: run {
            Log.w(TAG, "takePicture: no lifecycle owner; ignoring")
            return
        }
        val cameraProvider = provider ?: run {
            Log.w(TAG, "takePicture: provider not ready; ignoring")
            return
        }
        // Mutex serializes arm/fire cycles.
        if (!captureLock.tryLock()) {
            Log.d(TAG, "takePicture: another arm in progress, ignoring")
            return
        }
        try {
            scope.launch {
                captureLock.withLock {
                    try {
                        withContext(Dispatchers.Main.immediate) {
                            armOneShotAnalyzer(cameraProvider, owner)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "takePicture: arm failed", t)
                    }
                }
            }
        } finally {
            // Lock is held inside the coroutine; the .tryLock() above just
            // guards against a third tap while one is queued.
        }
    }

    /**
     * Bind a one-shot [ImageAnalysis] on the main thread. The next frame
     * is delivered to [OneShotAnalyzer], which then disarms itself
     * (unbinds the analysis) and invokes the user listener.
     */
    private fun armOneShotAnalyzer(
        cameraProvider: ProcessCameraProvider,
        owner: LifecycleOwner,
    ) {
        if (armed) return
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        analysis.setAnalyzer(cameraExecutor, OneShotAnalyzer())
        imageAnalysis = analysis
        // Re-add the analysis use case to the existing camera.
        cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
        armed = true
        Log.d(TAG, "takePicture: armed; waiting for next frame")
    }

    /**
     * Fires on the camera thread exactly once per [takePicture] call.
     * Captures the frame, invokes the listener, then schedules an
     * async disarm on the main thread.
     */
    private inner class OneShotAnalyzer : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            try {
                val raw: Bitmap = image.toBitmap()
                val rotated = applyRotationIfNeeded(raw, image.imageInfo.rotationDegrees)
                if (rotated !== raw) raw.recycle()
                val (resized, w, h) = resizeBitmapLongEdge(rotated, effectiveMaxEdgePx())
                if (resized !== rotated) rotated.recycle()
                val out = ByteArrayOutputStream((w * h / 4).coerceAtLeast(1024))
                val ok = resized.compress(Bitmap.CompressFormat.JPEG, effectiveJpegQuality(), out)
                resized.recycle()
                if (!ok) {
                    Log.w(TAG, "OneShotAnalyzer: JPEG compress failed")
                    scheduleDisarm()
                    return
                }
                Log.d(TAG, "OneShotAnalyzer: captured ${w}x$h, delivering to listener")
                listener?.invoke(out.toByteArray(), w, h)
                scheduleDisarm()
            } catch (t: Throwable) {
                Log.w(TAG, "OneShotAnalyzer: analyze failed", t)
                scheduleDisarm()
            } finally {
                image.close()
            }
        }
    }

    /**
     * Async-unbind the ImageAnalysis so subsequent frames don't pile up.
     * Runs on Main because [ProcessCameraProvider.unbind] is not thread-safe.
     */
    private fun scheduleDisarm() {
        val owner = lifecycleOwner ?: return
        val cameraProvider = provider ?: return
        scope.launch {
            try {
                withContext(Dispatchers.Main.immediate) {
                    imageAnalysis?.clearAnalyzer()
                    imageAnalysis = null
                    // Rebind preview-only (drops the analysis use case).
                    cameraProvider.unbindAll()
                    val preview = Preview.Builder().build()
                    cameraProvider.bindToLifecycle(
                        owner, CameraSelector.DEFAULT_BACK_CAMERA, preview,
                    )
                    armed = false
                    Log.d(TAG, "OneShotAnalyzer: disarmed, back to preview-only")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "scheduleDisarm: failed", t)
                armed = false
            }
        }
    }

    override fun setOnFrameListener(listener: (ByteArray, Int, Int) -> Unit) {
        this.listener = listener
    }

    override fun clearOnFrameListener() {
        this.listener = null
    }

    override fun switchToMultiPlateMode() {
        if (mode == Mode.MULTI) {
            Log.d(TAG, "switchToMultiPlateMode: already in MULTI mode, ignoring")
            return
        }
        Log.d(TAG, "switchToMultiPlateMode: flipping to MULTI (max edge ${multiModeMaxEdgePx}px)")
        mode = Mode.MULTI
        // v0.7 multi-mode re-binds the camera at a higher resolution
        // (1920x1080) instead of the v0.6 1280x720. The next takePicture
        // call picks up the new resolution via [effectiveMaxEdgePx].
        rebindForMode()
    }

    override fun switchToSingleMode() {
        if (mode == Mode.SINGLE) {
            Log.d(TAG, "switchToSingleMode: already in SINGLE mode, ignoring")
            return
        }
        Log.d(TAG, "switchToSingleMode: flipping to SINGLE (max edge ${maxEdgePx}px)")
        mode = Mode.SINGLE
        rebindForMode()
    }

    override fun currentMode(): Mode = mode

    /**
     * Re-bind the preview use case at the resolution appropriate for the
     * current [mode]. Called by [switchToMultiPlateMode] /
     * [switchToSingleMode]. We unbind everything and re-bind Preview only
     * (the OneShotAnalyzer is arm-on-tap so it doesn't need a permanent
     * binding here).
     */
    private fun rebindForMode() {
        val owner = lifecycleOwner ?: return
        val surface = previewView ?: return
        val cameraProvider = provider ?: return
        val targetSize = if (mode == Mode.MULTI) {
            android.util.Size(1920, 1080)
        } else {
            android.util.Size(1280, 720)
        }
        scope.launch {
            try {
                withContext(Dispatchers.Main.immediate) {
                    cameraProvider.unbindAll()
                    val preview = Preview.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        targetSize,
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                        .build()
                    preview.setSurfaceProvider(surface.surfaceProvider)
                    cameraProvider.bindToLifecycle(
                        owner, CameraSelector.DEFAULT_BACK_CAMERA, preview,
                    )
                    Log.d(TAG, "rebound preview for $mode at ${targetSize.width}x${targetSize.height}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "rebindForMode: failed", t)
            }
        }
    }

    /**
     * Per-mode edge cap. SINGLE is v0.6's 500px (saves tokens, 1 car in frame).
     * MULTI is 1500px (3 cars in frame; each plate still ~150px wide, well
     * above the model's minimum).
     */
    private fun effectiveMaxEdgePx(): Int = when (mode) {
        Mode.SINGLE -> maxEdgePx
        Mode.MULTI -> multiModeMaxEdgePx
    }

    /**
     * Per-mode JPEG quality. SINGLE is 60 (saves tokens). MULTI is 70
     * because compressed artifacts on a small plate in a wide frame are
     * more damaging than on a single-car close-up.
     */
    private fun effectiveJpegQuality(): Int = when (mode) {
        Mode.SINGLE -> JPEG_QUALITY
        Mode.MULTI -> MULTI_JPEG_QUALITY
    }

    /** Release the camera executor. Safe to call multiple times. */
    fun shutdown() {
        stop()
        cameraExecutor.shutdown()
        try {
            cameraExecutor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        scope.cancel()
    }

    private fun applyRotationIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val TAG = "CameraXController"
        const val MAX_EDGE_PX = 500
        // Lowered from 70 → 60 to shrink the upload payload (M3 charges per
        // token, and JPEG artifacts at this resolution are barely visible to
        // the model). 500px long edge is unchanged.
        const val JPEG_QUALITY = 60

        // v0.7 wide-shot cap. See [CameraXController.multiModeMaxEdgePx].
        const val MULTI_MAX_EDGE_PX = 1500
        // v0.7 wide-shot quality. 70 (vs single-mode 60) to preserve small
        // plate details under heavy JPEG compression.
        const val MULTI_JPEG_QUALITY = 70
    }
}

/**
 * Resize [src] so its longest edge is [target] px, preserving aspect ratio.
 * Returns the resized bitmap and its dimensions.
 */
private fun resizeBitmapLongEdge(src: Bitmap, target: Int): Triple<Bitmap, Int, Int> {
    val longest = max(src.width, src.height)
    if (longest <= target) return Triple(src, src.width, src.height)
    val scale = target.toFloat() / longest.toFloat()
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val resized = Bitmap.createScaledBitmap(src, w, h, /* filter = */ true)
    return Triple(resized, w, h)
}
