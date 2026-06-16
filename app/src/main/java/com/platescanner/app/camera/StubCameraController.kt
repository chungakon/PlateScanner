package com.platescanner.app.camera

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

/**
 * Placeholder implementation. Logs lifecycle calls and drops frames. Track 2
 * replaces this with [CameraXController] in the Hilt graph.
 *
 * The class is kept (and is binary-compatible with [CameraController]) so unit
 * tests can wire the contract without bringing up the camera stack.
 */
class StubCameraController : CameraController {

    @Volatile
    private var listener: ((ByteArray, Int, Int) -> Unit)? = null

    override fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        Log.d(TAG, "bindToLifecycle() — stub; no frames will be delivered")
    }

    override fun start() {
        Log.d(TAG, "start() — stub; no frames will be delivered")
    }

    override fun stop() {
        Log.d(TAG, "stop()")
    }

    override fun takePicture() {
        Log.d(TAG, "takePicture() — stub; no frame will be delivered")
    }

    override fun setOnFrameListener(listener: (ByteArray, Int, Int) -> Unit) {
        this.listener = listener
    }

    override fun clearOnFrameListener() {
        this.listener = null
    }

    private companion object {
        const val TAG = "StubCameraController"
    }
}
