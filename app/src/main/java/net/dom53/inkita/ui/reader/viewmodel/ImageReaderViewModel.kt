package net.dom53.inkita.ui.reader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.reader.ImageReader
import net.dom53.inkita.domain.repository.ReaderRepository

class ImageReaderViewModel(
    chapterId: Int,
    initialPage: Int,
    readerRepository: ReaderRepository,
    seriesId: Int?,
    volumeId: Int?,
    anonymous: Boolean = false,
) : BaseReaderViewModel(
        chapterId = chapterId,
        initialPage = initialPage,
        reader = ImageReader(readerRepository),
        seriesId = seriesId,
        volumeId = volumeId,
        anonymous = anonymous,
    ) {
    private val loadingPages = mutableSetOf<Pair<Int, Int>>()
    private var webtoonProgressJob: Job? = null

    override fun updateProgress(
        pageIndex: Int,
        bookScrollId: String?,
        totalPagesOverride: Int?,
    ) {
        val pageCount = totalPagesOverride ?: _state.value.pageCount
        val effectivePage =
            if (pageCount > 0 && pageIndex >= pageCount - 1) {
                pageCount
            } else {
                pageIndex
            }
        super.updateProgress(effectivePage, bookScrollId, totalPagesOverride)
    }

    override suspend fun saveCurrentProgress() {
        webtoonProgressJob?.cancel()
        val state = _state.value
        val effectivePage =
            if (state.pageCount > 0 && state.pageIndex >= state.pageCount - 1) {
                state.pageCount
            } else {
                state.pageIndex
            }
        persistCurrentProgressLocally(effectivePage)
    }

    override fun onPageLoaded(pageIndex: Int) {
        syncPrimaryWebtoonChapter()
        preloadAdjacentPages(
            targetChapterId = chapterId,
            pageIndex = pageIndex,
            pageCount = _state.value.pageCount,
        )
    }

    override fun loadPage(index: Int) {
        val current = _state.value
        val activeChapterId = current.activeChapterId ?: chapterId
        val cachedUrl =
            current.imageUrls[index]
                ?: when (index) {
                    current.pageIndex - 1 -> current.previousImageUrl
                    current.pageIndex + 1 -> current.nextImageUrl
                    else -> null
                }
        if (cachedUrl != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    imageUrl = cachedUrl,
                    imageUrls = it.imageUrls + (index to cachedUrl),
                    pageIndex = index,
                    previousImageUrl = null,
                    nextImageUrl = null,
                )
            }
            updateProgress(index)
            val pageCount = current.webtoonChapters.firstOrNull { it.chapterId == activeChapterId }?.pageCount ?: current.pageCount
            preloadAdjacentPages(activeChapterId, index, pageCount)
            return
        }
        if (activeChapterId != chapterId) {
            loadWebtoonChapterPage(activeChapterId, index)
            return
        }
        super.loadPage(index)
    }

    fun onWebtoonPageVisible(
        chapterId: Int,
        pageIndex: Int,
        prefetchPages: Int,
    ) {
        syncPrimaryWebtoonChapter()
        val chapter = _state.value.webtoonChapters.firstOrNull { it.chapterId == chapterId } ?: return
        val pageCount = chapter.pageCount
        if (pageIndex < 0 || (pageCount > 0 && pageIndex >= pageCount)) return

        val currentState = _state.value
        val pageChanged = currentState.activeChapterId != chapterId || currentState.pageIndex != pageIndex
        if (pageChanged &&
            net.dom53.inkita.core.logging.LoggingManager
                .isDebugEnabled()
        ) {
            net.dom53.inkita.core.logging.LoggingManager.d(
                "InkitaProgress",
                "Webtoon visible chapter=$chapterId page=$pageIndex",
            )
        }
        _state.update { state ->
            state.copy(
                activeChapterId = chapterId,
                pageIndex = pageIndex,
                pageCount = chapter.pageCount,
                imageUrl = chapter.imageUrls[pageIndex],
                imageUrls = chapter.imageUrls,
                bookInfo = chapter.toBookInfo(),
            )
        }
        if (pageChanged) {
            webtoonProgressJob?.cancel()
            webtoonProgressJob =
                viewModelScope.launch {
                    delay(WEBTOON_PROGRESS_DELAY_MS)
                    if (_state.value.activeChapterId == chapterId && _state.value.pageIndex == pageIndex) {
                        updateProgress(pageIndex)
                    }
                }
        }

        val lastIndex =
            if (pageCount > 0) {
                (pageCount - 1).coerceAtLeast(0)
            } else {
                pageIndex + prefetchPages.coerceAtLeast(1)
            }
        val firstPrefetchIndex = (pageIndex - WEBTOON_BACKWARD_PREFETCH_PAGES).coerceAtLeast(0)
        val lastPrefetchIndex = (pageIndex + prefetchPages.coerceAtLeast(1)).coerceAtMost(lastIndex)
        for (index in firstPrefetchIndex..lastPrefetchIndex) {
            ensureImagePageLoaded(chapterId, index)
        }
    }

    fun onWebtoonChapterBoundaryVisible(
        chapterId: Int,
        prefetchPages: Int,
    ) {
        syncPrimaryWebtoonChapter()
        val state = _state.value
        val chapterIndex = state.webtoonChapters.indexOfFirst { it.chapterId == chapterId }
        if (chapterIndex < 0 || chapterIndex < state.webtoonChapters.lastIndex) return
        val chapter = state.webtoonChapters[chapterIndex]
        if (chapter.isLoadingNextChapter || chapter.hasNextChapter != null) return

        _state.update { current ->
            current.copy(
                webtoonChapters =
                    current.webtoonChapters.map {
                        if (it.chapterId == chapterId) it.copy(isLoadingNextChapter = true) else it
                    },
            )
        }
        viewModelScope.launch {
            val seriesId = chapter.seriesId ?: this@ImageReaderViewModel.seriesId
            val volumeId = chapter.volumeId ?: this@ImageReaderViewModel.volumeId
            val nextNav =
                if (seriesId != null && volumeId != null) {
                    runCatching { reader.getNextChapter(seriesId, volumeId, chapterId) }.getOrNull()
                } else {
                    null
                }
            val nextChapterId = nextNav?.chapterId
            val nextInfo = nextChapterId?.let { runCatching { reader.getBookInfo(it) }.getOrNull() }
            val nextPageCount = nextInfo?.pages ?: 0
            if (nextChapterId == null || nextInfo == null || nextPageCount <= 0) {
                _state.update { current ->
                    current.copy(
                        webtoonChapters =
                            current.webtoonChapters.map {
                                if (it.chapterId == chapterId) {
                                    it.copy(isLoadingNextChapter = false, hasNextChapter = false)
                                } else {
                                    it
                                }
                            },
                    )
                }
                return@launch
            }

            val nextChapter = nextInfo.toWebtoonChapter(nextChapterId)
            _state.update { current ->
                val updated =
                    current.webtoonChapters.map {
                        if (it.chapterId == chapterId) {
                            it.copy(isLoadingNextChapter = false, hasNextChapter = true)
                        } else {
                            it
                        }
                    }
                current.copy(
                    webtoonChapters =
                        if (updated.any { it.chapterId == nextChapterId }) {
                            updated
                        } else {
                            updated + nextChapter
                        },
                )
            }
            val lastPrefetchIndex = (prefetchPages.coerceAtLeast(1) - 1).coerceAtMost(nextPageCount - 1)
            for (pageIndex in 0..lastPrefetchIndex) {
                ensureImagePageLoaded(nextChapterId, pageIndex)
            }
        }
    }

    private fun ensureImagePageLoaded(
        targetChapterId: Int,
        pageIndex: Int,
    ) {
        val chapter = _state.value.webtoonChapters.firstOrNull { it.chapterId == targetChapterId } ?: return
        val loadingKey = targetChapterId to pageIndex
        if (chapter.imageUrls.containsKey(pageIndex) || !loadingPages.add(loadingKey)) return
        viewModelScope.launch {
            try {
                val result = reader.loadPage(targetChapterId, pageIndex)
                val url = result.imageUrl ?: return@launch
                _state.update { state ->
                    val chapterImages =
                        state.webtoonChapters
                            .firstOrNull { it.chapterId == targetChapterId }
                            ?.imageUrls
                            .orEmpty() + (pageIndex to url)
                    state.copy(
                        webtoonChapters =
                            state.webtoonChapters.map {
                                if (it.chapterId == targetChapterId) it.copy(imageUrls = chapterImages) else it
                            },
                        imageUrls = if (state.activeChapterId == targetChapterId) chapterImages else state.imageUrls,
                        imageUrl =
                            if (state.activeChapterId == targetChapterId && state.pageIndex == pageIndex) {
                                url
                            } else {
                                state.imageUrl
                            },
                    )
                }
            } finally {
                loadingPages.remove(loadingKey)
            }
        }
    }

    private fun preloadAdjacentPages(
        targetChapterId: Int,
        pageIndex: Int,
        pageCount: Int,
    ) {
        viewModelScope.launch {
            val previousIndex = pageIndex - 1
            val nextIndex = pageIndex + 1
            val previousUrl =
                if (previousIndex >= 0) {
                    runCatching { reader.loadPage(targetChapterId, previousIndex).imageUrl }.getOrNull()
                } else {
                    null
                }
            val nextUrl =
                if (pageCount <= 0 || nextIndex < pageCount) {
                    runCatching { reader.loadPage(targetChapterId, nextIndex).imageUrl }.getOrNull()
                } else {
                    null
                }
            _state.update { state ->
                if (state.activeChapterId == targetChapterId && state.pageIndex == pageIndex) {
                    val adjacentUrls =
                        buildMap {
                            previousUrl?.let { put(previousIndex, it) }
                            nextUrl?.let { put(nextIndex, it) }
                        }
                    val chapterImages =
                        state.webtoonChapters
                            .firstOrNull { it.chapterId == targetChapterId }
                            ?.imageUrls
                            .orEmpty() + adjacentUrls
                    state.copy(
                        previousImageUrl = if (state.activeChapterId == targetChapterId) previousUrl else state.previousImageUrl,
                        nextImageUrl = if (state.activeChapterId == targetChapterId) nextUrl else state.nextImageUrl,
                        imageUrls = if (state.activeChapterId == targetChapterId) chapterImages else state.imageUrls,
                        webtoonChapters =
                            state.webtoonChapters.map {
                                if (it.chapterId == targetChapterId) it.copy(imageUrls = chapterImages) else it
                            },
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun syncPrimaryWebtoonChapter() {
        _state.update { state ->
            val info = state.bookInfo ?: return@update state
            val existing = state.webtoonChapters.firstOrNull { it.chapterId == chapterId }
            if (existing != null) return@update state
            val primary =
                info
                    .toWebtoonChapter(chapterId)
                    .copy(
                        pageCount =
                            (info.pages ?: 0).coerceAtLeast(
                                (state.imageUrls.keys.maxOrNull() ?: state.pageIndex) + 1,
                            ),
                        imageUrls = state.imageUrls,
                    )
            state.copy(
                activeChapterId = state.activeChapterId ?: chapterId,
                webtoonChapters = listOf(primary) + state.webtoonChapters,
            )
        }
    }

    private fun loadWebtoonChapterPage(
        targetChapterId: Int,
        pageIndex: Int,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { reader.loadPage(targetChapterId, pageIndex) }
                .onSuccess { result ->
                    val url = result.imageUrl
                    _state.update { state ->
                        val chapter = state.webtoonChapters.firstOrNull { it.chapterId == targetChapterId }
                        val images = if (url != null) chapter?.imageUrls.orEmpty() + (pageIndex to url) else chapter?.imageUrls.orEmpty()
                        state.copy(
                            isLoading = false,
                            pageIndex = pageIndex,
                            imageUrl = url ?: state.imageUrl,
                            imageUrls = images,
                            webtoonChapters =
                                state.webtoonChapters.map {
                                    if (it.chapterId == targetChapterId) it.copy(imageUrls = images) else it
                                },
                        )
                    }
                    updateProgress(pageIndex)
                    val pageCount =
                        _state.value.webtoonChapters
                            .firstOrNull { it.chapterId == targetChapterId }
                            ?.pageCount ?: 0
                    preloadAdjacentPages(targetChapterId, pageIndex, pageCount)
                }.onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Error loading page") }
                }
        }
    }

    private fun ReaderBookInfo.toWebtoonChapter(chapterId: Int): WebtoonChapterUiState =
        WebtoonChapterUiState(
            chapterId = chapterId,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            title = pageTitle?.takeIf { it.isNotBlank() },
            chapterNumber = chapterNumber,
            bookTitle = title,
            pageCount = pages ?: 0,
        )

    private fun WebtoonChapterUiState.toBookInfo(): ReaderBookInfo =
        ReaderBookInfo(
            pages = pageCount,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            title = bookTitle,
            pageTitle = title,
            chapterNumber = chapterNumber,
        )

    companion object {
        private const val WEBTOON_BACKWARD_PREFETCH_PAGES = 2
        private const val WEBTOON_PROGRESS_DELAY_MS = 250L

        fun provideFactory(
            chapterId: Int,
            initialPage: Int,
            readerRepository: ReaderRepository,
            seriesId: Int?,
            volumeId: Int?,
            anonymous: Boolean = false,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ImageReaderViewModel(
                        chapterId,
                        initialPage,
                        readerRepository,
                        seriesId,
                        volumeId,
                        anonymous,
                    ) as T
                }
            }
    }
}
