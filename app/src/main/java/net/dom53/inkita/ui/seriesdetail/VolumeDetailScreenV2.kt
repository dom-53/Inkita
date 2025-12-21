package net.dom53.inkita.ui.seriesdetail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.ui.common.seriesCoverUrl
import net.dom53.inkita.ui.common.volumeCoverUrl
import net.dom53.inkita.ui.seriesdetail.utils.cleanHtml

@Composable
fun VolumeDetailScreenV2(
    volumeId: Int,
    appPreferences: AppPreferences,
    readerReturn: net.dom53.inkita.ui.reader.ReaderReturn? = null,
    onConsumeReaderReturn: () -> Unit = {},
    onOpenReader: (chapterId: Int, page: Int, seriesId: Int, volumeId: Int, formatId: Int?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val payload = remember(volumeId) { VolumeDetailCache.get(volumeId) }
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    var coverExpanded by remember { mutableStateOf(false) }
    var summaryExpanded by remember { mutableStateOf(false) }

    if (payload == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = net.dom53.inkita.R.string.volume_detail_not_found),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    var volumeState by remember(volumeId) { mutableStateOf(payload.volume) }
    LaunchedEffect(readerReturn) {
        if (readerReturn != null) {
            val cfg = appPreferences.configFlow.first()
            if (cfg.isConfigured) {
                val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
                val resp = api.getVolumeById(volumeId)
                if (resp.isSuccessful) {
                    resp.body()?.let { updated ->
                        val merged =
                            updated.copy(
                                name = payload.volume.name,
                                title = payload.volume.title,
                            )
                        volumeState = merged
                        VolumeDetailCache.put(payload.copy(volume = merged))
                    }
                }
            }
            onConsumeReaderReturn()
        }
    }

    val volume = volumeState
    val coverUrl = volumeCoverUrl(config, volume.id) ?: seriesCoverUrl(config, payload.seriesId)
    val chapterList = volume.chapters.orEmpty()
    val summary = chapterList.firstOrNull { !it.summary.isNullOrBlank() }?.summary
    val releaseYear =
        chapterList
            .firstOrNull { !it.releaseDate.isNullOrBlank() }
            ?.releaseDate
            ?.let { formatYear(it) }
    val wordCount =
        chapterList
            .mapNotNull { it.wordCount }
            .sum()
            .takeIf { it > 0 }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null)
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    CoverImage(
                        coverUrl = coverUrl,
                        context = context,
                        modifier =
                            Modifier
                                .width(140.dp)
                                .aspectRatio(2f / 3f)
                                .clickable { coverExpanded = true },
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val title =
                            volume.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(
                                    id = net.dom53.inkita.R.string.series_detail_volume_fallback,
                                    volumeId,
                                )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = net.dom53.inkita.R.string.volume_detail_time_label,
                                    formatHoursRangeInt(volume.minHoursToRead, volume.maxHoursToRead) ?: "-",
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                "${stringResource(id = net.dom53.inkita.R.string.general_words)}: " +
                                    (wordCount?.let { formatCount(it) } ?: "-"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = net.dom53.inkita.R.string.volume_detail_release_year_label,
                                    releaseYear ?: "-",
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                SummarySectionV2(
                    summary = cleanHtml(summary) ?: summary,
                    genres = emptyList(),
                    tags = emptyList(),
                    expanded = summaryExpanded,
                    onToggle = { summaryExpanded = !summaryExpanded },
                )
                val volText =
                    volumeNumberText(volume)?.let {
                        stringResource(id = net.dom53.inkita.R.string.series_detail_vol_short, it)
                    }
                        ?: stringResource(id = net.dom53.inkita.R.string.series_detail_vol_short_plain)
                val pagesRead = volume.pagesRead ?: 0
                val pagesTotal = volume.pages
                val buttonLabel =
                    when {
                        pagesTotal != null && pagesRead >= pagesTotal ->
                            stringResource(id = net.dom53.inkita.R.string.volume_detail_re_read)
                        pagesRead <= 0 ->
                            stringResource(
                                id = net.dom53.inkita.R.string.volume_detail_start_reading,
                                volText,
                            )
                        else ->
                            stringResource(
                                id = net.dom53.inkita.R.string.volume_detail_continue_label,
                                volText,
                                pagesRead + 1,
                            )
                    }
                Button(
                    onClick = {
                        val chapters = chapterList
                        if (chapters.isEmpty()) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            return@Button
                        }
                        val pagesReadSafe = pagesRead.coerceAtLeast(0)
                        val target =
                            if (pagesReadSafe <= 0) {
                                chapters.firstOrNull()?.let { it to 0 }
                            } else {
                                var cumulative = 0
                                val found =
                                    chapters.firstOrNull { ch ->
                                        val count = ch.pages ?: 0
                                        val next = cumulative + count
                                        val within = count > 0 && pagesReadSafe < next
                                        if (!within) cumulative = next
                                        within
                                    }
                                if (found != null) {
                                    val pageInChapter = pagesReadSafe - cumulative
                                    found to pageInChapter
                                } else {
                                    chapters.firstOrNull()?.let { it to pagesReadSafe }
                                }
                            }
                        val chapter = target?.first
                        val page = target?.second
                        if (chapter == null || page == null) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            return@Button
                        }
                        onOpenReader(
                            chapter.id,
                            page,
                            payload.seriesId,
                            volume.id,
                            payload.formatId,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = buttonLabel)
                }
                val tabs =
                    listOf(
                        TabItem(SeriesDetailTab.Books, chapterList.size),
                    ).filter { it.count > 0 }
                if (tabs.isNotEmpty()) {
                    var selectedTab by remember { mutableStateOf(tabs.first().id) }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEach { tab ->
                            SectionChip(
                                label = tab.id.label,
                                count = tab.count,
                                selected = selectedTab == tab.id,
                                onClick = { selectedTab = tab.id },
                            )
                        }
                    }
                    if (selectedTab == SeriesDetailTab.Books) {
                        ChapterListV2(
                            chapters = chapterList,
                            config = config,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        if (coverExpanded) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { coverExpanded = false },
                contentAlignment = Alignment.Center,
            ) {
                CoverImage(
                    coverUrl = coverUrl,
                    context = context,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .aspectRatio(2f / 3f)
                            .clickable { coverExpanded = false },
                )
            }
        }
    }
}
