package net.dom53.inkita.ui.browse

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.filter.KavitaCombination
import net.dom53.inkita.domain.model.filter.KavitaSortField
import net.dom53.inkita.domain.model.filter.TriState
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.domain.usecase.AppliedFilter
import net.dom53.inkita.domain.usecase.ReadStatusFilter
import net.dom53.inkita.domain.usecase.SpecialFilter
import net.dom53.inkita.ui.browse.utils.AgeRatings
import net.dom53.inkita.ui.browse.utils.PublicationState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    seriesRepository: SeriesRepository,
    appPreferences: AppPreferences,
    cacheManager: CacheManager,
    onOpenSeries: (Int) -> Unit,
) {
    val viewModel: BrowseViewModel =
        viewModel(
            factory = BrowseViewModel.provideFactory(seriesRepository, appPreferences, cacheManager),
        )
    val uiState by viewModel.state.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var sheetHasFocus by remember { mutableStateOf(false) }
    val specialOptions = listOf(SpecialFilter.WantToRead)
    val ageRatingsList =
        AgeRatings.entries.map {
            it.code to stringResource(it.titleRes)
        }
    val publicationItems =
        PublicationState.entries.map {
            it.code to stringResource(it.titleRes)
        }
    val gridState = rememberLazyGridState()
    var hideSearch by remember { mutableStateOf(false) }
    var lastScrollValue by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", userId = 0),
    )
    val browsePageSize by appPreferences.browsePageSizeFlow.collectAsState(initial = 25)

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val value = index * 10000 + offset
                val delta = value - lastScrollValue
                lastScrollValue = value
                hideSearch =
                    when {
                        index == 0 && offset < 20 -> false
                        delta > 10 -> true
                        delta < -10 -> false
                        else -> hideSearch
                    }
            }
    }

    LaunchedEffect(showFilterSheet) {
        if (showFilterSheet) {
            viewModel.loadMetadataIfNeeded(
                uiState.draftFilter.libraries.keys
                    .toList(),
            )
        }
    }

    // Initial load handled by ViewModel on init; search/filter changes applied via ViewModel.
    // back handling solved via confirmValueChange in sheet state

    when {
        uiState.error != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("${stringResource(R.string.general_error)}: ${uiState.error}")
            }
        }
        uiState.series.isEmpty() && !uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.general_no_results))
            }
        }
        else -> {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimatedVisibility(visible = !hideSearch) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = uiState.searchInput,
                                onValueChange = { viewModel.updateSearch(it) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.general_search)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions =
                                    KeyboardActions(
                                        onSearch = {
                                            viewModel.applySearchAndReload()
                                            focusManager.clearFocus()
                                        },
                                    ),
                            )
                        }
                    }

                    if (uiState.series.isEmpty() && uiState.isLoading) {
                        PlaceholderSeriesGrid(
                            placeholderCount = browsePageSize,
                            gridState = gridState,
                        )
                    } else {
                        SeriesGrid(
                            seriesList = uiState.series,
                            config = config,
                            isLoadingMore = uiState.isLoadingMore,
                            loadingPlaceholderCount = browsePageSize,
                            gridState = gridState,
                            onSeriesClick = { onOpenSeries(it.id) },
                            onLoadMore = { viewModel.loadNextPage() },
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { showFilterSheet = true },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.general_filter))
                }

                if (showFilterSheet) {
                    var minFocused by remember { mutableStateOf(false) }
                    var maxFocused by remember { mutableStateOf(false) }
                    val sheetState =
                        rememberModalBottomSheetState(
                            skipPartiallyExpanded = true,
                            confirmValueChange = { target ->
                                if (target == SheetValue.Hidden && (minFocused || maxFocused)) {
                                    focusManager.clearFocus(force = true)
                                    false
                                } else {
                                    true
                                }
                            },
                        )
                    var combinationExpanded by remember { mutableStateOf(false) }
                    var sortExpanded by remember { mutableStateOf(false) }
                    var statusExpanded by remember { mutableStateOf(false) }
                    var yearExpanded by remember { mutableStateOf(false) }
                    var genresExpanded by remember { mutableStateOf(false) }
                    var tagsExpanded by remember { mutableStateOf(false) }
                    var languagesExpanded by remember { mutableStateOf(false) }
                    var ageExpanded by remember { mutableStateOf(false) }
                    var publicationExpanded by remember { mutableStateOf(false) }
                    var collectionsExpanded by remember { mutableStateOf(false) }
                    var librariesExpanded by remember { mutableStateOf(false) }
                    var specialExpanded by remember { mutableStateOf(false) }
                    var smartExpanded by remember { mutableStateOf(false) }

                    BackHandler(enabled = showFilterSheet) {
                        if (sheetHasFocus || minFocused || maxFocused) {
                            focusManager.clearFocus(force = true)
                        } else {
                            showFilterSheet = false
                        }
                    }

                    ModalBottomSheet(
                        onDismissRequest = { showFilterSheet = false },
                        sheetState = sheetState,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(stringResource(R.string.general_filter), style = MaterialTheme.typography.titleLarge)

                                SectionHeader(stringResource(R.string.general_combination), combinationExpanded) {
                                    combinationExpanded = !combinationExpanded
                                }
                                if (combinationExpanded) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = uiState.draftFilter.combination == KavitaCombination.MatchAll,
                                            onClick = { viewModel.setCombination(KavitaCombination.MatchAll) },
                                            label = { Text(stringResource(R.string.browse_filter_match_all)) },
                                        )
                                        FilterChip(
                                            selected = uiState.draftFilter.combination == KavitaCombination.MatchAny,
                                            onClick = { viewModel.setCombination(KavitaCombination.MatchAny) },
                                            label = { Text(stringResource(R.string.browse_filter_match_any)) },
                                        )
                                    }
                                }

                                SectionHeader(stringResource(R.string.browse_filter_sort_by), sortExpanded) { sortExpanded = !sortExpanded }
                                if (sortExpanded) {
                                    val sortOptions =
                                        listOf(
                                            stringResource(R.string.browse_filter_sort_name) to KavitaSortField.SortName,
                                            stringResource(R.string.browse_filter_sort_created) to KavitaSortField.Created,
                                            stringResource(R.string.browse_filter_sort_last_modified) to KavitaSortField.LastModified,
                                            stringResource(R.string.browse_filter_sort_item_added) to KavitaSortField.ItemAdded,
                                            stringResource(R.string.browse_filter_sort_time_to_read) to KavitaSortField.TimeToRead,
                                            stringResource(R.string.browse_filter_sort_release_year) to KavitaSortField.ReleaseYear,
                                            stringResource(R.string.browse_filter_sort_last_read) to KavitaSortField.LastRead,
                                            stringResource(R.string.browse_filter_sort_avg_rating) to KavitaSortField.AverageRating,
                                            stringResource(R.string.browse_filter_sort_random) to KavitaSortField.Random,
                                        )
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        sortOptions.chunked(3).forEach { chunk ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                chunk.forEach { (label, field) ->
                                                    SortChip(
                                                        label = label,
                                                        field = field,
                                                        current = uiState.draftFilter.sortField,
                                                        descending = uiState.draftFilter.sortDesc,
                                                    ) { f, d -> viewModel.setSort(f, d) }
                                                }
                                            }
                                        }
                                    }
                                }

                                SectionHeader(stringResource(R.string.browse_filter_status), statusExpanded) { statusExpanded = !statusExpanded }
                                if (statusExpanded) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        StatusChip(ReadStatusFilter.Any, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                                        StatusChip(ReadStatusFilter.Unread, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                                        StatusChip(ReadStatusFilter.InProgress, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                                        StatusChip(ReadStatusFilter.Completed, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                                    }
                                }

                                SectionHeader(stringResource(R.string.browse_filter_release_year), yearExpanded) { yearExpanded = !yearExpanded }
                                if (yearExpanded) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = uiState.draftFilter.minYear,
                                            onValueChange = { viewModel.setYearBounds(it, uiState.draftFilter.maxYear) },
                                            label = { Text(stringResource(R.string.general_min)) },
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .onFocusChanged { focusState ->
                                                        minFocused = focusState.isFocused
                                                        sheetHasFocus = focusState.isFocused || maxFocused
                                                    },
                                        )
                                        OutlinedTextField(
                                            value = uiState.draftFilter.maxYear,
                                            onValueChange = { viewModel.setYearBounds(uiState.draftFilter.minYear, it) },
                                            label = { Text(stringResource(R.string.general_max)) },
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .onFocusChanged { focusState ->
                                                        maxFocused = focusState.isFocused
                                                        sheetHasFocus = focusState.isFocused || minFocused
                                                    },
                                        )
                                    }
                                }

                                val genreItems = uiState.genresRemote.map { (it.id ?: 0) to (it.title ?: (it.id ?: 0).toString()) }
                                SectionHeader(stringResource(R.string.general_genres), genresExpanded) { genresExpanded = !genresExpanded }
                                if (genresExpanded) {
                                    TriStateRow(genreItems, uiState.draftFilter.genres) { id, st ->
                                        viewModel.setTriStateGenre(id, st)
                                    }
                                }
                                val tagItems = uiState.tagsRemote.map { (it.id ?: 0) to (it.title ?: (it.id ?: 0).toString()) }
                                SectionHeader(stringResource(R.string.general_tags), tagsExpanded) { tagsExpanded = !tagsExpanded }
                                if (tagsExpanded) {
                                    TriStateRow(tagItems, uiState.draftFilter.tags) { id, st ->
                                        viewModel.setTriStateTag(id, st)
                                    }
                                }
                                val languageItems = uiState.languagesRemote.map { (it.isoCode) to (it.title ?: it.isoCode) }
                                SectionHeader(stringResource(R.string.general_languages), languagesExpanded) { languagesExpanded = !languagesExpanded }
                                if (languagesExpanded) {
                                    TriStateRowString(languageItems, uiState.draftFilter.languages) { code, st ->
                                        viewModel.setTriStateLanguage(code, st)
                                    }
                                }
                                SectionHeader(stringResource(R.string.browse_filter_age_rating), ageExpanded) { ageExpanded = !ageExpanded }
                                if (ageExpanded) {
                                    TriStateRow(ageRatingsList, uiState.draftFilter.ageRatings) { id, st ->
                                        viewModel.setTriStateAge(id, st)
                                    }
                                }
                                SectionHeader(stringResource(R.string.general_publication_status), publicationExpanded) { publicationExpanded = !publicationExpanded }
                                if (publicationExpanded) {
                                    MultiToggleRow(publicationItems, uiState.draftFilter.publication) { id, checked ->
                                        viewModel.setPublication(id, checked)
                                    }
                                }
                                SectionHeader(stringResource(R.string.general_collections), collectionsExpanded) { collectionsExpanded = !collectionsExpanded }
                                if (collectionsExpanded) {
                                    val collectionItems = uiState.collectionsRemote.map { it.id to (it.title ?: it.id.toString()) }
                                    TriStateRow(collectionItems, uiState.draftFilter.collections) { id, st ->
                                        viewModel.setTriStateCollection(id, st)
                                    }
                                }

                                SectionHeader(stringResource(R.string.general_libraries), librariesExpanded) { librariesExpanded = !librariesExpanded }
                                if (librariesExpanded) {
                                    val libraryItems = uiState.librariesRemote.map { it.id to (it.name ?: it.id.toString()) }
                                    TriStateRow(libraryItems, uiState.draftFilter.libraries) { id, st ->
                                        viewModel.setTriStateLibrary(id, st)
                                    }
                                }

                                HorizontalDivider()
                                Text(stringResource(R.string.browse_filter_filter_no_others))
                                SectionHeader(stringResource(R.string.browse_filter_special_list), specialExpanded) { specialExpanded = !specialExpanded }
                                if (specialExpanded) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        specialOptions.forEach { option ->
                                            val label =
                                                when (option) {
                                                    SpecialFilter.WantToRead -> stringResource(R.string.general_want_to_read)
                                                }
                                            FilterChip(
                                                selected = uiState.draftFilter.special == option,
                                                onClick = {
                                                    viewModel.setSmartFilter(null, null)
                                                    viewModel.setSpecial(option)
                                                },
                                                label = { Text(label) },
                                            )
                                        }
                                        FilterChip(
                                            selected = uiState.draftFilter.special == null,
                                            onClick = { viewModel.setSpecial(null) },
                                            label = { Text(stringResource(R.string.general_none)) },
                                        )
                                    }
                                }

                                SectionHeader(stringResource(R.string.browse_filter_smart_filters), smartExpanded) { smartExpanded = !smartExpanded }
                                if (smartExpanded) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        val smartItems = uiState.smartFilters.map { it.id to (it.name ?: "${stringResource(R.string.general_filter)} ${it.id}") }
                                        smartItems.forEach { (id, name) ->
                                            FilterChip(
                                                selected = uiState.draftFilter.smartFilterId == id,
                                                onClick = { viewModel.onSmartSelected(id) },
                                                label = { Text(name) },
                                            )
                                        }
                                        FilterChip(
                                            selected = uiState.draftFilter.smartFilterId == null,
                                            onClick = { viewModel.onSmartSelected(null) },
                                            label = { Text(stringResource(R.string.general_none)) },
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        viewModel.updateDraftFilter { AppliedFilter() }
                                        viewModel.applyFilterAndReload()
                                        showFilterSheet = false
                                    },
                                ) {
                                    Text(stringResource(R.string.general_reset))
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        showFilterSheet = false
                                        scope.launch {
                                            val smartId = uiState.draftFilter.smartFilterId
                                            if (smartId != null && uiState.draftFilter.decodedSmartFilter == null) {
                                                val decoded = viewModel.decodeSmartFilter(smartId)
                                                if (decoded != null) {
                                                    viewModel.setSmartFilter(smartId, decoded)
                                                }
                                            }
                                            viewModel.applyFilterAndReload()
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.general_filter))
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
private fun PlaceholderSeriesGrid(
    placeholderCount: Int,
    gridState: LazyGridState,
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(placeholderCount) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(placeholderColor),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.9f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor),
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
    loadingPlaceholderCount: Int,
    gridState: LazyGridState,
    onLoadMore: () -> Unit,
    onSeriesClick: (Series) -> Unit,
) {
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

    val context = LocalContext.current

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
                val imageData = series.localThumbPath?.let { java.io.File(it) } ?: seriesCoverUrl(config, series.id)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = imageData,
                        contentDescription = series.name,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    ) {
                        when (painter.state) {
                            is coil.compose.AsyncImagePainter.State.Loading -> {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                }
                            }
                            is coil.compose.AsyncImagePainter.State.Error -> {
                                Icon(
                                    imageVector = Icons.Filled.ImageNotSupported,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                }

                Text(
                    series.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                readingStatusLabel(context, series.readState)?.let { status ->
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
            items(loadingPlaceholderCount, key = { "loading_$it" }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.9f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.6f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}

private fun seriesCoverUrl(
    config: AppConfig,
    seriesId: Int,
): String? {
    if (config.serverUrl.isBlank() || config.apiKey.isBlank()) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/api/Image/series-cover?seriesId=$seriesId&apiKey=${config.apiKey}"
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

@Composable
private fun SortChip(
    label: String,
    field: KavitaSortField,
    current: KavitaSortField,
    descending: Boolean,
    onToggle: (KavitaSortField, Boolean) -> Unit,
) {
    val selected = current == field
    val arrow =
        when {
            !selected -> ""
            descending -> "v"
            else -> "^"
        }
    FilterChip(
        selected = selected,
        onClick = {
            if (selected) onToggle(field, !descending) else onToggle(field, false)
        },
        label = { Text("$label $arrow") },
    )
}

@Composable
private fun StatusChip(
    value: ReadStatusFilter,
    current: ReadStatusFilter,
    onSelect: (ReadStatusFilter) -> Unit,
) {
    val label =
        when (value) {
            ReadStatusFilter.Any -> stringResource(R.string.general_any)
            ReadStatusFilter.Unread -> stringResource(R.string.general_reading_status_unread)
            ReadStatusFilter.InProgress -> stringResource(R.string.general_reading_status_in_progress)
            ReadStatusFilter.Completed -> stringResource(R.string.general_reading_status_completed)
        }
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
    }
}

@Composable
private fun TriStateRow(
    items: List<Pair<Int, String>>,
    stateMap: Map<Int, TriState>,
    onStateChange: (Int, TriState) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, title) ->
            val state = stateMap[id] ?: TriState.None
            val prefix = ""
            val colors =
                when (state) {
                    TriState.Include ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    TriState.Exclude ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    TriState.None -> FilterChipDefaults.filterChipColors()
                }
            FilterChip(
                selected = state != TriState.None,
                onClick = {
                    val next =
                        when (state) {
                            TriState.None -> TriState.Include
                            TriState.Include -> TriState.Exclude
                            TriState.Exclude -> TriState.None
                        }
                    onStateChange(id, next)
                },
                label = { Text("$prefix$title") },
                colors = colors,
            )
        }
    }
}

@Composable
private fun MultiToggleRow(
    items: List<Pair<Int, String>>,
    stateMap: Map<Int, Boolean>,
    onStateChange: (Int, Boolean) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, title) ->
            val selected = stateMap[id] ?: false
            FilterChip(
                selected = selected,
                onClick = { onStateChange(id, !selected) },
                label = { Text(title) },
            )
        }
    }
}

@Composable
private fun TriStateRowString(
    items: List<Pair<String, String>>,
    stateMap: Map<String, TriState>,
    onStateChange: (String, TriState) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, title) ->
            val state = stateMap[id] ?: TriState.None
            val colors =
                when (state) {
                    TriState.Include ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    TriState.Exclude ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    TriState.None -> FilterChipDefaults.filterChipColors()
                }
            FilterChip(
                selected = state != TriState.None,
                onClick = {
                    val next =
                        when (state) {
                            TriState.None -> TriState.Include
                            TriState.Include -> TriState.Exclude
                            TriState.Exclude -> TriState.None
                        }
                    onStateChange(id, next)
                },
                label = { Text(title) },
                colors = colors,
            )
        }
    }
}
