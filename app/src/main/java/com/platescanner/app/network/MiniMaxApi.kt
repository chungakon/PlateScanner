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
     * Lightweight connection test — sends a minimal request to verify the
     * configured base URL + API key are reachable and the model accepts
     * the expected message format.
     *
     * @return a [Result] whose [Result.getOrNull] is non-null on success,
     *         containing the model name that echoed back.
     */
    suspend fun testConnection(): Result<String>
}
