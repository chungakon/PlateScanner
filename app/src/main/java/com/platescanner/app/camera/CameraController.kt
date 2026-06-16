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
     * The implementation captures a single JPEG frame, hands it to the
     * listener, then waits for the next tap. Multiple in-flight calls
     * are coalesced — a burst of taps produces a single frame.
     */
    fun takePicture()

    /**
     * Switch the camera into v0.7 "横屏多车" mode. Implementation should:
     *   1. Re-bind the camera with a landscape orientation (1920x1080 or
     *      whatever the device's back camera supports in landscape).
     *   2. Use a higher resolution than the v0.6 single-plate mode
     *      (which is 500px long edge) — small plates in a wide frame
     *      need the full sensor to stay above the model's 30px minimum.
     *   3. Subsequent [takePicture] calls in this mode return a JPEG
     *      with the higher resolution; the caller is responsible for
     *      asking the model to look for *all* plates, not the primary.
     *
     * Calling this twice is a no-op. Calling [switchToSingleMode] reverts.
     */
    fun switchToMultiPlateMode()

    /**
     * Revert to v0.6 single-plate vertical mode. Re-binds the camera with
     * the lower resolution so the JPEG payload is small enough for the
     * single-plate 500px-long-edge budget.
     */
    fun switchToSingleMode()

    /**
     * Current mode. Defaults to [Mode.SINGLE] (v0.6 behavior). Implementations
     * update this on every successful [switchToMultiPlateMode] /
     * [switchToSingleMode] call. Used by the UI to know whether to label
     * the next capture as "wide-shot" or "single".
     */
    fun currentMode(): Mode

    enum class Mode { SINGLE, MULTI }

    fun setOnFrameListener(listener: (ByteArray, Int, Int) -> Unit)

    fun clearOnFrameListener()
}
