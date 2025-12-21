package net.dom53.inkita.core.downloadv2

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity

class DownloadManagerV2(
    private val appContext: Context,
    private val downloadDao: DownloadV2Dao,
    private val strategies: Map<String, DownloadStrategyV2>,
) {
    suspend fun enqueueSeries(seriesId: Int, format: String?): Long {
        val now = System.currentTimeMillis()
        val job =
            DownloadJobV2Entity(
                type = DownloadJobV2Entity.TYPE_SERIES,
                format = format,
                strategy = format,
                seriesId = seriesId,
                volumeId = null,
                chapterId = null,
                status = DownloadJobV2Entity.STATUS_PENDING,
                totalItems = null,
                completedItems = null,
                priority = 0,
                createdAt = now,
                updatedAt = now,
                error = null,
            )
        return downloadDao.insertJob(job)
    }

    suspend fun enqueueVolume(volumeId: Int, format: String?): Long {
        val now = System.currentTimeMillis()
        val job =
            DownloadJobV2Entity(
                type = DownloadJobV2Entity.TYPE_VOLUME,
                format = format,
                strategy = format,
                seriesId = null,
                volumeId = volumeId,
                chapterId = null,
                status = DownloadJobV2Entity.STATUS_PENDING,
                totalItems = null,
                completedItems = null,
                priority = 0,
                createdAt = now,
                updatedAt = now,
                error = null,
            )
        return downloadDao.insertJob(job)
    }

    suspend fun enqueueChapter(chapterId: Int, format: String?): Long {
        val now = System.currentTimeMillis()
        val job =
            DownloadJobV2Entity(
                type = DownloadJobV2Entity.TYPE_CHAPTER,
                format = format,
                strategy = format,
                seriesId = null,
                volumeId = null,
                chapterId = chapterId,
                status = DownloadJobV2Entity.STATUS_PENDING,
                totalItems = null,
                completedItems = null,
                priority = 0,
                createdAt = now,
                updatedAt = now,
                error = null,
            )
        return downloadDao.insertJob(job)
    }

    fun enqueueWorker() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            OneTimeWorkRequestBuilder<DownloadWorkerV2>()
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(appContext).enqueue(request)
    }
}
