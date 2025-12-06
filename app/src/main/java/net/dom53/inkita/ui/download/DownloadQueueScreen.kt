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
import androidx.compose.ui.unit.dp
import net.dom53.inkita.data.local.db.entity.DownloadTaskEntity
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity
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
            DownloadTaskEntity.STATE_PENDING,
            DownloadTaskEntity.STATE_RUNNING,
            DownloadTaskEntity.STATE_PAUSED,
            DownloadTaskEntity.STATE_FAILED,
            DownloadTaskEntity.STATE_CANCELED,
        )
    val completedStates = listOf(DownloadTaskEntity.STATE_COMPLETED)
    val visibleTasks =
        when (selectedTab) {
            0 -> tasks.filter { it.state in queueStates }
            1 -> tasks.filter { it.state in completedStates }
            else -> emptyList()
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineSmall)
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Queue", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Completed", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                Text("Downloaded", modifier = Modifier.padding(12.dp))
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
                        key = { _, item -> "${item.chapterId}-${item.page}" },
                    ) { _, page ->
                        DownloadedRow(
                            page = page,
                            onOpen = {
                                val uri =
                                    if (page.htmlPath.startsWith("file://") || page.htmlPath.startsWith("content://")) {
                                        Uri.parse(page.htmlPath)
                                    } else {
                                        Uri.fromFile(File(page.htmlPath))
                                    }
                                val intent =
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, "text/html")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(intent) }
                            },
                            onDelete = { viewModel.deleteDownloaded(page.chapterId, page.page) },
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
                            Text("Clear completed")
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
    task: DownloadTaskEntity,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    val labelType =
        when (task.type) {
            DownloadTaskEntity.TYPE_PAGES -> "Pages"
            DownloadTaskEntity.TYPE_VOLUME -> "Volume"
            DownloadTaskEntity.TYPE_SERIES -> "Series"
            else -> task.type
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Series ${task.seriesId} · $labelType", style = MaterialTheme.typography.bodyLarge)
        Text("Chapter ${task.chapterId ?: "-"} pages ${task.pageStart ?: "-"}..${task.pageEnd ?: "-"}")
        Text("State: ${task.state}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Created: ${formatDate(task.createdAt)}", style = MaterialTheme.typography.bodySmall)
            Text("Updated: ${formatDate(task.updatedAt)}", style = MaterialTheme.typography.bodySmall)
        }
        if (task.total > 0) {
            val pct = (task.progress * 100f / task.total).toInt().coerceIn(0, 100)
            Text("Progress: ${task.progress}/${task.total} ($pct%)", style = MaterialTheme.typography.bodySmall)
        }
        if (task.bytesTotal > 0) {
            val pct = (task.bytes * 100f / task.bytesTotal).toInt().coerceIn(0, 100)
            Text("Bytes: ${formatBytes(task.bytes)} / ${formatBytes(task.bytesTotal)} ($pct%)", style = MaterialTheme.typography.bodySmall)
        }
        task.error?.takeIf { it.isNotBlank() }?.let { err ->
            Text("Error: $err", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (task.state) {
                DownloadTaskEntity.STATE_PENDING,
                DownloadTaskEntity.STATE_RUNNING,
                -> {
                    Button(onClick = onPause) {
                        Text("Pause")
                    }
                    Button(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Cancel")
                    }
                }
                DownloadTaskEntity.STATE_PAUSED -> {
                    Button(onClick = onResume) {
                        Text("Resume")
                    }
                }
                DownloadTaskEntity.STATE_FAILED -> {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                    Button(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Cancel")
                    }
                }
                DownloadTaskEntity.STATE_CANCELED -> {
                    Button(onClick = onClear) {
                        Text("Clear")
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun DownloadedRow(
    page: DownloadedPageEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Series ${page.seriesId} · Chapter ${page.chapterId} · Page ${page.page}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Status: ${page.status}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Updated: ${formatDate(page.updatedAt)}", style = MaterialTheme.typography.bodySmall)
            Text("Size: ${formatBytes(page.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
        }
        Text(page.htmlPath, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onOpen) {
                Text("Open")
            }
            Button(onClick = onDelete, modifier = Modifier.padding(start = 8.dp)) {
                Text("Delete")
            }
        }
    }
}
