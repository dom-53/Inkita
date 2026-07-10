package net.dom53.inkita.ui.reader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val loadingPageIndexes = mutableSetOf<Int>()
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

    override fun onPageLoaded(pageIndex: Int) {
        preloadAdjacentPages(pageIndex)
    }

    override fun loadPage(index: Int) {
        val current = _state.value
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
            onPageLoaded(index)
            return
        }
        super.loadPage(index)
    }

    fun onWebtoonPageVisible(
        pageIndex: Int,
        prefetchPages: Int,
    ) {
        val pageCount = _state.value.pageCount
        if (pageIndex < 0 || (pageCount > 0 && pageIndex >= pageCount)) return

        val pageChanged = _state.value.pageIndex != pageIndex
        _state.update { state ->
            state.copy(
                pageIndex = pageIndex,
                imageUrl = state.imageUrls[pageIndex],
            )
        }
        if (pageChanged) {
            webtoonProgressJob?.cancel()
            webtoonProgressJob =
                viewModelScope.launch {
                    delay(WEBTOON_PROGRESS_DELAY_MS)
                    if (_state.value.pageIndex == pageIndex) {
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
            ensureImagePageLoaded(index)
        }
    }

    private fun ensureImagePageLoaded(pageIndex: Int) {
        if (_state.value.imageUrls.containsKey(pageIndex) || !loadingPageIndexes.add(pageIndex)) return
        viewModelScope.launch {
            try {
                val result = reader.loadPage(chapterId, pageIndex)
                val url = result.imageUrl ?: return@launch
                _state.update { state ->
                    state.copy(
                        imageUrls = state.imageUrls + (pageIndex to url),
                        imageUrl = if (state.pageIndex == pageIndex) url else state.imageUrl,
                    )
                }
            } finally {
                loadingPageIndexes.remove(pageIndex)
            }
        }
    }

    private fun preloadAdjacentPages(pageIndex: Int) {
        viewModelScope.launch {
            val pageCount = _state.value.pageCount
            val previousIndex = pageIndex - 1
            val nextIndex = pageIndex + 1
            val previousUrl =
                if (previousIndex >= 0) {
                    runCatching { reader.loadPage(chapterId, previousIndex).imageUrl }.getOrNull()
                } else {
                    null
                }
            val nextUrl =
                if (pageCount <= 0 || nextIndex < pageCount) {
                    runCatching { reader.loadPage(chapterId, nextIndex).imageUrl }.getOrNull()
                } else {
                    null
                }
            _state.update { state ->
                if (state.pageIndex == pageIndex) {
                    val adjacentUrls =
                        buildMap {
                            previousUrl?.let { put(previousIndex, it) }
                            nextUrl?.let { put(nextIndex, it) }
                        }
                    state.copy(
                        previousImageUrl = previousUrl,
                        nextImageUrl = nextUrl,
                        imageUrls = state.imageUrls + adjacentUrls,
                    )
                } else {
                    state
                }
            }
        }
    }

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
