package net.dom53.inkita.ui.seriesdetail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.ui.browse.utils.PublicationState
import net.dom53.inkita.ui.common.collectionCoverUrl
import net.dom53.inkita.ui.common.seriesCoverUrl
import net.dom53.inkita.ui.common.volumeCoverUrl
import net.dom53.inkita.ui.seriesdetail.utils.cleanHtml

@Composable
fun SeriesDetailScreenV2(
    seriesId: Int,
    appPreferences: AppPreferences,
    collectionsRepository: net.dom53.inkita.domain.repository.CollectionsRepository,
    onOpenReader: (chapterId: Int, page: Int, seriesId: Int, volumeId: Int, formatId: Int?) -> Unit,
    onOpenVolume: (Int) -> Unit,
    onOpenSeries: (Int) -> Unit,
    readerReturn: net.dom53.inkita.ui.reader.ReaderReturn? = null,
    onConsumeReaderReturn: () -> Unit = {},
    refreshSignal: Boolean = false,
    onConsumeRefreshSignal: () -> Unit = {},
    onBack: () -> Unit,
) {
    val viewModel: SeriesDetailViewModelV2 =
        viewModel(
            factory = SeriesDetailViewModelV2.provideFactory(seriesId, appPreferences, collectionsRepository),
        )
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(uiState.showLoadedToast) {
        if (uiState.showLoadedToast) {
            Toast.makeText(context, "Detail data loaded", Toast.LENGTH_SHORT).show()
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
    var summaryExpanded by remember { mutableStateOf(false) }
    var coverExpanded by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null)
            }
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Loading...",
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
                            text = uiState.error ?: "Error",
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
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionsRowV2(
                            wantToRead = detail?.wantToRead == true,
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
                                    Toast.makeText(context, "Missing library id", Toast.LENGTH_SHORT).show()
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
                                val title = series?.name?.ifBlank { null } ?: "Series $seriesId"
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
                            genres = metadata?.genres?.mapNotNull { it.title }.orEmpty(),
                            tags = metadata?.tags?.mapNotNull { it.title }.orEmpty(),
                            expanded = summaryExpanded,
                            onToggle = { summaryExpanded = !summaryExpanded },
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
                                    "Pokračovat Vol. $volumeNumber Ch. $page"
                                } else {
                                    "Pokračovat Ch. $page"
                                }
                            } else {
                                "Začíst číst"
                            }
                        Button(
                            onClick = {
                                val chapterId = readerProgress?.chapterId ?: continuePoint?.id
                                val volumeId = readerProgress?.volumeId ?: continuePoint?.volumeId
                                val sid = detail?.series?.id ?: seriesId
                                val fmt = detail?.series?.format
                                if (chapterId == null || volumeId == null) {
                                    Toast.makeText(context, "Not implemented yet", Toast.LENGTH_SHORT).show()
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
                            VolumeGridRow(
                                volumes = detail?.detail?.volumes.orEmpty(),
                                config = config,
                                seriesCoverUrl = series?.id?.let { seriesCoverUrl(config, it) },
                                onOpenVolume = { volume ->
                                    VolumeDetailCache.put(
                                        VolumeDetailPayload(
                                            seriesId = seriesId,
                                            volume = volume,
                                            formatId = detail?.series?.format,
                                        ),
                                    )
                                    onOpenVolume(volume.id)
                                },
                            )
                        }
                        if (selectedTab == SeriesDetailTab.Related) {
                            RelatedCollectionsSection(
                                related = detail?.related,
                                collections = detail?.collections.orEmpty(),
                                config = config,
                                onOpenSeries = onOpenSeries,
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
                        Toast.makeText(
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
                            .clickable { coverExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun RelatedCollectionsSection(
    related: net.dom53.inkita.data.api.dto.RelatedSeriesDto?,
    collections: List<net.dom53.inkita.data.api.dto.AppUserCollectionDto>,
    config: AppConfig,
    onOpenSeries: (Int) -> Unit,
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
                            text = item.name?.ifBlank { null } ?: "Series ${item.id}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = group.title,
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
                    modifier = Modifier.width(140.dp),
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
                        text = collection.title?.ifBlank { null } ?: "Collection ${collection.id}",
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
                contentDescription = stringResource(net.dom53.inkita.R.string.general_collections),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(net.dom53.inkita.R.string.general_collections))
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
    onOpenVolume: (net.dom53.inkita.data.api.dto.VolumeDto) -> Unit,
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
                    ?: "Volume ${index + 1}"
            Column(
                modifier =
                    Modifier
                        .width(140.dp)
                        .clickable { onOpenVolume(volume) },
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
                    text = "Vol. ${index + 1}",
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
        val seriesTitle = series?.name?.ifBlank { null } ?: "Series $seriesId"
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
            text = "Author: ${writerNames ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                "Publication: " +
                    (
                        metadata?.publicationStatus?.let { status ->
                            PublicationState.entries.firstOrNull { it.code == status }?.let { state ->
                                context.getString(state.titleRes)
                            }
                        } ?: "-"
                    ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                "Release year: " +
                    (metadata?.releaseYear?.toString() ?: "-"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                "Avg time: " +
                    (formatHours(series?.avgHoursToRead) ?: "-") +
                    " / " +
                    (formatHours(detail?.timeLeft?.avgHours) ?: "-"),
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
                "Status: " +
                    (
                        readStateLabel(
                            context,
                            unreadCount = detail?.detail?.unreadCount,
                            totalCount = detail?.detail?.totalCount,
                            hasProgress = detail?.hasProgress,
                        ) ?: "-"
                    ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lastRead = series?.latestReadDate?.takeIf { it.isNotBlank() }
        val lastUpdated = series?.lastChapterAdded?.takeIf { it.isNotBlank() }
        Text(
            text = "Last read: ${lastRead?.let { formatDate(it) } ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Last update: ${lastUpdated?.let { formatDate(it) } ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun relatedSeriesCount(related: net.dom53.inkita.data.api.dto.RelatedSeriesDto): Int {
    return relatedSeriesGroups(related).sumOf { it.items.size }
}

private data class RelatedGroupUi(
    val title: String,
    val items: List<net.dom53.inkita.data.api.dto.SeriesDto>,
)

private fun relatedSeriesGroups(
    related: net.dom53.inkita.data.api.dto.RelatedSeriesDto?,
): List<RelatedGroupUi> {
    if (related == null) return emptyList()
    val groups =
        listOf(
            RelatedGroupUi("Sequels", related.sequels.orEmpty()),
            RelatedGroupUi("Prequels", related.prequels.orEmpty()),
            RelatedGroupUi("Spin-offs", related.spinOffs.orEmpty()),
            RelatedGroupUi("Adaptations", related.adaptations.orEmpty()),
            RelatedGroupUi("Side stories", related.sideStories.orEmpty()),
            RelatedGroupUi("Characters", related.characters.orEmpty()),
            RelatedGroupUi("Contains", related.contains.orEmpty()),
            RelatedGroupUi("Others", related.others.orEmpty()),
            RelatedGroupUi("Alternative settings", related.alternativeSettings.orEmpty()),
            RelatedGroupUi("Alternative versions", related.alternativeVersions.orEmpty()),
            RelatedGroupUi("Doujinshis", related.doujinshis.orEmpty()),
            RelatedGroupUi("Parent", related.parent.orEmpty()),
            RelatedGroupUi("Editions", related.editions.orEmpty()),
            RelatedGroupUi("Annuals", related.annuals.orEmpty()),
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
