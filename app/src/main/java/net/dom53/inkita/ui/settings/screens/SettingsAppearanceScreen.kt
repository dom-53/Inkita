package net.dom53.inkita.ui.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.AppTheme

@Composable
fun SettingsAppearanceScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedTheme by remember { mutableStateOf(AppTheme.System) }

    LaunchedEffect(Unit) {
        appPreferences.appThemeFlow.collectLatest { theme ->
            selectedTheme = theme
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
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)

        ThemeOptionRow(
            label = stringResource(R.string.settings_appearance_by_system),
            selected = selectedTheme == AppTheme.System,
        ) {
            selectedTheme = AppTheme.System
            scope.launch { appPreferences.setAppTheme(AppTheme.System) }
        }
        ThemeOptionRow(
            label = stringResource(R.string.settings_appearance_light),
            selected = selectedTheme == AppTheme.Light,
        ) {
            selectedTheme = AppTheme.Light
            scope.launch { appPreferences.setAppTheme(AppTheme.Light) }
        }
        ThemeOptionRow(
            label = stringResource(R.string.settings_appearance_dark),
            selected = selectedTheme == AppTheme.Dark,
        ) {
            selectedTheme = AppTheme.Dark
            scope.launch { appPreferences.setAppTheme(AppTheme.Dark) }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
