package net.dom53.inkita.domain.repository

import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderChapterNav
import net.dom53.inkita.domain.model.ReaderPageResult
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.ReaderTimeLeft

interface ReaderRepository {
    suspend fun getPageResult(
        chapterId: Int,
        page: Int,
    ): ReaderPageResult

    suspend fun isPageDownloaded(
        chapterId: Int,
        page: Int,
    ): Boolean

    suspend fun getPage(
        chapterId: Int,
        page: Int,
    ): String = getPageResult(chapterId, page).html

    suspend fun getProgress(chapterId: Int): ReaderProgress?

    suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int? = null,
    )

    suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft?

    suspend fun getPdfFile(chapterId: Int): java.io.File?

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

    suspend fun getContinuePoint(seriesId: Int): ReaderChapterNav?

    suspend fun getBookInfo(chapterId: Int): ReaderBookInfo?

    suspend fun markSeriesRead(seriesId: Int)

    suspend fun markSeriesUnread(seriesId: Int)

    suspend fun markVolumeRead(
        seriesId: Int,
        volumeIds: List<Int>,
    )

    suspend fun markVolumeUnread(
        seriesId: Int,
        volumeIds: List<Int>,
    )

    suspend fun syncLocalProgress()

    suspend fun getLatestLocalProgress(seriesId: Int): ReaderProgress?

    suspend fun getLatestLocalProgressForChapters(chapterIds: Set<Int>): ReaderProgress?
}
