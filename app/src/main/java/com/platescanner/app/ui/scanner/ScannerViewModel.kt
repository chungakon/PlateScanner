package com.platescanner.app.ui.scanner

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platescanner.app.camera.CameraController
import com.platescanner.app.data.PlateRecord
import com.platescanner.app.data.PlateRecordRepository
import com.platescanner.app.domain.BoundingBox
import com.platescanner.app.domain.PlateCandidate
import com.platescanner.app.network.MiniMaxApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Drives the scanner screen:
 *  1. Wires [CameraController] frame callbacks into a coroutine that calls
 *     [MiniMaxApi.recognizePlate] with the JPEG bytes.
 *  2. On a hit, does NOT write to disk — instead sets [UiState.pendingConfirmation]
 *     and lets the user confirm / skip via a Compose dialog.
 *  3. The repository's 5s dedup window still applies: if a candidate comes
 *     in while a dialog for the same plate is on screen, or within 5s of
 *     the last successful insert, we silently skip re-prompting.
 *  4. The dialog has a 30s auto-confirm timer (patrol workflow: the user
 *     doesn't want to tap ✓ for every car passing through the gate).
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cameraController: CameraController,
    private val api: MiniMaxApi,
    private val repository: PlateRecordRepository,
) : ViewModel() {

    data class UiState(
        val scanning: Boolean = false,
        val lastCandidates: List<PlateCandidate> = emptyList(),
        val recentRecords: List<PlateRecord> = emptyList(),
        val lastRecognizedPlate: String? = null,
        val statusMessage: String? = null,
        /**
         * The plate the overlay should highlight. We track it independently
         * of [lastCandidates] because the dedup logic below keeps the overlay
         * "frozen" while a *new* candidate inside the 5s window is suppressed
         * — visually the user is still looking at the same box.
         */
        val latestPlate: String? = null,
        /**
         * Normalized 0..1 bbox for [latestPlate], or null when the model did
         * not return one / the most recent hit was suppressed by dedup.
         */
        val latestBbox: BoundingBox? = null,
        /**
         * The actual width / height (in pixels) of the JPEG we sent to M3
         * for the most recent hit. The overlay layer needs these to
         * back-project a normalized 0..1 bbox onto the PreviewView's
         * pixel rect under FILL_CENTER scale. Null until the first
         * successful recognition.
         */
        val latestFrameWidth: Int? = null,
        val latestFrameHeight: Int? = null,

        // ----- Confirmation dialog state (change 3) -----

        /**
         * Whether a tap-to-capture is currently in flight. UI uses this to
         * dim the preview, show a spinner, and disable re-tap until the
         * recognition round-trip finishes.
         */
        val isCapturing: Boolean = false,

        /**
         * When non-null, the Compose dialog is visible with this candidate.
         * The user must tap ✓ (or wait 30s) to commit, or ✗ to dismiss.
         */
        val pendingConfirmation: PlateCandidate? = null,
        /**
         * Raw JPEG bytes backing the dialog's thumbnail. Kept alongside the
         * candidate so the dialog can show a preview without re-fetching
         * from the network. Released when the dialog closes.
         */
        val pendingFrameBytes: ByteArray? = null,
        /**
         * Wall-clock ms (System.currentTimeMillis()) at which the pending
         * dialog was opened. The dialog auto-confirms after 30s. The screen
         * recomputes "remaining" by subtracting from this on each tick.
         */
        val pendingOpenedAtMs: Long = 0L,
    )

    sealed interface Event {
        data class Recognized(val plate: String) : Event
        data class Error(val message: String) : Event
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val scope = kotlinx.coroutines.CoroutineScope(
        SupervisorJob() + Dispatchers.Default,
    )

    private var inflightJob: Job? = null

    /**
     * Per-plate last-shown time, used to suppress re-prompting for the same
     * plate inside the 5s dedup window. A dialog being on-screen *also*
     * counts as "recently prompted" — see [processFrame].
     */
    private val recentlyPrompted: MutableMap<String, Long> = java.util.concurrent.ConcurrentHashMap()

    init {
        cameraController.setOnFrameListener { bytes, width, height ->
            // Off-load to the viewModelScope's IO pool; do not block the
            // camera executor's single thread.
            if (inflightJob?.isActive == true) {
                // Skip — already processing a frame.
                return@setOnFrameListener
            }
            inflightJob = scope.launch {
                processFrame(bytes, width, height)
            }
        }
    }

    /**
     * Bind the camera to [lifecycleOwner] + [previewView] and start streaming
     * frames into the recogniser.
     */
    fun startScanning(
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        previewView: androidx.camera.view.PreviewView,
    ) {
        cameraController.bindToLifecycle(lifecycleOwner, previewView)
        cameraController.start()
        _uiState.value = _uiState.value.copy(
            scanning = true,
            statusMessage = "已启动扫描",
        )
        Timber.d("scanner: started")
    }

    fun stopScanning() {
        cameraController.stop()
        _uiState.value = _uiState.value.copy(scanning = false, statusMessage = "已停止")
        Timber.d("scanner: stopped")
    }

    /**
     * User tapped the screen. Ask the camera for a single frame, dim the
     * UI (so the user knows it's working), and let the existing
     * `setOnFrameListener` callback in [init] push the JPEG into
     * [processFrame] for recognition.
     */
    fun captureNow() {
        if (_uiState.value.isCapturing) return  // tap debounce
        _uiState.value = _uiState.value.copy(
            isCapturing = true,
            statusMessage = "识别中…",
        )
        cameraController.takePicture()
        Timber.d("scanner: capture requested")
    }

    /**
     * Called by the frame listener when a captured frame finishes
     * processing. Clears the "识别中" indicator regardless of whether the
     * recognition matched anything.
     */
    private fun finishCaptureRound() {
        _uiState.value = _uiState.value.copy(isCapturing = false)
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.stop()
        cameraController.clearOnFrameListener()
    }

    private suspend fun processFrame(bytes: ByteArray, width: Int, height: Int) {
        val result = api.recognizePlate(bytes)
        result.onSuccess { candidates ->
            // Always finish the capture round (clear the "识别中" indicator)
            // whether or not we got a hit.
            _uiState.value = _uiState.value.copy(isCapturing = false)
            if (candidates.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    lastCandidates = emptyList(),
                    statusMessage = "未识别到车牌",
                )
                return@onSuccess
            }
            Timber.d("scanner: parsed ${candidates.size} candidates: ${candidates.joinToString { it.plate }}")
            // Always update the candidate list + status — the HUD is cheap
            // and useful even when the dedup window would suppress the prompt.
            _uiState.value = _uiState.value.copy(
                lastCandidates = candidates,
                statusMessage = "识别到 ${candidates.size} 个候选",
                latestFrameWidth = width.takeIf { it > 0 },
                latestFrameHeight = height.takeIf { it > 0 },
            )

            // New flow: do NOT insert directly. Hand the first fresh
            // candidate to the confirmation dialog and stop. We only handle
            // one pending plate at a time — if the user is in the middle of
            // confirming plate A and a new plate B is recognised, B is held
            // off until A is resolved. The first candidate is the most
            // confident one in the list (model returns them in order).
            val candidate = candidates.first()
            val now = System.currentTimeMillis()

            val current = _uiState.value
            // Don't re-prompt if a dialog is already open for the same plate
            // (just refresh the bytes if the user is still looking at it).
            if (current.pendingConfirmation?.plate == candidate.plate) {
                return@onSuccess
            }
            // Don't stack a new dialog on top of an existing one.
            if (current.pendingConfirmation != null) {
                return@onSuccess
            }
            // 5s dedup: don't show the dialog for the same plate twice in a
            // row inside this window. Mirrors the repository's DEDUP_WINDOW_MS.
            val lastPrompted = recentlyPrompted[candidate.plate]
            if (lastPrompted != null && now - lastPrompted < DEDUP_WINDOW_MS) {
                return@onSuccess
            }
            recentlyPrompted[candidate.plate] = now

            // Update the bbox overlay so the user sees WHERE the plate is
            // even before they confirm.
            val plateChanged = current.latestPlate != candidate.plate
            _uiState.value = current.copy(
                latestPlate = candidate.plate,
                latestBbox = if (current.latestPlate == null || plateChanged) {
                    candidate.bbox ?: current.latestBbox
                } else {
                    current.latestBbox
                },
                latestFrameWidth = width.takeIf { it > 0 } ?: current.latestFrameWidth,
                latestFrameHeight = height.takeIf { it > 0 } ?: current.latestFrameHeight,
                pendingConfirmation = candidate,
                pendingFrameBytes = bytes,
                pendingOpenedAtMs = now,
            )
        }.onFailure { t ->
            Timber.w(t, "recognizePlate failed")
            _uiState.value = _uiState.value.copy(isCapturing = false)
            _events.emit(Event.Error(t.message ?: "识别失败"))
        }
    }

    /**
     * User tapped ✓ on the confirmation dialog. Persist the pending record,
     * fire haptic + Event, and close the dialog. If [pendingFrameBytes] is
     * null (e.g. cleared by a late config change) we still persist, just
     * without a thumbnail.
     */
    fun confirmPending() {
        val current = _uiState.value
        val candidate = current.pendingConfirmation ?: return
        val bytes = current.pendingFrameBytes
        val now = System.currentTimeMillis()
        // Drop the dialog first so the user sees the camera preview again
        // while the IO insert happens in the background.
        _uiState.value = current.copy(
            pendingConfirmation = null,
            pendingFrameBytes = null,
            pendingOpenedAtMs = 0L,
        )
        scope.launch {
            val inserted = withContext(Dispatchers.IO) {
                repository.insertIfFresh(
                    record = PlateRecord(
                        plate = candidate.plate,
                        capturedAt = now,
                        thumbnailPath = null,
                        confidence = candidate.confidence,
                    ),
                    thumbnailBytes = bytes,
                )
            }
            if (inserted) {
                _events.emit(Event.Recognized(candidate.plate))
                triggerHaptic()
                refreshRecent()
            }
        }
    }

    /**
     * User tapped ✗. Drop the pending state without persisting.
     */
    fun skipPending() {
        val current = _uiState.value
        if (current.pendingConfirmation == null) return
        _uiState.value = current.copy(
            pendingConfirmation = null,
            pendingFrameBytes = null,
            pendingOpenedAtMs = 0L,
        )
    }

    private fun refreshRecent() {
        scope.launch {
            repository.observeAll().collect { list ->
                _uiState.value = _uiState.value.copy(
                    recentRecords = list.take(5),
                    lastRecognizedPlate = list.firstOrNull()?.plate,
                )
            }
        }
    }

    private fun triggerHaptic() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() != true) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100L)
            }
        } catch (t: Throwable) {
            Timber.w(t, "vibrate failed")
        }
    }

    companion object {
        /**
         * Local copy of the dedup window. We don't want the scanner flow
         * to import the repository's private constant; 5s matches the
         * documented DEDUP_WINDOW_MS in [com.platescanner.app.data.PlateRecordRepository].
         */
        const val DEDUP_WINDOW_MS = 2_000L

        /**
         * How long the confirmation dialog stays on screen before it
         * auto-confirms. Tuned for the "drive through a gate" workflow:
         * long enough to read the plate, short enough that the operator
         * can wave a hand and let the timer take over.
         */
        const val CONFIRM_AUTO_AFTER_MS = 30_000L
    }
}
