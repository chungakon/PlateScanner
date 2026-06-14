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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * CameraX-backed implementation of [CameraController].
 *
 * Pipeline:
 *  1. CameraSelector.DEFAULT_BACK_CAMERA → [Preview] + [ImageAnalysis].
 *  2. ImageAnalysis format = YUV_420_888, runs on a single-thread executor.
 *  3. Each emitted frame is converted YUV → RGB Bitmap via CameraX's built-in
 *     `ImageProxy.toBitmap()`, downscaled so the long edge is [MAX_EDGE_PX],
 *     then JPEG-compressed at quality 70.
 *  4. Throttled to at most one emit every [EMIT_INTERVAL_MS] (drop the rest).
 *  5. JPEG bytes + final width/height pushed to the listener on the camera
 *     thread; the listener is responsible for any further offloading.
 */
class CameraXController(
    private val maxEdgePx: Int = MAX_EDGE_PX,
    private val emitIntervalMs: Long = EMIT_INTERVAL_MS,
) : CameraController {

    @Volatile
    private var listener: ((ByteArray, Int, Int) -> Unit)? = null

    @Volatile
    private var lifecycleOwner: LifecycleOwner? = null

    @Volatile
    private var previewView: PreviewView? = null

    @Volatile
    private var provider: ProcessCameraProvider? = null

    @Volatile
    private var lastEmitElapsedMs: Long = 0L

    private val cameraExecutor: java.util.concurrent.ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "CameraXController-Analysis").apply { isDaemon = true }
        }

    private val analysisDispatcher = cameraExecutor.asCoroutineDispatcher()

    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + analysisDispatcher)

    private var imageAnalysis: ImageAnalysis? = null

    private var bindJob: Job? = null

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
                // We hop to Main here, with the provider already initialised.
                withContext(Dispatchers.Main.immediate) {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(surface.surfaceProvider)
                    }

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
                        .also { ia ->
                            ia.setAnalyzer(cameraExecutor, FrameAnalyzer())
                        }
                    imageAnalysis = analysis

                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(owner, selector, preview, analysis)
                    Log.d(TAG, "camera bound; emitting frames")
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
        provider = null
        Log.d(TAG, "camera stopped")
    }

    override fun setOnFrameListener(listener: (ByteArray, Int, Int) -> Unit) {
        this.listener = listener
    }

    override fun clearOnFrameListener() {
        this.listener = null
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

    /**
     * Analyzer that runs on [cameraExecutor]. Each frame is YUV → Bitmap →
     * downscale → JPEG bytes, then throttled before being emitted.
     */
    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            try {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastEmitElapsedMs < emitIntervalMs) {
                    // Drop frame to enforce throttle.
                    return
                }
                // CameraX 1.3.3 ships `ImageProxy.toBitmap()` for YUV/JPEG.
                // The return type is `@NonNull Bitmap` on the Java side; the
                // Kotlin compiler infers a non-null receiver, so we can drop
                // any defensive null check.
                val raw: Bitmap = image.toBitmap()
                val rotated = applyRotationIfNeeded(raw, image.imageInfo.rotationDegrees)
                if (rotated !== raw) raw.recycle()
                val (resized, w, h) = resizeBitmapLongEdge(rotated, maxEdgePx)
                if (resized !== rotated) rotated.recycle()
                val out = ByteArrayOutputStream((w * h / 4).coerceAtLeast(1024))
                val ok = resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                resized.recycle()
                if (!ok) return
                lastEmitElapsedMs = now
                listener?.invoke(out.toByteArray(), w, h)
            } catch (t: Throwable) {
                Log.w(TAG, "analyze failed", t)
            } finally {
                image.close()
            }
        }
    }

    private fun applyRotationIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val TAG = "CameraXController"
        const val MAX_EDGE_PX = 500
        // Tightened from 500ms → 300ms after user testing: with the same
        // network, 300ms gives ~1.6x more recognition attempts per minute
        // and the UI's bbox overlay still updates smoothly.
        const val EMIT_INTERVAL_MS = 150L
        // Lowered from 70 → 60 to shrink the upload payload (M3 charges per
        // token, and JPEG artifacts at this resolution are barely visible to
        // the model). 800px long edge is unchanged.
        const val JPEG_QUALITY = 60
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
