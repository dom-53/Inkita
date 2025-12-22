package net.dom53.inkita.ui.library

import android.Manifest
import androidx.annotation.RequiresPermission
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
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.notification.AppNotificationManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.filter.FilterClause
import net.dom53.inkita.domain.model.filter.KavitaCombination
import net.dom53.inkita.domain.model.filter.KavitaComparison
import net.dom53.inkita.domain.model.filter.KavitaField
import net.dom53.inkita.domain.model.filter.KavitaSortField
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
import net.dom53.inkita.domain.model.library.LibraryTabType
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.SeriesRepository

data class LibraryUiState(
    val collections: List<Collection> = emptyList(),
    val selectedTabIndex: Int = 0,
    val series: List<Series> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
    val prefetchInProgress: Boolean = false,
    val prefetchCompleted: Boolean = false,
)

class LibraryViewModel(
    private val seriesRepository: SeriesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val appPreferences: AppPreferences,
    private val cacheManager: CacheManager,
) : ViewModel() {
    private val allowedFormats = setOf(Format.Epub, Format.Pdf)
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    private var prefetchStarted = false
    private val freshCacheNotificationId = 2002

    init {
        refreshCollectionsAndFirstPage()
    }

    fun refreshCollectionsAndFirstPage() {
        viewModelScope.launch {
            prefetchStarted = false
            _state.update { it.copy(isLoading = true, error = null) }
            val cacheEnabled = libraryCacheAllowed()
            val ttlMinutes = appPreferences.cacheRefreshTtlMinutesFlow.first()
            val lastRefresh = appPreferences.lastLibraryRefreshFlow.first()
            val now = System.currentTimeMillis()

            var cachedCollections: List<Collection> = emptyList()
            if (cacheEnabled) {
                cachedCollections = runCatching { appPreferences.loadCachedCollections() }.getOrDefault(emptyList())
                if (cachedCollections.isNotEmpty()) {
                    _state.update { it.copy(collections = cachedCollections) }
                }
            }

            // Nejprve zkuste ukázat cache pro první tab, aby UI bylo rychlé i online
            if (cacheEnabled) {
                val firstTabKey = LibraryTabCacheKey(LibraryTabType.InProgress, null)
                val cached =
                    runCatching { seriesRepository.getCachedSeriesForTab(firstTabKey) }
                        .getOrNull()
                        .orEmpty()
                        .filter { it.format in allowedFormats }
                if (cached.isNotEmpty()) {
                    LoggingManager.d("LibraryVM", "Showing cached series for first tab (${cached.size})")
                    _state.update {
                        it.copy(
                            series = cached,
                            currentPage = 1,
                            canLoadMore = true,
                            selectedTabIndex = 0,
                            isLoading = true, // stále proběhne čerstvý fetch na pozadí
                            error = null,
                        )
                    }
                }
            }

            val collectionsResult =
                runCatching { collectionsRepository.getCollections() }
                    .onFailure { e ->
                        LoggingManager.w("LibraryVM", "Collections fetch failed: ${e.message}")
                        if (cacheEnabled) {
                            if (cachedCollections.isNotEmpty()) {
                                _state.update { st -> st.copy(collections = cachedCollections) }
                            }
                        } else {
                            _state.update { st -> st.copy(isLoading = false, error = e.message) }
                        }
                    }
            val collections = collectionsResult.getOrElse { cachedCollections }
            if (collections.isNotEmpty()) {
                runCatching { appPreferences.saveCollectionsCache(collections) }
            }

            val firstTabKey = LibraryTabCacheKey(LibraryTabType.InProgress, null)
            if (cacheEnabled) {
                val cached =
                    runCatching { seriesRepository.getCachedSeriesForTab(firstTabKey) }
                        .getOrNull()
                        .orEmpty()
                        .filter { it.format in allowedFormats }
                if (cached.isNotEmpty()) {
                    LoggingManager.d("LibraryVM", "Showing cached series for first tab (${cached.size})")
                    _state.update {
                        it.copy(
                            collections = collections,
                            series = cached,
                            currentPage = 1,
                            canLoadMore = true,
                            selectedTabIndex = 0,
                            isLoading = true, // still fetch fresh if possible
                            error = null,
                        )
                    }
                }
            }

            val ttlMillis = ttlMinutes * 60_000L
            if (cacheEnabled && ttlMinutes > 0 && lastRefresh > 0 && (now - lastRefresh) < ttlMillis) {
                showFreshCacheNotification(ttlMinutes - ((now - lastRefresh) / 60_000L).toInt())
                _state.update { it.copy(isLoading = false, error = null, canLoadMore = false) }
                return@launch
            }

            val firstPage =
                runCatching { fetchSeriesPage(0, 1, collections) }
                    .onFailure { e ->
                        if (!cacheEnabled) {
                            _state.update { st -> st.copy(isLoading = false, error = e.message) }
                        } else {
                            LoggingManager.w("LibraryVM", "Network fetch failed, keeping cached list: ${e.message}")
                            _state.update { st ->
                                st.copy(
                                    isLoading = false,
                                    error = null,
                                    canLoadMore = false, // nepokračuj v pokusech o další stránky offline
                                )
                            }
                        }
                    }.getOrNull()

            if (firstPage == null && cacheEnabled && _state.value.series.isNotEmpty()) {
                // Already showed cache and network failed; finish loading without error
                _state.update { it.copy(isLoading = false, error = null, canLoadMore = false) }
                return@launch
            }
            if (firstPage == null) return@launch

            if (cacheEnabled) {
                seriesRepository.cacheTabResults(firstTabKey, firstPage)
                runCatching { appPreferences.setLastLibraryRefresh(System.currentTimeMillis()) }
            }

            _state.update {
                it.copy(
                    collections = collections,
                    series = firstPage,
                    currentPage = 1,
                    canLoadMore = firstPage.isNotEmpty(),
                    selectedTabIndex = 0,
                    isLoading = false,
                    error = null,
                )
            }

            launchPrefetchIfNeeded()
        }
    }

    fun selectTab(index: Int) {
        if (index == _state.value.selectedTabIndex) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedTabIndex = index,
                    series = emptyList(),
                    currentPage = 1,
                    canLoadMore = true,
                    isLoading = true,
                    error = null,
                )
            }
            val cacheEnabled = libraryCacheAllowed()
            val collections = _state.value.collections
            val key = tabKey(index, collections)
            if (cacheEnabled && key != null) {
                val cached =
                    runCatching { seriesRepository.getCachedSeriesForTab(key) }
                        .getOrNull()
                        .orEmpty()
                        .filter { it.format in allowedFormats }
                if (cached.isNotEmpty()) {
                    LoggingManager.d("LibraryVM", "Showing cached series for tab $index (${cached.size})")
                    _state.update { st ->
                        st.copy(
                            series = cached,
                            currentPage = 1,
                            canLoadMore = true,
                            isLoading = true, // will still fetch fresh
                            error = null,
                        )
                    }
                }
            }

            val firstPage =
                runCatching { fetchSeriesPage(index, 1, collections) }
                    .onFailure { e ->
                        if (!cacheEnabled) {
                            _state.update { st -> st.copy(isLoading = false, error = e.message) }
                        } else {
                            LoggingManager.w("LibraryVM", "Network fetch failed for tab $index, keeping cached: ${e.message}")
                            _state.update { st -> st.copy(isLoading = false, error = null) }
                        }
                    }.getOrNull() ?: return@launch

            if (cacheEnabled && key != null) {
                seriesRepository.cacheTabResults(key, firstPage)
            }

            _state.update {
                it.copy(
                    series = firstPage,
                    currentPage = 1,
                    canLoadMore = firstPage.isNotEmpty(),
                    isLoading = false,
                    error = null,
                )
            }
        }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true, error = null) }
            val cacheEnabled = libraryCacheAllowed()
            val collections = _state.value.collections
            val nextPageNumber = current.currentPage + 1
            val next =
                runCatching { fetchSeriesPage(current.selectedTabIndex, nextPageNumber, collections) }
                    .onFailure { e ->
                        if (!cacheEnabled) {
                            _state.update { st -> st.copy(isLoadingMore = false, error = e.message) }
                        } else {
                            LoggingManager.w("LibraryVM", "Next page fetch failed, keeping cached list: ${e.message}")
                            _state.update { st ->
                                st.copy(
                                    isLoadingMore = false,
                                    error = null,
                                    canLoadMore = false, // přestaň zkoušet další stránky offline
                                )
                            }
                        }
                    }.getOrNull()

            if (next == null) return@launch
            val key = tabKey(current.selectedTabIndex, collections)
            if (cacheEnabled && key != null) {
                val combined = (current.series + next).distinctBy { it.id }
                seriesRepository.cacheTabResults(key, combined)
            }
            _state.update {
                it.copy(
                    series = (it.series + next).distinctBy { s -> s.id },
                    currentPage = nextPageNumber,
                    canLoadMore = next.isNotEmpty(),
                    isLoadingMore = false,
                )
            }
        }
    }

    fun refreshSeriesReadState(seriesId: Int) {
        viewModelScope.launch {
            val detail = runCatching { seriesRepository.getSeriesDetail(seriesId) }.getOrNull() ?: return@launch
            val refreshed = detail.series.copy(readState = detail.readState ?: detail.series.readState)
            _state.update { st ->
                st.copy(series = st.series.map { if (it.id == seriesId) refreshed else it })
            }
        }
    }

    private suspend fun fetchSeriesPage(
        selectedTabIndex: Int,
        page: Int,
        collections: List<Collection>,
        pageSize: Int = 50,
    ): List<Series> {
        return when (selectedTabIndex) {
            0 -> {
                val baseClauses =
                    listOf(
                        FilterClause(
                            field = KavitaField.ReadingProgress,
                            comparison = KavitaComparison.GreaterThan,
                            value = "0",
                        ),
                        FilterClause(
                            field = KavitaField.ReadingProgress,
                            comparison = KavitaComparison.LessThan,
                            value = "100",
                        ),
                    )
                loadSeriesWithFormats(
                    baseClauses = baseClauses,
                    page = page,
                    pageSize = pageSize,
                )
            }
            1 -> {
                val baseClauses =
                    listOf(
                        FilterClause(
                            field = KavitaField.WantToRead,
                            comparison = KavitaComparison.Equal,
                            value = "true",
                        ),
                    )
                loadSeriesWithFormats(
                    baseClauses = baseClauses,
                    page = page,
                    pageSize = pageSize,
                )
            }
            else -> {
                val collection = collections.getOrNull(selectedTabIndex - 2) ?: return emptyList()
                collectionsRepository.getSeriesForCollection(
                    collectionId = collection.id,
                    page = page,
                    pageSize = pageSize,
                )
            }
        }
    }

    private suspend fun loadSeriesWithFormats(
        baseClauses: List<FilterClause>,
        page: Int,
        pageSize: Int,
    ): List<Series> {
        val formats = listOf(Format.Epub, Format.Pdf)
        val results =
            formats.flatMap { format ->
                val query =
                    SeriesQuery(
                        clauses =
                            baseClauses +
                                FilterClause(
                                    field = KavitaField.Formats,
                                    comparison = KavitaComparison.Equal,
                                    value = format.id.toString(),
                                ),
                        combination = KavitaCombination.MatchAll,
                        sortField = KavitaSortField.SortName,
                        page = page,
                        pageSize = pageSize,
                    )
                seriesRepository.getSeries(query)
            }
        return results.associateBy { it.id }.values.toList()
    }

    private fun tabKey(
        index: Int,
        collections: List<Collection>,
    ): LibraryTabCacheKey? =
        when (index) {
            0 -> LibraryTabCacheKey(LibraryTabType.InProgress, null)
            1 -> LibraryTabCacheKey(LibraryTabType.WantToRead, null)
            else -> {
                val collection = collections.getOrNull(index - 2) ?: return null
                LibraryTabCacheKey(LibraryTabType.Collection, collection.id)
            }
        }

    private fun launchPrefetchIfNeeded() {
        if (prefetchStarted) return
        viewModelScope.launch {
            val cacheAllowed = libraryCacheAllowed()
            if (!cacheAllowed) {
                LoggingManager.d("PrefetchWorker", "Prefetch skipped: cache disabled")
                return@launch
            }
            val policy = appPreferences.prefetchPolicy()
            if (!policy.hasTargetsEnabled) {
                LoggingManager.d("PrefetchWorker", "Prefetch skipped: no targets enabled")
                return@launch
            }
            prefetchStarted = true
            LoggingManager.d("PrefetchWorker", "Prefetch enqueue requested")
            PrefetchWorker.enqueue(appPreferences.appContext)
            // necháme práci na WorkManageru; UI neblokuje start
        }
    }

    private suspend fun libraryCacheAllowed(): Boolean = cacheManager.policy().libraryEnabled

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showFreshCacheNotification(minutesRemaining: Int) {
        val content =
            if (minutesRemaining > 0) {
                "Cache is fresh. Next refresh in ~$minutesRemaining min."
            } else {
                "Cache is fresh. Refresh postponed."
            }
        AppNotificationManager.showInfo(
            id = freshCacheNotificationId,
            channel = AppNotificationManager.CHANNEL_GENERAL,
            title = "Cache still fresh",
            text = content,
            autoCancel = true,
        )
    }

    companion object {
        fun provideFactory(
            seriesRepository: SeriesRepository,
            collectionsRepository: CollectionsRepository,
            appPreferences: AppPreferences,
            cacheManager: CacheManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(seriesRepository, collectionsRepository, appPreferences, cacheManager) as T
                }
            }
    }
}
