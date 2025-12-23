package net.dom53.inkita.ui.seriesdetail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.downloadv2.DownloadManagerV2
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.MarkVolumeReadDto
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.mapper.flattenToc
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.ui.browse.utils.PublicationState
import net.dom53.inkita.ui.common.chapterCoverUrl
import net.dom53.inkita.ui.common.collectionCoverUrl
import net.dom53.inkita.ui.common.seriesCoverUrl
import net.dom53.inkita.ui.common.volumeCoverUrl
import net.dom53.inkita.ui.seriesdetail.utils.cleanHtml

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SeriesDetailScreenV2(
    seriesId: Int,
    appPreferences: AppPreferences,
    collectionsRepository: net.dom53.inkita.domain.repository.CollectionsRepository,
    readerRepository: net.dom53.inkita.domain.repository.ReaderRepository,
    cacheManager: CacheManager,
    onOpenReader: (chapterId: Int, page: Int, seriesId: Int, volumeId: Int, formatId: Int?) -> Unit,
    onOpenVolume: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenBrowseGenre: (id: Int, name: String) -> Unit,
    onOpenBrowseTag: (id: Int, name: String) -> Unit,
    onOpenCollection: ((id: Int, name: String) -> Unit)? = null,
    readerReturn: net.dom53.inkita.ui.reader.ReaderReturn? = null,
    onConsumeReaderReturn: () -> Unit = {},
    refreshSignal: Boolean = false,
    onConsumeRefreshSignal: () -> Unit = {},
    onBack: () -> Unit,
) {
    val viewModel: SeriesDetailViewModelV2 =
        viewModel(
            factory =
                SeriesDetailViewModelV2.provideFactory(
                    seriesId,
                    appPreferences,
                    collectionsRepository,
                    readerRepository,
                    cacheManager,
                ),
        )
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(uiState.showLoadedToast) {
        if (uiState.showLoadedToast) {
            viewModel.consumeLoadedToast()
        }
    }
    LaunchedEffect(readerReturn) {
        if (readerReturn != null) {
            viewModel.reload()
            onConsumeReaderReturn()
        }
    }
    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.reload()
            onConsumeRefreshSignal()
        }
    }
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    val offlineMode by appPreferences.offlineModeFlow.collectAsState(initial = false)
    val haptics = LocalHapticFeedback.current
    var summaryExpanded by remember { mutableStateOf(false) }
    var coverExpanded by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadVolumeDialog by remember { mutableStateOf(false) }
    var showVolumeActionsDialog by remember { mutableStateOf(false) }
    var downloadVolumeState by remember { mutableStateOf(DownloadVolumeState.None) }
    var selectedVolume by remember { mutableStateOf<net.dom53.inkita.data.api.dto.VolumeDto?>(null) }
    var selectedSpecialChapter by remember { mutableStateOf<net.dom53.inkita.data.api.dto.ChapterDto?>(null) }
    var selectedSpecialIndex by remember { mutableStateOf<Int?>(null) }
    val specialPageTitleCache = remember(seriesId) { mutableStateMapOf<Int, Map<Int, String>>() }
    val volumeDownloadStates = remember { mutableStateMapOf<Int, DownloadVolumeState>() }
    val downloadDao =
        remember(context.applicationContext) {
            InkitaDatabase.getInstance(context.applicationContext).downloadV2Dao()
        }
    val downloadedItemsBySeries =
        downloadDao
            .observeItemsForSeries(seriesId)
            .collectAsState(initial = emptyList())
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
    LaunchedEffect(selectedSpecialChapter?.id, offlineMode, config.serverUrl, config.apiKey) {
        val chapter = selectedSpecialChapter ?: return@LaunchedEffect
        if (offlineMode) return@LaunchedEffect
        val format = Format.fromId(uiState.detail?.series?.format)
        if (format != Format.Epub) return@LaunchedEffect
        if (specialPageTitleCache.containsKey(chapter.id)) return@LaunchedEffect
        if (!config.isConfigured) return@LaunchedEffect
        val pages = chapter.pages ?: 0
        if (pages <= 0) return@LaunchedEffect
        val api =
            net.dom53.inkita.core.network.KavitaApiFactory
                .createAuthenticated(config.serverUrl, config.apiKey)
        val tocResponse = api.getBookChapters(chapter.id)
        if (!tocResponse.isSuccessful) return@LaunchedEffect
        val tocItems = tocResponse.body().orEmpty().flatMap { flattenToc(it) }
        specialPageTitleCache[chapter.id] = buildPageTitleMap(context, pages, tocItems)
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(net.dom53.inkita.R.string.series_download_all)) },
                            onClick = {
                                showMenu = false
                                if (offlineMode) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@DropdownMenuItem
                                }
                                val detail = uiState.detail
                                val format = Format.fromId(detail?.series?.format)
                                if (format != Format.Epub) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@DropdownMenuItem
                                }
                                val volumes = detail?.detail?.volumes.orEmpty()
                                val chaptersWithVolume =
                                    buildList {
                                        volumes.forEach { volume ->
                                            volume.chapters
                                                ?.forEach { chapter ->
                                                    add(chapter to (chapter.volumeId ?: volume.id))
                                                }
                                        }
                                        detail
                                            ?.detail
                                            ?.chapters
                                            ?.forEach { chapter ->
                                                add(chapter to chapter.volumeId)
                                            }
                                        detail
                                            ?.detail
                                            ?.specials
                                            ?.forEach { chapter ->
                                                add(chapter to chapter.volumeId)
                                            }
                                        detail
                                            ?.detail
                                            ?.storylineChapters
                                            ?.forEach { chapter ->
                                                add(chapter to chapter.volumeId)
                                            }
                                    }.distinctBy { it.first.id }
                                        .filter { (it.first.pages ?: 0) > 0 }
                                if (chaptersWithVolume.isEmpty()) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.series_detail_pages_unavailable),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@DropdownMenuItem
                                }
                                scope.launch {
                                    val sid = detail?.series?.id ?: seriesId
                                    chaptersWithVolume.forEach { (chapter, volumeId) ->
                                        val pages = chapter.pages ?: return@forEach
                                        val request =
                                            DownloadRequestV2(
                                                type = DownloadJobV2Entity.TYPE_CHAPTER,
                                                format = EpubDownloadStrategyV2.FORMAT_EPUB,
                                                seriesId = sid,
                                                volumeId = volumeId,
                                                chapterId = chapter.id,
                                                pageCount = pages,
                                            )
                                        downloadManagerV2.enqueue(request)
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.download_queued),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(net.dom53.inkita.R.string.series_clear_downloads)) },
                            onClick = {
                                showMenu = false
                                val sid = uiState.detail?.series?.id ?: seriesId
                                scope.launch {
                                    downloadDao.deleteItemsForSeries(sid)
                                    downloadDao.deleteJobsForSeries(sid)
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.settings_downloads_clear_downloaded_toast),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(net.dom53.inkita.R.string.general_mark_read)) },
                            onClick = {
                                showMenu = false
                                if (offlineMode) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@DropdownMenuItem
                                }
                                if (!config.isConfigured) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_no_server_logged_in),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@DropdownMenuItem
                                }
                                viewModel.markSeriesRead(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(net.dom53.inkita.R.string.general_mark_unread)) },
                            onClick = {
                                showMenu = false
                                if (offlineMode) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@DropdownMenuItem
                                }
                                if (!config.isConfigured) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_no_server_logged_in),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@DropdownMenuItem
                                }
                                viewModel.markSeriesRead(false)
                            },
                        )
                    }
                }
            }
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(id = net.dom53.inkita.R.string.general_loading),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = uiState.error ?: stringResource(id = net.dom53.inkita.R.string.general_error),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    val detail = uiState.detail
                    val series = detail?.series
                    val metadata = detail?.metadata
                    val coverUrl = series?.id?.let { seriesCoverUrl(config, it) }
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { coverExpanded = true },
                            )
                            HeaderInfo(
                                seriesId = seriesId,
                                series = series,
                                metadata = metadata,
                                detail = detail,
                                context = context,
                                clipboardManager = clipboardManager,
                                onCopyToast = {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_copied_to_clipboard),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionsRowV2(
                            wantToRead = detail?.wantToRead == true,
                            collectionsEnabled = !offlineMode,
                            wantToReadEnabled = !offlineMode,
                            onToggleWant = {
                                viewModel.toggleWantToRead()
                            },
                            onOpenCollections = {
                                viewModel.loadCollections()
                                showCollectionDialog = true
                            },
                            onOpenWeb = {
                                val url = webUrl(config, series?.libraryId, seriesId)
                                if (url == null) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.series_detail_missing_library_id),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@ActionsRowV2
                                }
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_unable_to_share),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            onShare = {
                                val url = webUrl(config, series?.libraryId, seriesId)
                                val title =
                                    series?.name?.ifBlank { null }
                                        ?: context.getString(net.dom53.inkita.R.string.series_detail_series_fallback, seriesId)
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, title)
                                        putExtra(Intent.EXTRA_TEXT, url ?: title)
                                    }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(net.dom53.inkita.R.string.general_share),
                                        ),
                                    )
                                }.onFailure {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_unable_to_share),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        SummarySectionV2(
                            summary = cleanHtml(metadata?.summary),
                            genres =
                                metadata
                                    ?.genres
                                    ?.mapNotNull { genre ->
                                        val title = genre.title ?: return@mapNotNull null
                                        genre.id to title
                                    }.orEmpty(),
                            tags =
                                metadata
                                    ?.tags
                                    ?.mapNotNull { tag ->
                                        val title = tag.title ?: return@mapNotNull null
                                        tag.id to title
                                    }.orEmpty(),
                            expanded = summaryExpanded,
                            onToggle = { summaryExpanded = !summaryExpanded },
                            onGenreClick = { id, name -> onOpenBrowseGenre(id, name) },
                            onTagClick = { id, name -> onOpenBrowseTag(id, name) },
                        )
                        val continuePoint = detail?.continuePoint
                        val readerProgress = detail?.readerProgress
                        val continueLabel =
                            if (continuePoint != null && detail?.hasProgress == true) {
                                val page = (continuePoint.pagesRead ?: 0) + 1
                                val volId = readerProgress?.volumeId ?: continuePoint.volumeId
                                val volumeNumber =
                                    detail
                                        ?.detail
                                        ?.volumes
                                        ?.firstOrNull { it.id == volId }
                                        ?.let { volumeNumberText(it) }
                                if (volumeNumber != null) {
                                    stringResource(
                                        id = net.dom53.inkita.R.string.series_detail_continue_vol_ch,
                                        volumeNumber,
                                        page,
                                    )
                                } else {
                                    stringResource(id = net.dom53.inkita.R.string.series_detail_continue_ch, page)
                                }
                            } else {
                                stringResource(id = net.dom53.inkita.R.string.series_detail_start_reading)
                            }
                        Button(
                            onClick = {
                                val chapterId = readerProgress?.chapterId ?: continuePoint?.id
                                val volumeId = readerProgress?.volumeId ?: continuePoint?.volumeId
                                val sid = detail?.series?.id ?: seriesId
                                val fmt = detail?.series?.format
                                if (chapterId == null || volumeId == null) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@Button
                                }
                                val page =
                                    readerProgress?.pageNum ?: continuePoint?.pagesRead ?: 0
                                onOpenReader(chapterId, page, sid, volumeId, fmt)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = continueLabel)
                        }
                        val booksCount = detail?.detail?.volumes?.size
                        val chaptersCount = detail?.detail?.chapters?.size
                        val specialsCount = detail?.detail?.specials?.size
                        val relatedCount =
                            (detail?.related?.let { relatedSeriesCount(it) } ?: 0) +
                                (detail?.collections?.size ?: 0)
                        val tabs =
                            listOf(
                                TabItem(SeriesDetailTab.Books, booksCount ?: 0),
                                TabItem(SeriesDetailTab.Chapters, chaptersCount ?: 0),
                                TabItem(SeriesDetailTab.Specials, specialsCount ?: 0),
                                TabItem(SeriesDetailTab.Related, relatedCount ?: 0),
                                TabItem(SeriesDetailTab.Recommendations, 0),
                                TabItem(SeriesDetailTab.Reviews, 0),
                            ).filter { it.count > 0 }
                        var selectedTab by remember { mutableStateOf(tabs.firstOrNull()?.id ?: SeriesDetailTab.Books) }
                        LaunchedEffect(tabs) {
                            if (tabs.none { it.id == selectedTab }) {
                                tabs.firstOrNull()?.let { selectedTab = it.id }
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
                            val volumes = detail?.detail?.volumes.orEmpty()
                            LaunchedEffect(volumes, downloadedItemsBySeries.value) {
                                val items = downloadedItemsBySeries.value
                                val grouped =
                                    items
                                        .filter { it.volumeId != null }
                                        .groupBy { it.volumeId!! }
                                volumes.forEach { volume ->
                                    val list = grouped[volume.id].orEmpty()
                                    val completed =
                                        list.count { item ->
                                            item.status == net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity.STATUS_COMPLETED &&
                                                isItemPathPresent(item.localPath)
                                        }
                                    val expected =
                                        volume.pages
                                            ?.takeIf { it > 0 }
                                            ?: volume.chapters
                                                ?.sumOf { it.pages ?: 0 }
                                                ?.takeIf { it > 0 }
                                            ?: 0
                                    val state =
                                        when {
                                            expected > 0 && completed >= expected -> DownloadVolumeState.Complete
                                            completed > 0 -> DownloadVolumeState.Partial
                                            else -> DownloadVolumeState.None
                                        }
                                    volumeDownloadStates[volume.id] = state
                                }
                            }
                            VolumeGridRow(
                                volumes = volumes,
                                config = config,
                                seriesCoverUrl = series?.id?.let { seriesCoverUrl(config, it) },
                                downloadStates = volumeDownloadStates,
                                onOpenVolume = { volume ->
                                    VolumeDetailCache.put(
                                        VolumeDetailPayload(
                                            seriesId = seriesId,
                                            libraryId = detail?.series?.libraryId,
                                            volume = volume,
                                            formatId = detail?.series?.format,
                                        ),
                                    )
                                    onOpenVolume(volume.id)
                                },
                                onLongPressVolume = { volume ->
                                    scope.launch {
                                        val completed =
                                            downloadedItemsBySeries.value
                                                .filter { it.volumeId == volume.id }
                                                .count { item ->
                                                    item.status ==
                                                        net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity.STATUS_COMPLETED &&
                                                        isItemPathPresent(item.localPath)
                                                }
                                        val expected =
                                            volume.pages
                                                ?.takeIf { it > 0 }
                                                ?: volume.chapters
                                                    ?.sumOf { it.pages ?: 0 }
                                                    ?.takeIf { it > 0 }
                                                ?: 0
                                        downloadVolumeState =
                                            when {
                                                expected > 0 && completed >= expected -> DownloadVolumeState.Complete
                                                completed > 0 -> DownloadVolumeState.Partial
                                                else -> DownloadVolumeState.None
                                            }
                                        selectedVolume = volume
                                        showDownloadVolumeDialog = true
                                        showVolumeActionsDialog = true
                                    }
                                },
                            )
                        }
                        if (selectedTab == SeriesDetailTab.Specials) {
                            val specials = detail?.detail?.specials.orEmpty()
                            val specialDownloadStates = remember { mutableStateMapOf<Int, ChapterDownloadState>() }
                            var showSpecialDialog by remember { mutableStateOf(false) }
                            var selectedSpecial by remember { mutableStateOf<net.dom53.inkita.data.api.dto.ChapterDto?>(null) }
                            var specialDownloadState by remember { mutableStateOf(ChapterDownloadState.None) }
                            LaunchedEffect(specials, downloadedItemsBySeries.value) {
                                val items = downloadedItemsBySeries.value
                                val grouped = items.groupBy { it.chapterId }
                                specials.forEach { chapter ->
                                    val list = grouped[chapter.id].orEmpty()
                                    val completed =
                                        list.count { item ->
                                            item.status ==
                                                net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity.STATUS_COMPLETED &&
                                                isItemPathPresent(item.localPath)
                                        }
                                    val expected = chapter.pages?.takeIf { it > 0 } ?: 0
                                    val state =
                                        when {
                                            expected > 0 && completed >= expected -> ChapterDownloadState.Complete
                                            completed > 0 -> ChapterDownloadState.Partial
                                            else -> ChapterDownloadState.None
                                        }
                                    specialDownloadStates[chapter.id] = state
                                }
                            }
                            if (selectedSpecialChapter != null) {
                                val chapter = selectedSpecialChapter
                                val downloadedPages =
                                    downloadedItemsBySeries.value
                                        .filter { it.chapterId == chapter?.id }
                                        .filter { item ->
                                            item.status ==
                                                net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity.STATUS_COMPLETED &&
                                                isItemPathPresent(item.localPath)
                                        }.mapNotNull { it.page }
                                        .toSet()
                                ChapterPagesSection(
                                    chapter = chapter,
                                    chapterIndex = selectedSpecialIndex ?: 0,
                                    downloadedPages = downloadedPages,
                                    pageTitles = chapter?.id?.let { specialPageTitleCache[it] },
                                    onTogglePageDownload = { target, page, isDownloaded ->
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
                                                downloadDao.deleteItemsForChapterPage(target.id, page)
                                            }
                                        } else {
                                            val format = Format.fromId(detail?.series?.format)
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
                                                        seriesId = seriesId,
                                                        volumeId = target.volumeId,
                                                        chapterId = target.id,
                                                        pageIndex = page,
                                                    )
                                                downloadManagerV2.enqueue(request)
                                            }
                                        }
                                    },
                                    onUpdateProgress = { target, pageNum ->
                                        if (offlineMode) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            return@ChapterPagesSection
                                        }
                                        if (!config.isConfigured) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.general_no_server_logged_in),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            return@ChapterPagesSection
                                        }
                                        val libraryId = detail?.series?.libraryId
                                        if (libraryId == null) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.general_error),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            return@ChapterPagesSection
                                        }
                                        val markRead = pageNum > (target.pagesRead ?: 0)
                                        scope.launch {
                                            val api =
                                                net.dom53.inkita.core.network.KavitaApiFactory.createAuthenticated(
                                                    config.serverUrl,
                                                    config.apiKey,
                                                )
                                            val resp =
                                                api.setReaderProgress(
                                                    net.dom53.inkita.data.api.dto.ReaderProgressDto(
                                                        libraryId = libraryId,
                                                        seriesId = seriesId,
                                                        volumeId = target.volumeId,
                                                        chapterId = target.id,
                                                        pageNum = pageNum,
                                                        bookScrollId = null,
                                                    ),
                                                )
                                            if (resp.isSuccessful) {
                                                selectedSpecialChapter =
                                                    target.copy(pagesRead = pageNum)
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(
                                                            if (markRead) {
                                                                net.dom53.inkita.R.string.general_mark_read
                                                            } else {
                                                                net.dom53.inkita.R.string.general_mark_unread
                                                            },
                                                        ),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                viewModel.reload()
                                            } else {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(net.dom53.inkita.R.string.general_error),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                        }
                                    },
                                    onOpenPage = { target, page ->
                                        if ((offlineMode || !NetworkUtils.isOnline(context)) && !downloadedPages.contains(page)) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.reader_page_not_downloaded),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            return@ChapterPagesSection
                                        }
                                        onOpenReader(
                                            target.id,
                                            page,
                                            seriesId,
                                            target.volumeId ?: 0,
                                            detail?.series?.format,
                                        )
                                    },
                                    onBack = {
                                        selectedSpecialChapter = null
                                        selectedSpecialIndex = null
                                    },
                                )
                            } else {
                                SpecialsGridRow(
                                    specials = specials,
                                    config = config,
                                    seriesCoverUrl = series?.id?.let { seriesCoverUrl(config, it) },
                                    downloadStates = specialDownloadStates,
                                    onSelectSpecial = { chapter, index ->
                                        selectedSpecialChapter = chapter
                                        selectedSpecialIndex = index
                                    },
                                    onLongPressSpecial = { chapter ->
                                        scope.launch {
                                            val completed =
                                                downloadedItemsBySeries.value
                                                    .filter { it.chapterId == chapter.id }
                                                    .count { item ->
                                                        item.status ==
                                                            net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity.STATUS_COMPLETED &&
                                                            isItemPathPresent(item.localPath)
                                                    }
                                            val expected = chapter.pages?.takeIf { it > 0 } ?: 0
                                            specialDownloadState =
                                                when {
                                                    expected > 0 && completed >= expected -> ChapterDownloadState.Complete
                                                    completed > 0 -> ChapterDownloadState.Partial
                                                    else -> ChapterDownloadState.None
                                                }
                                            selectedSpecial = chapter
                                            showSpecialDialog = true
                                        }
                                    },
                                )
                            }
                            if (showSpecialDialog) {
                                val chapter = selectedSpecial
                                AlertDialog(
                                    onDismissRequest = {
                                        showSpecialDialog = false
                                        selectedSpecial = null
                                    },
                                    title = {
                                        val titleRes =
                                            when (specialDownloadState) {
                                                ChapterDownloadState.Complete ->
                                                    net.dom53.inkita.R.string.series_detail_remove_chapter_title
                                                ChapterDownloadState.Partial ->
                                                    net.dom53.inkita.R.string.series_detail_resume_chapter_title
                                                ChapterDownloadState.None ->
                                                    net.dom53.inkita.R.string.series_detail_download_chapter_title
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
                                                ?: stringResource(
                                                    net.dom53.inkita.R.string.series_detail_chapter_fallback,
                                                    (specials.indexOf(chapter).coerceAtLeast(0) + 1),
                                                )
                                        val bodyRes =
                                            when (specialDownloadState) {
                                                ChapterDownloadState.Complete ->
                                                    net.dom53.inkita.R.string.series_detail_remove_chapter_body
                                                ChapterDownloadState.Partial ->
                                                    net.dom53.inkita.R.string.series_detail_resume_chapter_body
                                                ChapterDownloadState.None ->
                                                    net.dom53.inkita.R.string.series_detail_download_chapter_body
                                            }
                                        Text(stringResource(bodyRes, label))
                                    },
                                    confirmButton = {
                                        val confirmRes =
                                            when (specialDownloadState) {
                                                ChapterDownloadState.Complete ->
                                                    net.dom53.inkita.R.string.series_detail_remove_chapter_confirm
                                                ChapterDownloadState.Partial ->
                                                    net.dom53.inkita.R.string.series_detail_resume_chapter_confirm
                                                ChapterDownloadState.None ->
                                                    net.dom53.inkita.R.string.series_detail_download_chapter_confirm
                                            }
                                        Button(
                                            onClick = {
                                                showSpecialDialog = false
                                                val target = chapter ?: return@Button
                                                if (specialDownloadState == ChapterDownloadState.Complete) {
                                                    scope.launch {
                                                        downloadDao.deleteItemsForChapter(target.id)
                                                        downloadDao.deleteJobsForChapter(target.id)
                                                    }
                                                    selectedSpecial = null
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
                                                val format = Format.fromId(detail?.series?.format)
                                                if (format != Format.Epub) {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    return@Button
                                                }
                                                val pages = target.pages ?: 0
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
                                                            seriesId = seriesId,
                                                            volumeId = target.volumeId,
                                                            chapterId = target.id,
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
                                                selectedSpecial = null
                                            },
                                        ) {
                                            Text(stringResource(confirmRes))
                                        }
                                    },
                                    dismissButton = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (specialDownloadState == ChapterDownloadState.Partial) {
                                                Button(
                                                    onClick = {
                                                        showSpecialDialog = false
                                                        val target = chapter ?: return@Button
                                                        scope.launch {
                                                            downloadDao.deleteItemsForChapter(target.id)
                                                            downloadDao.deleteJobsForChapter(target.id)
                                                        }
                                                        selectedSpecial = null
                                                    },
                                                ) {
                                                    Text(stringResource(net.dom53.inkita.R.string.series_detail_remove_chapter_confirm))
                                                }
                                            }
                                            Button(
                                                onClick = {
                                                    showSpecialDialog = false
                                                    selectedSpecial = null
                                                },
                                            ) {
                                                Text(stringResource(net.dom53.inkita.R.string.general_cancel))
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        if (selectedTab == SeriesDetailTab.Related) {
                            RelatedCollectionsSection(
                                related = detail?.related,
                                collections = detail?.collections.orEmpty(),
                                config = config,
                                onOpenSeries = onOpenSeries,
                                onOpenCollection = onOpenCollection,
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        if (showCollectionDialog) {
            CollectionDialogV2(
                collections = uiState.collections,
                isLoading = uiState.isLoadingCollections,
                error = uiState.collectionError,
                membership = uiState.collectionsWithSeries,
                onDismiss = { showCollectionDialog = false },
                onLoadCollections = { viewModel.loadCollections() },
                onToggle = { collection, add ->
                    if (!config.isConfigured) {
                        Toast
                            .makeText(
                                context,
                                context.getString(net.dom53.inkita.R.string.general_no_server_logged_in),
                                Toast.LENGTH_SHORT,
                            ).show()
                        return@CollectionDialogV2
                    }
                    viewModel.toggleCollection(collection, add)
                },
                onCreateCollection = { title ->
                    if (title.isBlank() || !config.isConfigured) return@CollectionDialogV2
                    viewModel.createCollection(title)
                },
            )
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
                    coverUrl =
                        uiState.detail
                            ?.series
                            ?.id
                            ?.let { seriesCoverUrl(config, it) },
                    context = context,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { coverExpanded = false },
                )
            }
        }
        if (showDownloadVolumeDialog) {
            val volume = selectedVolume
            AlertDialog(
                onDismissRequest = {
                    showDownloadVolumeDialog = false
                    selectedVolume = null
                },
                title = {
                    val titleRes =
                        when (downloadVolumeState) {
                            DownloadVolumeState.Complete -> net.dom53.inkita.R.string.series_detail_remove_volume_title
                            DownloadVolumeState.Partial -> net.dom53.inkita.R.string.series_detail_resume_volume_title
                            DownloadVolumeState.None -> net.dom53.inkita.R.string.series_detail_download_volume_title
                        }
                    Text(stringResource(titleRes))
                },
                text = {
                    val volLabel =
                        volume
                            ?.name
                            ?.takeIf { it.isNotBlank() }
                            ?: volume
                                ?.let { volumeNumberText(it) }
                                ?.let { number ->
                                    context.getString(net.dom53.inkita.R.string.series_detail_vol_short, number)
                                }
                            ?: context.getString(net.dom53.inkita.R.string.series_detail_vol_short_plain)
                    val bodyRes =
                        when (downloadVolumeState) {
                            DownloadVolumeState.Complete -> net.dom53.inkita.R.string.series_detail_remove_volume_body
                            DownloadVolumeState.Partial -> net.dom53.inkita.R.string.series_detail_resume_volume_body
                            DownloadVolumeState.None -> net.dom53.inkita.R.string.series_detail_download_volume_body
                        }
                    Text(text = stringResource(bodyRes, volLabel))
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val confirmRes =
                            when (downloadVolumeState) {
                                DownloadVolumeState.Complete -> net.dom53.inkita.R.string.series_detail_remove_volume_confirm
                                DownloadVolumeState.Partial -> net.dom53.inkita.R.string.series_detail_resume_volume_confirm
                                DownloadVolumeState.None -> net.dom53.inkita.R.string.series_detail_download_volume_confirm
                            }
                        Button(
                            onClick = {
                                showDownloadVolumeDialog = false
                                showVolumeActionsDialog = false
                                selectedVolume = null
                                val volume = volume ?: return@Button
                                if (downloadVolumeState == DownloadVolumeState.Complete) {
                                    scope.launch {
                                        downloadDao.deleteItemsForVolume(volume.id)
                                        downloadDao.deleteJobsForVolume(volume.id)
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
                                val detail = uiState.detail
                                val format = Format.fromId(detail?.series?.format)
                                if (format != Format.Epub) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.general_not_implemented),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@Button
                                }
                                val chapters =
                                    volume.chapters
                                        ?.filter { (it.pages ?: 0) > 0 }
                                        .orEmpty()
                                if (chapters.isEmpty()) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.series_detail_pages_unavailable),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    return@Button
                                }
                                scope.launch {
                                    val seriesId = detail?.series?.id ?: seriesId
                                    chapters.forEach { chapter ->
                                        val pages = chapter.pages ?: return@forEach
                                        val request =
                                            DownloadRequestV2(
                                                type = DownloadJobV2Entity.TYPE_CHAPTER,
                                                format = EpubDownloadStrategyV2.FORMAT_EPUB,
                                                seriesId = seriesId,
                                                volumeId = volume.id,
                                                chapterId = chapter.id,
                                                pageCount = pages,
                                            )
                                        downloadManagerV2.enqueue(request)
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(net.dom53.inkita.R.string.download_queued),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(confirmRes))
                        }
                        if (showVolumeActionsDialog) {
                            OutlinedButton(
                                onClick = {
                                    showDownloadVolumeDialog = false
                                    showVolumeActionsDialog = false
                                    val volume = volume ?: return@OutlinedButton
                                    if (offlineMode) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        return@OutlinedButton
                                    }
                                    if (!config.isConfigured) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(net.dom53.inkita.R.string.general_no_server_logged_in),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        return@OutlinedButton
                                    }
                                    scope.launch {
                                        val api =
                                            net.dom53.inkita.core.network.KavitaApiFactory.createAuthenticated(
                                                config.serverUrl,
                                                config.apiKey,
                                            )
                                        val resp =
                                            api.markVolumeRead(
                                                MarkVolumeReadDto(
                                                    seriesId = seriesId,
                                                    volumeId = volume.id,
                                                ),
                                            )
                                        if (resp.isSuccessful) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.series_detail_mark_volume_read),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            viewModel.reload()
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.general_error),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                    selectedVolume = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(net.dom53.inkita.R.string.series_detail_mark_volume_read))
                            }
                            OutlinedButton(
                                onClick = {
                                    showDownloadVolumeDialog = false
                                    showVolumeActionsDialog = false
                                    val volume = volume ?: return@OutlinedButton
                                    if (offlineMode) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(net.dom53.inkita.R.string.general_offline_mode),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        return@OutlinedButton
                                    }
                                    if (!config.isConfigured) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(net.dom53.inkita.R.string.general_no_server_logged_in),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        return@OutlinedButton
                                    }
                                    scope.launch {
                                        val api =
                                            net.dom53.inkita.core.network.KavitaApiFactory.createAuthenticated(
                                                config.serverUrl,
                                                config.apiKey,
                                            )
                                        val resp =
                                            api.markVolumeUnread(
                                                MarkVolumeReadDto(
                                                    seriesId = seriesId,
                                                    volumeId = volume.id,
                                                ),
                                            )
                                        if (resp.isSuccessful) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.series_detail_mark_volume_unread),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            viewModel.reload()
                                        } else {
                                            Toast
                                                .makeText(
                                                    context,
                                                    context.getString(net.dom53.inkita.R.string.general_error),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                    selectedVolume = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(net.dom53.inkita.R.string.series_detail_mark_volume_unread))
                            }
                        }
                        if (downloadVolumeState == DownloadVolumeState.Partial) {
                            OutlinedButton(
                                onClick = {
                                    showDownloadVolumeDialog = false
                                    showVolumeActionsDialog = false
                                    val volume = volume ?: return@OutlinedButton
                                    scope.launch {
                                        downloadDao.deleteItemsForVolume(volume.id)
                                        downloadDao.deleteJobsForVolume(volume.id)
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(net.dom53.inkita.R.string.settings_downloads_clear_downloaded_toast),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                    selectedVolume = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(net.dom53.inkita.R.string.series_detail_remove_volume_confirm))
                            }
                        }
                        TextButton(
                            onClick = {
                                showDownloadVolumeDialog = false
                                showVolumeActionsDialog = false
                                selectedVolume = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(net.dom53.inkita.R.string.general_cancel))
                        }
                    }
                },
            )
        }
    }
}

private enum class DownloadVolumeState {
    None,
    Partial,
    Complete,
}

private fun isItemPathPresent(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    return if (path.startsWith("content://")) {
        true
    } else {
        val normalized = path.removePrefix("file://")
        java.io.File(normalized).exists()
    }
}

@Composable
private fun RelatedCollectionsSection(
    related: net.dom53.inkita.data.api.dto.RelatedSeriesDto?,
    collections: List<net.dom53.inkita.data.api.dto.AppUserCollectionDto>,
    config: AppConfig,
    onOpenSeries: (Int) -> Unit,
    onOpenCollection: ((Int, String) -> Unit)? = null,
) {
    val relatedGroups = relatedSeriesGroups(related)
    if (relatedGroups.isNotEmpty()) {
        Text(
            text = stringResource(id = net.dom53.inkita.R.string.general_related),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            relatedGroups.forEach { group ->
                group.items.forEach { item ->
                    val coverUrl = seriesCoverUrl(config, item.id)
                    Column(
                        modifier =
                            Modifier
                                .width(140.dp)
                                .clickable { onOpenSeries(item.id) },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CoverImage(
                            coverUrl = coverUrl,
                            context = LocalContext.current,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                        )
                        Text(
                            text =
                                item.name?.ifBlank { null }
                                    ?: stringResource(id = net.dom53.inkita.R.string.series_detail_series_fallback, item.id),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(id = group.titleRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (collections.isNotEmpty()) {
        Text(
            text = stringResource(id = net.dom53.inkita.R.string.general_collections),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            collections.forEach { collection ->
                val coverUrl = collectionCoverUrl(config, collection.id)
                Column(
                    modifier =
                        Modifier
                            .width(140.dp)
                            .clickable { onOpenCollection?.invoke(collection.id, collection.title ?: "") },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CoverImage(
                        coverUrl = coverUrl,
                        context = LocalContext.current,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                    )
                    Text(
                        text =
                            collection.title?.ifBlank { null }
                                ?: stringResource(
                                    id = net.dom53.inkita.R.string.series_detail_collection_fallback,
                                    collection.id,
                                ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun webUrl(
    config: AppConfig,
    libraryId: Int?,
    seriesId: Int,
): String? {
    if (!config.isConfigured || libraryId == null) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/library/$libraryId/series/$seriesId"
}

@Composable
private fun ActionsRowV2(
    wantToRead: Boolean,
    collectionsEnabled: Boolean,
    wantToReadEnabled: Boolean,
    onToggleWant: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenWeb: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onOpenCollections,
            enabled = collectionsEnabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(net.dom53.inkita.R.string.general_collections),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(net.dom53.inkita.R.string.general_collections))
        }
        FilledTonalButton(
            onClick = onToggleWant,
            enabled = wantToReadEnabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = if (wantToRead) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = stringResource(net.dom53.inkita.R.string.general_want_to_read),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(net.dom53.inkita.R.string.general_want_to_read))
        }
        IconButton(onClick = onOpenWeb) {
            Icon(Icons.Filled.Public, contentDescription = stringResource(net.dom53.inkita.R.string.general_open_in_browser))
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(net.dom53.inkita.R.string.general_share))
        }
    }
}

@Composable
private fun VolumeGridRow(
    volumes: List<net.dom53.inkita.data.api.dto.VolumeDto>,
    config: AppConfig,
    seriesCoverUrl: String?,
    downloadStates: Map<Int, DownloadVolumeState>,
    onOpenVolume: (net.dom53.inkita.data.api.dto.VolumeDto) -> Unit,
    onLongPressVolume: (net.dom53.inkita.data.api.dto.VolumeDto) -> Unit,
) {
    if (volumes.isEmpty()) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        volumes.forEachIndexed { index, volume ->
            val coverUrl = volumeCoverUrl(config, volume.id) ?: seriesCoverUrl
            val title =
                volume.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = net.dom53.inkita.R.string.series_detail_volume_fallback, index + 1)
            Column(
                modifier =
                    Modifier
                        .width(140.dp)
                        .combinedClickable(
                            onClick = { onOpenVolume(volume) },
                            onLongClick = { onLongPressVolume(volume) },
                        ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    CoverImage(
                        coverUrl = coverUrl,
                        context = LocalContext.current,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                    )
                    val downloadState = downloadStates[volume.id]
                    if (downloadState == DownloadVolumeState.Complete || downloadState == DownloadVolumeState.Partial) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 6.dp, bottom = 10.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                        shape = MaterialTheme.shapes.small,
                                    ).padding(4.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (downloadState == DownloadVolumeState.Complete) {
                                        Icons.Filled.DownloadDone
                                    } else {
                                        Icons.Filled.Downloading
                                    },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if ((volume.pagesRead ?: 0) == 0) {
                        Canvas(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                        ) {
                            val sizePx = 26.dp.toPx()
                            val path =
                                Path().apply {
                                    moveTo(size.width - sizePx, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width, sizePx)
                                    close()
                                }
                            drawPath(
                                path = path,
                                color = Color(0xFFE91E63),
                            )
                        }
                    }
                    val pagesRead = volume.pagesRead ?: 0
                    val pagesTotal = volume.pages ?: 0
                    if (pagesTotal > 0 && pagesRead in 1 until pagesTotal) {
                        val progress =
                            (pagesRead.toFloat() / pagesTotal.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .align(Alignment.BottomStart)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .align(Alignment.BottomStart)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            id = net.dom53.inkita.R.string.series_detail_vol_short,
                            (index + 1).toString(),
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpecialsGridRow(
    specials: List<net.dom53.inkita.data.api.dto.ChapterDto>,
    config: AppConfig,
    seriesCoverUrl: String?,
    downloadStates: Map<Int, ChapterDownloadState>,
    onSelectSpecial: (net.dom53.inkita.data.api.dto.ChapterDto, Int) -> Unit,
    onLongPressSpecial: (net.dom53.inkita.data.api.dto.ChapterDto) -> Unit,
) {
    if (specials.isEmpty()) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        specials.forEachIndexed { index, chapter ->
            val coverUrl = chapterCoverUrl(config, chapter.id) ?: seriesCoverUrl
            val title =
                chapter.titleName?.takeIf { it.isNotBlank() }
                    ?: chapter.title?.takeIf { it.isNotBlank() }
                    ?: chapter.range?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = net.dom53.inkita.R.string.series_detail_chapter_fallback, index + 1)
            Column(
                modifier =
                    Modifier
                        .width(140.dp)
                        .combinedClickable(
                            onClick = { onSelectSpecial(chapter, index) },
                            onLongClick = { onLongPressSpecial(chapter) },
                        ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    CoverImage(
                        coverUrl = coverUrl,
                        context = LocalContext.current,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                    )
                    val downloadState = downloadStates[chapter.id]
                    if (downloadState == ChapterDownloadState.Complete || downloadState == ChapterDownloadState.Partial) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 6.dp, bottom = 10.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                        shape = MaterialTheme.shapes.small,
                                    ).padding(4.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (downloadState == ChapterDownloadState.Complete) {
                                        Icons.Filled.DownloadDone
                                    } else {
                                        Icons.Filled.Downloading
                                    },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    val pagesRead = chapter.pagesRead ?: 0
                    if (pagesRead == 0) {
                        Canvas(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                        ) {
                            val sizePx = 26.dp.toPx()
                            val path =
                                Path().apply {
                                    moveTo(size.width - sizePx, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width, sizePx)
                                    close()
                                }
                            drawPath(
                                path = path,
                                color = Color(0xFFE91E63),
                            )
                        }
                    }
                    val pagesTotal = chapter.pages ?: 0
                    if (pagesTotal > 0 && pagesRead in 1 until pagesTotal) {
                        val progress =
                            (pagesRead.toFloat() / pagesTotal.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .align(Alignment.BottomStart)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .align(Alignment.BottomStart)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            id = net.dom53.inkita.R.string.series_detail_chapter_fallback,
                            index + 1,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeaderInfo(
    seriesId: Int,
    series: net.dom53.inkita.data.api.dto.SeriesDto?,
    metadata: net.dom53.inkita.data.api.dto.SeriesMetadataDto?,
    detail: InkitaDetailV2?,
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onCopyToast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val seriesTitle =
            series?.name?.ifBlank { null }
                ?: context.getString(net.dom53.inkita.R.string.series_detail_series_fallback, seriesId)
        Text(
            text = seriesTitle,
            style = MaterialTheme.typography.titleLarge,
            modifier =
                Modifier.clickable {
                    if (seriesTitle.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(seriesTitle))
                        onCopyToast()
                    }
                },
        )
        val writerNames =
            metadata
                ?.writers
                ?.mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
                ?.joinToString(", ")
                ?.ifBlank { null }
        Text(
            text =
                stringResource(
                    id = net.dom53.inkita.R.string.series_detail_author_label,
                    writerNames ?: "-",
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    id = net.dom53.inkita.R.string.series_detail_publication_label,
                    metadata?.publicationStatus?.let { status ->
                        PublicationState.entries.firstOrNull { it.code == status }?.let { state ->
                            context.getString(state.titleRes)
                        }
                    } ?: "-",
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    id = net.dom53.inkita.R.string.series_detail_release_year_label,
                    metadata?.releaseYear?.toString() ?: "-",
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    id = net.dom53.inkita.R.string.series_detail_avg_time_label,
                    formatHours(detail?.timeLeft?.avgHours) ?: "-",
                    formatHours(series?.avgHoursToRead) ?: "-",
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                "${stringResource(id = net.dom53.inkita.R.string.general_words)}: " +
                    (series?.wordCount?.let { formatCount(it) } ?: "-"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    id = net.dom53.inkita.R.string.series_detail_status_label,
                    readStateLabel(
                        context,
                        unreadCount = detail?.detail?.unreadCount,
                        totalCount = detail?.detail?.totalCount,
                        hasProgress = detail?.hasProgress,
                    ) ?: "-",
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lastRead = series?.latestReadDate?.takeIf { it.isNotBlank() }
        val lastUpdated = series?.lastChapterAdded?.takeIf { it.isNotBlank() }
        Text(
            text =
                stringResource(
                    id = net.dom53.inkita.R.string.series_detail_last_read_label,
                    lastRead?.let { formatDate(it) } ?: "-",
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    id = net.dom53.inkita.R.string.series_detail_last_update_label,
                    lastUpdated?.let { formatDate(it) } ?: "-",
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun relatedSeriesCount(related: net.dom53.inkita.data.api.dto.RelatedSeriesDto): Int = relatedSeriesGroups(related).sumOf { it.items.size }

private data class RelatedGroupUi(
    val titleRes: Int,
    val items: List<net.dom53.inkita.data.api.dto.SeriesDto>,
)

private fun relatedSeriesGroups(related: net.dom53.inkita.data.api.dto.RelatedSeriesDto?): List<RelatedGroupUi> {
    if (related == null) return emptyList()
    val groups =
        listOf(
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_sequels, related.sequels.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_prequels, related.prequels.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_spin_offs, related.spinOffs.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_adaptations, related.adaptations.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_side_stories, related.sideStories.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_characters, related.characters.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_contains, related.contains.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_others, related.others.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_alternative_settings, related.alternativeSettings.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_alternative_versions, related.alternativeVersions.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_doujinshis, related.doujinshis.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_parent, related.parent.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_editions, related.editions.orEmpty()),
            RelatedGroupUi(net.dom53.inkita.R.string.series_detail_related_annuals, related.annuals.orEmpty()),
        )
    return groups.filter { it.items.isNotEmpty() }
}

private fun readStateLabel(
    context: android.content.Context,
    unreadCount: Int?,
    totalCount: Int?,
    hasProgress: Boolean?,
): String? {
    if (unreadCount == null || totalCount == null) {
        return when (hasProgress) {
            true -> context.resources.getString(net.dom53.inkita.R.string.general_reading_status_in_progress)
            false -> context.resources.getString(net.dom53.inkita.R.string.general_reading_status_unread)
            null -> null
        }
    }
    if (totalCount <= 0) {
        return null
    }
    return when {
        unreadCount <= 0 -> context.resources.getString(net.dom53.inkita.R.string.general_reading_status_completed)
        unreadCount >= totalCount -> context.resources.getString(net.dom53.inkita.R.string.general_reading_status_unread)
        else -> context.resources.getString(net.dom53.inkita.R.string.general_reading_status_in_progress)
    }
}
