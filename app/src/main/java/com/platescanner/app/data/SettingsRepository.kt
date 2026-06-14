package com.platescanner.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.platescanner.app.BuildConfig
import com.platescanner.app.network.ProviderPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's runtime configuration:
 *
 *   - `apiKey`  — bearer token sent in the `Authorization` header.
 *   - `modelId` — chat-completions model name (free-form; user-overridable).
 *   - `baseUrl` — endpoint base (free-form; user-overridable).
 *
 * Backed by Preferences DataStore at [DATASTORE_NAME]. Reads are exposed as
 * [Flow]s so the UI and the network layer can observe changes without
 * explicitly re-fetching after a save.
 *
 * Fallback semantics: when the user hasn't filled in a value yet, getters
 * ([apiKeyFlow], [modelIdFlow], [baseUrlFlow]) transparently return the
 * [BuildConfig] defaults — so a fresh install still has a working key baked
 * in via `gradle.properties`, and the user can override per-device without
 * rebuilding.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Bearer token. Falls back to `BuildConfig.MINIMAX_API_KEY` when unset. */
    val apiKeyFlow: Flow<String> = dataStore.data
        .catch { t ->
            // DataStore wraps IOExceptions in `CorruptionException` for some
            // failures; either way we just yield an empty preferences so
            // downstream collectors stay alive and we fall back to defaults.
            if (t is IOException) {
                Timber.w(t, "DataStore read failed; using defaults")
                emit(emptyPreferences())
            } else {
                throw t
            }
        }
        .map { prefs -> prefs[KEY_API_KEY].orEmpty() }

    /** Model id (e.g. `MiniMax-Text-01`, `gpt-4o-mini`, `qwen-vl-plus`). */
    val modelIdFlow: Flow<String> = dataStore.data
        .catch { t ->
            if (t is IOException) emit(emptyPreferences()) else throw t
        }
        .map { prefs -> prefs[KEY_MODEL_ID].orEmpty() }

    /** Endpoint base (e.g. `https://api.minimax.chat`). */
    val baseUrlFlow: Flow<String> = dataStore.data
        .catch { t ->
            if (t is IOException) emit(emptyPreferences()) else throw t
        }
        .map { prefs -> prefs[KEY_BASE_URL].orEmpty() }

    /**
     * Snapshot accessor used by the network layer at request time. Returns
     * the runtime override if non-blank, otherwise the BuildConfig default.
     * Reads with `first()` (suspend, non-blocking) so callers can use it from
     * a coroutine without spinning on a hot Flow.
     */
    suspend fun apiKey(): String {
        val stored = apiKeyFlow.first()
        return stored.ifBlank { BuildConfig.MINIMAX_API_KEY }
    }

    suspend fun modelId(): String {
        val stored = modelIdFlow.first()
        return stored.ifBlank { MiniMaxApiDefaults.DEFAULT_VISION_MODEL }
    }

    suspend fun baseUrl(): String {
        val stored = baseUrlFlow.first()
        return stored.ifBlank { MiniMaxApiDefaults.DEFAULT_BASE_URL }
    }

    suspend fun setApiKey(value: String) {
        dataStore.edit { prefs -> prefs[KEY_API_KEY] = value.trim() }
    }

    suspend fun setModelId(value: String) {
        dataStore.edit { prefs -> prefs[KEY_MODEL_ID] = value.trim() }
    }

    suspend fun setBaseUrl(value: String) {
        dataStore.edit { prefs -> prefs[KEY_BASE_URL] = value.trim() }
    }

    /**
     * One-shot write: apply a [preset]'s `baseUrl` + `modelId` to the
     * DataStore. The user can still tweak either field by hand afterwards;
     * this is just a convenience shortcut for the radio-button UI.
     */
    suspend fun setProvider(preset: ProviderPreset) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = preset.baseUrl
            prefs[KEY_MODEL_ID] = preset.modelId
        }
    }

    /** Best-effort read of the currently-configured provider. */
    suspend fun currentProvider(): ProviderPreset =
        ProviderPreset.fromModelId(modelId())

    companion object {
        /** Preferences DataStore file name (lives under `filesDir/datastore/`). */
        const val DATASTORE_NAME = "plate_scanner_settings"

        val KEY_API_KEY: Preferences.Key<String> = stringPreferencesKey("api_key")
        val KEY_MODEL_ID: Preferences.Key<String> = stringPreferencesKey("model_id")
        val KEY_BASE_URL: Preferences.Key<String> = stringPreferencesKey("base_url")
    }
}

/**
 * Static defaults shared by the network + repository layers. Lives outside
 * [SettingsRepository] so it's accessible without an injected instance.
 */
object MiniMaxApiDefaults {
    /** Default vision-capable multimodal chat model. */
    const val DEFAULT_VISION_MODEL: String = "MiniMax-Text-01"

    /** Default endpoint base, taken from [BuildConfig.MINIMAX_API_BASE]. */
    const val DEFAULT_BASE_URL: String = BuildConfig.MINIMAX_API_BASE
}