package com.platescanner.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platescanner.app.data.SettingsRepository
import com.platescanner.app.network.MiniMaxApi
import com.platescanner.app.network.ProviderPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [SettingsScreen]. Owns:
 *   - The editable form state (key / baseUrl / modelId + selected preset).
 *   - One-shot writes back to the [SettingsRepository] (DataStore).
 *   - Snackbar/Toast event channel for "已保存" / validation errors.
 *
 * Form state is sourced initially from DataStore (so the screen pre-fills
 * with whatever the user previously typed, falling back to BuildConfig
 * defaults via the repository). After "保存", the in-memory copy is also
 * updated so the UI reflects the just-saved values immediately.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val miniMaxApi: MiniMaxApi,
) : ViewModel() {

    data class FormState(
        val apiKey: String = "",
        val baseUrl: String = "",
        val modelId: String = "",
        val selectedProvider: ProviderPreset = ProviderPreset.MINIMAX,
    )

    /** null = not testing, false = fail, true = ok */
    private val _testResult = MutableStateFlow<Boolean?>(null)
    val testResult: StateFlow<Boolean?> = _testResult.asStateFlow()

    sealed interface Event {
        data object Saved : Event
        data class Error(val message: String) : Event
        data class TestSuccess(val model: String) : Event
        data class TestFailed(val reason: String) : Event
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var loaded: Boolean = false

    /**
     * One-shot load on first composition: pulls the persisted values out of
     * DataStore and seeds [form]. Idempotent — calling it again is a no-op.
     */
    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val apiKey = settingsRepository.apiKeyFlow.first()
            val baseUrl = settingsRepository.baseUrlFlow.first()
            val modelId = settingsRepository.modelIdFlow.first()
            val selected = ProviderPreset.fromModelId(modelId)
            _form.value = FormState(
                apiKey = apiKey,
                baseUrl = baseUrl,
                modelId = modelId,
                selectedProvider = selected,
            )
        }
    }

    fun onProviderSelected(preset: ProviderPreset) {
        _form.value = _form.value.copy(
            selectedProvider = preset,
            baseUrl = preset.baseUrl,
            modelId = preset.modelId,
        )
    }

    fun onApiKeyChange(value: String) {
        _form.value = _form.value.copy(apiKey = value)
    }

    fun onBaseUrlChange(value: String) {
        _form.value = _form.value.copy(baseUrl = value)
    }

    fun onModelIdChange(value: String) {
        _form.value = _form.value.copy(modelId = value)
    }

    /**
     * Persist the current form state. Validates that an API key is set (a
     * non-blank key is required even though baseUrl/modelId have sensible
     * defaults). Emits [Event.Saved] on success or [Event.Error] otherwise.
     */
    fun save() {
        val current = _form.value
        val apiKey = current.apiKey.trim()
        if (apiKey.isBlank()) {
            viewModelScope.launch { _events.emit(Event.Error("请填写 API Key")) }
            return
        }
        viewModelScope.launch {
            settingsRepository.setApiKey(apiKey)
            settingsRepository.setBaseUrl(current.baseUrl.trim())
            settingsRepository.setModelId(current.modelId.trim())
            _events.emit(Event.Saved)
        }
    }

    fun testConnection() {
        _testResult.value = null
        viewModelScope.launch {
            val result = miniMaxApi.testConnection()
            result.fold(
                onSuccess = { model ->
                    _testResult.value = true
                    _events.emit(Event.TestSuccess(model))
                },
                onFailure = { e ->
                    _testResult.value = false
                    _events.emit(Event.TestFailed(e.message ?: "未知错误"))
                },
            )
        }
    }

    /**
     * One-tap convenience: apply the selected preset's base URL + model
     * without yet requiring the user to type a key. Persists immediately so
     * the user can navigate away without losing the change.
     */
    fun applyPreset(preset: ProviderPreset) {
        _form.value = _form.value.copy(
            selectedProvider = preset,
            baseUrl = preset.baseUrl,
            modelId = preset.modelId,
        )
        viewModelScope.launch {
            settingsRepository.setProvider(preset)
        }
    }
}