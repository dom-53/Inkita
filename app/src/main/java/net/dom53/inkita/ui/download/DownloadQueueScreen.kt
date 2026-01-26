package net.dom53.inkita.ui.download

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.dom53.inkita.R
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

private fun formatDate(ts: Long): String = if (ts > 0) dateFormatter.format(Date(ts)) else "—"

private fun formatBytes(value: Long): String {
    if (value <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = value.toDouble()
    var idx = 0
    while (v >= 1024 && idx < units.lastIndex) {
        v /= 1024
        idx++
    }
    return String.format(Locale.getDefault(), "%.1f %s", v, units[idx])
}

enum class DownloadTabs {
    TAB_QUEUE, TAB_COMPLETED, TAB_DOWNLOADED
}

@Composable
fun DownloadQueueScreen(viewModel: DownloadQueueViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val downloaded by viewModel.downloaded.collectAsState()
    val downloadedBySeries = downloaded
        .filter{it.seriesId != null}
        .groupBy { it.seriesId ?: 0 }
        .map { Pair(it.key, it.value) }
    val lookup by viewModel.lookup.collectAsState()
    var selectedTab by remember { mutableStateOf(DownloadTabs.TAB_QUEUE) }

    val queueStates =
        listOf(
            DownloadJobV2Entity.STATUS_PENDING,
            DownloadJobV2Entity.STATUS_RUNNING,
            DownloadJobV2Entity.STATUS_PAUSED,
            DownloadJobV2Entity.STATUS_FAILED,
            DownloadJobV2Entity.STATUS_CANCELED,
        )
    val completedStates = listOf(DownloadJobV2Entity.STATUS_COMPLETED)
    val queueCount = tasks.count { it.status in queueStates }
    val completedCount = tasks.count { it.status in completedStates }
    val downloadedCount = downloaded.size
    val visibleTasks =
        when (selectedTab) {
            DownloadTabs.TAB_QUEUE -> tasks.filter { it.status in queueStates }
            DownloadTabs.TAB_COMPLETED -> tasks.filter { it.status in completedStates }
            else -> emptyList()
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.download_screen_title), style = MaterialTheme.typography.headlineSmall)
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(selected = selectedTab == DownloadTabs.TAB_QUEUE, onClick = { selectedTab = DownloadTabs.TAB_QUEUE }) {
                Text(
                    "${stringResource(R.string.general_queue)} ($queueCount)",
                    modifier = Modifier.padding(12.dp),
                )
            }
            Tab(selected = selectedTab == DownloadTabs.TAB_COMPLETED, onClick = { selectedTab = DownloadTabs.TAB_COMPLETED }) {
                Text(
                    "${stringResource(R.string.general_completed)} ($completedCount)",
                    modifier = Modifier.padding(12.dp),
                )
            }
            Tab(selected = selectedTab == DownloadTabs.TAB_DOWNLOADED, onClick = { selectedTab = DownloadTabs.TAB_DOWNLOADED }) {
                Text(
                    "${stringResource(R.string.general_downloaded)} ($downloadedCount)",
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        when (selectedTab) {
            DownloadTabs.TAB_DOWNLOADED -> {
                if (downloaded.isEmpty()) {
                    EmptyState(text = stringResource(R.string.download_empty_downloaded))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(0.dp, 4.dp)
                    ) {
                        items(downloadedBySeries, key = { it.first }) { downloads ->
                            DownloadedRow(
                                seriesId = downloads.first,
                                downloads = downloads.second,
                                lookup = lookup,
                                deleteDownloaded = viewModel::deleteDownloaded
                            )
                        }
                    }
                }
            }
            else -> {
                if (selectedTab == DownloadTabs.TAB_COMPLETED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { viewModel.clearCompleted() }) {
                            Text(stringResource(R.string.download_clear_completed))
                        }
                    }
                }
                if (visibleTasks.isEmpty()) {
                    val label =
                        if (selectedTab == DownloadTabs.TAB_QUEUE) {
                            stringResource(R.string.download_empty_queue)
                        } else {
                            stringResource(R.string.download_empty_completed)
                        }
                    EmptyState(text = label)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(0.dp, 4.dp)
                    ) {
                        items(visibleTasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                lookup = lookup,
                                onCancel = { viewModel.cancelTask(task.id) },
                                onPause = { viewModel.pauseTask(task.id) },
                                onResume = { viewModel.resumeTask(task.id) },
                                onRetry = { viewModel.retryTask(task.id) },
                                onClear = { viewModel.deleteTask(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: DownloadJobV2Entity,
    lookup: DownloadLookup,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    val labelType =
        when (task.type) {
            DownloadJobV2Entity.TYPE_CHAPTER -> stringResource(R.string.general_chapters)
            DownloadJobV2Entity.TYPE_VOLUME -> stringResource(R.string.download_type_volume)
            DownloadJobV2Entity.TYPE_SERIES -> stringResource(R.string.general_series)
            else -> task.type
        }
    val seriesLabel = lookup.seriesNames[task.seriesId] ?: "—"
    val volumeLabel = lookup.volumeNames[task.volumeId]
    val chapterLabelText = lookup.chapterTitles[task.chapterId]
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.download_item_title, seriesLabel, labelType),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DownloadStatusChip(status = task.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (volumeLabel != null) {
                    Text(
                        text = "$volumeLabel:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (chapterLabelText != null) {
                    Text(
                        text = chapterLabelText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (task.createdAt > 0) {
                    Text(
                        stringResource(R.string.download_item_created, formatDate(task.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.updatedAt > 0) {
                    Text(
                        stringResource(R.string.download_item_updated, formatDate(task.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if ((task.totalItems ?: 0) > 0 && task.totalItems != task.completedItems) {
                val total = task.totalItems ?: 0
                val progress = task.completedItems ?: 0
                val pct = (progress * 100f / total).toInt().coerceIn(0, 100)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.download_item_progress, progress, total, pct),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            task.error?.takeIf { it.isNotBlank() }?.let { err ->
                Text(
                    stringResource(R.string.download_item_error, err),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (task.status) {
                    DownloadJobV2Entity.STATUS_PENDING,
                    DownloadJobV2Entity.STATUS_RUNNING,
                    -> {
                        FilledTonalButton(onClick = onPause) {
                            Text(stringResource(R.string.download_action_pause))
                        }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(R.string.general_cancel))
                        }
                    }
                    DownloadJobV2Entity.STATUS_PAUSED -> {
                        FilledTonalButton(onClick = onResume) {
                            Text(stringResource(R.string.download_action_resume))
                        }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(R.string.general_cancel))
                        }
                    }
                    DownloadJobV2Entity.STATUS_FAILED -> {
                        FilledTonalButton(onClick = onRetry) {
                            Text(stringResource(R.string.download_action_retry))
                        }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(R.string.general_cancel))
                        }
                    }
                    DownloadJobV2Entity.STATUS_CANCELED -> {
                        OutlinedButton(onClick = onClear) {
                            Text(stringResource(R.string.download_action_clear))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun DownloadedRow(
    seriesId: Int,
    downloads: List<DownloadedItemV2Entity>,
    lookup: DownloadLookup,
    deleteDownloaded: (itemId: Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val seriesLabel = lookup.seriesNames[seriesId] ?: "—"
                Text(
                    stringResource(R.string.download_complete_series_group, seriesLabel, downloads.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    modifier = Modifier.indication(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false)
                    ),
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.download_completed_toggle_description)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    downloads.forEach { item ->
                        DownloadedItem(
                            item = item,
                            lookup = lookup,
                            onOpen = {
                                val path = item.localPath ?: return@DownloadedItem
                                val uri =
                                    if (path.startsWith("file://") || path.startsWith("content://")) {
                                        path.toUri()
                                    } else {
                                        Uri.fromFile(File(path))
                                    }
                                val mime =
                                    if (item.type == DownloadedItemV2Entity.TYPE_FILE) {
                                        if (path.endsWith(".pdf")) {
                                            "application/pdf"
                                        } else {
                                            "application/octet-stream"
                                        }
                                    } else {
                                        "text/html"
                                    }
                                val intent =
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, mime)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(intent) }
                            },
                            onDelete = { deleteDownloaded(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedItem(
    item: DownloadedItemV2Entity,
    lookup: DownloadLookup,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val volumeLabel = lookup.volumeNames[item.volumeId]
    val chapterLabel = lookup.chapterTitles[item.chapterId]

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (volumeLabel != null) {
                Text(
                    text = volumeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chapterLabel != null) {
                Text(
                    text = chapterLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.download_completed_size, formatBytes(item.bytes ?: 0L)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.download_completed_updated, formatDate(item.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.localPath != null) {
            Text(
                item.localPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onOpen) {
                Text(stringResource(R.string.download_completed_open))
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.download_completed_delete))
            }
        }
    }
}

@Composable
private fun DownloadStatusChip(status: String) {
    val (bg, fg) =
        when (status) {
            DownloadJobV2Entity.STATUS_RUNNING -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            DownloadJobV2Entity.STATUS_PENDING -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
            DownloadJobV2Entity.STATUS_PAUSED -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
            DownloadJobV2Entity.STATUS_FAILED -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
            DownloadJobV2Entity.STATUS_CANCELED -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.onSurface
            DownloadJobV2Entity.STATUS_COMPLETED -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    AssistChip(
        onClick = {},
        label = { Text(status) },
        enabled = false,
        colors =
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                containerColor = bg,
                labelColor = fg,
                disabledContainerColor = bg,
                disabledLabelColor = fg,
            ),
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
