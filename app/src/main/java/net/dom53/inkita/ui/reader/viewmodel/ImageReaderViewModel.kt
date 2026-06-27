package net.dom53.inkita.ui.reader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
        val adjacentUrl =
            when (index) {
                current.pageIndex - 1 -> current.previousImageUrl
                current.pageIndex + 1 -> current.nextImageUrl
                else -> null
            }
        if (adjacentUrl != null) {
            _state.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    imageUrl = adjacentUrl,
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
                    state.copy(
                        previousImageUrl = previousUrl,
                        nextImageUrl = nextUrl,
                    )
                } else {
                    state
                }
            }
        }
    }

    companion object {
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
