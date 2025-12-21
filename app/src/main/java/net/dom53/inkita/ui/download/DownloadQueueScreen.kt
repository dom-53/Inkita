package net.dom53.inkita.ui.download

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
                Text(stringResource(R.string.general_queue), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text(stringResource(R.string.general_completed), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text(stringResource(R.string.general_downloaded), modifier = Modifier.padding(12.dp))
            }
        }
        when (selectedTab) {
            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                val intent =
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, "text/html")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(intent) }
                            },
                            onDelete = { viewModel.deleteDownloaded(page.id) },
                        )
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val seriesLabel = task.seriesId?.toString() ?: "-"
        Text(
            stringResource(R.string.download_item_title, seriesLabel, labelType),
            style = MaterialTheme.typography.bodyLarge,
        )
        val chapterLabel = task.chapterId?.toString() ?: "-"
        val total = task.totalItems ?: 0
        val pageEnd = if (total > 0) total - 1 else "-"
        Text(stringResource(R.string.download_item_chapter_pages, chapterLabel, 0, pageEnd))
        Text(stringResource(R.string.download_item_state, task.status))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (task.createdAt > 0) {
                Text(
                    stringResource(R.string.download_item_created, formatDate(task.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (task.updatedAt > 0) {
                Text(
                    stringResource(R.string.download_item_updated, formatDate(task.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if ((task.totalItems ?: 0) > 0) {
            val total = task.totalItems ?: 0
            val progress = task.completedItems ?: 0
            val pct = (progress * 100f / total).toInt().coerceIn(0, 100)
            Text(
                stringResource(R.string.download_item_progress, progress, total, pct),
                style = MaterialTheme.typography.bodySmall,
            )
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
                    Button(onClick = onPause) {
                        Text(stringResource(R.string.download_action_pause))
                    }
                    Button(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.general_cancel))
                    }
                }
                DownloadJobV2Entity.STATUS_PAUSED -> {
                    Button(onClick = onResume) {
                        Text(stringResource(R.string.download_action_resume))
                    }
                }
                DownloadJobV2Entity.STATUS_FAILED -> {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.download_action_retry))
                    }
                    Button(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.general_cancel))
                    }
                }
                DownloadJobV2Entity.STATUS_CANCELED -> {
                    Button(onClick = onClear) {
                        Text(stringResource(R.string.download_action_clear))
                    }
                }
                else -> Unit
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(
                R.string.download_item_v2_title,
                page.id,
                page.chapterId ?: "-",
                page.page ?: "-",
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(stringResource(R.string.download_completed_status, page.status))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.download_completed_updated, formatDate(page.updatedAt)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.download_completed_size, formatBytes(page.bytes ?: 0L)), style = MaterialTheme.typography.bodySmall)
        }
        Text(page.localPath ?: "-", style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onOpen) {
                Text(stringResource(R.string.download_completed_open))
            }
            Button(onClick = onDelete, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.download_completed_delete))
            }
        }
    }
}
