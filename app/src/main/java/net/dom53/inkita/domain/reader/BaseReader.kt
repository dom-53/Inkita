package net.dom53.inkita.domain.reader

import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderChapterNav
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.ReaderTimeLeft

interface BaseReader {
    val format: Format

    suspend fun loadInitial(
        chapterId: Int,
        initialPage: Int,
    ): ReaderLoadResult

    suspend fun loadPage(
        chapterId: Int,
        pageIndex: Int,
    ): ReaderLoadResult

    suspend fun getBookInfo(chapterId: Int): ReaderBookInfo?

    suspend fun getProgress(chapterId: Int): ReaderProgress?

    suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int? = null,
    )

    suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft?

    suspend fun isPageDownloaded(
        chapterId: Int,
        pageIndex: Int,
    ): Boolean

    suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav?

    suspend fun getPreviousChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav?
}
