package net.dom53.inkita.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.dom53.inkita.domain.reader.EpubReader
import net.dom53.inkita.domain.repository.ReaderRepository

class EpubReaderViewModel(
    chapterId: Int,
    initialPage: Int,
    readerRepository: ReaderRepository,
    seriesId: Int?,
    volumeId: Int?,
    anonymous: Boolean = false,
) : BaseReaderViewModel(
        chapterId = chapterId,
        initialPage = initialPage,
        reader = EpubReader(readerRepository),
        seriesId = seriesId,
        volumeId = volumeId,
        anonymous = anonymous,
    ) {
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
                    return EpubReaderViewModel(
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
