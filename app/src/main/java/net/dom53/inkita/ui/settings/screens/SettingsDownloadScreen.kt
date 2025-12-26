package net.dom53.inkita.ui.settings.screens

import android.widget.Toast
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.downloadv2.DownloadPaths
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import java.io.File

@Composable
fun SettingsDownloadScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var allowMetered by remember { mutableStateOf(false) }
    var allowLowBattery by remember { mutableStateOf(false) }
    var maxConcurrent by remember { mutableStateOf(2) }
    var preferOffline by remember { mutableStateOf(true) }
    var showDownloadBadges by remember { mutableStateOf(true) }
    var retryEnabled by remember { mutableStateOf(true) }
    var maxRetries by remember { mutableStateOf(3) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var statsLoading by remember { mutableStateOf(false) }
    var statsBody by remember { mutableStateOf("") }
    var deleteAfterMarkRead by remember { mutableStateOf(false) }
    var deleteAfterReadDepth by remember { mutableStateOf(1) }

    val downloadV2Dao =
        remember {
            InkitaDatabase.getInstance(context).downloadV2Dao()
        }

    LaunchedEffect(Unit) {
        appPreferences.downloadAllowMeteredFlow.collectLatest { allowMetered = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.downloadAllowLowBatteryFlow.collectLatest { allowLowBattery = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.downloadMaxConcurrentFlow.collectLatest { maxConcurrent = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.preferOfflinePagesFlow.collectLatest { preferOffline = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.showDownloadBadgesFlow.collectLatest { showDownloadBadges = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.downloadRetryEnabledFlow.collectLatest { retryEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.downloadRetryMaxAttemptsFlow.collectLatest { maxRetries = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.deleteAfterMarkReadFlow.collectLatest { deleteAfterMarkRead = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.deleteAfterReadDepthFlow.collectLatest { deleteAfterReadDepth = it }
    }

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(net.dom53.inkita.R.string.general_back))
            }
            Text(stringResource(net.dom53.inkita.R.string.settings_downloads_title), style = MaterialTheme.typography.titleLarge)
        }

        SettingToggleRow(
            title = stringResource(net.dom53.inkita.R.string.settings_downloads_allow_metered_title),
            subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_allow_metered_subtitle),
            checked = allowMetered,
            onCheckedChange = {
                allowMetered = it
                scope.launch { appPreferences.setDownloadAllowMetered(it) }
            },
        )

        SettingToggleRow(
            title = stringResource(net.dom53.inkita.R.string.settings_downloads_allow_low_battery_title),
            subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_allow_low_battery_subtitle),
            checked = allowLowBattery,
            onCheckedChange = {
                allowLowBattery = it
                scope.launch { appPreferences.setDownloadAllowLowBattery(it) }
            },
        )

        SettingToggleRow(
            title = stringResource(net.dom53.inkita.R.string.settings_downloads_prefer_offline_title),
            subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_prefer_offline_subtitle),
            checked = preferOffline,
            onCheckedChange = {
                preferOffline = it
                scope.launch { appPreferences.setPreferOfflinePages(it) }
            },
        )

        SettingToggleRow(
            title = stringResource(net.dom53.inkita.R.string.settings_downloads_show_badges_title),
            subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_show_badges_subtitle),
            checked = showDownloadBadges,
            onCheckedChange = {
                showDownloadBadges = it
                scope.launch { appPreferences.setShowDownloadBadges(it) }
            },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(stringResource(net.dom53.inkita.R.string.settings_downloads_delete_after_section), style = MaterialTheme.typography.titleMedium)

        SettingToggleRow(
            title = stringResource(net.dom53.inkita.R.string.settings_downloads_delete_after_mark_title),
            subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_delete_after_mark_subtitle),
            checked = deleteAfterMarkRead,
            onCheckedChange = {
                deleteAfterMarkRead = it
                scope.launch { appPreferences.setDeleteAfterMarkRead(it) }
            },
        )

        SelectionRow(
            title = stringResource(net.dom53.inkita.R.string.settings_downloads_keep_recent_title),
            subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_keep_recent_subtitle),
            options = deleteAfterOptions(),
            selected = deleteAfterReadDepth,
            onSelect = { next ->
                deleteAfterReadDepth = next
                scope.launch { appPreferences.setDeleteAfterReadDepth(next) }
            },
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SettingToggleRow(
            title = stringResource(net.dom53.inkita.R.string.settings_downloads_auto_retry_title),
            subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_auto_retry_subtitle),
            checked = retryEnabled,
            onCheckedChange = {
                retryEnabled = it
                scope.launch { appPreferences.setDownloadRetryEnabled(it) }
            },
        )

        if (retryEnabled) {
            NumberStepperRow(
                title = stringResource(net.dom53.inkita.R.string.settings_downloads_max_retry_title),
                subtitle = stringResource(net.dom53.inkita.R.string.settings_downloads_max_retry_subtitle),
                value = maxRetries,
                range = 1..5,
                onChange = { next ->
                    val clamped = next.coerceIn(1, 5)
                    maxRetries = clamped
                    scope.launch { appPreferences.setDownloadRetryMaxAttempts(clamped) }
                },
            )
        }

        ConcurrentRow(
            value = maxConcurrent,
            onChange = { next ->
                val clamped = next.coerceIn(1, 6)
                maxConcurrent = clamped
                scope.launch { appPreferences.setDownloadMaxConcurrent(clamped) }
            },
        )

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(net.dom53.inkita.R.string.settings_downloads_limits_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Button(
            onClick = { showClearDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(net.dom53.inkita.R.string.settings_downloads_clear_downloaded))
        }
        OutlinedButton(
            onClick = {
                showStatsDialog = true
                statsLoading = true
                statsBody = ""
                scope.launch {
                    val body =
                        withContext(Dispatchers.IO) {
                            buildDownloadStats(context, downloadV2Dao)
                        }
                    statsBody = body
                    statsLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(net.dom53.inkita.R.string.settings_downloads_stats_button))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(net.dom53.inkita.R.string.settings_downloads_clear_downloaded_confirm_title)) },
            text = { Text(stringResource(net.dom53.inkita.R.string.settings_downloads_clear_downloaded_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                clearDownloadV2(context, downloadV2Dao)
                            }
                            Toast.makeText(context, context.getString(net.dom53.inkita.R.string.settings_downloads_clear_downloaded_toast), Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(stringResource(net.dom53.inkita.R.string.settings_downloads_clear))
                }
            },
            dismissButton = {
                Button(onClick = { showClearDialog = false }) {
                    Text(stringResource(net.dom53.inkita.R.string.general_cancel))
                }
            },
        )
    }

    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = { Text(stringResource(net.dom53.inkita.R.string.settings_downloads_stats_title)) },
            text = {
                if (statsLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(statsBody)
                }
            },
            confirmButton = {
                Button(onClick = { showStatsDialog = false }) {
                    Text(stringResource(net.dom53.inkita.R.string.general_back))
                }
            },
        )
    }
}

private suspend fun clearDownloadV2(
    context: android.content.Context,
    downloadV2Dao: DownloadV2Dao,
) {
    val root = DownloadPaths.baseDir(context)
    if (root.exists()) {
        runCatching { root.deleteRecursively() }
    }
    downloadV2Dao.clearAllItems()
    downloadV2Dao.clearAllJobs()
}

private suspend fun buildDownloadStats(
    context: android.content.Context,
    downloadV2Dao: DownloadV2Dao,
): String {
    val completedItems = downloadV2Dao.getItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
    val pendingItems = downloadV2Dao.getItemsByStatus(DownloadedItemV2Entity.STATUS_PENDING)
    val runningItems = downloadV2Dao.getItemsByStatus(DownloadedItemV2Entity.STATUS_RUNNING)
    val failedItems = downloadV2Dao.getItemsByStatus(DownloadedItemV2Entity.STATUS_FAILED)

    val pagesCompleted = completedItems.count { it.type == DownloadedItemV2Entity.TYPE_PAGE }
    val filesCompleted = completedItems.count { it.type == DownloadedItemV2Entity.TYPE_FILE }
    val seriesCount = completedItems.mapNotNull { it.seriesId }.toSet().size
    val volumeCount = completedItems.mapNotNull { it.volumeId }.toSet().size
    val chapterCount = completedItems.mapNotNull { it.chapterId }.toSet().size

    val jobTotal = downloadV2Dao.countJobs()
    val jobPending = downloadV2Dao.countJobsByStatus(DownloadJobV2Entity.STATUS_PENDING)
    val jobRunning = downloadV2Dao.countJobsByStatus(DownloadJobV2Entity.STATUS_RUNNING)
    val jobFailed = downloadV2Dao.countJobsByStatus(DownloadJobV2Entity.STATUS_FAILED)
    val jobCompleted = downloadV2Dao.countJobsByStatus(DownloadJobV2Entity.STATUS_COMPLETED)

    val itemsTotal = completedItems.size + pendingItems.size + runningItems.size + failedItems.size
    val diskBytes = dirSize(DownloadPaths.baseDir(context))
    val sizeText = Formatter.formatFileSize(context, diskBytes)

    return buildString {
        appendLine(context.getString(net.dom53.inkita.R.string.settings_downloads_stats_series, seriesCount))
        appendLine(context.getString(net.dom53.inkita.R.string.settings_downloads_stats_volumes, volumeCount))
        appendLine(context.getString(net.dom53.inkita.R.string.settings_downloads_stats_chapters, chapterCount))
        appendLine(context.getString(net.dom53.inkita.R.string.settings_downloads_stats_pages, pagesCompleted))
        appendLine(context.getString(net.dom53.inkita.R.string.settings_downloads_stats_files, filesCompleted))
        appendLine(
            context.getString(
                net.dom53.inkita.R.string.settings_downloads_stats_jobs,
                jobTotal,
                jobPending,
                jobRunning,
                jobFailed,
                jobCompleted,
            ),
        )
        appendLine(
            context.getString(
                net.dom53.inkita.R.string.settings_downloads_stats_items,
                itemsTotal,
                pendingItems.size,
                runningItems.size,
                failedItems.size,
                completedItems.size,
            ),
        )
        appendLine(context.getString(net.dom53.inkita.R.string.settings_downloads_stats_size, sizeText))
    }.trimEnd()
}

private fun dirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    return dir
        .walkBottomUp()
        .filter { it.isFile }
        .sumOf { it.length() }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConcurrentRow(
    value: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(net.dom53.inkita.R.string.settings_downloads_max_concurrent_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(net.dom53.inkita.R.string.settings_downloads_max_concurrent_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onChange(value - 1) }) {
                Text("-", style = MaterialTheme.typography.titleMedium)
            }
            Text(value.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onChange(value + 1) }) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun NumberStepperRow(
    title: String,
    subtitle: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(range.first)) }) {
                Text("-", style = MaterialTheme.typography.titleMedium)
            }
            Text(value.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onChange((value + 1).coerceAtMost(range.last)) }) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SelectionRow(
    title: String,
    subtitle: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Select"

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Open menu")
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

private fun deleteAfterOptions(): List<Pair<Int, String>> = (1..5).map { value -> value to "After ${ordinalLabel(value)} read" }

private fun ordinalLabel(value: Int): String =
    when (value) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${value}th"
    }
