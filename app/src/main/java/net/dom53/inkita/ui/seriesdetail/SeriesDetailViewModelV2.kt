package net.dom53.inkita.ui.seriesdetail

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.AnnotationDto
import net.dom53.inkita.data.api.dto.AppUserCollectionDto
import net.dom53.inkita.data.api.dto.BookmarkDto
import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.HourEstimateRangeDto
import net.dom53.inkita.data.api.dto.RatingDto
import net.dom53.inkita.data.api.dto.ReaderProgressDto
import net.dom53.inkita.data.api.dto.ReadingListDto
import net.dom53.inkita.data.api.dto.RelatedSeriesDto
import net.dom53.inkita.data.api.dto.SeriesDetailDto
import net.dom53.inkita.data.api.dto.SeriesDetailPlusDto
import net.dom53.inkita.data.api.dto.SeriesDto
import net.dom53.inkita.data.api.dto.SeriesMetadataDto
import retrofit2.Response

data class SeriesDetailUiStateV2(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: InkitaDetailV2? = null,
    val showLoadedToast: Boolean = false,
    val collections: List<net.dom53.inkita.domain.model.Collection> = emptyList(),
    val isLoadingCollections: Boolean = false,
    val collectionError: String? = null,
    val collectionsWithSeries: Set<Int> = emptySet(),
)

data class InkitaDetailV2(
    val series: SeriesDto?,
    val metadata: SeriesMetadataDto?,
    val wantToRead: Boolean?,
    val readingLists: List<ReadingListDto>?,
    val collections: List<AppUserCollectionDto>?,
    val bookmarks: List<BookmarkDto>?,
    val annotations: List<AnnotationDto>?,
    val timeLeft: HourEstimateRangeDto?,
    val hasProgress: Boolean?,
    val continuePoint: ChapterDto?,
    val seriesDetailPlus: SeriesDetailPlusDto?,
    val related: RelatedSeriesDto?,
    val detail: SeriesDetailDto?,
    val rating: RatingDto?,
    val readerProgress: ReaderProgressDto?,
)

class SeriesDetailViewModelV2(
    val seriesId: Int,
    private val appPreferences: AppPreferences,
    private val collectionsRepository: net.dom53.inkita.domain.repository.CollectionsRepository,
    private val cacheManager: CacheManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SeriesDetailUiStateV2())
    val state: StateFlow<SeriesDetailUiStateV2> = _state
    private var latestConfig: net.dom53.inkita.core.storage.AppConfig? = null

    init {
        load()
    }

    fun consumeLoadedToast() {
        _state.update { it.copy(showLoadedToast = false) }
    }

    fun reload() {
        load()
    }

    fun loadCollections() {
        if (_state.value.isLoadingCollections) return
        viewModelScope.launch {
            if (appPreferences.offlineModeFlow.first()) {
                _state.update { it.copy(collectionError = "Offline mode") }
                return@launch
            }
            _state.update { it.copy(isLoadingCollections = true, collectionError = null) }
            val result = runCatching { collectionsRepository.getCollections() }
            result
                .onSuccess { list -> _state.update { it.copy(collections = list) } }
                .onFailure { e -> _state.update { it.copy(collectionError = e.message ?: "Failed to load collections.") } }
            _state.update { it.copy(isLoadingCollections = false) }
        }
    }

    fun toggleCollection(
        collection: net.dom53.inkita.domain.model.Collection,
        add: Boolean,
    ) {
        val cfg = latestConfig ?: return
        viewModelScope.launch {
            if (appPreferences.offlineModeFlow.first()) {
                _state.update { it.copy(collectionError = "Offline mode") }
                return@launch
            }
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            val result =
                runCatching {
                    if (add) {
                        api.addSeriesToCollection(
                            net.dom53.inkita.data.api.dto.CollectionTagBulkAddDto(
                                collectionTagId = collection.id,
                                collectionTagTitle = collection.name,
                                seriesIds = listOf(seriesId),
                            ),
                        )
                    } else {
                        api.updateSeriesForCollection(
                            net.dom53.inkita.data.api.dto.UpdateSeriesForTagDto(
                                tag =
                                    net.dom53.inkita.data.api.dto.AppUserCollectionDto(
                                        id = collection.id,
                                        title = collection.name,
                                        promoted = false,
                                        coverImageLocked = false,
                                    ),
                                seriesIdsToRemove = listOf(seriesId),
                            ),
                        )
                    }
                }
            result
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        refreshCollectionsForSeries()
                        runCatching { collectionsRepository.getCollections() }
                            .onSuccess { list -> _state.update { it.copy(collections = list) } }
                    } else {
                        _state.update { it.copy(collectionError = "HTTP ${resp.code()} ${resp.message()}") }
                    }
                }.onFailure { e ->
                    _state.update { it.copy(collectionError = e.message ?: "Error updating collection.") }
                }
        }
    }

    fun createCollection(title: String) {
        if (title.isBlank()) return
        val cfg = latestConfig ?: return
        viewModelScope.launch {
            if (appPreferences.offlineModeFlow.first()) {
                _state.update { it.copy(collectionError = "Offline mode") }
                return@launch
            }
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            val response =
                runCatching {
                    api.addSeriesToCollection(
                        net.dom53.inkita.data.api.dto.CollectionTagBulkAddDto(
                            collectionTagId = 0,
                            collectionTagTitle = title,
                            seriesIds = listOf(seriesId),
                        ),
                    )
                }
            response
                .onSuccess { resp ->
                    if (!resp.isSuccessful) {
                        _state.update { it.copy(collectionError = "HTTP ${resp.code()} ${resp.message()}") }
                        return@onSuccess
                    }
                    runCatching { collectionsRepository.getCollections() }
                        .onSuccess { list -> _state.update { it.copy(collections = list) } }
                    refreshCollectionsForSeries()
                }.onFailure { e ->
                    _state.update { it.copy(collectionError = e.message ?: "Failed to create collection.") }
                }
        }
    }

    private fun refreshCollectionsForSeries() {
        val cfg = latestConfig ?: return
        viewModelScope.launch {
            if (appPreferences.offlineModeFlow.first()) {
                _state.update { it.copy(collectionError = "Offline mode") }
                return@launch
            }
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            runCatching {
                api.getCollectionsForSeries(seriesId)
            }.onSuccess { resp ->
                if (resp.isSuccessful) {
                    val ids =
                        resp
                            .body()
                            .orEmpty()
                            .map { it.id }
                            .toSet()
                    _state.update { it.copy(collectionsWithSeries = ids) }
                }
            }.onFailure { e ->
                _state.update { it.copy(collectionError = e.message ?: "Failed to load collections.") }
            }
        }
    }

    fun toggleWantToRead() {
        viewModelScope.launch {
            val current = _state.value.detail ?: return@launch
            val currentSeriesId = current.series?.id ?: seriesId
            if (appPreferences.offlineModeFlow.first()) {
                _state.update { it.copy(error = "Offline mode") }
                return@launch
            }
            val config = appPreferences.configFlow.first()
            if (!config.isConfigured) {
                _state.update { it.copy(error = "Not configured") }
                return@launch
            }
            val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
            val want = current.wantToRead == true
            val response =
                if (want) {
                    api.removeWantToRead(
                        net.dom53.inkita.data.api.dto
                            .WantToReadDto(listOf(currentSeriesId)),
                    )
                } else {
                    api.addWantToRead(
                        net.dom53.inkita.data.api.dto
                            .WantToReadDto(listOf(currentSeriesId)),
                    )
                }
            if (!response.isSuccessful) {
                _state.update { it.copy(error = "Failed to update want-to-read") }
                return@launch
            }
            val updatedDetail = current.copy(wantToRead = !want)
            _state.update { state ->
                state.copy(
                    detail = updatedDetail,
                )
            }
            val policy = cacheManager.policy()
            if (policy.globalEnabled && policy.libraryEnabled && policy.libraryDetailsEnabled) {
                cacheManager.cacheSeriesDetailV2(seriesId, updatedDetail)
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val offlineMode = appPreferences.offlineModeFlow.first()
            val isOnline = !offlineMode && NetworkUtils.isOnline(appPreferences.appContext)
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleHours = appPreferences.cacheStaleHoursFlow.first()
            val policy = cacheManager.policy()
            val canCache = policy.globalEnabled && policy.libraryEnabled && policy.libraryDetailsEnabled
            val cachedDetail =
                if (canCache) cacheManager.getCachedSeriesDetailV2(seriesId) else null
            val cachedUpdatedAt =
                if (canCache) cacheManager.getSeriesDetailV2UpdatedAt(seriesId) else null
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleHours * 60L * 60L * 1000L
            if (cachedDetail != null) {
                val membership =
                    cachedDetail.collections
                        ?.map { it.id }
                        ?.toSet()
                        .orEmpty()
                _state.update {
                    it.copy(
                        detail = cachedDetail,
                        collectionsWithSeries = membership,
                        isLoading = true,
                        error = null,
                    )
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    showDebugToast(net.dom53.inkita.R.string.debug_cache_use)
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        net.dom53.inkita.R.string.debug_cache_force_online
                    } else {
                        net.dom53.inkita.R.string.debug_cache_stale_reload
                    }
                showDebugToast(message)
            } else {
                val message =
                    if (isOnline) {
                        net.dom53.inkita.R.string.debug_cache_use_fresh
                    } else {
                        net.dom53.inkita.R.string.debug_cache_no_cache
                    }
                showDebugToast(message)
                if (!isOnline) {
                    _state.update { it.copy(isLoading = false, error = "Offline mode") }
                    return@launch
                }
            }
            val config = appPreferences.configFlow.first()
            latestConfig = config
            if (!config.isConfigured) {
                _state.update { it.copy(isLoading = false, error = "Not configured") }
                return@launch
            }

            val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
            val errors = mutableListOf<String>()

            val seriesDeferred = async { api.getSeriesById(seriesId) }
            val metadataDeferred = async { api.getSeriesMetadata(seriesId) }
            val wantDeferred = async { api.hasWantToRead(seriesId) }
            val readingListsDeferred = async { api.getReadingListsForSeries(seriesId) }
            val collectionsDeferred = async { api.getCollectionsForSeriesOwned(seriesId, ownedOnly = false) }
            val bookmarksDeferred = async { api.getSeriesBookmarks(seriesId) }
            val annotationsDeferred = async { api.getAnnotationsForSeries(seriesId) }
            val timeLeftDeferred = async { api.getSeriesTimeLeft(seriesId) }
            val hasProgressDeferred = async { api.getHasProgress(seriesId) }
            val continuePointDeferred = async { api.getContinuePoint(seriesId) }
            val seriesDetailDeferred = async { api.getSeriesDetail(seriesId) }
            val relatedDeferred = async { api.getAllRelated(seriesId) }
            val ratingDeferred = async { api.getOverallSeriesRating(seriesId) }
            val librariesDeferred = async { api.getLibrariesFilter() }

            val seriesResponse = seriesDeferred.await()
            val series = seriesResponse.extract("series", errors)
            val metadata = metadataDeferred.await().extract("metadata", errors)
            val wantToRead = wantDeferred.await().extract("wantToRead", errors)
            val readingLists = readingListsDeferred.await().extractList("readingLists", errors)
            val collections = collectionsDeferred.await().extractList("collections", errors)
            val bookmarks = bookmarksDeferred.await().extractList("bookmarks", errors)
            val annotations = annotationsDeferred.await().extractList("annotations", errors)
            val timeLeft = timeLeftDeferred.await().extract("timeLeft", errors)
            val hasProgress = hasProgressDeferred.await().extract("hasProgress", errors)
            val continuePoint = continuePointDeferred.await().extract("continuePoint", errors)
            val readerProgress =
                if (continuePoint != null) {
                    api.getReaderProgress(continuePoint.id).extract("readerProgress", errors)
                } else {
                    null
                }
            val seriesDetail = seriesDetailDeferred.await().extract("seriesDetail", errors)
            val related = relatedDeferred.await().extract("related", errors)
            val rating = ratingDeferred.await().extract("rating", errors)

            val libraries = librariesDeferred.await().extractList("libraries", errors)
            val libraryType =
                series?.libraryId?.let { id ->
                    libraries?.firstOrNull { it.id == id }?.type
                }
            val seriesDetailPlus =
                if (libraryType != null) {
                    api.getSeriesDetailPlus(seriesId, libraryType).extract("seriesDetailPlus", errors)
                } else {
                    null
                }

            val detail =
                InkitaDetailV2(
                    series = series,
                    metadata = metadata,
                    wantToRead = wantToRead,
                    readingLists = readingLists,
                    collections = collections,
                    bookmarks = bookmarks,
                    annotations = annotations,
                    timeLeft = timeLeft,
                    hasProgress = hasProgress,
                    continuePoint = continuePoint,
                    seriesDetailPlus = seriesDetailPlus,
                    related = related,
                    detail = seriesDetail,
                    rating = rating,
                    readerProgress = readerProgress,
                )
            val membership =
                detail.collections
                    ?.map { it.id }
                    ?.toSet()
                    .orEmpty()

            _state.update {
                it.copy(
                    isLoading = false,
                    error = errors.firstOrNull(),
                    detail = detail,
                    showLoadedToast = true,
                    collectionsWithSeries = membership,
                )
            }
            if (canCache) {
                cacheManager.cacheSeriesDetailV2(seriesId, detail)
            }
        }
    }

    private fun <T> Response<T>.extract(
        label: String,
        errors: MutableList<String>,
    ): T? {
        if (!isSuccessful) {
            errors.add("$label: HTTP ${code()} ${message()}")
            return null
        }
        return body()
    }

    private fun <T> Response<List<T>>.extractList(
        label: String,
        errors: MutableList<String>,
    ): List<T>? {
        if (!isSuccessful) {
            errors.add("$label: HTTP ${code()} ${message()}")
            return null
        }
        return body() ?: emptyList()
    }

    companion object {
        fun provideFactory(
            seriesId: Int,
            appPreferences: AppPreferences,
            collectionsRepository: net.dom53.inkita.domain.repository.CollectionsRepository,
            cacheManager: CacheManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SeriesDetailViewModelV2(seriesId, appPreferences, collectionsRepository, cacheManager) as T
                }
            }
    }

    private suspend fun showDebugToast(messageRes: Int) {
        if (!appPreferences.debugToastsFlow.first()) return
        Toast.makeText(
            appPreferences.appContext,
            appPreferences.appContext.getString(messageRes),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
