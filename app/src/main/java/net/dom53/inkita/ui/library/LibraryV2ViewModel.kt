package net.dom53.inkita.ui.library

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.cache.LibraryV2CacheKeys
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Library
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadingList
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.LibraryRepository
import net.dom53.inkita.domain.repository.PersonRepository
import net.dom53.inkita.domain.repository.ReadingListRepository
import net.dom53.inkita.domain.repository.SeriesRepository

data class HomeSeriesItem(
    val id: Int,
    val title: String,
    val localThumbPath: String? = null,
)

enum class LibraryV2Section {
    Home,
    WantToRead,
    Collections,
    ReadingList,
    BrowsePeople,
    LibrarySeries,
}

data class LibraryV2UiState(
    val libraries: List<Library> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val onDeck: List<HomeSeriesItem> = emptyList(),
    val recentlyUpdated: List<HomeSeriesItem> = emptyList(),
    val recentlyAdded: List<HomeSeriesItem> = emptyList(),
    val isHomeLoading: Boolean = true,
    val homeError: String? = null,
    val wantToRead: List<net.dom53.inkita.domain.model.Series> = emptyList(),
    val isWantToReadLoading: Boolean = false,
    val wantToReadError: String? = null,
    val collections: List<Collection> = emptyList(),
    val isCollectionsLoading: Boolean = false,
    val collectionsError: String? = null,
    val selectedCollectionId: Int? = null,
    val selectedCollectionName: String? = null,
    val collectionSeries: List<net.dom53.inkita.domain.model.Series> = emptyList(),
    val isCollectionSeriesLoading: Boolean = false,
    val collectionSeriesError: String? = null,
    val readingLists: List<ReadingList> = emptyList(),
    val isReadingListsLoading: Boolean = false,
    val readingListsError: String? = null,
    val people: List<Person> = emptyList(),
    val isPeopleLoading: Boolean = false,
    val peopleError: String? = null,
    val peoplePage: Int = 1,
    val canLoadMorePeople: Boolean = true,
    val isPeopleLoadingMore: Boolean = false,
    val selectedLibraryId: Int? = null,
    val selectedLibraryName: String? = null,
    val librarySeries: List<net.dom53.inkita.domain.model.Series> = emptyList(),
    val isLibrarySeriesLoading: Boolean = false,
    val isLibrarySeriesLoadingMore: Boolean = false,
    val librarySeriesError: String? = null,
    val librarySeriesPage: Int = 1,
    val canLoadMoreLibrarySeries: Boolean = true,
    val libraryAccessDenied: Boolean = false,
    val selectedSection: LibraryV2Section = LibraryV2Section.Home,
)

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

    init {
        loadLibraries()
        loadHome()
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
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
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
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
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
                if (!isOnline || (!alwaysRefresh && !isStale)) {
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
        }
    }

    private fun ensureWantToRead() {
        val current = _state.value
        if (current.isWantToReadLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isWantToReadLoading = true, wantToReadError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
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
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(wantToRead = cached, isWantToReadLoading = true, wantToReadError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isWantToReadLoading = false) }
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
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.WANT_TO_READ,
                "",
                result.getOrDefault(emptyList()),
            )
        }
    }

    private fun ensureCollections() {
        val current = _state.value
        if (current.isCollectionsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isCollectionsLoading = true, collectionsError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2Collections(LibraryV2CacheKeys.COLLECTIONS)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2CollectionsUpdatedAt(
                    LibraryV2CacheKeys.COLLECTIONS,
                )
            val hasStateData = current.collections.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(collections = cached, isCollectionsLoading = true, collectionsError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
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
        }
    }

    private fun ensureReadingLists() {
        val current = _state.value
        if (current.isReadingListsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isReadingListsLoading = true, readingListsError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2ReadingLists(LibraryV2CacheKeys.READING_LISTS)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2ReadingListsUpdatedAt(
                    LibraryV2CacheKeys.READING_LISTS,
                )
            val hasStateData = current.readingLists.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(readingLists = cached, isReadingListsLoading = true, readingListsError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
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
        }
    }

    private fun ensurePeople() {
        val current = _state.value
        if (current.isPeopleLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isPeopleLoading = true, peopleError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
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
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
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
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isPeopleLoading = false) }
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
            cacheManager.cacheLibraryV2People(
                LibraryV2CacheKeys.BROWSE_PEOPLE,
                1,
                pageItems,
            )
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
        }
    }

    private fun loadCollectionSeries(collectionId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isCollectionSeriesLoading = true, collectionSeriesError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
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
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update {
                        it.copy(collectionSeries = cached, isCollectionSeriesLoading = true, collectionSeriesError = null)
                    }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isCollectionSeriesLoading = false) }
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
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.COLLECTION_SERIES,
                cacheKey,
                result.getOrDefault(emptyList()),
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

    private fun showCollectionSeriesDebugToast(collectionId: Int) {
        viewModelScope.launch {
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cacheKey = collectionId.toString()
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.COLLECTION_SERIES,
                    cacheKey,
                )
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
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
