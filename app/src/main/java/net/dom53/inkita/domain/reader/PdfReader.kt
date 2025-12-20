package net.dom53.inkita.domain.reader

import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderChapterNav
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.ReaderTimeLeft
import net.dom53.inkita.domain.repository.ReaderRepository

class PdfReader(
    private val readerRepository: ReaderRepository,
) : BaseReader {
    override val format: Format = Format.Pdf

    override suspend fun loadInitial(
        chapterId: Int,
        initialPage: Int,
    ): ReaderLoadResult {
        val file = readerRepository.getPdfFile(chapterId)
        return ReaderLoadResult(pdfPath = file?.absolutePath)
    }

    override suspend fun loadPage(
        chapterId: Int,
        pageIndex: Int,
    ): ReaderLoadResult = ReaderLoadResult()

    override suspend fun getBookInfo(chapterId: Int): ReaderBookInfo? = readerRepository.getBookInfo(chapterId)

    override suspend fun getProgress(chapterId: Int): ReaderProgress? = readerRepository.getProgress(chapterId)

    override suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        readerRepository.setProgress(progress, totalPages)
    }

    override suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft? = readerRepository.getTimeLeft(seriesId, chapterId)

    override suspend fun isPageDownloaded(
        chapterId: Int,
        pageIndex: Int,
    ): Boolean = true

    override suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? = readerRepository.getNextChapter(seriesId, volumeId, currentChapterId)

    override suspend fun getPreviousChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? = readerRepository.getPreviousChapter(seriesId, volumeId, currentChapterId)
}
