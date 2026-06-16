package com.platescanner.app.network

import com.platescanner.app.domain.PlateCandidate

/**
 * Contract for the plate-recognition API. The track-2 implementation binds
 * this to a Retrofit-generated proxy that talks to the M3 multimodal endpoint.
 *
 * Errors are surfaced via [Result] so callers (the scanner ViewModel) can
 * treat the API as a `suspend fun ... : Result<List<PlateCandidate>>` and
 * apply their own retry / backoff policy.
 */
interface MiniMaxApi {
    suspend fun recognizePlate(imageBytes: ByteArray): Result<List<PlateCandidate>>

    /**
     * Wide-shot multi-plate recognition. Used by the v0.7 "横屏多车" mode:
     * the user holds the phone landscape and fits 2-3 cars in one frame.
     *
     * Same wire format as [recognizePlate] but with a different prompt that
     * emphasises "all visible plates" rather than the "primary" plate. The
     * implementation may also use a higher JPEG quality / resolution to
     * preserve the smaller plate details in a wide frame.
     *
     * Returns 0..N candidates; 0 is fine if the user captured an empty lane.
     */
    suspend fun recognizeMultiPlate(imageBytes: ByteArray): Result<List<PlateCandidate>> =
        recognizePlate(imageBytes)

    /**
     * Lightweight connection test — sends a minimal request to verify the
     * configured base URL + API key are reachable and the model accepts
     * the expected message format.
     *
     * @return a [Result] whose [Result.getOrNull] is non-null on success,
     *         containing the model name that echoed back.
     */
    suspend fun testConnection(): Result<String>
}
