package net.dom53.inkita.ui.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.AuthRepository
import net.dom53.inkita.domain.repository.KavitaConnectionException
import net.dom53.inkita.domain.repository.KavitaConnectionException.Problem

@Composable
fun SettingsKavitaScreen(
    appPreferences: AppPreferences,
    authRepository: AuthRepository,
    onBack: () -> Unit,
) {
    SettingsKavitaScreen(
        configFlow = appPreferences.configFlow,
        authRepository = authRepository,
        onBack = onBack,
        onLogout = { appPreferences.clearAuth(clearServer = true) },
    )
}

@Composable
internal fun SettingsKavitaScreen(
    configFlow: Flow<AppConfig>,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onLogout: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var serverUrl by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var storedApiKey by remember { mutableStateOf("") }
    var imageApiKeyInput by remember { mutableStateOf("") }
    var storedImageApiKey by remember { mutableStateOf("") }
    var isConfigured by remember { mutableStateOf(false) }
    var isEditingApiKey by remember { mutableStateOf(false) }
    var isEditingImageApiKey by remember { mutableStateOf(false) }
    var showHttpWarning by remember { mutableStateOf(false) }
    var pendingConfig by remember { mutableStateOf<AppConfig?>(null) }
    var useHttps by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun applyConfig(config: AppConfig) {
        serverUrl = config.serverUrl.withoutHttpScheme()
        useHttps = !config.serverUrl.startsWith("http://", ignoreCase = true)
        storedApiKey = config.apiKey
        storedImageApiKey = config.imageApiKey
        isConfigured = config.isConfigured
        isEditingApiKey = false
        apiKeyInput = ""
        isEditingImageApiKey = false
        imageApiKeyInput = ""
        showHttpWarning = false
        pendingConfig = null
    }

    fun saveConfig(config: AppConfig) {
        isSaving = true
        scope.launch {
            try {
                authRepository.configure(config.serverUrl, config.apiKey, config.imageApiKey)
                applyConfig(config)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = context.connectionErrorMessage(e)
                return@launch
            } finally {
                isSaving = false
            }
            snackbarHostState.showSnackbar(context.getString(R.string.settings_kavita_saved_toast))
        }
    }

    LaunchedEffect(configFlow) {
        configFlow.collectLatest { applyConfig(it) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.settings_kavita_server)) },
                placeholder = { Text("kavita.myserver.com") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_kavita_use_https))
                Switch(
                    checked = useHttps,
                    enabled = !isSaving,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            useHttps = true
                        } else {
                            pendingConfig = null
                            showHttpWarning = true
                        }
                    },
                )
            }
            OutlinedTextField(
                value =
                    if (!isEditingApiKey && isConfigured && storedApiKey.isNotBlank()) {
                        "********"
                    } else {
                        apiKeyInput
                    },
                onValueChange = { new ->
                    apiKeyInput = new
                    isEditingApiKey = true
                },
                label = { Text(stringResource(R.string.settings_kavita_api_key)) },
                visualTransformation =
                    if (isConfigured && !isEditingApiKey && storedApiKey.isNotBlank()) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                singleLine = true,
                enabled = !isSaving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (focus.isFocused && !isEditingApiKey) {
                                apiKeyInput = ""
                                isEditingApiKey = true
                            }
                        },
            )
            OutlinedTextField(
                value =
                    if (!isEditingImageApiKey && isConfigured && storedImageApiKey.isNotBlank()) {
                        "********"
                    } else {
                        imageApiKeyInput
                    },
                onValueChange = { new ->
                    imageApiKeyInput = new
                    isEditingImageApiKey = true
                },
                label = { Text(stringResource(R.string.settings_kavita_image_api_key)) },
                visualTransformation =
                    if (isConfigured && !isEditingImageApiKey && storedImageApiKey.isNotBlank()) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                singleLine = true,
                enabled = !isSaving,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (focus.isFocused && !isEditingImageApiKey) {
                                imageApiKeyInput = ""
                                isEditingImageApiKey = true
                            }
                        },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (isConfigured) {
                            stringResource(
                                R.string.settings_kavita_status_in,
                            )
                        } else {
                            stringResource(R.string.settings_kavita_status_out)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isConfigured) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = {
                            scope.launch {
                                onLogout()
                                applyConfig(AppConfig("", "", "", 0))
                                saveError = null
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_kavita_logged_out))
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_kavita_logout))
                    }
                }
            }
            saveError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                enabled = !isSaving,
                onClick = {
                    focusManager.clearFocus()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    saveError = null
                    val host = serverUrl.withoutHttpScheme()
                    val apiKey = apiKeyInput.trim().ifBlank { storedApiKey }
                    if (host.isBlank() || apiKey.isBlank()) {
                        saveError = context.getString(R.string.settings_kavita_login_toast)
                        return@Button
                    }
                    val imageKey = imageApiKeyInput.trim().ifBlank { storedImageApiKey.ifBlank { apiKey } }
                    val url = (if (useHttps) "https://" else "http://") + host
                    val config = AppConfig(url, apiKey, imageKey, 0)
                    if (useHttps) {
                        saveConfig(config)
                    } else {
                        pendingConfig = config
                        showHttpWarning = true
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (isSaving) R.string.settings_kavita_checking_connection else R.string.settings_kavita_button_login))
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }

    if (showHttpWarning) {
        AlertDialog(
            onDismissRequest = {
                showHttpWarning = false
                pendingConfig = null
            },
            title = { Text(stringResource(R.string.settings_kavita_http_warning_title)) },
            text = { Text(stringResource(R.string.settings_kavita_http_warning_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val config = pendingConfig
                        pendingConfig = null
                        showHttpWarning = false
                        useHttps = false
                        config?.let { saveConfig(it) }
                    },
                ) {
                    Text(stringResource(R.string.settings_kavita_http_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHttpWarning = false
                        pendingConfig = null
                    },
                ) {
                    Text(stringResource(R.string.general_cancel))
                }
            },
        )
    }
}

private val httpScheme = Regex("^https?://", RegexOption.IGNORE_CASE)

private fun String.withoutHttpScheme(): String = trim().replaceFirst(httpScheme, "").trimEnd('/')

private fun Context.connectionErrorMessage(error: Exception): String {
    val connectionError = error as? KavitaConnectionException
    return when (connectionError?.problem) {
        Problem.InvalidUrl -> getString(R.string.settings_kavita_invalid_url)
        Problem.RejectedKey -> getString(R.string.settings_kavita_rejected_key)
        Problem.Unreachable -> getString(R.string.settings_kavita_unreachable)
        Problem.Tls -> getString(R.string.settings_kavita_tls_failed)
        Problem.UnexpectedResponse ->
            connectionError.statusCode?.let { getString(R.string.settings_kavita_http_error, it) }
                ?: getString(R.string.settings_kavita_unexpected_response)
        null -> getString(R.string.settings_kavita_save_failed, getString(R.string.general_unknown_error))
    }
}
