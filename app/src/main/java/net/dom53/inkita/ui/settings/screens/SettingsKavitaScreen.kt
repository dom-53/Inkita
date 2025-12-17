package net.dom53.inkita.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.onFocusChanged
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

    LaunchedEffect(Unit) {
        appPreferences.configFlow.collectLatest { config: AppConfig ->
            serverUrl = config.serverUrl
            storedApiKey = config.apiKey
            isConfigured = config.isConfigured
            isEditingApiKey = false
            apiKeyInput = ""
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
                placeholder = { Text("https://kavita.myserver.com") },
                modifier = Modifier.fillMaxWidth(),
            )
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
            Text(
                text = stringResource(R.string.settings_kavita_https_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                            authRepository.configure(
                                serverUrl = serverUrl.trim(),
                                apiKey = candidateKey,
                            )

                            storedApiKey = candidateKey
                            apiKeyInput = ""
                            isEditingApiKey = false
                            snackbarHostState.showSnackbar("Configuration saved.")
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
}
