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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.core.downloadv2.DownloadManagerV2
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.ui.common.seriesCoverUrl
import net.dom53.inkita.ui.common.volumeCoverUrl
import net.dom53.inkita.ui.seriesdetail.utils.cleanHtml
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

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
    val downloadDao = remember(context.applicationContext) { InkitaDatabase.getInstance(context.applicationContext).downloadV2Dao() }
    val chapterDownloadStates = remember { mutableStateMapOf<Int, ChapterDownloadState>() }
    val payload = remember(volumeId) { VolumeDetailCache.get(volumeId) }
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    var coverExpanded by remember { mutableStateOf(false) }
    var summaryExpanded by remember { mutableStateOf(false) }
    val offlineMode by appPreferences.offlineModeFlow.collectAsState(initial = false)
    val haptics = LocalHapticFeedback.current
    var selectedChapter by remember(volumeId) { mutableStateOf<net.dom53.inkita.data.api.dto.ChapterDto?>(null) }
    var selectedChapterIndex by remember(volumeId) { mutableStateOf<Int?>(null) }
    var selectedChapterForDownload by remember(volumeId) { mutableStateOf<net.dom53.inkita.data.api.dto.ChapterDto?>(null) }
    var selectedChapterDownloadIndex by remember(volumeId) { mutableStateOf<Int?>(null) }
    var showDownloadChapterDialog by remember { mutableStateOf(false) }
    var downloadChapterState by remember { mutableStateOf(ChapterDownloadState.None) }
    val scope = rememberCoroutineScope()
    val selectedChapterId = selectedChapter?.id
    val downloadManagerV2 =
        remember(context.applicationContext) {
            val strategy =
                EpubDownloadStrategyV2(
                    appContext = context.applicationContext,
                    downloadDao = downloadDao,
                    appPreferences = appPreferences,
                )
            DownloadManagerV2(
                appContext = context.applicationContext,
                downloadDao = downloadDao,
                strategies = mapOf(strategy.key to strategy),
            )
        }

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
            if (offlineMode) {
                onConsumeReaderReturn()
                return@LaunchedEffect
            }
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
    val downloadedItemsByVolume =
        downloadDao
            .observeItemsForVolume(volume.id)
            .collectAsState(initial = emptyList())
    val downloadedItemsForChapterFlow =
        remember(selectedChapterId) {
            if (selectedChapterId != null) {
                downloadDao.observeItemsForChapter(selectedChapterId)
            } else {
                flowOf(emptyList())
            }
        }
    val downloadedItemsForChapter = downloadedItemsForChapterFlow.collectAsState(initial = emptyList())
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
                    enabled = !offlineMode,
                ) {
                    Text(text = buttonLabel)
                }
                val tabs =
                    listOf(
                        TabItem(SeriesDetailTab.Books, chapterList.size),
                    ).filter { it.count > 0 }
                if (selectedChapter != null) {
                    val downloadedPages =
                        downloadedItemsForChapter.value
                            .filter { it.status == net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity.STATUS_COMPLETED }
                            .mapNotNull { it.page }
                            .toSet()
                    ChapterPagesSection(
                        chapter = selectedChapter,
                        chapterIndex = selectedChapterIndex ?: 0,
                        downloadedPages = downloadedPages,
                        onTogglePageDownload = { chapter, page, isDownloaded ->
                            if (offlineMode) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                return@ChapterPagesSection
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isDownloaded) {
                                scope.launch {
                                    downloadDao.deleteItemsForChapterPage(chapter.id, page)
                                }
                            } else {
                                val format = Format.fromId(payload.formatId)
                                if (format != Format.Epub) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@ChapterPagesSection
                                }
                                scope.launch {
                                    val request =
                                        DownloadRequestV2(
                                            type = DownloadJobV2Entity.TYPE_CHAPTER,
                                            format = EpubDownloadStrategyV2.FORMAT_EPUB,
                                            seriesId = payload.seriesId,
                                            volumeId = volume.id,
                                            chapterId = chapter.id,
                                            pageIndex = page,
                                        )
                                    downloadManagerV2.enqueue(request)
                                }
                            }
                        },
                        onOpenPage = { chapter, page ->
                            onOpenReader(
                                chapter.id,
                                page,
                                payload.seriesId,
                                volume.id,
                                payload.formatId,
                            )
                        },
                        onBack = {
                            selectedChapter = null
                            selectedChapterIndex = null
                        },
                    )
                } else if (tabs.isNotEmpty()) {
                    var selectedTab by remember { mutableStateOf(tabs.first().id) }
                    LaunchedEffect(chapterList, downloadedItemsByVolume.value) {
                        val items = downloadedItemsByVolume.value
                        val grouped = items.groupBy { it.chapterId }
                        chapterList.forEach { chapter ->
                            val list = grouped[chapter.id].orEmpty()
                            val completed =
                                list.count {
                                    it.status == net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity.STATUS_COMPLETED
                                }
                            val expected = chapter.pages?.takeIf { it > 0 } ?: 0
                            val state =
                                when {
                                    expected > 0 && completed >= expected -> ChapterDownloadState.Complete
                                    completed > 0 -> ChapterDownloadState.Partial
                                    else -> ChapterDownloadState.None
                                }
                            chapterDownloadStates[chapter.id] = state
                        }
                    }
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
                            downloadStates = chapterDownloadStates,
                            onChapterClick = { chapter, index ->
                                selectedChapter = chapter
                                selectedChapterIndex = index
                            },
                            onChapterLongPress = { chapter, index ->
                                scope.launch {
                                    val completed = downloadDao.countCompletedItemsForChapter(chapter.id)
                                    val expected = chapter.pages?.takeIf { it > 0 } ?: 0
                                    if (expected == 0 && completed == 0) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(net.dom53.inkita.R.string.series_detail_pages_unavailable),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        return@launch
                                    }
                                    downloadChapterState =
                                        when {
                                            expected > 0 && completed >= expected -> ChapterDownloadState.Complete
                                            completed > 0 -> ChapterDownloadState.Partial
                                            else -> ChapterDownloadState.None
                                        }
                                    selectedChapterForDownload = chapter
                                    selectedChapterDownloadIndex = index
                                    showDownloadChapterDialog = true
                                }
                            },
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
        if (showDownloadChapterDialog) {
            val chapter = selectedChapterForDownload
            val chapterIndex = selectedChapterDownloadIndex
            AlertDialog(
                onDismissRequest = {
                    showDownloadChapterDialog = false
                    selectedChapterForDownload = null
                    selectedChapterDownloadIndex = null
                },
                title = {
                    val titleRes =
                        when (downloadChapterState) {
                            ChapterDownloadState.Complete -> net.dom53.inkita.R.string.series_detail_remove_chapter_title
                            ChapterDownloadState.Partial -> net.dom53.inkita.R.string.series_detail_resume_chapter_title
                            ChapterDownloadState.None -> net.dom53.inkita.R.string.series_detail_download_chapter_title
                        }
                    Text(stringResource(titleRes))
                },
                text = {
                    val label =
                        chapter
                            ?.titleName
                            ?.takeIf { it.isNotBlank() }
                            ?: chapter
                                ?.title
                                ?.takeIf { it.isNotBlank() }
                            ?: chapter
                                ?.range
                                ?.takeIf { it.isNotBlank() }
                            ?: context.getString(
                                net.dom53.inkita.R.string.series_detail_chapter_fallback,
                                (chapterIndex ?: 0) + 1,
                            )
                    val bodyRes =
                        when (downloadChapterState) {
                            ChapterDownloadState.Complete -> net.dom53.inkita.R.string.series_detail_remove_chapter_body
                            ChapterDownloadState.Partial -> net.dom53.inkita.R.string.series_detail_resume_chapter_body
                            ChapterDownloadState.None -> net.dom53.inkita.R.string.series_detail_download_chapter_body
                        }
                    Text(text = stringResource(bodyRes, label))
                },
                confirmButton = {
                    val confirmRes =
                        when (downloadChapterState) {
                            ChapterDownloadState.Complete -> net.dom53.inkita.R.string.series_detail_remove_chapter_confirm
                            ChapterDownloadState.Partial -> net.dom53.inkita.R.string.series_detail_resume_chapter_confirm
                            ChapterDownloadState.None -> net.dom53.inkita.R.string.series_detail_download_chapter_confirm
                        }
                    Button(
                        onClick = {
                            showDownloadChapterDialog = false
                            selectedChapterForDownload = null
                            selectedChapterDownloadIndex = null
                            val chapter = chapter ?: return@Button
                            if (downloadChapterState == ChapterDownloadState.Complete) {
                                scope.launch {
                                    downloadDao.deleteItemsForChapter(chapter.id)
                                    downloadDao.deleteJobsForChapter(chapter.id)
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.settings_downloads_clear_downloaded_toast),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                                return@Button
                            }
                            if (offlineMode) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                return@Button
                            }
                            val format = Format.fromId(payload.formatId)
                            if (format != Format.Epub) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                return@Button
                            }
                            val pages = chapter.pages ?: 0
                            if (pages <= 0) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(net.dom53.inkita.R.string.series_detail_pages_unavailable),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                return@Button
                            }
                            scope.launch {
                                val request =
                                    DownloadRequestV2(
                                        type = DownloadJobV2Entity.TYPE_CHAPTER,
                                        format = EpubDownloadStrategyV2.FORMAT_EPUB,
                                        seriesId = payload.seriesId,
                                        volumeId = volume.id,
                                        chapterId = chapter.id,
                                        pageCount = pages,
                                    )
                                downloadManagerV2.enqueue(request)
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(net.dom53.inkita.R.string.download_queued),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    ) {
                        Text(stringResource(confirmRes))
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (downloadChapterState == ChapterDownloadState.Partial) {
                            Button(
                                onClick = {
                                    showDownloadChapterDialog = false
                                    val chapter = chapter ?: return@Button
                                    scope.launch {
                                        downloadDao.deleteItemsForChapter(chapter.id)
                                        downloadDao.deleteJobsForChapter(chapter.id)
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(net.dom53.inkita.R.string.settings_downloads_clear_downloaded_toast),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                    selectedChapterForDownload = null
                                    selectedChapterDownloadIndex = null
                                },
                            ) {
                                Text(stringResource(net.dom53.inkita.R.string.series_detail_remove_chapter_confirm))
                            }
                        }
                        Button(
                            onClick = {
                                showDownloadChapterDialog = false
                                selectedChapterForDownload = null
                                selectedChapterDownloadIndex = null
                            },
                        ) {
                            Text(stringResource(net.dom53.inkita.R.string.general_cancel))
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ChapterPagesSection(
    chapter: net.dom53.inkita.data.api.dto.ChapterDto?,
    chapterIndex: Int,
    downloadedPages: Set<Int>,
    onTogglePageDownload: (net.dom53.inkita.data.api.dto.ChapterDto, Int, Boolean) -> Unit,
    onOpenPage: (net.dom53.inkita.data.api.dto.ChapterDto, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val title =
        chapter
            ?.titleName
            ?.takeIf { it.isNotBlank() }
            ?: chapter
                ?.title
                ?.takeIf { it.isNotBlank() }
            ?: chapter
                ?.range
                ?.takeIf { it.isNotBlank() }
            ?: context.getString(net.dom53.inkita.R.string.series_detail_chapter_fallback, chapterIndex + 1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    val pages = chapter?.pages ?: 0
    val pagesRead = chapter?.pagesRead ?: 0
    if (pages <= 0) {
        Text(
            text = stringResource(net.dom53.inkita.R.string.series_detail_pages_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(10.dp)
        repeat(pages) { index ->
            val isRead = index < pagesRead
            val isCurrent = pagesRead in 0 until pages && index == pagesRead
            val isDownloaded = downloadedPages.contains(index)
            val containerColor =
                if (isRead) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                }
            val textColor =
                when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isRead -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            val border =
                if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            val density = LocalDensity.current
            val swipeDistance = with(density) { 160.dp.toPx() }
            val swipeState = rememberSwipeableState(initialValue = 0)
            val anchors = remember(swipeDistance) { mapOf(0f to 0, -swipeDistance to 1) }
            var actionTriggered by remember { mutableStateOf(false) }
            LaunchedEffect(swipeState.currentValue) {
                if (swipeState.currentValue == 1 && !actionTriggered) {
                    actionTriggered = true
                    chapter?.let { onTogglePageDownload(it, index, isDownloaded) }
                    swipeState.animateTo(0)
                    actionTriggered = false
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .swipeable(
                            state = swipeState,
                            anchors = anchors,
                            thresholds = { _, _ -> FractionalThreshold(0.25f) },
                            orientation = Orientation.Horizontal,
                        ),
            ) {
                val icon =
                    if (isDownloaded) {
                        Icons.Filled.Delete
                    } else {
                        Icons.Filled.FileDownload
                    }
                val tint =
                    if (isDownloaded) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .offset { IntOffset(swipeState.offset.value.roundToInt(), 0) }
                            .fillMaxWidth()
                            .clip(shape)
                            .background(containerColor)
                            .then(
                                if (border != null) {
                                    Modifier.border(border, shape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { chapter?.let { onOpenPage(it, index) } }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "${stringResource(net.dom53.inkita.R.string.general_page)} ${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.width(86.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
