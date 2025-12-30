package net.dom53.inkita.ui.library

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.cache.LibraryV2CacheKeys
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.Library
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.LibraryRepository
import net.dom53.inkita.domain.repository.PersonRepository
import net.dom53.inkita.domain.repository.ReadingListRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.ui.common.DownloadState
import net.dom53.inkita.ui.common.DownloadStateResolver
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2

@Suppress("LargeClass")
class LibraryV2ViewModel(
    private val libraryRepository: LibraryRepository,
    private val seriesRepository: SeriesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val readingListRepository: ReadingListRepository,
    private val personRepository: PersonRepository,
    private val cacheManager: CacheManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryV2UiState())
    val state: StateFlow<LibraryV2UiState> = _state
    private val downloadDao =
        InkitaDatabase.getInstance(appPreferences.appContext).downloadV2Dao()
    private var lastDownloadedItems: List<DownloadedItemV2Entity> = emptyList()

    init {
        loadLibraries()
        loadHome()
        observeDownloadStates()
    }

    fun selectSection(section: LibraryV2Section) {
        if (section == LibraryV2Section.Home) {
            goHome()
            return
        }
        _state.update { it.copy(selectedSection = section) }
        if (section == LibraryV2Section.WantToRead) {
            ensureWantToRead()
        }
        if (section == LibraryV2Section.Collections) {
            ensureCollections()
        }
        if (section == LibraryV2Section.ReadingList) {
            ensureReadingLists()
        }
        if (section == LibraryV2Section.BrowsePeople) {
            ensurePeople()
        }
    }

    fun openCollectionFromExternal(
        collectionId: Int,
        collectionName: String?,
    ) {
        _state.update {
            it.copy(
                selectedSection = LibraryV2Section.Collections,
                selectedCollectionId = collectionId,
                selectedCollectionName = collectionName,
                collectionSeries = emptyList(),
                isCollectionSeriesLoading = true,
                collectionSeriesError = null,
            )
        }
        viewModelScope.launch {
            ensureCollections()
            loadCollectionSeries(collectionId)
        }
    }

    fun handleBack(): Boolean {
        val current = _state.value
        return when {
            current.selectedSection == LibraryV2Section.Collections && current.selectedCollectionId != null -> {
                selectCollection(null)
                true
            }

            current.selectedSection != LibraryV2Section.Home -> {
                goHome()
                true
            }

            else -> false
        }
    }

    fun goHome() {
        _state.update {
            clearLibrarySelection(clearCollectionSelection(it.copy(selectedSection = LibraryV2Section.Home)))
        }
        loadHome()
    }

    fun selectLibrary(library: Library) {
        _state.update {
            it.copy(
                selectedSection = LibraryV2Section.LibrarySeries,
                selectedLibraryId = library.id,
                selectedLibraryName = library.name,
                librarySeries = emptyList(),
                librarySeriesError = null,
                librarySeriesPage = 1,
                canLoadMoreLibrarySeries = true,
                libraryAccessDenied = false,
            )
        }
        viewModelScope.launch {
            val accessResult = runCatching { libraryRepository.hasLibraryAccess(library.id) }
            val hasAccess = accessResult.getOrDefault(false)
            if (!hasAccess) {
                _state.update {
                    it.copy(
                        libraryAccessDenied = true,
                        isLibrarySeriesLoading = false,
                        librarySeriesError = if (accessResult.isFailure) accessResult.exceptionOrNull()?.message else null,
                    )
                }
                return@launch
            }
            loadLibrarySeries(pageNumber = 1, reset = true)
        }
    }

    fun loadMoreLibrarySeries() {
        val current = _state.value
        if (
            current.isLibrarySeriesLoading ||
            current.isLibrarySeriesLoadingMore ||
            !current.canLoadMoreLibrarySeries ||
            current.selectedLibraryId == null
        ) {
            return
        }
        val nextPage = current.librarySeriesPage + 1
        loadLibrarySeries(pageNumber = nextPage, reset = false)
    }

    fun selectCollection(collection: Collection?) {
        if (collection == null) {
            _state.update {
                it.copy(
                    selectedCollectionId = null,
                    selectedCollectionName = null,
                    collectionSeries = emptyList(),
                    collectionSeriesError = null,
                )
            }
            return
        }

        if (_state.value.selectedCollectionId == collection.id &&
            _state.value.collectionSeries.isNotEmpty()
        ) {
            showCollectionSeriesDebugToast(collection.id)
            return
        }

        _state.update {
            it.copy(
                selectedCollectionId = collection.id,
                selectedCollectionName = collection.name,
                collectionSeries = emptyList(),
                collectionSeriesError = null,
            )
        }
        loadCollectionSeries(collection.id)
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = runCatching { libraryRepository.getLibraries() }
            _state.update {
                it.copy(
                    libraries = result.getOrDefault(emptyList()),
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun loadHome() {
        viewModelScope.launch {
            _state.update { it.copy(isHomeLoading = true, homeError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cachedOnDeck =
                cacheManager.getCachedLibraryV2SeriesList(
                    LibraryV2CacheKeys.HOME_ON_DECK,
                    "",
                )
            val cachedUpdated =
                cacheManager.getCachedLibraryV2SeriesList(
                    LibraryV2CacheKeys.HOME_RECENTLY_UPDATED,
                    "",
                )
            val cachedAdded =
                cacheManager.getCachedLibraryV2SeriesList(
                    LibraryV2CacheKeys.HOME_RECENTLY_ADDED,
                    "",
                )
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.HOME_ON_DECK,
                    "",
                )
            val cachedHasData =
                cachedOnDeck.isNotEmpty() || cachedUpdated.isNotEmpty() || cachedAdded.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            logCacheDecision(
                "home",
                "online=$isOnline cache=$cachedHasData stale=$isStale refresh=$alwaysRefresh onDeck=${cachedOnDeck.size} updated=${cachedUpdated.size} added=${cachedAdded.size}",
            )
            val showDebugToast = appPreferences.debugToastsFlow.first()
            if (cachedHasData) {
                _state.update {
                    it.copy(
                        onDeck = cachedOnDeck.map { series -> HomeSeriesItem(series.id, series.name, series.localThumbPath) },
                        recentlyUpdated = cachedUpdated.map { series -> HomeSeriesItem(series.id, series.name, series.localThumbPath) },
                        recentlyAdded = cachedAdded.map { series -> HomeSeriesItem(series.id, series.name, series.localThumbPath) },
                        isHomeLoading = true,
                        homeError = null,
                    )
                }
                updateDownloadStates(lastDownloadedItems)
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("home", "using cached data only")
                    if (showDebugToast) {
                        android.widget.Toast
                            .makeText(
                                appPreferences.appContext,
                                appPreferences.appContext.getString(R.string.debug_cache_use),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                    }
                    _state.update { it.copy(isHomeLoading = false) }
                    return@launch
                }
                if (showDebugToast) {
                    val message =
                        if (alwaysRefresh) {
                            R.string.debug_cache_force_online
                        } else {
                            R.string.debug_cache_stale_reload
                        }
                    android.widget.Toast
                        .makeText(
                            appPreferences.appContext,
                            appPreferences.appContext.getString(message),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
            } else if (showDebugToast) {
                logCacheDecision("home", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                android.widget.Toast
                    .makeText(
                        appPreferences.appContext,
                        appPreferences.appContext.getString(message),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }
            val onDeckResult = runCatching { seriesRepository.getOnDeckSeries(1, 20, 0) }
            val updatedResult = runCatching { seriesRepository.getRecentlyUpdatedSeries(1, 20) }
            val addedResult = runCatching { seriesRepository.getRecentlyAddedSeries(1, 20) }
            logCacheDecision(
                "home",
                "fetched onDeck=${onDeckResult.getOrDefault(
                    emptyList(),
                ).size} updated=${updatedResult.getOrDefault(emptyList()).size} added=${addedResult.getOrDefault(emptyList()).size}",
            )

            val onDeck =
                onDeckResult.getOrDefault(emptyList()).map { series ->
                    val title = series.name.ifBlank { "Series ${series.id}" }
                    HomeSeriesItem(id = series.id, title = title, localThumbPath = series.localThumbPath)
                }
            val recentlyUpdated =
                updatedResult.getOrDefault(emptyList()).mapNotNull { item ->
                    val id = item.seriesId ?: return@mapNotNull null
                    val title =
                        item.seriesName?.ifBlank { null }
                            ?: item.title?.ifBlank { null }
                            ?: "Series $id"
                    HomeSeriesItem(id = id, title = title, localThumbPath = null)
                }
            val recentlyAdded =
                addedResult.getOrDefault(emptyList()).map { series ->
                    val title = series.name.ifBlank { "Series ${series.id}" }
                    HomeSeriesItem(id = series.id, title = title, localThumbPath = series.localThumbPath)
                }
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.HOME_ON_DECK,
                "",
                onDeckResult.getOrDefault(emptyList()),
            )
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.HOME_RECENTLY_ADDED,
                "",
                addedResult.getOrDefault(emptyList()),
            )
            val updatedSeries =
                updatedResult.getOrDefault(emptyList()).mapNotNull { item ->
                    val id = item.seriesId ?: return@mapNotNull null
                    val title =
                        item.seriesName?.ifBlank { null }
                            ?: item.title?.ifBlank { null }
                            ?: "Series $id"
                    net.dom53.inkita.domain.model.Series(
                        id = id,
                        name = title,
                        summary = null,
                        libraryId = null,
                        format = null,
                        pages = null,
                        pagesRead = null,
                        readState = null,
                    )
                }
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.HOME_RECENTLY_UPDATED,
                "",
                updatedSeries,
            )
            logCacheDecision("home", "cached lists stored")

            val error =
                onDeckResult.exceptionOrNull()
                    ?: updatedResult.exceptionOrNull()
                    ?: addedResult.exceptionOrNull()
            _state.update {
                it.copy(
                    onDeck = onDeck,
                    recentlyUpdated = recentlyUpdated,
                    recentlyAdded = recentlyAdded,
                    isHomeLoading = false,
                    homeError = error?.message,
                )
            }
            updateDownloadStates(lastDownloadedItems)
        }
    }

    private fun ensureWantToRead() {
        val current = _state.value
        if (current.isWantToReadLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isWantToReadLoading = true, wantToReadError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached =
                cacheManager.getCachedLibraryV2SeriesList(
                    LibraryV2CacheKeys.WANT_TO_READ,
                    "",
                )
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.WANT_TO_READ,
                    "",
                )
            val hasStateData = current.wantToRead.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "want",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(wantToRead = cached, isWantToReadLoading = true, wantToReadError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("want", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isWantToReadLoading = false) }
                    updateDownloadStates(lastDownloadedItems)
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("want", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { seriesRepository.getWantToReadSeries(1, 50) }
            _state.update {
                it.copy(
                    wantToRead = result.getOrDefault(emptyList()),
                    isWantToReadLoading = false,
                    wantToReadError = result.exceptionOrNull()?.message,
                )
            }
            updateDownloadStates(lastDownloadedItems)
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.WANT_TO_READ,
                "",
                result.getOrDefault(emptyList()),
            )
            logCacheDecision("want", "cached list stored size=${result.getOrDefault(emptyList()).size}")
        }
    }

    private fun ensureCollections() {
        val current = _state.value
        if (current.isCollectionsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isCollectionsLoading = true, collectionsError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2Collections(LibraryV2CacheKeys.COLLECTIONS)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2CollectionsUpdatedAt(
                    LibraryV2CacheKeys.COLLECTIONS,
                )
            val hasStateData = current.collections.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "collections",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(collections = cached, isCollectionsLoading = true, collectionsError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("collections", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isCollectionsLoading = false) }
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("collections", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { collectionsRepository.getCollectionsAll(ownedOnly = false) }
            _state.update {
                it.copy(
                    collections = result.getOrDefault(emptyList()),
                    isCollectionsLoading = false,
                    collectionsError = result.exceptionOrNull()?.message,
                )
            }
            cacheManager.cacheLibraryV2Collections(
                LibraryV2CacheKeys.COLLECTIONS,
                result.getOrDefault(emptyList()),
            )
            logCacheDecision("collections", "cached list stored size=${result.getOrDefault(emptyList()).size}")
        }
    }

    private fun ensureReadingLists() {
        val current = _state.value
        if (current.isReadingListsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isReadingListsLoading = true, readingListsError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2ReadingLists(LibraryV2CacheKeys.READING_LISTS)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2ReadingListsUpdatedAt(
                    LibraryV2CacheKeys.READING_LISTS,
                )
            val hasStateData = current.readingLists.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "readingLists",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(readingLists = cached, isReadingListsLoading = true, readingListsError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("readingLists", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isReadingListsLoading = false) }
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("readingLists", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { readingListRepository.getReadingLists(includePromoted = true, sortByLastModified = false) }
            _state.update {
                it.copy(
                    readingLists = result.getOrDefault(emptyList()),
                    isReadingListsLoading = false,
                    readingListsError = result.exceptionOrNull()?.message,
                )
            }
            cacheManager.cacheLibraryV2ReadingLists(
                LibraryV2CacheKeys.READING_LISTS,
                result.getOrDefault(emptyList()),
            )
            logCacheDecision("readingLists", "cached list stored size=${result.getOrDefault(emptyList()).size}")
        }
    }

    private fun ensurePeople() {
        val current = _state.value
        if (current.isPeopleLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isPeopleLoading = true, peopleError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2People(LibraryV2CacheKeys.BROWSE_PEOPLE, 1)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2PeopleUpdatedAt(
                    LibraryV2CacheKeys.BROWSE_PEOPLE,
                    1,
                )
            val hasStateData = current.people.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "people",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update {
                        it.copy(
                            people = cached,
                            isPeopleLoading = true,
                            peopleError = null,
                            peoplePage = 1,
                            canLoadMorePeople = cached.size == 50,
                        )
                    }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("people", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isPeopleLoading = false) }
                    updateDownloadStates(lastDownloadedItems)
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("people", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { personRepository.getBrowsePeople(pageNumber = 1, pageSize = 50) }
            val pageItems = result.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    people = pageItems,
                    isPeopleLoading = false,
                    peopleError = result.exceptionOrNull()?.message,
                    peoplePage = 1,
                    canLoadMorePeople = pageItems.size == 50,
                )
            }
            updateDownloadStates(lastDownloadedItems)
            cacheManager.cacheLibraryV2People(
                LibraryV2CacheKeys.BROWSE_PEOPLE,
                1,
                pageItems,
            )
            logCacheDecision("people", "cached list stored size=${pageItems.size}")
        }
    }

    private fun loadLibrarySeries(
        pageNumber: Int,
        reset: Boolean,
    ) {
        val libraryId = _state.value.selectedLibraryId ?: return
        viewModelScope.launch {
            if (reset) {
                _state.update { it.copy(isLibrarySeriesLoading = true, librarySeriesError = null) }
                val message =
                    if (NetworkUtils.isOnline(appPreferences.appContext)) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            } else {
                _state.update { it.copy(isLibrarySeriesLoadingMore = true) }
            }
            val result = runCatching { seriesRepository.getSeriesForLibrary(libraryId, pageNumber, 25) }
            val pageItems = result.getOrDefault(emptyList())
            _state.update {
                val merged =
                    if (reset) {
                        pageItems
                    } else {
                        it.librarySeries + pageItems
                    }
                it.copy(
                    librarySeries = merged,
                    isLibrarySeriesLoading = false,
                    isLibrarySeriesLoadingMore = false,
                    librarySeriesError = result.exceptionOrNull()?.message,
                    librarySeriesPage = if (pageItems.isNotEmpty()) pageNumber else it.librarySeriesPage,
                    canLoadMoreLibrarySeries = pageItems.size == 25,
                )
            }
            updateDownloadStates(lastDownloadedItems)
        }
    }

    private fun clearCollectionSelection(state: LibraryV2UiState): LibraryV2UiState =
        state.copy(
            selectedCollectionId = null,
            selectedCollectionName = null,
            collectionSeries = emptyList(),
            isCollectionSeriesLoading = false,
            collectionSeriesError = null,
        )

    private fun clearLibrarySelection(state: LibraryV2UiState): LibraryV2UiState =
        state.copy(
            selectedLibraryId = null,
            selectedLibraryName = null,
            librarySeries = emptyList(),
            isLibrarySeriesLoading = false,
            isLibrarySeriesLoadingMore = false,
            librarySeriesError = null,
            librarySeriesPage = 1,
            canLoadMoreLibrarySeries = true,
            libraryAccessDenied = false,
        )

    fun loadMorePeople() {
        val current = _state.value
        if (current.isPeopleLoading || current.isPeopleLoadingMore || !current.canLoadMorePeople) return
        viewModelScope.launch {
            val nextPage = current.peoplePage + 1
            _state.update { it.copy(isPeopleLoadingMore = true) }
            val result = runCatching { personRepository.getBrowsePeople(pageNumber = nextPage, pageSize = 50) }
            val pageItems = result.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    people = it.people + pageItems,
                    isPeopleLoadingMore = false,
                    peopleError = result.exceptionOrNull()?.message ?: it.peopleError,
                    peoplePage = if (pageItems.isNotEmpty()) nextPage else it.peoplePage,
                    canLoadMorePeople = pageItems.size == 50,
                )
            }
            updateDownloadStates(lastDownloadedItems)
        }
    }

    private fun loadCollectionSeries(collectionId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isCollectionSeriesLoading = true, collectionSeriesError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cacheKey = collectionId.toString()
            val cached =
                cacheManager.getCachedLibraryV2SeriesList(
                    LibraryV2CacheKeys.COLLECTION_SERIES,
                    cacheKey,
                )
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.COLLECTION_SERIES,
                    cacheKey,
                )
            val hasStateData = _state.value.collectionSeries.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "collection:$collectionId",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update {
                        it.copy(collectionSeries = cached, isCollectionSeriesLoading = true, collectionSeriesError = null)
                    }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("collection:$collectionId", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isCollectionSeriesLoading = false) }
                    updateDownloadStates(lastDownloadedItems)
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("collection:$collectionId", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { seriesRepository.getSeriesForCollection(collectionId, 1, 50) }
            _state.update {
                it.copy(
                    collectionSeries = result.getOrDefault(emptyList()),
                    isCollectionSeriesLoading = false,
                    collectionSeriesError = result.exceptionOrNull()?.message,
                )
            }
            updateDownloadStates(lastDownloadedItems)
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.COLLECTION_SERIES,
                cacheKey,
                result.getOrDefault(emptyList()),
            )
            logCacheDecision(
                "collection:$collectionId",
                "cached list stored size=${result.getOrDefault(emptyList()).size}",
            )
        }
    }

    private suspend fun maybeShowDebugToast(
        @StringRes messageRes: Int,
    ) {
        if (!appPreferences.debugToastsFlow.first()) return
        Toast
            .makeText(
                appPreferences.appContext,
                appPreferences.appContext.getString(messageRes),
                Toast.LENGTH_SHORT,
            ).show()
    }

    private fun logCacheDecision(
        section: String,
        message: String,
    ) {
        if (!LoggingManager.isDebugEnabled()) return
        LoggingManager.d("LibraryV2Cache", "[$section] $message")
    }

    private fun showCollectionSeriesDebugToast(collectionId: Int) {
        viewModelScope.launch {
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cacheKey = collectionId.toString()
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.COLLECTION_SERIES,
                    cacheKey,
                )
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val message =
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    R.string.debug_cache_use
                } else if (alwaysRefresh) {
                    R.string.debug_cache_force_online
                } else {
                    R.string.debug_cache_stale_reload
                }
            maybeShowDebugToast(message)
        }
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadDao
                .observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
                .collectLatest { items ->
                    lastDownloadedItems = items
                    updateDownloadStates(items)
                }
        }
    }

    private suspend fun updateDownloadStates(items: List<DownloadedItemV2Entity>) {
        val seriesInfoById = buildSeriesInfoMap(_state.value)
        if (seriesInfoById.isEmpty()) {
            _state.update { it.copy(downloadStates = emptyMap()) }
            return
        }
        val cacheIds =
            seriesInfoById.values
                .filter { info ->
                    info.format == null ||
                        info.format == Format.Pdf ||
                        (info.pages ?: 0) <= 0
                }.map { it.id }
                .distinct()
                .sorted()
        val cachedDetails =
            withContext(Dispatchers.IO) {
                val result = mutableMapOf<Int, InkitaDetailV2>()
                cacheIds.forEach { id ->
                    cacheManager.getCachedSeriesDetailV2(id)?.let { result[id] = it }
                }
                result
            }
        val states =
            buildSeriesDownloadStates(
                seriesInfoById = seriesInfoById,
                downloadedItems = items,
                cachedDetails = cachedDetails,
            )
        _state.update { it.copy(downloadStates = states) }
    }

    private data class SeriesDownloadInfo(
        val id: Int,
        val format: Format?,
        val pages: Int?,
    )

    private fun buildSeriesInfoMap(state: LibraryV2UiState): Map<Int, SeriesDownloadInfo> {
        val map = mutableMapOf<Int, SeriesDownloadInfo>()

        fun add(
            id: Int,
            format: Format?,
            pages: Int?,
        ) {
            val current = map[id]
            val resolvedFormat = format ?: current?.format
            val resolvedPages = pages ?: current?.pages
            map[id] = SeriesDownloadInfo(id, resolvedFormat, resolvedPages)
        }
        state.onDeck.forEach { add(it.id, null, null) }
        state.recentlyUpdated.forEach { add(it.id, null, null) }
        state.recentlyAdded.forEach { add(it.id, null, null) }
        state.wantToRead.forEach { add(it.id, it.format, it.pages) }
        state.collectionSeries.forEach { add(it.id, it.format, it.pages) }
        state.librarySeries.forEach { add(it.id, it.format, it.pages) }
        return map
    }

    private fun buildSeriesDownloadStates(
        seriesInfoById: Map<Int, SeriesDownloadInfo>,
        downloadedItems: List<DownloadedItemV2Entity>,
        cachedDetails: Map<Int, InkitaDetailV2>,
    ): Map<Int, DownloadState> {
        val itemsBySeries =
            downloadedItems
                .filter { it.seriesId != null }
                .groupBy { it.seriesId!! }
        val result = mutableMapOf<Int, DownloadState>()
        seriesInfoById.forEach { (id, info) ->
            val items = itemsBySeries[id].orEmpty()
            val detail = cachedDetails[id]
            val format = info.format ?: Format.fromId(detail?.series?.format)
            result[id] =
                DownloadStateResolver.resolveSeriesState(
                    format = format,
                    pagesHint = info.pages,
                    detail = detail?.detail,
                    items = items,
                )
        }
        return result
    }

    companion object {
        fun provideFactory(
            libraryRepository: LibraryRepository,
            seriesRepository: SeriesRepository,
            collectionsRepository: CollectionsRepository,
            readingListRepository: ReadingListRepository,
            personRepository: PersonRepository,
            cacheManager: CacheManager,
            appPreferences: AppPreferences,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryV2ViewModel(
                        libraryRepository,
                        seriesRepository,
                        collectionsRepository,
                        readingListRepository,
                        personRepository,
                        cacheManager,
                        appPreferences,
                    ) as T
            }
    }
}
