package net.dom53.inkita.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.ReaderPrefs
import net.dom53.inkita.core.storage.ReaderThemeMode
import net.dom53.inkita.ui.reader.ReaderFontOption
import net.dom53.inkita.ui.reader.readerFontOptions
import net.dom53.inkita.ui.reader.readerThemeOptions

@Composable
fun SettingsReaderScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf(ReaderPrefs()) }

    LaunchedEffect(Unit) {
        appPreferences.readerPrefsFlow.collectLatest { prefs = it }
    }

    var fontSize by remember { mutableStateOf(prefs.fontSize) }
    var lineHeight by remember { mutableStateOf(prefs.lineHeight) }
    var padding by remember { mutableStateOf(prefs.paddingDp.dp) }
    var textAlign by remember { mutableStateOf(prefs.textAlign) }
    var fontFamilyId by remember { mutableStateOf(prefs.fontFamily) }
    var themeMode by remember { mutableStateOf(prefs.readerTheme) }

    LaunchedEffect(prefs) {
        fontSize = prefs.fontSize
        lineHeight = prefs.lineHeight
        padding = prefs.paddingDp.dp
        textAlign = prefs.textAlign
        fontFamilyId = prefs.fontFamily
        themeMode = prefs.readerTheme
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
        Text(
            text = stringResource(R.string.settings_item_reader),
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(stringResource(R.string.reader_text_settings), style = MaterialTheme.typography.titleMedium)
        StepperRow(stringResource(R.string.reader_font_size), "${fontSize.toInt()}") { delta ->
            val newVal = (fontSize + delta).coerceIn(8f, 36f)
            fontSize = newVal
            scope.launch { appPreferences.updateReaderPrefs { copy(fontSize = newVal) } }
        }
        StepperRow(stringResource(R.string.reader_line_height), String.format("%.1f", lineHeight)) { delta ->
            val newVal = (lineHeight + delta * 0.1f).coerceIn(0.8f, 2.5f)
            lineHeight = newVal
            scope.launch { appPreferences.updateReaderPrefs { copy(lineHeight = newVal) } }
        }
        StepperRow(stringResource(R.string.reader_padding), "${padding.value.toInt()} dp") { delta ->
            val newVal = (padding + delta.dp).coerceIn(0.dp, 32.dp)
            padding = newVal
            scope.launch { appPreferences.updateReaderPrefs { copy(paddingDp = newVal.value) } }
        }

        Text(stringResource(R.string.reader_alignment), style = MaterialTheme.typography.titleMedium)
        AlignmentRow(
            textAlign = textAlign,
            onAlignChange = {
                textAlign = it
                scope.launch { appPreferences.updateReaderPrefs { copy(textAlign = it) } }
            },
        )

        FontDropdown(
            fontOptions = readerFontOptions,
            selectedId = fontFamilyId,
            onSelect = { option ->
                fontFamilyId = option.id
                scope.launch {
                    appPreferences.updateReaderPrefs {
                        copy(fontFamily = option.id, useSerif = option.isSerif)
                    }
                }
            },
        )

        ThemeChooser(
            themeMode = themeMode,
            onThemeChange = { mode ->
                themeMode = mode
                scope.launch { appPreferences.updateReaderPrefs { copy(readerTheme = mode) } }
            },
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onDelta: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { onDelta(-1f) },
            ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.general_decrease)) }
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onDelta(1f) }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.general_increase)) }
        }
    }
}

@Composable
private fun AlignmentRow(
    textAlign: TextAlign,
    onAlignChange: (TextAlign) -> Unit,
) {
    val alignItems =
        listOf(
            TextAlign.Start to Icons.Filled.FormatAlignLeft,
            TextAlign.Center to Icons.Filled.FormatAlignCenter,
            TextAlign.End to Icons.Filled.FormatAlignRight,
            TextAlign.Justify to Icons.Filled.FormatAlignJustify,
        )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        alignItems.forEach { (align, icon) ->
            val tint = if (align == textAlign) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            IconButton(onClick = { onAlignChange(align) }) {
                Icon(icon, contentDescription = null, tint = tint)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FontDropdown(
    fontOptions: List<ReaderFontOption>,
    selectedId: String,
    onSelect: (ReaderFontOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = fontOptions.firstOrNull { it.id == selectedId }?.label ?: stringResource(R.string.reader_font_selection)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reader_font_family), style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            TextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.reader_font_family)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors =
                    ExposedDropdownMenuDefaults.textFieldColors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                fontOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ThemeChooser(
    themeMode: ReaderThemeMode,
    onThemeChange: (ReaderThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reader_theme_header), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            readerThemeOptions.forEach { option ->
                FilterChip(
                    selected = themeMode == option.mode,
                    onClick = { onThemeChange(option.mode) },
                    label = { Text(stringResource(option.labelRes)) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}
