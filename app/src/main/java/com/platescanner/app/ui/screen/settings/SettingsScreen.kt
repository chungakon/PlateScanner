package com.platescanner.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.platescanner.app.R
import com.platescanner.app.network.ProviderPreset
import kotlinx.coroutines.flow.collectLatest

/**
 * Settings screen. Lets the user:
 *   1. Pick a provider preset (one tap fills base URL + model name).
 *   2. Override the base URL or model name by hand.
 *   3. Paste / edit the API key (masked by default).
 *   4. Save → persists to DataStore and emits a Snackbar.
 *
 * The save is per-device only — no app restart needed; the next network
 * request picks up the new values via [SettingsRepository].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    val form by viewModel.form.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Translate VM events to snackbar messages.
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                SettingsViewModel.Event.Saved -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.settings_saved))
                }
                is SettingsViewModel.Event.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is SettingsViewModel.Event.TestSuccess -> {
                    snackbarHostState.showSnackbar("连接成功，模型: ${event.model}")
                }
                is SettingsViewModel.Event.TestFailed -> {
                    snackbarHostState.showSnackbar("连接失败: ${event.reason}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SettingsForm(
            form = form,
            testResult = testResult,
            onProviderSelected = viewModel::onProviderSelected,
            onApiKeyChange = viewModel::onApiKeyChange,
            onBaseUrlChange = viewModel::onBaseUrlChange,
            onModelIdChange = viewModel::onModelIdChange,
            onSave = viewModel::save,
            onTestConnection = viewModel::testConnection,
            onOpenAbout = onOpenAbout,
            contentPadding = padding,
        )
    }
}

@Composable
private fun SettingsForm(
    form: SettingsViewModel.FormState,
    testResult: Boolean?,
    onProviderSelected: (ProviderPreset) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    onOpenAbout: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_provider_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_provider_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        ProviderPreset.ALL.forEach { preset ->
            ProviderCard(
                preset = preset,
                selected = form.selectedProvider == preset,
                onSelect = { onProviderSelected(preset) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_runtime_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_runtime_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = form.apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_api_key_label)) },
            placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
        )

        OutlinedTextField(
            value = form.baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_base_url_label)) },
            placeholder = { Text("https://api.example.com") },
            singleLine = true,
        )

        OutlinedTextField(
            value = form.modelId,
            onValueChange = onModelIdChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_model_label)) },
            placeholder = { Text("MiniMax-Text-01") },
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        // Connection test row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = testResult == null || form.apiKey.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                if (testResult == null) {
                    Text(stringResource(R.string.settings_test_connection))
                } else {
                    Icon(
                        imageVector = if (testResult) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (testResult) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (testResult) "已连接" else "连接失败，点击重试",
                        color = if (testResult) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            if (testResult == null && form.apiKey.isNotBlank()) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.about_title))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProviderCard(
    preset: ProviderPreset,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null, // selection handled by the outer `selectable`
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${preset.baseUrl}  ·  ${preset.modelId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}