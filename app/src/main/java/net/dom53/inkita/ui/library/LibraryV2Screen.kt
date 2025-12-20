package net.dom53.inkita.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.LibraryRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.ui.common.seriesCoverUrl

@Composable
fun LibraryV2Screen(
    libraryRepository: LibraryRepository,
    seriesRepository: SeriesRepository,
    appPreferences: AppPreferences,
    onOpenSeries: (Int) -> Unit,
) {
    val viewModel: LibraryV2ViewModel =
        viewModel(
            factory = LibraryV2ViewModel.provideFactory(libraryRepository, seriesRepository),
        )
    val uiState by viewModel.state.collectAsState()
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", userId = 0),
    )
    val showDrawerDebug = true
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
                        .background(if (showDrawerDebug) Color.Red else Color.Transparent)
                        .onSizeChanged { drawerWidthPx = it.width }
                        .alpha(drawerAlpha)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(text = "Menu", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                DrawerItem(icon = Icons.Filled.Home, label = "Home")
                DrawerItem(icon = Icons.Filled.BookmarkAdd, label = "Want To Read")
                DrawerItem(icon = Icons.Filled.CollectionsBookmark, label = "Collections")
                DrawerItem(icon = Icons.Filled.MenuBook, label = "Reading List")
                DrawerItem(icon = Icons.Filled.Bookmarks, label = "Bookmarks")
                DrawerItem(icon = Icons.Filled.LibraryBooks, label = "All Series")
                DrawerItem(icon = Icons.Filled.People, label = "Browse People")
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
                            DrawerItem(icon = Icons.Filled.LocalLibrary, label = label)
                        }
                    }
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            IconButton(
                onClick = {
                    scope.launch { drawerState.open() }
                },
            ) {
                Icon(Icons.Filled.Menu, contentDescription = null)
            }
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
    }
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
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
            items(items, key = { it.id }) { item ->
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
    val imageUrl = seriesCoverUrl(config, item.id)
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
                    .height(170.dp),
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
