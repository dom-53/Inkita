package net.dom53.inkita.ui.reader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
