package com.platescanner.app.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

/**
 * Abstraction over the camera pipeline.
 *
 * Implementations are safe to call [start] / [stop] / [bindToLifecycle] / [takePicture]
 * on the main thread, but the listener may be invoked on a background thread.
 *
 * The user-facing flow is **tap-to-capture**, not auto-capture:
 *   1. [bindToLifecycle] + [start] open the live preview only.
 *   2. The user taps the preview area to invoke [takePicture].
 *   3. The implementation captures a single JPEG frame, hands it to the
 *      listener, then waits for the next tap.
 *
 * Frame callback receives:
 *  - [ByteArray] — JPEG-encoded image bytes (quality-controlled by impl).
 *  - [Int] width in pixels (after any downscaling).
 *  - [Int] height in pixels (after any downscaling).
 */
interface CameraController {
    /**
     * Bind to a [LifecycleOwner] (typically an Activity or Fragment) and a
     * [PreviewView] for the live preview surface. The controller will:
     *   1. Acquire the back camera.
     *   2. Attach a [Preview] use case to the [PreviewView].
     *   3. NOT attach an [ImageAnalysis] use case (capture is on-demand).
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    )

    fun start()

    fun stop()

    /**
     * Capture a single frame now, encoding it as JPEG and pushing the
     * resulting bytes to the registered [setOnFrameListener] listener.
     *
     * Safe to call from the main thread. Reentrant-safe — multiple taps in
     * rapid succession will be processed FIFO on the camera executor.
     */
    fun takePicture()

    fun setOnFrameListener(listener: (ByteArray, Int, Int) -> Unit)

    fun clearOnFrameListener()
}
