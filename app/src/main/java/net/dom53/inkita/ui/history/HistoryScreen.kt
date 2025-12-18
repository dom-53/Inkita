package net.dom53.inkita.ui.history

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import net.dom53.inkita.R
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.ReadHistoryEventDto
import net.dom53.inkita.ui.common.seriesCoverUrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun HistoryScreen(appPreferences: AppPreferences) {
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", userId = 0),
    )
    val isLoading = remember { mutableStateOf(true) }
    val error = remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateOf<List<ReadHistoryEventDto>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(config.serverUrl, config.apiKey) {
        if (!config.isConfigured) {
            isLoading.value = false
            error.value = context.resources.getString(R.string.general_server_or_api_key_not_set)
            return@LaunchedEffect
        }
        if (!NetworkUtils.isOnline(context)) {
            isLoading.value = false
            error.value = context.getString(R.string.general_offline)
            return@LaunchedEffect
        }
        isLoading.value = true
        error.value = null
        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
        val userIdToUse =
            if (config.userId > 0) {
                config.userId
            } else {
                val acc = runCatching { api.getAccount() }.getOrNull()
                val accId = acc?.body()?.id ?: 0
                if (accId > 0) {
                    appPreferences.updateUserId(accId)
                }
                accId
            }
        runCatching { api.getReadingHistory(userId = userIdToUse) }
            .onSuccess { resp ->
                if (resp.isSuccessful) {
                    history.value = resp.body().orEmpty()
                } else {
                    error.value = context.getString(R.string.history_error_http, resp.code())
                }
            }.onFailure { e -> error.value = e.message }
        isLoading.value = false
    }

    val withDate: List<Pair<LocalDate?, ReadHistoryEventDto>> =
        history.value.map { evt ->
            val rawDate = evt.readDate ?: evt.readDateUtc
            val date = rawDate?.take(10)?.let { v -> runCatching { LocalDate.parse(v) }.getOrNull() }
            date to evt
        }
    val groupedByDate: Map<LocalDate?, List<DaySeriesItem>> =
        withDate
            .groupBy { it.first }
            .mapValues { (_, list) ->
                list
                    .groupBy { it.second.seriesId ?: it.second.seriesName ?: it.hashCode() }
                    .values
                    .map { events ->
                        val sorted = events.sortedBy { it.second.readDate ?: it.second.readDateUtc ?: "" }
                        val first = sorted.first().second
                        val last = sorted.last().second
                        DaySeriesItem(
                            seriesId = last.seriesId,
                            seriesName = last.seriesName.orEmpty(),
                            chapterStart = first.chapterNumber,
                            chapterEnd = last.chapterNumber,
                            count = events.size,
                            timeStart = first.readDate ?: first.readDateUtc,
                            timeEnd = last.readDate ?: last.readDateUtc,
                        )
                    }
            }
    val sortedKeys: List<LocalDate?> =
        groupedByDate.keys
            .filterIsInstance<LocalDate>()
            .sortedDescending() + groupedByDate.keys.filter { it == null }

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading.value -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            error.value != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error.value ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (groupedByDate.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.history_no_history))
                            }
                        }
                    } else {
                        sortedKeys.forEach { dateKey ->
                            val itemsForDay = groupedByDate[dateKey].orEmpty()
                            item(key = "header-$dateKey") {
                                Text(
                                    text = dayLabel(dateKey, context),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(itemsForDay, key = { it.seriesId ?: it.seriesName.hashCode() }) { item ->
                                HistoryRow(
                                    item = item,
                                    config = config,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: DaySeriesItem,
    config: AppConfig,
) {
    val seriesId = item.seriesId
    val cover = seriesId?.let { seriesCoverUrl(config, it) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.25f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = cover,
                contentDescription = item.seriesName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    is AsyncImagePainter.State.Error -> Text(stringResource(R.string.history_no_cover), style = MaterialTheme.typography.labelSmall)
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                item.seriesName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val chapterRange =
                when {
                    item.chapterStart != null && item.chapterEnd != null && item.chapterStart != item.chapterEnd ->
                        stringResource(
                            R.string.history_chapter_range,
                            chapterLabel(item.chapterStart),
                            chapterLabel(item.chapterEnd),
                        )
                    item.chapterEnd != null -> stringResource(R.string.history_chapter_single, chapterLabel(item.chapterEnd))
                    else -> null
                }
            chapterRange?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val timeRange = formatTimeRange(item.timeStart, item.timeEnd)
            if (timeRange != null) {
                Text(timeRange, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.history_entries_count, item.count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun dayLabel(
    date: LocalDate?,
    context: Context,
): String {
    if (date == null) return context.resources.getString(R.string.general_unknown_date)
    val today = LocalDate.now()
    return when (ChronoUnit.DAYS.between(date, today)) {
        0L -> context.resources.getString(R.string.general_today)
        1L -> context.resources.getString(R.string.general_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.getDefault()))
    }
}

private fun chapterLabel(num: Float): String = if (num % 1f == 0f) num.toInt().toString() else String.format(Locale.getDefault(), "%.1f", num)

@Suppress("UnusedPrivateProperty")
private fun formatTimeRange(
    start: String?,
    end: String?,
): String? {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    val startTime = start?.takeIf { it.length >= 16 }?.substring(11, 16)
    val endTime = end?.takeIf { it.length >= 16 }?.substring(11, 16)
    return when {
        startTime != null && endTime != null -> "$startTime – $endTime"
        endTime != null -> endTime
        startTime != null -> startTime
        else -> null
    }
}

private data class DaySeriesItem(
    val seriesId: Int?,
    val seriesName: String,
    val chapterStart: Float?,
    val chapterEnd: Float?,
    val count: Int,
    val timeStart: String?,
    val timeEnd: String?,
)
