package net.dom53.inkita.domain.repository

import kotlinx.coroutines.flow.Flow
import net.dom53.inkita.data.local.db.entity.DownloadTaskEntity
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity

interface DownloadRepository {
    fun observeTasks(): Flow<List<DownloadTaskEntity>>

    fun observeDownloadedPages(): Flow<List<DownloadedPageEntity>>

    fun observeValidDownloadedPages(): Flow<List<DownloadedPageEntity>>

    suspend fun enqueuePages(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
        pageStart: Int,
        pageEnd: Int = pageStart,
    )

    suspend fun cancelTask(taskId: Long)

    suspend fun pauseTask(taskId: Long)

    suspend fun resumeTask(taskId: Long)

    suspend fun retryTask(taskId: Long)

    suspend fun deleteDownloadedPage(
        chapterId: Int,
        page: Int,
    )

    suspend fun cleanupMissingDownloads()

    suspend fun deleteTask(id: Long)

    suspend fun clearCompletedTasks()

    suspend fun clearAllDownloads()

    suspend fun enqueuePdf(
        seriesId: Int,
        volumeId: Int?,
        bookId: Int,
        title: String? = null,
    )
}
