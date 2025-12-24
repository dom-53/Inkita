package net.dom53.inkita.ui.download

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import net.dom53.inkita.R
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormatter by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

private fun formatDate(ts: Long): String = if (ts > 0) dateFormatter.format(Date(ts)) else "-"

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

@Composable
fun DownloadQueueScreen(viewModel: DownloadQueueViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val downloaded by viewModel.downloaded.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

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
            0 -> tasks.filter { it.status in queueStates }
            1 -> tasks.filter { it.status in completedStates }
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
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text(
                    "${stringResource(R.string.general_queue)} ($queueCount)",
                    modifier = Modifier.padding(12.dp),
                )
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text(
                    "${stringResource(R.string.general_completed)} ($completedCount)",
                    modifier = Modifier.padding(12.dp),
                )
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text(
                    "${stringResource(R.string.general_downloaded)} ($downloadedCount)",
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        when (selectedTab) {
            2 -> {
                if (downloaded.isEmpty()) {
                    EmptyState(text = stringResource(R.string.download_empty_downloaded))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            downloaded,
                            key = { _, item -> item.id },
                        ) { _, page ->
                            DownloadedRow(
                                page = page,
                                onOpen = {
                                    val path = page.localPath ?: return@DownloadedRow
                                    val uri =
                                        if (path.startsWith("file://") || path.startsWith("content://")) {
                                            Uri.parse(path)
                                        } else {
                                            Uri.fromFile(File(path))
                                        }
                                    val mime =
                                        if (page.type == DownloadedItemV2Entity.TYPE_FILE || path.endsWith(".pdf")) {
                                            "application/pdf"
                                        } else {
                                            "text/html"
                                        }
                                    val intent =
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(uri, mime)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    runCatching { context.startActivity(intent) }
                                },
                                onDelete = { viewModel.deleteDownloaded(page.id) },
                            )
                        }
                    }
                }
            }
            else -> {
                if (selectedTab == 1) {
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
                        if (selectedTab == 0) {
                            stringResource(R.string.download_empty_queue)
                        } else {
                            stringResource(R.string.download_empty_completed)
                        }
                    EmptyState(text = label)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visibleTasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val seriesLabel = task.seriesId?.toString() ?: "-"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.download_item_title, seriesLabel, labelType),
                    style = MaterialTheme.typography.titleMedium,
                )
                DownloadStatusChip(status = task.status)
            }
            val chapterLabel = task.chapterId?.toString() ?: "-"
            val total = task.totalItems ?: 0
            val pageEnd = if (total > 0) total - 1 else "-"
            Text(
                stringResource(R.string.download_item_chapter_pages, chapterLabel, 0, pageEnd),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            if ((task.totalItems ?: 0) > 0) {
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
    page: DownloadedItemV2Entity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(
                    R.string.download_item_v2_title,
                    page.id,
                    page.chapterId ?: "-",
                    page.page ?: "-",
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            DownloadStatusChip(status = page.status)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.download_completed_updated, formatDate(page.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.download_completed_size, formatBytes(page.bytes ?: 0L)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                page.localPath ?: "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
