package net.dom53.inkita.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var selectedLang by remember { mutableStateOf("system") }
    var offlineMode by remember { mutableStateOf(false) }

    val languages =
        listOf(
            "system" to stringResource(R.string.general_language_system),
            "en" to stringResource(R.string.general_language_en),
            "cs" to stringResource(R.string.general_language_cs),
        )

    LaunchedEffect(Unit) {
        appPreferences.appLanguageFlow.collectLatest { lang ->
            selectedLang = lang
        }
    }
    LaunchedEffect(Unit) {
        appPreferences.offlineModeFlow.collectLatest { enabled ->
            offlineMode = enabled
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
        Text(stringResource(R.string.general_title), style = MaterialTheme.typography.headlineSmall)

        // Language first
        Box(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = languages.firstOrNull { it.first == selectedLang }?.second ?: selectedLang,
                    onValueChange = {},
                    readOnly = true,
                    modifier =
                        Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    label = { Text(stringResource(R.string.general_language_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    singleLine = true,
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.exposedDropdownSize(), // to match width
                ) {
                    languages.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                if (selectedLang != code) {
                                    selectedLang = code
                                    scope.launch { appPreferences.setAppLanguage(code) }
                                }
                            },
                        )
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.settings_general_offline_mode_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_general_offline_mode_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = offlineMode,
                onCheckedChange = { checked ->
                    offlineMode = checked
                    scope.launch { appPreferences.setOfflineMode(checked) }
                },
            )
        }
    }
}
