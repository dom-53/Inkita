package net.dom53.inkita.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.LibraryRepository
import net.dom53.inkita.domain.repository.PersonRepository
import net.dom53.inkita.domain.repository.ReadingListRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.ui.common.collectionCoverUrl
import net.dom53.inkita.ui.common.personCoverUrl
import net.dom53.inkita.ui.common.readingListCoverUrl
import net.dom53.inkita.ui.common.seriesCoverUrl
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems

@Composable
fun LibraryV2Screen(
    libraryRepository: LibraryRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readingListRepository: ReadingListRepository,
    personRepository: PersonRepository,
    cacheManager: CacheManager,
    appPreferences: AppPreferences,
    onOpenSeries: (Int) -> Unit,
    initialCollectionId: Int? = null,
    initialCollectionName: String? = null,
) {
    val viewModel: LibraryV2ViewModel =
        viewModel(
            factory =
                LibraryV2ViewModel.provideFactory(
                    libraryRepository,
                    seriesRepository,
                    collectionsRepository,
                    readingListRepository,
                    personRepository,
                    cacheManager,
                    appPreferences,
                ),
        )
    val uiState by viewModel.state.collectAsState()
    var presetApplied by remember { mutableStateOf(false) }

    LaunchedEffect(initialCollectionId) {
        if (presetApplied) return@LaunchedEffect
        if (initialCollectionId != null) {
            viewModel.openCollectionFromExternal(initialCollectionId, initialCollectionName)
            presetApplied = true
        }
    }
    val context = LocalContext.current
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    val showDrawerDebug = false
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var drawerWidthPx by remember { mutableStateOf(0) }
    val drawerAlpha by remember(drawerState, drawerWidthPx) {
        derivedStateOf {
            val width = drawerWidthPx.toFloat()
            val offset = drawerState.currentOffset
            if (width <= 0f || offset.isNaN()) {
                if (drawerState.isOpen) 1f else 0f
            } else {
                ((offset + width) / width).coerceIn(0f, 1f)
            }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.75f)
                        .fillMaxHeight()
                        .background(if (showDrawerDebug) Color.Red else Color(0xFF121212))
                        .onSizeChanged { drawerWidthPx = it.width }
                        .alpha(drawerAlpha)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(text = "Menu", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                DrawerItem(
                    icon = Icons.Filled.Home,
                    label = "Home",
                    selected = uiState.selectedSection == LibraryV2Section.Home,
                    onClick = {
                        viewModel.selectSection(LibraryV2Section.Home)
                        scope.launch { drawerState.close() }
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.BookmarkAdd,
                    label = "Want To Read",
                    selected = uiState.selectedSection == LibraryV2Section.WantToRead,
                    onClick = {
                        viewModel.selectSection(LibraryV2Section.WantToRead)
                        scope.launch { drawerState.close() }
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.CollectionsBookmark,
                    label = "Collections",
                    selected = uiState.selectedSection == LibraryV2Section.Collections,
                    onClick = {
                        viewModel.selectSection(LibraryV2Section.Collections)
                        scope.launch { drawerState.close() }
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.MenuBook,
                    label = "Reading List",
                    selected = uiState.selectedSection == LibraryV2Section.ReadingList,
                    onClick = {
                        viewModel.selectSection(LibraryV2Section.ReadingList)
                        scope.launch { drawerState.close() }
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.Bookmarks,
                    label = "Bookmarks",
                    onClick = {
                        android.widget.Toast
                            .makeText(
                                context,
                                "Not implemented yet",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.LibraryBooks,
                    label = "All Series",
                    onClick = {
                        android.widget.Toast
                            .makeText(
                                context,
                                "Not implemented yet",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                    },
                )
                DrawerItem(
                    icon = Icons.Filled.People,
                    label = "Browse People",
                    selected = uiState.selectedSection == LibraryV2Section.BrowsePeople,
                    onClick = {
                        viewModel.selectSection(LibraryV2Section.BrowsePeople)
                        scope.launch { drawerState.close() }
                    },
                )
                HorizontalDivider()
                when {
                    uiState.isLoading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }

                    uiState.error != null -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = uiState.error ?: "", style = MaterialTheme.typography.bodyMedium)
                    }

                    else -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        uiState.libraries.forEach { library ->
                            val label = library.name.ifBlank { "Library ${library.id}" }
                            DrawerItem(
                                icon = Icons.Filled.LocalLibrary,
                                label = label,
                                selected = uiState.selectedLibraryId == library.id,
                                onClick = {
                                    viewModel.selectLibrary(library)
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    }
                }
            }
        },
    ) {
        val topTitle =
            when (uiState.selectedSection) {
                LibraryV2Section.Home -> "Home"
                LibraryV2Section.WantToRead -> "Want To Read"
                LibraryV2Section.Collections -> "Collections"
                LibraryV2Section.ReadingList -> "Reading List"
                LibraryV2Section.BrowsePeople -> "Browse People"
                LibraryV2Section.LibrarySeries -> uiState.selectedLibraryName ?: "Library"
            }
        val canHandleBack =
            uiState.selectedSection != LibraryV2Section.Home || uiState.selectedCollectionId != null
        if (canHandleBack) {
            BackHandler { viewModel.handleBack() }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    },
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = null)
                }
                Text(
                    text = topTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (uiState.selectedSection) {
                LibraryV2Section.Home -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        when {
                            uiState.isHomeLoading &&
                                uiState.onDeck.isEmpty() &&
                                uiState.recentlyUpdated.isEmpty() &&
                                uiState.recentlyAdded.isEmpty() -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            uiState.homeError != null &&
                                uiState.onDeck.isEmpty() &&
                                uiState.recentlyUpdated.isEmpty() &&
                                uiState.recentlyAdded.isEmpty() -> {
                                Text(
                                    text = uiState.homeError ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            else -> {
                                HomeSection(
                                    title = "On Deck",
                                    items = uiState.onDeck,
                                    config = config,
                                    onOpenSeries = onOpenSeries,
                                )
                                HomeSection(
                                    title = "Recently Updated Series",
                                    items = uiState.recentlyUpdated,
                                    config = config,
                                    onOpenSeries = onOpenSeries,
                                )
                                HomeSection(
                                    title = "Newly Added Series",
                                    items = uiState.recentlyAdded,
                                    config = config,
                                    onOpenSeries = onOpenSeries,
                                )
                            }
                        }
                    }
                }

                LibraryV2Section.WantToRead -> {
                    WantToReadGrid(
                        items = uiState.wantToRead,
                        isLoading = uiState.isWantToReadLoading,
                        error = uiState.wantToReadError,
                        config = config,
                        onOpenSeries = onOpenSeries,
                    )
                }

                LibraryV2Section.Collections -> {
                    if (uiState.selectedCollectionId != null) {
                        CollectionSeriesGrid(
                            title = uiState.selectedCollectionName ?: "Collection",
                            items = uiState.collectionSeries,
                            isLoading = uiState.isCollectionSeriesLoading,
                            error = uiState.collectionSeriesError,
                            config = config,
                            onBack = { viewModel.selectCollection(null) },
                            onOpenSeries = onOpenSeries,
                        )
                    } else {
                        CollectionsGrid(
                            items = uiState.collections,
                            isLoading = uiState.isCollectionsLoading,
                            error = uiState.collectionsError,
                            config = config,
                            onSelect = { collection -> viewModel.selectCollection(collection) },
                        )
                    }
                }
                LibraryV2Section.ReadingList -> {
                    ReadingListGrid(
                        items = uiState.readingLists,
                        isLoading = uiState.isReadingListsLoading,
                        error = uiState.readingListsError,
                        config = config,
                    )
                }

                LibraryV2Section.BrowsePeople -> {
                    PeopleGrid(
                        items = uiState.people,
                        isLoading = uiState.isPeopleLoading,
                        isLoadingMore = uiState.isPeopleLoadingMore,
                        error = uiState.peopleError,
                        config = config,
                        onLoadMore = { viewModel.loadMorePeople() },
                    )
                }
                LibraryV2Section.LibrarySeries -> {
                    LibrarySeriesGrid(
                        items = uiState.librarySeries,
                        isLoading = uiState.isLibrarySeriesLoading,
                        isLoadingMore = uiState.isLibrarySeriesLoadingMore,
                        error = uiState.librarySeriesError,
                        accessDenied = uiState.libraryAccessDenied,
                        config = config,
                        onLoadMore = { viewModel.loadMoreLibrarySeries() },
                        onOpenSeries = onOpenSeries,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier =
        if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        } else {
            Modifier.fillMaxWidth()
        }
    Row(
        modifier =
            rowModifier
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        val color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

@Composable
private fun HomeSection(
    title: String,
    items: List<HomeSeriesItem>,
    config: AppConfig,
    onOpenSeries: (Int) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowItems(items, key = { it.id }) { item ->
                SeriesCard(
                    item = item,
                    config = config,
                    onOpenSeries = onOpenSeries,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SeriesCard(
    item: HomeSeriesItem,
    config: AppConfig,
    onOpenSeries: (Int) -> Unit,
) {
    val imageUrl = item.localThumbPath ?: seriesCoverUrl(config, item.id)
    Column(
        modifier =
            Modifier
                .width(120.dp)
                .padding(bottom = 4.dp)
                .clickable { onOpenSeries(item.id) },
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(170.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WantToReadGrid(
    items: List<net.dom53.inkita.domain.model.Series>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
    onOpenSeries: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_empty_reading_list),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id }) { series ->
                        WantToReadCard(
                            series = series,
                            config = config,
                            onOpenSeries = onOpenSeries,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WantToReadCard(
    series: net.dom53.inkita.domain.model.Series,
    config: AppConfig,
    onOpenSeries: (Int) -> Unit,
) {
    val imageUrl = seriesCoverUrl(config, series.id)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenSeries(series.id) },
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = series.name.ifBlank { "Series ${series.id}" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionsGrid(
    items: List<net.dom53.inkita.domain.model.Collection>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
    onSelect: (net.dom53.inkita.domain.model.Collection) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_empty_reading_list),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id }) { collection ->
                        CollectionCard(
                            collection = collection,
                            config = config,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    collection: net.dom53.inkita.domain.model.Collection,
    config: AppConfig,
    onSelect: (net.dom53.inkita.domain.model.Collection) -> Unit,
) {
    val imageUrl = collectionCoverUrl(config, collection.id)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(collection) },
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = collection.name.ifBlank { "Collection ${collection.id}" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionSeriesGrid(
    title: String,
    items: List<net.dom53.inkita.domain.model.Series>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Back",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onBack() },
            )
        }
        WantToReadGrid(
            items = items,
            isLoading = isLoading,
            error = error,
            config = config,
            onOpenSeries = onOpenSeries,
        )
    }
}

@Composable
private fun ReadingListGrid(
    items: List<net.dom53.inkita.domain.model.ReadingList>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_empty_reading_list),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id }) { readingList ->
                        ReadingListCard(
                            readingList = readingList,
                            config = config,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingListCard(
    readingList: net.dom53.inkita.domain.model.ReadingList,
    config: AppConfig,
) {
    val context = LocalContext.current
    val imageUrl = readingListCoverUrl(config, readingList.id)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    android.widget.Toast
                        .makeText(context, "Not implemented yet", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = readingList.title.ifBlank { "Reading List ${readingList.id}" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PeopleGrid(
    items: List<net.dom53.inkita.domain.model.Person>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    config: AppConfig,
    onLoadMore: () -> Unit,
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            lastVisible to total
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 6) {
                onLoadMore()
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No people found.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id ?: 0 }) { person ->
                        PersonCard(
                            person = person,
                            config = config,
                            onClick = {
                                android.widget.Toast
                                    .makeText(context, "Not implemented yet", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            },
                        )
                    }
                    if (isLoadingMore) {
                        item(span = {
                            androidx.compose.foundation.lazy.grid
                                .GridItemSpan(3)
                        }) {
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
        }
    }
}

@Composable
private fun PersonCard(
    person: net.dom53.inkita.domain.model.Person,
    config: AppConfig,
    onClick: () -> Unit,
) {
    val imageUrl = person.id?.let { personCoverUrl(config, it) }
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
    ) {
        if (imageUrl == null) {
            PersonPlaceholder(modifier = imageModifier)
        } else {
            SubcomposeAsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
                loading = { PersonPlaceholder(modifier = imageModifier) },
                error = { PersonPlaceholder(modifier = imageModifier) },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = person.name?.ifBlank { null } ?: "Person ${person.id ?: 0}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibrarySeriesGrid(
    items: List<net.dom53.inkita.domain.model.Series>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    accessDenied: Boolean,
    config: AppConfig,
    onLoadMore: () -> Unit,
    onOpenSeries: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            lastVisible to total
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 6) {
                onLoadMore()
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                accessDenied -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.library_no_access),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                isLoading && items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null && items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = error, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.general_empty_collection),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(items, key = { it.id }) { series ->
                            WantToReadCard(
                                series = series,
                                config = config,
                                onOpenSeries = onOpenSeries,
                            )
                        }
                        if (isLoadingMore) {
                            item(span = {
                                androidx.compose.foundation.lazy.grid
                                    .GridItemSpan(3)
                            }) {
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
            }
        }
    }
}

@Composable
private fun PersonPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}
