@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.platescanner.app.network

import com.platescanner.app.data.SettingsRepository
import com.platescanner.app.domain.PlateCandidate

/**
 * Top-level facade for the [MiniMaxApi]. Hilt-provided.
 *
 * The actual implementation [MiniMaxApiImpl] is created lazily by Hilt; this
 * object only exposes a thin factory so non-DI callers (e.g. tests) can
 * build one explicitly.
 */
object ApiClient {

    /** Build a [MiniMaxApiImpl] with an explicit [SettingsRepository]. */
    fun create(settingsRepository: SettingsRepository): MiniMaxApi =
        MiniMaxApiImpl(settingsRepository)
}

/**
 * Fallback stub used when the API key is empty or for unit tests that don't
 * want to touch the network.
 */
class StubMiniMaxApi : MiniMaxApi {
    override suspend fun recognizePlate(imageBytes: ByteArray): Result<List<PlateCandidate>> {
        return Result.success(emptyList())
    }
    override suspend fun testConnection(): Result<String> = Result.success("stub")
}