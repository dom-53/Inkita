package net.dom53.inkita.ui.library

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.ui.common.seriesCoverUrl
import java.io.File

@Composable
fun LibraryScreen(
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    appPreferences: AppPreferences,
    cacheManager: CacheManager,
    onOpenSeries: (Int) -> Unit,
) {
    val viewModel: LibraryViewModel =
        viewModel(
            factory = LibraryViewModel.provideFactory(seriesRepository, collectionsRepository, appPreferences, cacheManager),
        )
    val uiState by viewModel.state.collectAsState()

    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", username = "", apiKey = "", token = "", refreshToken = "", userId = 0),
    )
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { /* no-op, notification manager will reflect permission */ },
        )
    var askedNotificationPermission by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.prefetchInProgress) {
        if (uiState.prefetchInProgress && !askedNotificationPermission) {
            askedNotificationPermission = true
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val nm = NotificationManagerCompat.from(context)
                if (!nm.areNotificationsEnabled()) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(8.dp),
    ) {
        val inProgressLabel = stringResource(R.string.library_in_progress)
        val wantToReadLabel = stringResource(R.string.general_want_to_read)
        val tabs =
            remember(uiState.collections) {
                buildList {
                    add(inProgressLabel)
                    add(wantToReadLabel)
                    addAll(uiState.collections.map { it.name })
                }
            }

        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTabIndex,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = uiState.selectedTabIndex == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { Text(title) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${stringResource(R.string.general_error)}: ${uiState.error}")
                }
            }

            uiState.series.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val msg =
                        when (uiState.selectedTabIndex) {
                            0 -> stringResource(R.string.library_empty_in_progress)
                            1 -> stringResource(R.string.library_empty_want_to_read)
                            else -> stringResource(R.string.general_empty_collection)
                        }
                    Text(msg)
                }
            }

            else -> {
                SeriesGrid(
                    seriesList = uiState.series,
                    config = config,
                    isLoadingMore = uiState.isLoadingMore,
                    onSeriesClick = { series ->
                        viewModel.refreshSeriesReadState(series.id)
                        onOpenSeries(series.id)
                    },
                    onLoadMore = { viewModel.loadNextPage() },
                )
            }
        }
    }
}

@Composable
private fun SeriesGrid(
    seriesList: List<Series>,
    config: AppConfig,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onSeriesClick: (Series) -> Unit,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            lastVisible to total
        }.distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (lastVisible >= total - 6) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seriesList, key = { it.id }) { series ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSeriesClick(series) },
            ) {
                val context = LocalContext.current
                val localFile =
                    series.localThumbPath?.let { java.io.File(it) }
                        ?: run {
                            val f = java.io.File(context.filesDir, "thumbnails/${series.id}.jpg")
                            if (f.exists()) f else null
                        }
                val imageData = localFile ?: seriesCoverUrl(config, series.id)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model =
                            imageData?.let {
                                ImageRequest
                                    .Builder(context)
                                    .data(it)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            },
                        contentDescription = series.name,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                Text(
                    series.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                readingStatusLabel(LocalContext.current, series.readState)?.let { status ->
                    Text(
                        text = status,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                color =
                                    when (series.readState) {
                                        ReadState.Completed -> MaterialTheme.colorScheme.primary
                                        ReadState.InProgress -> MaterialTheme.colorScheme.secondary
                                        ReadState.Unread, null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

private fun readingStatusLabel(
    context: Context,
    state: ReadState?,
): String? =
    when (state) {
        ReadState.Completed -> context.resources.getString(R.string.general_reading_status_completed)
        ReadState.InProgress -> context.resources.getString(R.string.general_reading_status_in_progress)
        ReadState.Unread -> context.resources.getString(R.string.general_reading_status_unread)
        null -> null
    }
