package net.dom53.inkita.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.AuthRepository

@Composable
fun SettingsKavitaScreen(
    appPreferences: AppPreferences,
    authRepository: AuthRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var serverUrl by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var storedApiKey by remember { mutableStateOf("") }
    var isConfigured by remember { mutableStateOf(false) }
    var isEditingApiKey by remember { mutableStateOf(false) }
    var showHttpWarning by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf("") }
    var pendingApiKey by remember { mutableStateOf("") }
    var warningFromSave by remember { mutableStateOf(false) }
    var useHttps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        appPreferences.configFlow.collectLatest { config: AppConfig ->
            val rawUrl = config.serverUrl
            val normalized =
                when {
                    rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl.removePrefix("https://")
                    rawUrl.startsWith("http://", ignoreCase = true) -> rawUrl.removePrefix("http://")
                    else -> rawUrl
                }.trimEnd('/')
            serverUrl = normalized
            useHttps = !config.serverUrl.startsWith("http://", ignoreCase = true)
            storedApiKey = config.apiKey
            isConfigured = config.isConfigured
            isEditingApiKey = false
            apiKeyInput = ""
            showHttpWarning = false
            pendingUrl = ""
            pendingApiKey = ""
            warningFromSave = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
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
//            Icon(Icons.Filled.Tune, contentDescription = null)
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text(stringResource(R.string.settings_kavita_server)) },
                placeholder = { Text("kavita.myserver.com") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.settings_kavita_use_https))
                Switch(
                    checked = useHttps,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            useHttps = true
                        } else {
                            warningFromSave = false
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
                        onClick = {
                            scope.launch {
                                appPreferences.clearAuth(clearServer = true)
                                storedApiKey = ""
                                apiKeyInput = ""
                                serverUrl = ""
                                useHttps = true
                                isConfigured = false
                                isEditingApiKey = false
                                snackbarHostState.showSnackbar(context.getString(R.string.settings_kavita_logged_out))
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_kavita_logout))
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val candidateKey =
                                when {
                                    apiKeyInput.isNotBlank() -> apiKeyInput.trim()
                                    isConfigured && storedApiKey.isNotBlank() -> storedApiKey
                                    else -> ""
                                }

                            if (serverUrl.isBlank() || candidateKey.isBlank()) {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.settings_kavita_login_toast),
                                )
                                return@launch
                            }

                            val host =
                                serverUrl
                                    .trim()
                                    .removePrefix("https://")
                                    .removePrefix("http://")
                                    .trimEnd('/')
                            val builtUrl = (if (useHttps) "https://" else "http://") + host
                            if (!useHttps) {
                                pendingUrl = builtUrl
                                pendingApiKey = candidateKey
                                warningFromSave = true
                                showHttpWarning = true
                                return@launch
                            }

                            authRepository.configure(
                                serverUrl = builtUrl,
                                apiKey = candidateKey,
                            )

                            storedApiKey = candidateKey
                            serverUrl = host
                            apiKeyInput = ""
                            isEditingApiKey = false
                            snackbarHostState.showSnackbar("Configuration saved. Please restart the app.")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(
                                "Save failed: ${e.message ?: "unknown error"}",
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.settings_kavita_button_login))
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }

    if (showHttpWarning) {
        AlertDialog(
            onDismissRequest = { showHttpWarning = false },
            title = { Text(stringResource(R.string.settings_kavita_http_warning_title)) },
            text = { Text(stringResource(R.string.settings_kavita_http_warning_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (warningFromSave) {
                                try {
                                    val host =
                                        pendingUrl
                                            .removePrefix("https://")
                                            .removePrefix("http://")
                                            .trimEnd('/')
                                    useHttps = false
                                    authRepository.configure(
                                        serverUrl = pendingUrl,
                                        apiKey = pendingApiKey,
                                    )
                                    storedApiKey = pendingApiKey
                                    serverUrl = host
                                    apiKeyInput = ""
                                    isEditingApiKey = false
                                    showHttpWarning = false
                                    warningFromSave = false
                                    snackbarHostState.showSnackbar("Configuration saved. Please restart the app.")
                                } catch (e: Exception) {
                                    showHttpWarning = false
                                    warningFromSave = false
                                    snackbarHostState.showSnackbar("Save failed: ${e.message ?: "unknown error"}")
                                }
                            } else {
                                useHttps = false
                                showHttpWarning = false
                                warningFromSave = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.settings_kavita_http_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHttpWarning = false
                        warningFromSave = false
                    },
                ) {
                    Text(stringResource(R.string.general_cancel))
                }
            },
        )
    }
}
