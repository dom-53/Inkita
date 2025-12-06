package net.dom53.inkita.ui.settings.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.dom53.inkita.R
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.DateTimePagesReadOnADayCountDto
import net.dom53.inkita.data.api.dto.Int32StatCountDto
import net.dom53.inkita.data.api.dto.UserReadStatisticsDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsStatsScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", username = "", apiKey = "", token = "", refreshToken = "", userId = 0),
    )
    val isLoading = remember { mutableStateOf(true) }
    val error = remember { mutableStateOf<String?>(null) }
    val userStats = remember { mutableStateOf<UserReadStatisticsDto?>(null) }
    val readingByDay = remember { mutableStateOf<List<DateTimePagesReadOnADayCountDto>>(emptyList()) }
    val pagesPerYear = remember { mutableStateOf<List<Int32StatCountDto>>(emptyList()) }
    val wordsPerYear = remember { mutableStateOf<List<Int32StatCountDto>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(config.token) {
        if (!config.isConfigured) {
            isLoading.value = false
            error.value = context.resources.getString(R.string.general_server_or_token_not_set)
            return@LaunchedEffect
        }
        isLoading.value = true
        error.value = null
        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.token)
        runCatching {
            val uid =
                if (config.userId > 0) {
                    config.userId
                } else {
                    val acc = api.getAccount()
                    val accId = acc.body()?.id ?: 0
                    if (accId > 0) appPreferences.updateUserId(accId)
                    accId
                }
            val statsResp = api.getUserReadStats(userId = uid)
            val dailyResp = api.getReadingCountByDay(userId = uid, days = 30)
            val pagesResp = api.getPagesPerYear(userId = uid)
            val wordsResp = api.getWordsPerYear(userId = uid)
            userStats.value = statsResp.body()
            readingByDay.value = dailyResp.body().orEmpty()
            pagesPerYear.value = pagesResp.body().orEmpty()
            wordsPerYear.value = wordsResp.body().orEmpty()
        }.onFailure { e ->
            error.value = e.message ?: context.resources.getString(R.string.stats_failed_stat_load)
        }
        isLoading.value = false
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
            }
            Text(stringResource(R.string.settings_item_stats), style = MaterialTheme.typography.headlineSmall)
        }

        error.value?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error) }
        }

        if (isLoading.value) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        userStats.value?.let { stats ->
            item {
                StatsCard(
                    title = stringResource(R.string.general_reading_status_unread),
                    icon = Icons.Filled.TrendingUp,
                    description = summaryText(context, stats),
                    endpoints = listOf("/api/Stats/user/{userId}/read"),
                )
            }
        }

        if (readingByDay.value.isNotEmpty()) {
            val formatter = DateTimeFormatter.ofPattern("d.M.", Locale.getDefault())
            val normalized =
                readingByDay.value
                    .mapNotNull { item ->
                        val date =
                            item.value?.take(10)?.let { v ->
                                runCatching { LocalDate.parse(v) }.getOrNull()
                            }
                        val count = item.count?.toInt() ?: 0
                        date?.let { it to count }
                    }.groupBy { it.first }
                    .map { (date, entries) -> date to entries.sumOf { it.second } }
                    .sortedBy { it.first }
                    .map { it.first.format(formatter) to it.second }
            val maxCount = normalized.maxOfOrNull { it.second } ?: 1
            item {
                Text(stringResource(R.string.stats_activity_last_30d), style = MaterialTheme.typography.titleMedium)
            }
            items(normalized) { (label, count) ->
                BarRow(
                    label = label,
                    value = count,
                    max = maxCount,
                )
            }
        }

        if (pagesPerYear.value.isNotEmpty()) {
            val max = (pagesPerYear.value.maxOfOrNull { it.count ?: 0 } ?: 1L).toInt()
            item { Text(stringResource(R.string.stats_pages_per_yer), style = MaterialTheme.typography.titleMedium) }
            items(pagesPerYear.value) { item ->
                BarRow(
                    label = item.value?.toString() ?: "",
                    value = (item.count ?: 0).toInt(),
                    max = max,
                )
            }
        }

        if (wordsPerYear.value.isNotEmpty()) {
            val max = (wordsPerYear.value.maxOfOrNull { it.count ?: 0 } ?: 1L).toInt()
            item { Text(stringResource(R.string.stats_words_per_yer), style = MaterialTheme.typography.titleMedium) }
            items(wordsPerYear.value) { item ->
                BarRow(
                    label = item.value?.toString() ?: "",
                    value = (item.count ?: 0).toInt(),
                    max = max,
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.stats_source_kavita_api_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    endpoints: List<String>,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            endpoints.forEach { ep ->
                Text(
                    text = ep,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BarRow(
    label: String,
    value: Int,
    max: Int,
) {
    val clampedMax = if (max <= 0) 1 else max
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value.toString(), style = MaterialTheme.typography.labelSmall)
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val ratio = value.toFloat() / clampedMax.toFloat()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun summaryText(
    context: Context,
    stats: UserReadStatisticsDto,
): String {
    val pages = stats.totalPagesRead ?: 0
    val words = stats.totalWordsRead ?: 0
    val chapters = stats.chaptersRead ?: 0
    val hours = stats.timeSpentReading?.div(3600.0) ?: 0.0
    val avgWeek = stats.avgHoursPerWeekSpentReading ?: 0.0
    val hoursText = String.format(Locale.getDefault(), "%.1f", hours)
    val weekText = String.format(Locale.getDefault(), "%.1f", avgWeek)
    return "${context.resources.getString(
        R.string.general_pages,
    )}: $pages | ${context.resources.getString(
        R.string.general_words,
    )}: $words | ${context.resources.getString(
        R.string.general_chapters,
    )}: $chapters\n${context.resources.getString(
        R.string.general_reading_time,
    )}: $hoursText h | ${context.resources.getString(R.string.general_weekly)}: $weekText h"
}
