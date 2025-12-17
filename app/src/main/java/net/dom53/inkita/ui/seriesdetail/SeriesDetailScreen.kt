package net.dom53.inkita.ui.seriesdetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.download.DownloadManager
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.DownloadRepositoryImpl
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.Tag
import net.dom53.inkita.domain.model.Volume
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.DownloadRepository
import net.dom53.inkita.domain.repository.ReaderRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.ui.common.seriesCoverUrl
import net.dom53.inkita.ui.seriesdetail.model.RelatedFilter
import net.dom53.inkita.ui.seriesdetail.model.RelatedGroup
import net.dom53.inkita.ui.seriesdetail.model.SwipeDirection
import net.dom53.inkita.ui.seriesdetail.model.VolumeProgressUi
import net.dom53.inkita.ui.seriesdetail.utils.cleanHtml
import net.dom53.inkita.ui.seriesdetail.utils.formatHours
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DetailViewMode { Chapters, Specials, Related }

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    seriesRepository: SeriesRepository,
    appPreferences: AppPreferences,
    collectionsRepository: CollectionsRepository,
    readerRepository: ReaderRepository? = null,
    readerReturn: net.dom53.inkita.ui.reader.ReaderReturn? = null,
    onConsumeReaderReturn: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenSeries: ((Int) -> Unit)? = null,
    onOpenReader: ((chapterId: Int, page: Int?, seriesId: Int, volumeId: Int, formatId: Int?) -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager(context) }
    val downloadRepo: DownloadRepository =
        remember {
            val db = InkitaDatabase.getInstance(context)
            DownloadRepositoryImpl(db.downloadDao(), downloadManager, context.applicationContext)
        }
    val viewModel: SeriesDetailViewModel =
        viewModel(
            factory =
                SeriesDetailViewModel.provideFactory(
                    seriesId = seriesId,
                    seriesRepository = seriesRepository,
                    collectionsRepository = collectionsRepository,
                    readerRepository = readerRepository,
                    appPreferences = appPreferences,
                    downloadRepository = downloadRepo,
                    downloadManager = downloadManager,
                ),
        )
    val uiState by viewModel.state.collectAsState()
    val toast = remember { Toast.makeText(context, "", Toast.LENGTH_SHORT) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            toast.setText(msg)
            toast.show()
        }
    }
    val scope = rememberCoroutineScope()
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", username = "", apiKey = "", token = "", refreshToken = "", userId = 0),
    )
    var summaryExpanded by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(DetailViewMode.Chapters) }
    var relatedFilter by remember { mutableStateOf(RelatedFilter.All) }
    var targetVolumeId by remember { mutableStateOf<Int?>(null) }
    var targetPageIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(readerReturn) {
        if (readerReturn != null) {
            viewModel.loadDetail()
            onConsumeReaderReturn()
        }
    }
    var showResetProgressDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val volumeProgress = uiState.volumeProgress
    val detail = uiState.detail

    fun openChapter(
        currentDetail: SeriesDetail?,
        volume: Volume,
        chapterIndex: Int,
        openReader: ((Int, Int?, Int, Int, Int?) -> Unit)?,
    ) {
        val sId = currentDetail?.series?.id ?: seriesId
        val bookId = volume.bookId
        if (bookId == null || sId == null) {
            toast.setText(context.getString(R.string.series_detail_ch_loading_err))
            toast.show()
            return
        }
        openReader?.invoke(
            bookId,
            chapterIndex,
            sId,
            volume.id,
            currentDetail?.series?.format?.id ?: Format.Epub.id,
        )
    }

    // Return to last read page on detail when reader is closed
//    LaunchedEffect(readerReturn) {
//        if (readerReturn != null) {
//            targetVolumeId = readerReturn.volumeId
//            targetPageIndex = readerReturn.page
//            viewModel.loadDetail()
//            onConsumeReaderReturn()
//        }
//    }

    LaunchedEffect(targetVolumeId) {
        val volumesList = detail?.volumes.orEmpty()
        val idx = targetVolumeId?.let { id -> volumesList.indexOfFirst { it.id == id } } ?: -1
        if (idx >= 0) {
            val itemIndex = preVolumeItemCount(viewMode) + idx
            val offsetPx = with(density) { 200.dp.roundToPx() }
            listState.animateScrollToItem(itemIndex, scrollOffset = offsetPx)
        }
    }

    LaunchedEffect(viewMode) {
        if (viewMode == DetailViewMode.Related && uiState.relatedGroups.isEmpty() && !uiState.isLoadingRelated) {
            viewModel.loadRelated()
        }
    }

    when {
        uiState.isLoading && detail == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.error != null && detail == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("${stringResource(R.string.general_error)}: ${uiState.error}")
            }
        }

        else -> {
            val currentDetail = detail
            val isRefreshing = uiState.isLoading
            val pullRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.loadDetail() },
                state = pullRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = isRefreshing,
                        state = pullRefreshState,
                    )
                },
            ) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        var menuOpen by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                                }
                                Text(
                                    text = stringResource(R.string.general_detail),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Download all") },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.downloadAllVolumes()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Download missing") },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.downloadMissingVolumes()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear downloads") },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.clearDownloads()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Download queue") },
                                        onClick = {
                                            menuOpen = false
                                            onOpenDownloads?.invoke()
                                        },
                                    )
                                    val isSeriesCompleted = currentDetail?.readState == ReadState.Completed
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (isSeriesCompleted == true) {
                                                    stringResource(R.string.general_mark_unread)
                                                } else {
                                                    stringResource(R.string.general_mark_read)
                                                },
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.toggleSeriesRead()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HeaderSection(
                            detail = currentDetail,
                            config = config,
                        )
                    }

                    item {
                        ActionsRow(
                            wantToRead = uiState.wantToRead,
                            onToggleWant = {
                                if (uiState.isUpdatingWant || !config.isConfigured) return@ActionsRow
                                viewModel.toggleWant()
                            },
                            onOpenCollections = { showCollectionDialog = true },
                            onOpenWeb = {
                                val url = webUrl(config, currentDetail?.series?.libraryId, seriesId)
                                if (url != null) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                } else {
                                    Toast.makeText(context, context.resources.getString(R.string.general_url_not_available), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = {
                                val url = webUrl(config, currentDetail?.series?.libraryId, seriesId)
                                if (url == null) {
                                    Toast.makeText(context, context.resources.getString(R.string.general_url_not_available), Toast.LENGTH_SHORT).show()
                                    return@ActionsRow
                                }
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${currentDetail?.series?.name ?: context.resources.getString(R.string.general_series)} - $url")
                                    }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, context.resources.getString(R.string.series_detail_share_series)))
                                }.onFailure {
                                    Toast.makeText(context, context.resources.getString(R.string.general_unable_to_share), Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }

                    item {
                        SummarySection(
                            summary = cleanHtml(currentDetail?.metadata?.summary ?: currentDetail?.series?.summary),
                            tags = currentDetail?.metadata?.tags ?: emptyList(),
                            writers = currentDetail?.metadata?.writers ?: emptyList(),
                            expanded = summaryExpanded,
                            onToggle = { summaryExpanded = !summaryExpanded },
                        )
                    }

                    item {
                        Button(
                            onClick = {
                                if (currentDetail?.readState == ReadState.Completed) {
                                    showResetProgressDialog = true
                                    return@Button
                                }
                                val volumesList = currentDetail?.volumes.orEmpty()
                                val targetVolume =
                                    volumesList
                                        .firstOrNull { volumeProgress[it.id]?.page != null }
                                        ?: volumesList.firstOrNull()
                                val bookId = targetVolume?.bookId
                                val sId = currentDetail?.series?.id
                                if (targetVolume != null && bookId != null && sId != null) {
                                    val startPage = volumeProgress[targetVolume.id]?.page ?: 0
                                    openChapter(currentDetail, targetVolume, startPage, onOpenReader)
                                } else {
                                    Toast.makeText(context, context.resources.getString(R.string.series_detail_chapter_not_available), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val label =
                                when (currentDetail?.readState) {
                                    ReadState.Unread -> context.resources.getString(R.string.general_start_reading)
                                    ReadState.Completed -> context.resources.getString(R.string.general_completed)
                                    else -> {
                                        val volId = uiState.continueVolumeId
                                        val page = uiState.continuePage?.plus(1)
                                        val volumesList = currentDetail?.volumes.orEmpty()
                                        val targetVolume = volId?.let { id -> volumesList.firstOrNull { it.id == id } }
                                        val volumeIndex = targetVolume?.let { volumesList.indexOf(it) } ?: -1
                                        val volText =
                                            if (targetVolume != null && volumeIndex >= 0) {
                                                volumeNumberText(targetVolume, volumeIndex)
                                            } else {
                                                null
                                            }
                                        if (volText != null && page != null) {
                                            "${context.resources.getString(R.string.general_continue_reading)} Vol. $volText Ch. $page"
                                        } else {
                                            context.resources.getString(R.string.general_continue_reading)
                                        }
                                    }
                                }
                            Text(label)
                        }
                    }

                    item {
                        if (showResetProgressDialog && currentDetail?.series?.id != null) {
                            AlertDialog(
                                onDismissRequest = { showResetProgressDialog = false },
                                title = { Text(context.resources.getString(R.string.series_detail_start_over_question)) },
                                text = { Text(context.resources.getString(R.string.series_detail_reset_and_start_again)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.resetProgress()
                                        showResetProgressDialog = false
                                    }) {
                                        Text(context.resources.getString(R.string.series_detail_reset_progress))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showResetProgressDialog = false }) {
                                        Text(context.resources.getString(R.string.general_cancel))
                                    }
                                },
                            )
                        }
                    }

                    item {
                        ModeSwitcher(
                            viewMode = viewMode,
                            onModeChange = { viewMode = it },
                            hasSpecials = currentDetail?.specials?.isNotEmpty() == true,
                        )
                    }

                    when (viewMode) {
                        DetailViewMode.Chapters -> {
                            val vols = currentDetail?.volumes.orEmpty()
                            vols.forEachIndexed { idx, volume ->
                                item(key = "vol-$idx") {
                                    VolumeCard(
                                        volume = volume,
                                        index = idx,
                                        progress = volumeProgress[volume.id],
                                        seriesId = currentDetail?.series?.id,
                                        onOpenChapter = { idx -> openChapter(currentDetail, volume, idx, onOpenReader) },
                                        initiallyExpanded = volume.id == targetVolumeId,
                                        targetPageIndex = if (volume.id == targetVolumeId) targetPageIndex else null,
                                        onVolumeSwipe = { vol, dir -> viewModel.onVolumeSwipe(vol, dir) },
                                        onChapterSwipe = { vol, chIdx, dir -> viewModel.onChapterSwipe(vol, chIdx, dir) },
                                        isDownloaded = uiState.downloadedVolumeIds.contains(volume.id),
                                        isChapterDownloaded = { idx -> chapterDownloaded(volume, idx, uiState) },
                                        viewMode = viewMode,
                                    )
                                }
                            }
                        }
                        DetailViewMode.Specials -> {
                            val specials = currentDetail?.specials.orEmpty()
                            if (specials.isEmpty()) {
                                item { Text(text = stringResource(R.string.series_detail_no_specials), modifier = Modifier.padding(16.dp)) }
                            } else {
                                specials.forEachIndexed { idx, volume ->
                                    item(key = "spec-$idx") {
                                        VolumeCard(
                                            volume = volume,
                                            index = idx,
                                            progress = volumeProgress[volume.id],
                                            seriesId = currentDetail?.series?.id,
                                            onOpenChapter = { idx -> openChapter(currentDetail, volume, idx, onOpenReader) },
                                            initiallyExpanded = volume.id == targetVolumeId,
                                            targetPageIndex = if (volume.id == targetVolumeId) targetPageIndex else null,
                                            onVolumeSwipe = { vol, dir -> viewModel.onVolumeSwipe(vol, dir) },
                                            onChapterSwipe = { vol, chIdx, dir -> viewModel.onChapterSwipe(vol, chIdx, dir) },
                                            isDownloaded = uiState.downloadedVolumeIds.contains(volume.id),
                                            isChapterDownloaded = { idx -> chapterDownloaded(volume, idx, uiState) },
                                            viewMode = viewMode,
                                        )
                                    }
                                }
                            }
                        }

                        DetailViewMode.Related -> {
                            item {
                                RelatedFilterRow(
                                    selected = relatedFilter,
                                    onSelect = { relatedFilter = it },
                                )
                            }
                            when {
                                uiState.isLoadingRelated -> {
                                    item {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }

                                uiState.relatedError != null -> {
                                    item {
                                        Text(
                                            text = uiState.relatedError ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }

                                uiState.relatedGroups.isEmpty() -> {
                                    item { Text(context.resources.getString(R.string.series_detail_no_related)) }
                                }

                                else -> {
                                    items(
                                        items = applyRelatedFilter(uiState.relatedGroups, relatedFilter),
                                        key = { it.title },
                                    ) { group ->
                                        RelatedGroupCard(
                                            group = group,
                                            config = config,
                                            onClick = { series -> onOpenSeries?.invoke(series.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }

            uiState.pendingVolumeMark?.let { pending ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearPendingDialogs() },
                    title = { Text(context.resources.getString(R.string.series_detail_mark_prev_q)) },
                    text = { Text(context.resources.getString(R.string.series_detail_mark_read_this_and_prev, pendingNumberText(pending, detail?.volumes))) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmPendingVolume(includePrevious = true) }) {
                            Text(context.resources.getString(R.string.series_detail_mark_with_prev))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.confirmPendingVolume(includePrevious = false) }) {
                            Text(context.resources.getString(R.string.series_detail_only_this_vol))
                        }
                    },
                )
            }

            uiState.pendingUnreadVolume?.let { pending ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearPendingDialogs() },
                    title = { Text(stringResource(R.string.series_detail_mark_later_vol_unr_q)) },
                    text = { Text(context.resources.getString(R.string.series_detail_mark_this_and_later, pendingNumberText(pending, detail?.volumes))) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmPendingUnreadVolume(includeNext = true) }) {
                            Text(context.resources.getString(R.string.series_detail_mark_all))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.confirmPendingUnreadVolume(includeNext = false) }) {
                            Text(context.resources.getString(R.string.series_detail_mark_this_vol))
                        }
                    },
                )
            }

            val pendingChapterPage = uiState.pendingChapterPage
            uiState.pendingChapterMark?.let { pendingVol ->
                if (pendingChapterPage != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearPendingDialogs() },
                        title = { Text(context.resources.getString(R.string.series_detail_mark_prev_q)) },
                        text = { Text(context.resources.getString(R.string.series_detail_prev_unread_mark_read_too_q)) },
                        confirmButton = {
                            TextButton(onClick = { viewModel.confirmPendingChapter(markPrevious = true) }) {
                                Text(context.resources.getString(R.string.series_detail_mark_prev_and_continue))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.confirmPendingChapter(markPrevious = false) }) {
                                Text(context.resources.getString(R.string.series_detail_only_this_page))
                            }
                        },
                    )
                }
            }

            val pendingUnreadPage = uiState.pendingChapterUnreadPage
            uiState.pendingChapterUnreadVolume?.let { pendingVol ->
                if (pendingUnreadPage != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearPendingDialogs() },
                        title = { Text(context.resources.getString(R.string.series_detail_mark_later_vol_unr_q)) },
                        text = { Text(context.resources.getString(R.string.series_detail_mark_unred_vol_and_later_too_q)) },
                        confirmButton = {
                            TextButton(onClick = { viewModel.confirmPendingChapterUnread(includeNext = true) }) {
                                Text(context.resources.getString(R.string.series_detail_mark_all))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.confirmPendingChapterUnread(includeNext = false) }) {
                                Text(context.resources.getString(R.string.series_detail_only_this_vol))
                            }
                        },
                    )
                }
            }

            val showScrollToTop by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 2 }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    modifier = Modifier.padding(16.dp),
                ) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = context.resources.getString(R.string.series_detail_scroll_top))
                    }
                }
            }
        }
    }

    if (showCollectionDialog) {
        CollectionDialog(
            collections = uiState.collections,
            isLoading = uiState.isLoadingCollections,
            error = uiState.collectionError,
            seriesId = seriesId,
            series = detail?.series,
            membership = uiState.collectionsWithSeries,
            onDismiss = { showCollectionDialog = false },
            onLoadCollections = {
                viewModel.loadCollections()
            },
            onToggle = { collection, add ->
                if (!config.isConfigured) {
                    Toast.makeText(context, context.resources.getString(R.string.general_no_server_logged_in), Toast.LENGTH_SHORT).show()
                    return@CollectionDialog
                }
                viewModel.toggleCollection(collection, add)
            },
            onCreateCollection = { title ->
                if (title.isBlank()) return@CollectionDialog
                if (!config.isConfigured) return@CollectionDialog
                viewModel.createCollection(title)
            },
        )
    }
}

@Composable
private fun CollectionDialog(
    collections: List<Collection>,
    isLoading: Boolean,
    error: String?,
    seriesId: Int,
    series: Series?,
    membership: Set<Int>,
    onDismiss: () -> Unit,
    onLoadCollections: () -> Unit,
    onToggle: (Collection, Boolean) -> Unit,
    onCreateCollection: (String) -> Unit,
) {
    var newTitle by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.general_collections)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    if (collections.isEmpty()) {
                        Text(stringResource(R.string.series_detail_no_colls_reload))
                        TextButton(onClick = onLoadCollections) {
                            Text(stringResource(R.string.series_detail_load_colls))
                        }
                    } else {
                        collections.forEach { collection ->
                            val isInCollection = membership.contains(collection.id)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggle(collection, !isInCollection) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(collection.name)
                                androidx.compose.material3.Switch(
                                    checked = isInCollection,
                                    onCheckedChange = { onToggle(collection, it) },
                                )
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.general_create_new_coll))
                androidx.compose.material3.OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.general_coll_name)) },
                )
                Button(
                    onClick = {
                        onCreateCollection(newTitle.trim())
                        newTitle = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newTitle.isNotBlank(),
                ) {
                    Text(stringResource(R.string.general_create_and_add))
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_close))
            }
        },
    )
}

private fun publicationStatusLabel(
    context: Context,
    value: Int?,
): String? =
    when (value) {
        0 -> context.resources.getString(R.string.general_pub_status_ongoing)
        1 -> context.resources.getString(R.string.general_pub_status_hiatus)
        2 -> context.resources.getString(R.string.general_pub_status_completed)
        3 -> context.resources.getString(R.string.general_pub_status_cancelled)
        4 -> context.resources.getString(R.string.general_pub_status_ended)
        else -> null
    }

private fun readStateLabel(
    context: Context,
    state: ReadState?,
): String? =
    when (state) {
        ReadState.Completed -> context.resources.getString(R.string.general_reading_status_completed)
        ReadState.InProgress -> context.resources.getString(R.string.general_reading_status_in_progress)
        ReadState.Unread -> context.resources.getString(R.string.general_reading_status_unread)
        null -> null
    }

private fun formatVolumeTitle(
    volume: Volume,
    index: Int,
): String {
    val numberText = volumeNumberText(volume, index)
    val title = volume.name?.takeIf { it.isNotBlank() }
    return if (title != null) "Vol. $numberText - $title" else "Vol. $numberText"
}

private fun volumeNumberText(
    volume: Volume,
    index: Int,
): String {
    val volNumber = volume.minNumber ?: volume.maxNumber ?: (index + 1).toFloat()
    return if (volNumber % 1f == 0f) volNumber.toInt().toString() else volNumber.toString()
}

private fun pendingNumberText(
    volume: Volume,
    volumes: List<Volume>?,
): String {
    val idx = volumes?.indexOfFirst { it.id == volume.id } ?: -1
    return if (idx >= 0) {
        volumeNumberText(volume, idx)
    } else {
        volumeNumberText(volume, 0)
    }
}

private fun formatChapterTitle(
    context: Context,
    volume: Volume,
    volumeIndex: Int,
    chapterIndex: Int,
    totalChapters: Int,
    title: String?,
    viewMode: DetailViewMode = DetailViewMode.Chapters,
): String {
    val volNumber = volume.minNumber ?: volume.maxNumber ?: (volumeIndex + 1).toFloat()
    val numberText = if (volNumber % 1f == 0f) volNumber.toInt().toString() else volNumber.toString()
    val safeTitle =
        title
            ?.takeIf { it.isNotBlank() }
            ?: volume.name?.takeIf { it.isNotBlank() }
            ?: "${context.resources.getString(R.string.general_page)} ${chapterIndex + 1}"
    val prefix = if (viewMode == DetailViewMode.Specials) "Special" else "Vol."
    return "$prefix $numberText Ch. ${chapterIndex + 1}/$totalChapters - $safeTitle"
}

private fun preVolumeItemCount(viewMode: DetailViewMode): Int {
    // Items before volumes in Chapters mode:
    // 0: back bar, 1: header, 2: actions, 3: summary, 4: start button, 5: mode switcher
    return if (viewMode != DetailViewMode.Related) 6 else 0
}

private fun applyRelatedFilter(
    groups: List<RelatedGroup>,
    filter: RelatedFilter,
): List<RelatedGroup> {
    if (filter == RelatedFilter.All) return groups
    return groups.filter { it.type == filter }
}

private fun chapterDownloaded(
    volume: Volume,
    pageIdx: Int,
    state: SeriesDetailState,
): Boolean {
    if (state.detail?.series?.format == Format.Pdf) {
        return state.downloadedVolumeIds.contains(volume.id)
    }
    val id = volume.bookId ?: return false
    return state.downloadedChapters[id]?.contains(pageIdx) == true
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
private fun HeaderSection(
    detail: SeriesDetail?,
    config: AppConfig,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val context = LocalContext.current
        val clipboardManager = LocalClipboardManager.current
        val localCover =
            detail?.series?.localThumbPath?.let { java.io.File(it) }
                ?: detail?.series?.id?.let { id ->
                    val f = java.io.File(context.filesDir, "thumbnails/$id.jpg")
                    if (f.exists()) f else null
                }
        val coverUrl = detail?.series?.id?.let { seriesCoverUrl(config, it) }
        Box(
            modifier =
                Modifier
                    .width(140.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model =
                    (localCover ?: coverUrl)?.let {
                        ImageRequest
                            .Builder(context)
                            .data(it)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    },
                contentDescription = detail?.series?.name,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val seriesTitle = detail?.series?.name.orEmpty()
            Text(
                seriesTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.clickable {
                        if (seriesTitle.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(seriesTitle))
                        }
                    },
            )
            detail
                ?.metadata
                ?.writers
                ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
                ?.takeIf { it.isNotEmpty() }
                ?.let { writers ->
                    Text(
                        text = "${stringResource(R.string.general_author)}: ${writers.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            publicationStatusLabel(context, detail?.metadata?.publicationStatus)?.let {
                Text(
                    text = "${stringResource(R.string.general_publication)}: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            readStateLabel(context, detail?.readState)?.let {
                Text(
                    text = "${stringResource(R.string.general_state)}: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            formatHours(detail?.minHoursToRead, detail?.maxHoursToRead, detail?.avgHoursToRead)?.let { label ->
                Text(
                    text = "${stringResource(R.string.general_reading_time)}: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            (
                formatHours(detail?.timeLeftMin, detail?.timeLeftMax, detail?.timeLeftAvg)
                    ?: formatHours(detail?.minHoursToRead, detail?.maxHoursToRead, detail?.avgHoursToRead)
            )?.let { label ->
                Text(
                    text = "${stringResource(R.string.general_remaining_time)}: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionsRow(
    wantToRead: Boolean,
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
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = stringResource(R.string.general_collections),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.general_collections))
        }
        FilledTonalButton(
            onClick = onToggleWant,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = if (wantToRead) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = stringResource(R.string.general_want_to_read),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.general_want_to_read))
        }
        IconButton(onClick = onOpenWeb) {
            Icon(Icons.Filled.Public, contentDescription = stringResource(R.string.general_open_in_browser))
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.general_share))
        }
    }
}

@Composable
private fun VolumeCard(
    volume: Volume,
    index: Int,
    progress: VolumeProgressUi?,
    seriesId: Int?,
    onOpenChapter: (Int) -> Unit = {},
    initiallyExpanded: Boolean = false,
    targetPageIndex: Int? = null,
    onVolumeSwipe: (Volume, SwipeDirection) -> Unit = { _, _ -> },
    onChapterSwipe: (Volume, Int, SwipeDirection) -> Unit = { _, _, _ -> },
    isDownloaded: Boolean = false,
    isChapterDownloaded: (Int) -> Boolean = { false },
    viewMode: DetailViewMode = DetailViewMode.Chapters,
) {
    var expanded by remember(volume.id, initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val chapterCount = volume.chapters.size
    val context = LocalContext.current
    SwipeableContainer(
        onSwipeComplete = { dir -> onVolumeSwipe(volume, dir) },
    ) { frontModifier ->
        Card(
            modifier = frontModifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = formatVolumeTitle(volume, index),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val timeLabel = formatHours(volume.minHoursToRead, volume.maxHoursToRead, volume.avgHoursToRead)
                        val subtitle =
                            if (timeLabel != null) {
                                "$chapterCount ${stringResource(R.string.general_chapters)} - $timeLabel" // Todo: localize
                            } else {
                                "$chapterCount ${stringResource(R.string.general_chapters)}" // Todo: localize
                            }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val isVolumeCompleted = volume.chapters.all { it.status == ReadState.Completed }
                        if (!isVolumeCompleted) {
                            progress?.let { prog ->
                                val pageLabel =
                                    prog.page?.let { p ->
                                        val total = prog.totalPages ?: volume.chapters.size
                                        val totalSafe = if (total > 0) total else volume.chapters.size
                                        "${stringResource(R.string.series_detail_resume_page)} ${p + 1}/$totalSafe"
                                    }
                                if (pageLabel != null) {
                                    Text(
                                        text = pageLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Filled.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (volume.bookId == null || volume.chapters.isEmpty()) {
                        Text(
                            text = stringResource(R.string.series_detail_ch_loading_err),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        volume.chapters.forEachIndexed { idx, chapter ->
                            val bringIntoViewRequester = remember { BringIntoViewRequester() }
                            LaunchedEffect(expanded, targetPageIndex) {
                                if (expanded && targetPageIndex != null && targetPageIndex == idx) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                            val statusColor =
                                when (chapter.status) {
                                    ReadState.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
                                    ReadState.InProgress -> MaterialTheme.colorScheme.primary
                                    ReadState.Unread -> MaterialTheme.colorScheme.onSurface
                                }
                            val chapterIsDownloaded = isChapterDownloaded(idx)
                            // Keep text color based on read status; downloaded state is shown by icon, not color.
                            val chapterColor = statusColor
                            SwipeableContainer(
                                onSwipeComplete = { dir -> onChapterSwipe(volume, idx, dir) },
                            ) { rowModifier ->
                                Row(
                                    modifier =
                                        rowModifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable {
                                                onOpenChapter(idx)
                                            }.padding(horizontal = 10.dp, vertical = 8.dp)
                                            .bringIntoViewRequester(bringIntoViewRequester),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = formatChapterTitle(context, volume, index, idx, chapterCount, chapter.title, viewMode = viewMode),
                                        color = chapterColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (chapterIsDownloaded) {
                                        Icon(
                                            imageVector = Icons.Filled.DownloadDone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedFilterRow(
    selected: RelatedFilter,
    onSelect: (RelatedFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${stringResource(R.string.general_filter)}: ${selected.label}")
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        RelatedFilter.values().forEach { filter ->
            DropdownMenuItem(
                text = { Text(filter.label) },
                onClick = {
                    onSelect(filter)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun RelatedGroupCard(
    group: RelatedGroup,
    config: AppConfig,
    onClick: (Series) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(group.series, key = { it.id }) { series ->
                RelatedCardItem(
                    series = series,
                    config = config,
                    onClick = { onClick(series) },
                )
            }
        }
    }
}

@Composable
private fun RelatedCardItem(
    series: Series,
    config: AppConfig,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val localCover =
        series.localThumbPath?.let { java.io.File(it) }
            ?: run {
                val f = java.io.File(context.filesDir, "thumbnails/${series.id}.jpg")
                if (f.exists()) f else null
            }
    val coverUrl = seriesCoverUrl(config, series.id)
    ElevatedCard(
        modifier =
            Modifier
                .width(140.dp)
                .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model =
                    (localCover ?: coverUrl)?.let {
                        ImageRequest
                            .Builder(context)
                            .data(it)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    },
                contentDescription = series.name,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = series.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun ModeSwitcher(
    viewMode: DetailViewMode,
    onModeChange: (DetailViewMode) -> Unit,
    hasSpecials: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = viewMode == DetailViewMode.Chapters,
            onClick = { onModeChange(DetailViewMode.Chapters) },
            label = { Text(stringResource(R.string.general_chapters)) },
            leadingIcon = {
                if (viewMode == DetailViewMode.Chapters) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = null)
                }
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                ),
        )
        FilterChip(
            selected = viewMode == DetailViewMode.Specials,
            onClick = { onModeChange(DetailViewMode.Specials) },
            enabled = hasSpecials,
            label = { Text(stringResource(R.string.series_detail_specials)) },
            leadingIcon = {
                if (viewMode == DetailViewMode.Specials) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = null)
                }
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                ),
        )
        FilterChip(
            selected = viewMode == DetailViewMode.Related,
            onClick = { onModeChange(DetailViewMode.Related) },
            label = { Text(stringResource(R.string.general_related)) },
            leadingIcon = {
                if (viewMode == DetailViewMode.Related) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = null)
                }
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummarySection(
    summary: String?,
    tags: List<Tag>,
    writers: List<Person>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val summaryText = summary?.ifBlank { null }
        val nonNullTags = tags.mapNotNull { it.title?.takeIf { t -> t.isNotBlank() } }
        val writerNames = writers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
        if (summaryText != null) {
            Text(
                text = summaryText,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onToggle) {
                    Text(if (expanded) stringResource(R.string.general_less) else stringResource(R.string.general_more))
                }
            }
        }
        if (expanded) {
            if (nonNullTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.general_tags),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nonNullTags.forEach { title ->
                        AssistChip(onClick = {}, label = { Text(title) })
                    }
                }
            }
            if (writerNames.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "${stringResource(R.string.general_authors)}: ${writerNames.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SwipeableContainer(
    modifier: Modifier = Modifier,
    onSwipeComplete: (SwipeDirection) -> Unit,
    backgroundContent: @Composable ((Float) -> Unit)? = { progress -> SwipeBackground(progress) },
    content: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val threshold = 120f

    Box(modifier = modifier) {
        val progress = (abs(offsetX.value) / threshold).coerceIn(0f, 1.2f)
        if (backgroundContent != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
            ) {
                backgroundContent(progress.coerceIn(0f, 1f))
            }
        }
        val frontModifier =
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val absOffset = abs(offsetX.value)
                            if (absOffset >= threshold) {
                                onSwipeComplete(if (offsetX.value > 0) SwipeDirection.Right else SwipeDirection.Left)
                            }
                            scope.launch { offsetX.animateTo(0f, spring(stiffness = 300f)) }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, spring(stiffness = 300f)) }
                        },
                    ) { _, dragAmount ->
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                }
        content(frontModifier)
    }
}

@Composable
private fun SwipeBackground(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    val alpha = 0.15f + 0.85f * clamped
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
        }
    }
}
