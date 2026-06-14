package com.platescanner.app.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

/**
 * Abstraction over the camera pipeline.
 *
 * Implementations are safe to call [start] / [stop] / [bindToLifecycle] on the
 * main thread, but the listener may be invoked on a background thread.
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
     *   3. Attach an [ImageAnalysis] use case for frame callbacks.
     *
     * Calling this again with a new owner / surface rebinds the camera.
     */
    fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    )

    fun start()

    fun stop()

    fun setOnFrameListener(listener: (ByteArray, Int, Int) -> Unit)

    fun clearOnFrameListener()
}
