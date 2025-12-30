package net.dom53.inkita.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.ImageReaderMode
import net.dom53.inkita.core.storage.ReaderPrefs
import net.dom53.inkita.core.storage.ReaderThemeMode
import net.dom53.inkita.ui.reader.model.ReaderFontOption
import net.dom53.inkita.ui.reader.model.readerFontOptions
import net.dom53.inkita.ui.reader.model.readerThemeOptions

@Composable
fun SettingsReaderScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    var imageReaderMode by remember { mutableStateOf(prefs.imageReaderMode) }
    var showImageModeDialog by remember { mutableStateOf(false) }
    var imagePrefetchPages by remember { mutableStateOf(prefs.imagePrefetchPages) }
    var showImagePrefetchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        fontSize = prefs.fontSize
        lineHeight = prefs.lineHeight
        padding = prefs.paddingDp.dp
        textAlign = prefs.textAlign
        fontFamilyId = prefs.fontFamily
        themeMode = prefs.readerTheme
        imageReaderMode = prefs.imageReaderMode
        imagePrefetchPages = prefs.imagePrefetchPages
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

        Text(stringResource(R.string.reader_settings_general_section), style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()

        Text(stringResource(R.string.reader_settings_image_section), style = MaterialTheme.typography.titleMedium)
        ImageModeRow(
            mode = imageReaderMode,
            onClick = { showImageModeDialog = true },
        )
        ImagePrefetchRow(
            value = imagePrefetchPages,
            onClick = { showImagePrefetchDialog = true },
        )
        HorizontalDivider()

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

    if (showImageModeDialog) {
        val options =
            listOf(
                ImageReaderMode.LeftToRight to R.string.reader_image_mode_ltr,
                ImageReaderMode.RightToLeft to R.string.reader_image_mode_rtl,
                ImageReaderMode.Vertical to R.string.reader_image_mode_vertical,
                ImageReaderMode.Webtoon to R.string.reader_image_mode_webtoon,
            )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImageModeDialog = false },
            title = { Text(stringResource(R.string.reader_image_default_mode)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    options.forEach { (mode, labelRes) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        if (mode == ImageReaderMode.Webtoon) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    R.string.general_not_implemented,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        } else {
                                            imageReaderMode = mode
                                            scope.launch { appPreferences.updateReaderPrefs { copy(imageReaderMode = mode) } }
                                            showImageModeDialog = false
                                        }
                                    },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = imageReaderMode == mode,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(labelRes),
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f,
                                    ),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageModeDialog = false }) {
                    Text(stringResource(R.string.general_close))
                }
            },
        )
    }

    if (showImagePrefetchDialog) {
        val options = listOf(4, 6, 8, 10, 12, 14, 16, 20)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImagePrefetchDialog = false },
            title = { Text(stringResource(R.string.reader_image_prefetch_pages)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    options.forEach { count ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        imagePrefetchPages = count
                                        scope.launch { appPreferences.updateReaderPrefs { copy(imagePrefetchPages = count) } }
                                        showImagePrefetchDialog = false
                                    },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = imagePrefetchPages == count,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(R.string.reader_image_prefetch_pages_value, count),
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4f,
                                    ),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImagePrefetchDialog = false }) {
                    Text(stringResource(R.string.general_close))
                }
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
private fun ImageModeRow(
    mode: ImageReaderMode,
    onClick: () -> Unit,
) {
    val labelRes =
        when (mode) {
            ImageReaderMode.LeftToRight -> R.string.reader_image_mode_ltr
            ImageReaderMode.RightToLeft -> R.string.reader_image_mode_rtl
            ImageReaderMode.Vertical -> R.string.reader_image_mode_vertical
            ImageReaderMode.Webtoon -> R.string.reader_image_mode_webtoon
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.reader_image_default_mode), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.reader_image_default_mode_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ImagePrefetchRow(
    value: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.reader_image_prefetch_pages), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.reader_image_prefetch_pages_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.reader_image_prefetch_pages_short, value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp),
        )
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
