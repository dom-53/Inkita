package net.dom53.inkita.ui.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.download.DownloadManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.AppUserCollectionDto
import net.dom53.inkita.data.api.dto.CollectionTagBulkAddDto
import net.dom53.inkita.data.api.dto.UpdateSeriesForTagDto
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.Volume
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.DownloadRepository
import net.dom53.inkita.domain.repository.ReaderRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.ui.seriesdetail.model.RelatedGroup
import net.dom53.inkita.ui.seriesdetail.model.SwipeDirection
import net.dom53.inkita.ui.seriesdetail.model.VolumeProgressUi
import net.dom53.inkita.ui.seriesdetail.model.emptyFilter
import net.dom53.inkita.ui.seriesdetail.utils.loadRelated
import net.dom53.inkita.ui.seriesdetail.utils.toggleWantToRead
import java.net.UnknownHostException
import kotlin.math.max

data class SeriesDetailState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: SeriesDetail? = null,
    val volumeProgress: Map<Int, VolumeProgressUi> = emptyMap(),
    val wantToRead: Boolean = false,
    val isUpdatingWant: Boolean = false,
    val wantLoaded: Boolean = false,
    val collections: List<Collection> = emptyList(),
    val collectionsWithSeries: Set<Int> = emptySet(),
    val collectionsLoaded: Boolean = false,
    val isLoadingCollections: Boolean = false,
    val collectionError: String? = null,
    val relatedGroups: List<RelatedGroup> = emptyList(),
    val relatedError: String? = null,
    val isLoadingRelated: Boolean = false,
    val pendingVolumeMark: Volume? = null,
    val pendingPreviousVolumes: List<Int> = emptyList(),
    val pendingUnreadVolume: Volume? = null,
    val pendingNextReadVolumes: List<Int> = emptyList(),
    val pendingChapterMark: Volume? = null,
    val pendingChapterIndex: Int? = null,
    val pendingChapterPage: Int? = null,
    val pendingChapterPrevVolumes: List<Int> = emptyList(),
    val pendingChapterUnreadVolume: Volume? = null,
    val pendingChapterNextVolumes: List<Int> = emptyList(),
    val pendingChapterUnreadPage: Int? = null,
    val continueVolumeId: Int? = null,
    val continuePage: Int? = null,
    val continueChapterId: Int? = null,
    val downloadedChapters: Map<Int, Set<Int>> = emptyMap(),
    val downloadedVolumeIds: Set<Int> = emptySet(),
)

class SeriesDetailViewModel(
    private val seriesId: Int,
    private val seriesRepository: SeriesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val readerRepository: ReaderRepository?,
    private val appPreferences: AppPreferences,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SeriesDetailState())
    val state: StateFlow<SeriesDetailState> = _state.asStateFlow()

    private var latestConfig: AppConfig? = null
    private val _events = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events
    private var latestDownloads: Map<Int, Set<Int>> = emptyMap()

    init {
        viewModelScope.launch {
            appPreferences.configFlow.collectLatest { cfg ->
                latestConfig = cfg
                if (!cfg.isConfigured) {
                    _state.update { it.copy(isLoading = false, error = "Missing server configuration.") }
                } else {
                    loadDetail()
                }
            }
        }
        viewModelScope.launch {
            downloadRepository.observeValidDownloadedPages().collectLatest { pages ->
                val grouped = pages.groupBy { it.chapterId }.mapValues { entry -> entry.value.map { it.page }.toSet() }
                latestDownloads = grouped
                updateDownloadState(_state.value.detail, grouped)
            }
        }
        viewModelScope.launch { downloadRepository.cleanupMissingDownloads() }
    }

    fun loadDetail() {
        if (latestConfig == null) return
        viewModelScope.launch {
            // Start refresh state
            _state.update { it.copy(isLoading = true, error = null) }

            val result = runCatching { seriesRepository.getSeriesDetail(seriesId, false) }
            result
                .onSuccess { loaded ->
                    val progress = buildVolumeProgress(loaded)
                    val continueTarget = computeContinueTarget(loaded, progress)
                    _state.update {
                        it.copy(
                            detail = loaded,
                            volumeProgress = progress,
                            isLoading = false,
                            error = null,
                            continueVolumeId = continueTarget?.first,
                            continuePage = continueTarget?.second,
                            continueChapterId = continueTarget?.third,
                            downloadedChapters = latestDownloads,
                            downloadedVolumeIds = computeDownloadedVolumes(loaded, latestDownloads),
                        )
                    }
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load detail.") }
                    _events.tryEmit(e.message ?: "Failed to refresh detail")
                }
            // preload want/collections once after load
            ensureWantAndCollections()
        }
    }

    private suspend fun buildVolumeProgress(
        detail: SeriesDetail,
        allowNetwork: Boolean = true,
    ): Map<Int, VolumeProgressUi> {
        val repo = readerRepository ?: return emptyMap()
        if (!allowNetwork) return emptyMap()
        val progressMap = mutableMapOf<Int, VolumeProgressUi>()
        allVolumes(detail).forEach { vol ->
            val bookId = vol.bookId
            if (bookId != null) {
                val progress = runCatching { repo.getProgress(bookId) }.getOrNull()
                val total = vol.chapters.size
                val isCompleted = vol.chapters.all { it.status == ReadState.Completed }
                if (isCompleted) return@forEach
                val normalizedPage = progress?.page?.takeIf { p -> total == 0 || p in 0 until total }
                progressMap[vol.id] =
                    VolumeProgressUi(
                        page = normalizedPage,
                        totalPages = total.takeIf { it > 0 },
                        timeLeft = null,
                    )
            }
        }
        val continuePoint = runCatching { repo.getContinuePoint(detail.series.id ?: seriesId) }.getOrNull()
        if (continuePoint?.volumeId != null && continuePoint.chapterId != null) {
            val volumeForContinue = detail.volumes.firstOrNull { it.id == continuePoint.volumeId }
            val totalPages = volumeForContinue?.chapters?.size?.takeIf { it > 0 }
            val volDone = volumeForContinue?.chapters?.all { it.status == ReadState.Completed } == true
            val rawPage =
                continuePoint.pagesRead
                    ?: runCatching { repo.getProgress(continuePoint.chapterId) }.getOrNull()?.page
                    ?: 0
            val safePage = if (totalPages != null) rawPage.coerceIn(0, max(0, totalPages - 1)) else rawPage
            val shouldSkip = volDone || (totalPages != null && rawPage >= totalPages)
            if (!shouldSkip) {
                progressMap[continuePoint.volumeId!!] =
                    VolumeProgressUi(
                        page = safePage,
                        totalPages = totalPages ?: volumeForContinue?.chapters?.size?.takeIf { it > 0 },
                        timeLeft = null,
                    )
            }
        }
        return progressMap
    }

    private fun computeContinueTarget(
        detail: SeriesDetail,
        progressMap: Map<Int, VolumeProgressUi>,
    ): Triple<Int, Int, Int>? {
        // Pick first volume with progress, otherwise first volume
        val volumes = allVolumes(detail)
        val targetVolume =
            volumes.firstOrNull { progressMap[it.id]?.page != null }
                ?: volumes.firstOrNull()
        val volumeId = targetVolume?.id ?: return null
        val page = progressMap[volumeId]?.page ?: 0
        val chapterId = targetVolume.bookId ?: return Triple(volumeId, page, 0)
        return Triple(volumeId, page, chapterId)
    }

    private fun updateDownloadState(
        detail: SeriesDetail?,
        downloads: Map<Int, Set<Int>>,
    ) {
        _state.update {
            it.copy(
                downloadedChapters = downloads,
                downloadedVolumeIds = computeDownloadedVolumes(detail, downloads),
            )
        }
    }

    private fun computeDownloadedVolumes(
        detail: SeriesDetail?,
        downloads: Map<Int, Set<Int>>,
    ): Set<Int> {
        val seriesFormat = detail?.series?.format
        val isPdf = seriesFormat == Format.Pdf
        val volumes = detail?.let { allVolumes(it) }.orEmpty()
        return volumes
            .mapNotNull { volume ->
                val bookId = volume.bookId ?: return@mapNotNull null
                val isDownloaded =
                    if (isPdf) {
                        downloadManager.pdfFileFor(bookId).exists()
                    } else {
                        val total = volume.chapters.size
                        val pages = downloads[bookId] ?: emptySet()
                        total > 0 && pages.size >= total
                    }
                volume.takeIf { isDownloaded }?.id
            }.toSet()
    }

    private fun allVolumes(detail: SeriesDetail): List<Volume> = detail.volumes + detail.specials

    private fun ensureWantAndCollections() {
        val cfg = latestConfig ?: return
        viewModelScope.launch {
            if (!_state.value.wantLoaded) {
                runCatching {
                    KavitaApiFactory
                        .createAuthenticated(cfg.serverUrl, cfg.apiKey)
                        .getWantToRead(filter = emptyFilter(), pageNumber = 1, pageSize = 200)
                }.onSuccess { resp ->
                    if (resp.isSuccessful) {
                        val isWant = resp.body().orEmpty().any { it.id == seriesId }
                        _state.update { it.copy(wantToRead = isWant, wantLoaded = true) }
                    }
                }
            }
            if (!_state.value.collectionsLoaded) {
                refreshCollectionsForSeries()
            }
        }
    }

    fun toggleWant() {
        val cfg = latestConfig ?: return
        if (_state.value.isUpdatingWant) return
        viewModelScope.launch {
            _state.update { it.copy(isUpdatingWant = true) }
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            val add = !_state.value.wantToRead
            val result =
                runCatching {
                    toggleWantToRead(add, seriesId, cfg)
                }
            result.onSuccess {
                _state.update { it.copy(wantToRead = add) }
            }
            _state.update { it.copy(isUpdatingWant = false) }
        }
    }

    fun loadCollections() {
        if (_state.value.isLoadingCollections) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingCollections = true, collectionError = null) }
            val result = runCatching { collectionsRepository.getCollections() }
            result
                .onSuccess { list ->
                    _state.update { it.copy(collections = list) }
                }.onFailure { e ->
                    _state.update { it.copy(collectionError = e.message ?: "Failed to load collections.") }
                }
            _state.update { it.copy(isLoadingCollections = false) }
        }
    }

    fun toggleCollection(
        collection: Collection,
        add: Boolean,
    ) {
        val cfg = latestConfig ?: return
        viewModelScope.launch {
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            val result =
                runCatching {
                    if (add) {
                        api.addSeriesToCollection(
                            CollectionTagBulkAddDto(
                                collectionTagId = collection.id,
                                collectionTagTitle = collection.name,
                                seriesIds = listOf(seriesId),
                            ),
                        )
                    } else {
                        api.updateSeriesForCollection(
                            UpdateSeriesForTagDto(
                                tag =
                                    AppUserCollectionDto(
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
            val api = KavitaApiFactory.createAuthenticated(cfg.serverUrl, cfg.apiKey)
            val response =
                runCatching {
                    api.addSeriesToCollection(
                        CollectionTagBulkAddDto(
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
                    _state.update { it.copy(collectionsWithSeries = ids, collectionsLoaded = true) }
                }
            }.onFailure { e ->
                if (e !is UnknownHostException) {
                    _events.tryEmit(e.message ?: "Failed to load collections.")
                }
                _state.update { it.copy(collectionError = e.message ?: "Failed to load collections.") }
            }
        }
    }

    fun loadRelated() {
        val cfg = latestConfig ?: return
        if (_state.value.isLoadingRelated) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingRelated = true, relatedError = null) }
            loadRelated(
                seriesId = seriesId,
                config = cfg,
                onLoading = {},
                onError = { err -> _state.update { it.copy(relatedError = err) } },
                onResult = { groups -> _state.update { it.copy(relatedGroups = groups) } },
            )
            _state.update { it.copy(isLoadingRelated = false) }
        }
    }

    fun toggleSeriesRead() {
        val repo = readerRepository ?: return
        val current = _state.value.detail ?: return
        val seriesIdValue = current.series.id ?: return
        viewModelScope.launch {
            val targetCompleted = current.readState != ReadState.Completed
            runCatching {
                if (targetCompleted) repo.markSeriesRead(seriesIdValue) else repo.markSeriesUnread(seriesIdValue)
            }.onSuccess { loadDetail() }
        }
    }

    fun markVolumes(
        ids: List<Int>,
        state: ReadState,
    ) {
        val repo = readerRepository ?: return
        viewModelScope.launch {
            runCatching {
                if (state == ReadState.Completed) repo.markVolumeRead(seriesId, ids) else repo.markVolumeUnread(seriesId, ids)
            }.onSuccess { loadDetail() }
        }
    }

    fun setProgress(progress: ReaderProgress) {
        val repo = readerRepository ?: return
        viewModelScope.launch {
            runCatching { repo.setProgress(progress, totalPages = null) }
                .onSuccess { loadDetail() }
        }
    }

    fun onVolumeSwipe(
        volume: Volume,
        direction: SwipeDirection,
    ) {
        if (direction == SwipeDirection.Left) {
            downloadVolume(volume)
            return
        }
        val current = _state.value.detail ?: return
        if (direction != SwipeDirection.Right) return
        val volumesList = current.volumes
        val currentIndex = volumesList.indexOfFirst { it.id == volume.id }
        if (currentIndex == -1) return
        val latestVolume = volumesList[currentIndex]
        val isCompleted = latestVolume.chapters.all { it.status == ReadState.Completed }
        val previousUnreadIds =
            if (!isCompleted && currentIndex > 0) {
                volumesList
                    .take(currentIndex)
                    .filter { prev -> prev.chapters.any { it.status != ReadState.Completed } }
                    .map { it.id }
            } else {
                emptyList()
            }
        val nextCompletedIds =
            if (isCompleted && currentIndex < volumesList.lastIndex) {
                volumesList
                    .drop(currentIndex + 1)
                    .filter { next -> next.chapters.any { it.status == ReadState.Completed } }
                    .map { it.id }
            } else {
                emptyList()
            }
        when {
            !isCompleted && previousUnreadIds.isNotEmpty() -> {
                _state.update { it.copy(pendingVolumeMark = volume, pendingPreviousVolumes = previousUnreadIds) }
            }

            isCompleted && nextCompletedIds.isNotEmpty() -> {
                _state.update { it.copy(pendingUnreadVolume = volume, pendingNextReadVolumes = nextCompletedIds) }
            }

            else -> {
                markVolumes(listOf(volume.id), if (isCompleted) ReadState.Unread else ReadState.Completed)
            }
        }
    }

    fun onChapterSwipe(
        volume: Volume,
        chapterIndex: Int,
        direction: SwipeDirection,
    ) {
        if (direction == SwipeDirection.Left) {
            downloadChapter(volume, chapterIndex)
            return
        }
        val current = _state.value.detail ?: return
        val libraryId = current.series.libraryId ?: return
        val bookId = volume.bookId ?: return
        if (direction != SwipeDirection.Right) return

        val chapterStatus = volume.chapters.getOrNull(chapterIndex)?.status
        val currentPage = _state.value.volumeProgress[volume.id]?.page
        val isMarkingRead = chapterStatus != ReadState.Completed || (currentPage != null && currentPage >= chapterIndex)
        val pageNum = if (isMarkingRead) (chapterIndex + 1) else max(0, chapterIndex - 1)
        val volumesList = current.volumes
        val currentIdx = volumesList.indexOfFirst { it.id == volume.id }
        val previousUnreadIds =
            if (isMarkingRead && currentIdx > 0) {
                volumesList
                    .take(currentIdx)
                    .filter { prev -> prev.chapters.any { it.status != ReadState.Completed } }
                    .map { it.id }
            } else {
                emptyList()
            }
        val nextCompletedIds =
            if (!isMarkingRead && currentIdx < volumesList.lastIndex) {
                volumesList
                    .drop(currentIdx + 1)
                    .filter { next -> next.chapters.any { it.status == ReadState.Completed } }
                    .map { it.id }
            } else {
                emptyList()
            }
        when {
            isMarkingRead && previousUnreadIds.isNotEmpty() -> {
                _state.update {
                    it.copy(
                        pendingChapterMark = volume,
                        pendingChapterIndex = chapterIndex,
                        pendingChapterPage = pageNum,
                        pendingChapterPrevVolumes = previousUnreadIds,
                    )
                }
            }

            !isMarkingRead && nextCompletedIds.isNotEmpty() -> {
                _state.update {
                    it.copy(
                        pendingChapterUnreadVolume = volume,
                        pendingChapterUnreadPage = pageNum,
                        pendingChapterNextVolumes = nextCompletedIds,
                    )
                }
            }

            else -> {
                setProgress(
                    ReaderProgress(
                        chapterId = bookId,
                        page = pageNum,
                        seriesId = current.series.id,
                        volumeId = volume.id,
                        libraryId = libraryId,
                        bookScrollId = null,
                        lastModifiedUtcMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun confirmPendingVolume(includePrevious: Boolean) {
        val pending = _state.value.pendingVolumeMark ?: return
        val previous = _state.value.pendingPreviousVolumes
        val ids = if (includePrevious) (listOf(pending.id) + previous).distinct() else listOf(pending.id)
        clearPendingDialogs()
        markVolumes(ids, ReadState.Completed)
    }

    fun confirmPendingUnreadVolume(includeNext: Boolean) {
        val pending = _state.value.pendingUnreadVolume ?: return
        val next = _state.value.pendingNextReadVolumes
        val ids = if (includeNext) (listOf(pending.id) + next).distinct() else listOf(pending.id)
        clearPendingDialogs()
        markVolumes(ids, ReadState.Unread)
    }

    fun confirmPendingChapter(markPrevious: Boolean) {
        val pendingVol = _state.value.pendingChapterMark ?: return
        val page = _state.value.pendingChapterPage ?: return
        val sId =
            _state.value.detail
                ?.series
                ?.id ?: return
        val libId =
            _state.value.detail
                ?.series
                ?.libraryId ?: return
        val bookId = pendingVol.bookId ?: return
        val prevVolumes = _state.value.pendingChapterPrevVolumes
        clearPendingDialogs()
        viewModelScope.launch {
            if (markPrevious && prevVolumes.isNotEmpty()) {
                markVolumes((prevVolumes + pendingVol.id).distinct(), ReadState.Completed)
            }
            setProgress(
                ReaderProgress(
                    chapterId = bookId,
                    page = page,
                    seriesId = sId,
                    volumeId = pendingVol.id,
                    libraryId = libId,
                    bookScrollId = null,
                    lastModifiedUtcMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun confirmPendingChapterUnread(includeNext: Boolean) {
        val pendingVol = _state.value.pendingChapterUnreadVolume ?: return
        val page = _state.value.pendingChapterUnreadPage ?: return
        val sId =
            _state.value.detail
                ?.series
                ?.id ?: return
        val libId =
            _state.value.detail
                ?.series
                ?.libraryId ?: return
        val bookId = pendingVol.bookId ?: return
        val nextVolumes = _state.value.pendingChapterNextVolumes
        clearPendingDialogs()
        viewModelScope.launch {
            if (includeNext && nextVolumes.isNotEmpty()) {
                markVolumes((nextVolumes + pendingVol.id).distinct(), ReadState.Unread)
            } else {
                markVolumes(listOf(pendingVol.id), ReadState.Unread)
            }
            setProgress(
                ReaderProgress(
                    chapterId = bookId,
                    page = page,
                    seriesId = sId,
                    volumeId = pendingVol.id,
                    libraryId = libId,
                    bookScrollId = null,
                    lastModifiedUtcMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun clearPendingDialogs() {
        _state.update {
            it.copy(
                pendingVolumeMark = null,
                pendingPreviousVolumes = emptyList(),
                pendingUnreadVolume = null,
                pendingNextReadVolumes = emptyList(),
                pendingChapterMark = null,
                pendingChapterIndex = null,
                pendingChapterPage = null,
                pendingChapterPrevVolumes = emptyList(),
                pendingChapterUnreadVolume = null,
                pendingChapterNextVolumes = emptyList(),
                pendingChapterUnreadPage = null,
            )
        }
    }

    fun resetProgress() {
        val repo = readerRepository ?: return
        val sId =
            _state.value.detail
                ?.series
                ?.id ?: return
        viewModelScope.launch {
            runCatching { repo.markSeriesUnread(sId) }
                .onSuccess { loadDetail() }
        }
    }

    fun downloadAllVolumes() {
        val detail = _state.value.detail ?: return
        val seriesIdValue = detail.series.id ?: seriesId
        val isPdf = detail.series.format == Format.Pdf
        viewModelScope.launch {
            detail.volumes.forEach { vol ->
                val bookId = vol.bookId ?: return@forEach
                runCatching {
                    if (isPdf) {
                        downloadRepository.enqueuePdf(seriesIdValue, vol.id, bookId, vol.name)
                    } else {
                        val lastPage = (vol.chapters.size - 1).coerceAtLeast(0)
                        downloadRepository.enqueuePages(seriesIdValue, vol.id, bookId, 0, lastPage)
                    }
                }.onFailure { e -> _events.tryEmit(e.message ?: "Download failed") }
            }
        }
    }

    fun downloadMissingVolumes() {
        val detail = _state.value.detail ?: return
        val seriesIdValue = detail.series.id ?: seriesId
        val isPdf = detail.series.format == Format.Pdf
        val alreadyDownloaded = _state.value.downloadedVolumeIds
        viewModelScope.launch {
            detail.volumes.forEach { vol ->
                val bookId = vol.bookId ?: return@forEach
                runCatching {
                    if (isPdf) {
                        if (!alreadyDownloaded.contains(vol.id)) {
                            downloadRepository.enqueuePdf(seriesIdValue, vol.id, bookId, vol.name)
                        }
                    } else {
                        val lastPage = (vol.chapters.size - 1).coerceAtLeast(0)
                        downloadRepository.enqueuePages(seriesIdValue, vol.id, bookId, 0, lastPage)
                    }
                }.onFailure { e -> _events.tryEmit(e.message ?: "Download failed") }
            }
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { downloadRepository.clearAllDownloads() } }
                .onSuccess { _events.tryEmit("Downloads cleared") }
                .onFailure { e -> _events.tryEmit(e.message ?: "Failed to clear downloads") }
        }
    }

    private fun downloadVolume(volume: Volume) {
        val detail = _state.value.detail ?: return
        val seriesIdValue = detail.series.id ?: seriesId
        val bookId =
            volume.bookId
                ?: run {
                    _events.tryEmit("Chapters are not available yet.")
                    return
                }
        val isPdf = detail.series.format == Format.Pdf
        viewModelScope.launch {
            runCatching {
                if (isPdf) {
                    downloadRepository.enqueuePdf(seriesIdValue, volume.id, bookId, volume.name)
                } else {
                    val lastPage = (volume.chapters.size - 1).coerceAtLeast(0)
                    downloadRepository.enqueuePages(seriesIdValue, volume.id, bookId, 0, lastPage)
                }
            }.onFailure { e -> _events.tryEmit(e.message ?: "Download failed") }
        }
    }

    private fun downloadChapter(
        volume: Volume,
        chapterIndex: Int,
    ) {
        val detail = _state.value.detail ?: return
        val seriesIdValue = detail.series.id ?: seriesId
        val bookId =
            volume.bookId
                ?: run {
                    _events.tryEmit("Chapters are not available yet.")
                    return
                }
        val isPdf = detail.series.format == Format.Pdf
        viewModelScope.launch {
            runCatching {
                if (isPdf) {
                    downloadRepository.enqueuePdf(seriesIdValue, volume.id, bookId, volume.name)
                } else {
                    downloadRepository.enqueuePages(seriesIdValue, volume.id, bookId, chapterIndex, chapterIndex)
                }
            }.onFailure { e -> _events.tryEmit(e.message ?: "Download failed") }
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun provideFactory(
            seriesId: Int,
            seriesRepository: SeriesRepository,
            collectionsRepository: CollectionsRepository,
            readerRepository: ReaderRepository?,
            appPreferences: AppPreferences,
            downloadRepository: DownloadRepository,
            downloadManager: DownloadManager,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SeriesDetailViewModel(
                        seriesId = seriesId,
                        seriesRepository = seriesRepository,
                        collectionsRepository = collectionsRepository,
                        readerRepository = readerRepository,
                        appPreferences = appPreferences,
                        downloadRepository = downloadRepository,
                        downloadManager = downloadManager,
                    ) as T
            }
    }
}
