package net.dom53.inkita.ui.browse

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlinx.coroutines.yield
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.notification.AppNotificationManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.CollectionDto
import net.dom53.inkita.data.api.dto.DecodeFilterRequest
import net.dom53.inkita.data.api.dto.FilterDefinitionDto
import net.dom53.inkita.data.api.dto.FilterV2Dto
import net.dom53.inkita.data.api.dto.LanguageDto
import net.dom53.inkita.data.api.dto.LibraryDto
import net.dom53.inkita.data.api.dto.NamedDto
import net.dom53.inkita.data.api.dto.SeriesDetailDto
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.filter.KavitaCombination
import net.dom53.inkita.domain.model.filter.KavitaSortField
import net.dom53.inkita.domain.model.filter.TriState
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.domain.usecase.AppliedFilter
import net.dom53.inkita.domain.usecase.ReadStatusFilter
import net.dom53.inkita.domain.usecase.SpecialFilter
import net.dom53.inkita.domain.usecase.buildQueries
import net.dom53.inkita.ui.common.DownloadState
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class BrowseUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val series: List<Series> = emptyList(),
    val currentPage: Int = 1,
    val canLoadMore: Boolean = true,
    val appliedSearch: String = "",
    val searchInput: TextFieldValue = TextFieldValue(""),
    val appliedFilter: AppliedFilter = AppliedFilter(),
    val draftFilter: AppliedFilter = AppliedFilter(),
    val languagesRemote: List<LanguageDto> = emptyList(),
    val tagsRemote: List<NamedDto> = emptyList(),
    val genresRemote: List<NamedDto> = emptyList(),
    val collectionsRemote: List<CollectionDto> = emptyList(),
    val librariesRemote: List<LibraryDto> = emptyList(),
    val smartFilters: List<FilterDefinitionDto> = emptyList(),
    val decodedSmartFilter: FilterV2Dto? = null,
    val isMetadataLoading: Boolean = false,
    val metadataError: String? = null,
    val downloadStates: Map<Int, DownloadState> = emptyMap(),
)

class BrowseViewModel(
    private val seriesRepository: SeriesRepository,
    private val appPreferences: AppPreferences,
    private val cacheManager: CacheManager,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state
    private val cacheFreshNotificationId = 3001
    private val networkMonitor = NetworkMonitor.getInstance(appPreferences.appContext, appPreferences)
    private var lastOfflineError = false
    private val pagingInFlight = AtomicBoolean(false)
    private val downloadDao =
        InkitaDatabase.getInstance(appPreferences.appContext).downloadV2Dao()
    private var lastDownloadedItems: List<DownloadedItemV2Entity> = emptyList()

    init {
        reloadFirstPage()
        observeConnectivity()
        observeDownloadStates()
    }

    fun applyQuickGenreFilter(
        genreId: Int,
        genreName: String? = null,
    ) {
        viewModelScope.launch {
            val updated =
                _state.value.draftFilter.copy(
                    genres = mapOf(genreId to TriState.Include),
                    smartFilterId = null,
                    decodedSmartFilter = null,
                )
            _state.update {
                it.copy(
                    draftFilter = updated,
                    appliedFilter = updated,
                    appliedSearch = "",
                    searchInput = TextFieldValue(""),
                    currentPage = 1,
                    canLoadMore = true,
                    error = null,
                )
            }
            reloadFirstPage()
        }
    }

    fun applyQuickTagFilter(
        tagId: Int,
        tagName: String? = null,
    ) {
        viewModelScope.launch {
            val updated =
                _state.value.draftFilter.copy(
                    tags = mapOf(tagId to TriState.Include),
                    smartFilterId = null,
                    decodedSmartFilter = null,
                )
            _state.update {
                it.copy(
                    draftFilter = updated,
                    appliedFilter = updated,
                    appliedSearch = "",
                    searchInput = TextFieldValue(""),
                    currentPage = 1,
                    canLoadMore = true,
                    error = null,
                )
            }
            reloadFirstPage()
        }
    }

    fun updateSearch(text: TextFieldValue) {
        _state.update { it.copy(searchInput = text) }
    }

    fun applySearchAndReload() {
        _state.update {
            it.copy(
                appliedSearch = it.searchInput.text,
                currentPage = 1,
                canLoadMore = true,
                error = null,
            )
        }
        reloadFirstPage()
    }

    fun updateDraftFilter(update: (AppliedFilter) -> AppliedFilter) {
        _state.update { it.copy(draftFilter = update(it.draftFilter)) }
    }

    fun applyFilterAndReload() {
        val draft = _state.value.draftFilter
        _state.update {
            it.copy(
                appliedFilter = draft,
                decodedSmartFilter = draft.decodedSmartFilter,
                appliedSearch = it.searchInput.text,
                currentPage = 1,
                canLoadMore = true,
                error = null,
            )
        }
        reloadFirstPage()
    }

    fun reloadFirstPage() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, series = emptyList()) }
            lastOfflineError = false

            val cacheEnabled = browseCacheAllowed()
            val ttlMinutes = appPreferences.cacheRefreshTtlMinutesFlow.first()
            val lastRefresh = appPreferences.lastBrowseRefreshFlow.first()
            val now = System.currentTimeMillis()
            val queryKey = currentQueryKey(page = 1)
            val pageSize = appPreferences.browsePageSizeFlow.first()

            var cachedPage: List<Series> = emptyList()
            if (cacheEnabled) {
                cachedPage = runCatching { seriesRepository.getCachedBrowsePage(queryKey, 1) }.getOrNull().orEmpty()
                if (cachedPage.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            series = cachedPage,
                            currentPage = 1,
                            canLoadMore = true,
                            isLoading = true, // still fetch fresh unless cache is valid
                            isLoadingMore = false,
                            error = null,
                        )
                    }
                    updateDownloadStates(lastDownloadedItems)
                }
            }

            val ttlMillis = ttlMinutes * 60_000L
            if (cacheEnabled && cachedPage.isNotEmpty() && ttlMinutes > 0 && lastRefresh > 0 && (now - lastRefresh) < ttlMillis) {
                showFreshCacheNotification(ttlMinutes - ((now - lastRefresh) / 60_000L).toInt())
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        canLoadMore = true,
                        isLoadingMore = false,
                    )
                }
                updateDownloadStates(lastDownloadedItems)
                return@launch
            }

            val page =
                runCatching {
                    fetchPageProgressive(
                        page = 1,
                        pageSize = appPreferences.browsePageSizeFlow.first(),
                        cacheKey = if (cacheEnabled) queryKey else null,
                    ) { progress ->
                        _state.update {
                            it.copy(
                                series = progress,
                                currentPage = 1,
                                canLoadMore = true,
                                isLoading = true,
                                isLoadingMore = false,
                                error = null,
                            )
                        }
                    }
                }.onFailure { e ->
                    lastOfflineError = e is IOException || (e.message?.contains("offline", ignoreCase = true) == true)
                    _state.update { st -> st.copy(isLoading = false, error = e.message ?: "Failed to load series") }
                }.getOrNull() ?: return@launch

            lastOfflineError = false
            _state.update {
                it.copy(
                    series = page,
                    currentPage = 1,
                    canLoadMore = page.isNotEmpty(),
                    isLoading = false,
                    isLoadingMore = false,
                )
            }
            updateDownloadStates(lastDownloadedItems)
            if (cacheEnabled) {
                runCatching { appPreferences.setLastBrowseRefresh(System.currentTimeMillis()) }
            }
        }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
        if (!pagingInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoadingMore = true, error = null) }
                val nextPageNumber = current.currentPage + 1
                val cacheEnabled = browseCacheAllowed()
                val queryKey = currentQueryKey(page = nextPageNumber)
                val nextPage =
                    runCatching {
                        val pageSize = appPreferences.browsePageSizeFlow.first()
                        val base = _state.value.series
                        val baseIds = base.map { it.id }.toHashSet()
                        fetchPageProgressive(
                            page = nextPageNumber,
                            pageSize = pageSize,
                            cacheKey = if (cacheEnabled) queryKey else null,
                        ) { progress ->
                            val combined = base + progress.filter { it.id !in baseIds }
                            _state.update { st ->
                                st.copy(
                                    series = combined,
                                    isLoadingMore = true,
                                    error = null,
                                )
                            }
                        }
                    }.onFailure { e ->
                        lastOfflineError = e is IOException || (e.message?.contains("offline", ignoreCase = true) == true)
                        _state.update { st -> st.copy(isLoadingMore = false, error = e.message ?: "Failed to load page") }
                    }.getOrNull() ?: return@launch

                lastOfflineError = false
                _state.update {
                    it.copy(
                        series = (it.series + nextPage).distinctBy { s -> s.id },
                        currentPage = nextPageNumber,
                        canLoadMore = nextPage.isNotEmpty(),
                        isLoadingMore = false,
                    )
                }
                updateDownloadStates(lastDownloadedItems)
                if (cacheEnabled) {
                    runCatching { appPreferences.setLastBrowseRefresh(System.currentTimeMillis()) }
                }
            } finally {
                pagingInFlight.set(false)
            }
        }
    }

    @Suppress("NestedBlockDepth")
    private suspend fun fetchPageProgressive(
        page: Int,
        pageSize: Int,
        cacheKey: String? = null,
        onProgress: (List<Series>) -> Unit,
    ): List<Series> {
        val queries =
            buildQueries(
                appliedFilter = _state.value.appliedFilter,
                search = _state.value.appliedSearch,
                page = page,
                pageSize = pageSize,
            )
        val ordered = LinkedHashMap<Int, Series>()
        val emitChunkSize = 3
        for (query in queries) {
            val items = seriesRepository.getSeries(query, prefetchThumbnails = false)
            var pendingEmits = 0
            for (item in items) {
                if (!ordered.containsKey(item.id)) {
                    ordered[item.id] = item
                    pendingEmits++
                    if (pendingEmits >= emitChunkSize) {
                        onProgress(ordered.values.toList())
                        pendingEmits = 0
                        yield()
                    }
                }
            }
            if (pendingEmits > 0) {
                onProgress(ordered.values.toList())
                yield()
            }
        }
        val results = ordered.values.toList()
        if (cacheKey != null) {
            seriesRepository.cacheBrowsePage(cacheKey, page, results)
        }
        return results
    }

    private fun currentQueryKey(page: Int): String {
        val filter = _state.value.appliedFilter
        val search = _state.value.appliedSearch
        // simple stable fingerprint
        return "s=$search|f=$filter|p=$page"
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
        val series = _state.value.series
        if (series.isEmpty()) {
            _state.update { it.copy(downloadStates = emptyMap()) }
            return
        }
        val seriesInfoById =
            series.associate { entry ->
                entry.id to SeriesDownloadInfo(entry.id, entry.format, entry.pages)
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

    fun setCombination(value: KavitaCombination) = updateDraftFilter { it.copy(combination = value) }

    fun setSort(
        field: KavitaSortField,
        desc: Boolean,
    ) = updateDraftFilter { it.copy(sortField = field, sortDesc = desc) }

    fun setStatusFilter(value: ReadStatusFilter) = updateDraftFilter { it.copy(statusFilter = value) }

    fun setYearBounds(
        min: String,
        max: String,
    ) = updateDraftFilter { it.copy(minYear = min, maxYear = max) }

    fun setTriStateGenre(
        id: Int,
        state: TriState,
    ) = updateDraftFilter { it.copy(genres = it.genres + (id to state)) }

    fun setTriStateTag(
        id: Int,
        state: TriState,
    ) = updateDraftFilter { it.copy(tags = it.tags + (id to state)) }

    fun setTriStateLanguage(
        id: String,
        state: TriState,
    ) = updateDraftFilter { it.copy(languages = it.languages + (id to state)) }

    fun setTriStateAge(
        id: Int,
        state: TriState,
    ) = updateDraftFilter { it.copy(ageRatings = it.ageRatings + (id to state)) }

    fun setTriStateCollection(
        id: Int,
        state: TriState,
    ) = updateDraftFilter { it.copy(collections = it.collections + (id to state)) }

    fun setTriStateLibrary(
        id: Int,
        state: TriState,
    ) = updateDraftFilter { it.copy(libraries = it.libraries + (id to state)) }

    fun setPublication(
        id: Int,
        selected: Boolean,
    ) = updateDraftFilter { it.copy(publication = it.publication + (id to selected)) }

    fun setSpecial(value: SpecialFilter?) = updateDraftFilter { it.copy(special = value, smartFilterId = null, decodedSmartFilter = null) }

    fun setSmartFilter(
        id: Int?,
        decoded: FilterV2Dto?,
    ) = updateDraftFilter {
        it.copy(smartFilterId = id, decodedSmartFilter = decoded, special = null)
    }

    fun onSmartSelected(id: Int?) {
        viewModelScope.launch {
            if (id == null) {
                setSmartFilter(null, null)
                return@launch
            }
            val decoded = decodeSmartFilter(id)
            setSmartFilter(id, decoded)
        }
    }

    fun loadMetadataIfNeeded(selectedLibraryIds: List<Int>) {
        val current = _state.value
        if (current.isMetadataLoading || current.languagesRemote.isNotEmpty()) return
        viewModelScope.launch {
            val cfg = appPreferences.configFlow.first()
            if (!cfg.isConfigured) return@launch
            _state.update { it.copy(isMetadataLoading = true, metadataError = null) }
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            runCatching {
                val languages = api.getLanguagesMeta().body().orEmpty()
                val collections = api.getOwnedCollections(true).body().orEmpty()
                val libraries = api.getLibrariesFilter().body().orEmpty()
                val smart = api.getFilters().body().orEmpty()
                val libIds = selectedLibraryIds.ifEmpty { libraries.map { it.id } }
                val idsString = libIds.joinToString(",")
                val tags = if (idsString.isNotEmpty()) api.getTagsForLibraries(idsString).body().orEmpty() else emptyList()
                val genres = if (idsString.isNotEmpty()) api.getGenresForLibraries(idsString).body().orEmpty() else emptyList()
                _state.update {
                    it.copy(
                        languagesRemote = languages,
                        collectionsRemote = collections,
                        librariesRemote = libraries,
                        smartFilters = smart,
                        tagsRemote = tags,
                        genresRemote = genres,
                        isMetadataLoading = false,
                        metadataError = null,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isMetadataLoading = false, metadataError = e.message) }
            }
        }
    }

    suspend fun decodeSmartFilter(filterId: Int?): FilterV2Dto? {
        val cfg = runCatching { appPreferences.configFlow.first() }.getOrNull() ?: return null
        if (!cfg.isConfigured || filterId == null) return null
        val def = _state.value.smartFilters.find { it.id == filterId } ?: return null
        return runCatching {
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            api.decodeFilter(DecodeFilterRequest(def.filter)).body()
        }.getOrNull()
    }

    private data class SeriesDownloadInfo(
        val id: Int,
        val format: Format?,
        val pages: Int?,
    )

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
            result[id] = resolveSeriesDownloadState(info, items, detail)
        }
        return result
    }

    private fun resolveSeriesDownloadState(
        info: SeriesDownloadInfo,
        items: List<DownloadedItemV2Entity>,
        detail: InkitaDetailV2?,
    ): DownloadState {
        val format = info.format ?: Format.fromId(detail?.series?.format)
        val completedPages =
            items
                .filter { it.type == DownloadedItemV2Entity.TYPE_PAGE }
                .count { isItemPathPresent(it.localPath) }
        val completedFiles =
            items
                .filter { it.type == DownloadedItemV2Entity.TYPE_FILE }
                .count { isItemPathPresent(it.localPath) }
        val expected =
            if (format == Format.Pdf) {
                val chapters = countChapters(detail?.detail)
                if (chapters > 0) chapters else 0
            } else {
                val pages = info.pages?.takeIf { it > 0 } ?: sumPages(detail?.detail)
                if (pages > 0) pages else 0
            }
        val completed =
            when {
                format == Format.Pdf -> completedFiles
                completedPages > 0 -> completedPages
                else -> completedFiles
            }
        val state =
            when {
                expected > 0 && completed >= expected -> DownloadState.Complete
                completed > 0 -> DownloadState.Partial
                else -> DownloadState.None
            }
        if (LoggingManager.isDebugEnabled()) {
            val source = if (detail != null) "cache" else "fallback"
            LoggingManager.d(
                "BrowseBadge",
                "series=${info.id} format=${format?.id} expected=$expected completed=$completed state=$state source=$source",
            )
        }
        return state
    }

    private fun countChapters(detail: SeriesDetailDto?): Int =
        collectChapters(detail).size

    private fun sumPages(detail: SeriesDetailDto?): Int =
        collectChapters(detail)
            .sumOf { it.pages ?: 0 }

    private fun collectChapters(
        detail: SeriesDetailDto?,
    ): List<net.dom53.inkita.data.api.dto.ChapterDto> {
        if (detail == null) return emptyList()
        return buildList {
            detail.volumes?.forEach { volume ->
                volume.chapters?.let { addAll(it) }
            }
            detail.chapters?.let { addAll(it) }
            detail.specials?.let { addAll(it) }
            detail.storylineChapters?.let { addAll(it) }
        }.distinctBy { it.id }
    }

    private fun isItemPathPresent(path: String?): Boolean =
        path?.let { java.io.File(it).exists() } == true

    private suspend fun browseCacheAllowed(): Boolean = cacheManager.policy().browseEnabled

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showFreshCacheNotification(minutesRemaining: Int) {
        val content =
            if (minutesRemaining > 0) {
                "Cache is fresh. Next refresh in ~$minutesRemaining min."
            } else {
                "Cache is fresh. Refresh postponed."
            }
        AppNotificationManager.showInfo(
            id = cacheFreshNotificationId,
            channel = AppNotificationManager.CHANNEL_GENERAL,
            title = "Cache still fresh",
            text = content,
            autoCancel = true,
        )
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.status.collect { status ->
                if (status.isOnlineAllowed && lastOfflineError && !_state.value.isLoading) {
                    reloadFirstPage()
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            seriesRepository: SeriesRepository,
            appPreferences: AppPreferences,
            cacheManager: CacheManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return BrowseViewModel(seriesRepository, appPreferences, cacheManager) as T
                }
            }
    }
}
