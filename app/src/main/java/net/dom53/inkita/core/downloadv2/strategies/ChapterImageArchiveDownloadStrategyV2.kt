package net.dom53.inkita.core.downloadv2.strategies

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.downloadv2.DownloadPaths
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.DownloadStorageManager
import net.dom53.inkita.core.downloadv2.DownloadStrategyV2
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkLoggingInterceptor
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.network.forKavita
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import okhttp3.OkHttpClient
import okhttp3.Request

class ChapterImageArchiveDownloadStrategyV2(
    private val appContext: Context,
    private val downloadDao: DownloadV2Dao,
    private val appPreferences: AppPreferences,
    override val key: String,
) : DownloadStrategyV2 {
    private val httpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(NetworkLoggingInterceptor)
            .build()
    private val storageManager = DownloadStorageManager(appContext, appPreferences)

    override suspend fun enqueue(request: DownloadRequestV2): Long {
        if (request.type != DownloadJobV2Entity.TYPE_CHAPTER) return -1L
        val chapterId = request.chapterId ?: return -1L
        val existing =
            downloadDao.getDownloadedFileForChapter(
                chapterId = chapterId,
                type = DownloadedItemV2Entity.TYPE_FILE,
            )
        if (existing != null && storageManager.exists(existing.localPath)) {
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("ArchiveDownloadV2", "Skip enqueue; already downloaded chapter=$chapterId")
            }
            return existing.jobId
        }
        val now = System.currentTimeMillis()
        val job =
            DownloadJobV2Entity(
                type = DownloadJobV2Entity.TYPE_CHAPTER,
                format = request.format,
                strategy = key,
                seriesId = request.seriesId,
                volumeId = request.volumeId,
                chapterId = chapterId,
                status = DownloadJobV2Entity.STATUS_PENDING,
                totalItems = 1,
                completedItems = 0,
                priority = request.priority,
                createdAt = now,
                updatedAt = now,
                error = null,
            )
        val item =
            DownloadedItemV2Entity(
                jobId = 0,
                type = DownloadedItemV2Entity.TYPE_FILE,
                seriesId = request.seriesId,
                volumeId = request.volumeId,
                chapterId = chapterId,
                page = null,
                url = null,
                localPath = null,
                bytes = null,
                checksum = null,
                status = DownloadedItemV2Entity.STATUS_PENDING,
                createdAt = now,
                updatedAt = now,
                format = Format.Archive,
                error = null,
            )
        return downloadDao.insertJobWithItems(job, listOf(item))
    }

    override suspend fun run(jobId: Long) {
        val job = downloadDao.getJob(jobId) ?: return
        if (job.status != DownloadJobV2Entity.STATUS_RUNNING) return
        val chapterId = job.chapterId ?: return
        val offlineMode = appPreferences.offlineModeFlow.first()
        val networkStatus = NetworkMonitor.getInstance(appContext, appPreferences).status.value
        if (offlineMode || !networkStatus.isOnlineAllowed) {
            failJob(job, "Offline mode")
            return
        }
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) {
            failJob(job, "Not configured")
            return
        }
        var seriesId = job.seriesId
        var volumeId = job.volumeId
        if (seriesId == null || volumeId == null) {
            val api = KavitaApiFactory.createAuthenticated(config)
            val info = api.getBookInfo(chapterId).body()
            if (seriesId == null) seriesId = info?.seriesId
            if (volumeId == null) volumeId = info?.volumeId
        }
        val relativePath =
            seriesId?.let { DownloadPaths.cbzRelativePath(it, volumeId, chapterId) }
                ?: DownloadPaths.archiveDownloadRelativePath(null, null, chapterId, "chapter_$chapterId.cbz")

        try {
            val url =
                buildString {
                    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
                    append(base)
                    append("/api/Download/chapter?chapterId=")
                    append(chapterId)
                }
            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("x-api-key", config.apiKey)
                    .get()
                    .build()
            httpClient.forKavita(config).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    failJob(job, "HTTP ${response.code}")
                    return
                }
                val body =
                    response.body ?: run {
                        failJob(job, "Empty body")
                        return
                    }
                if (downloadDao.getJob(jobId)?.status != DownloadJobV2Entity.STATUS_RUNNING) return
                val completed = body.byteStream().use { input -> storageManager.saveDownloadFile(downloadDao, job, relativePath, input) }
                if (!completed) return
            }
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d(
                    "ArchiveDownloadV2",
                    "Completed job=$jobId chapter=$chapterId",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failJob(job, e.message ?: "Download failed")
        }
    }

    private suspend fun failJob(
        job: DownloadJobV2Entity,
        message: String,
    ) {
        downloadDao.updateRunningJob(
            job.copy(
                status = DownloadJobV2Entity.STATUS_FAILED,
                updatedAt = System.currentTimeMillis(),
                error = message,
            ),
        )
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.e("ArchiveDownloadV2", "Job failed id=${job.id}: $message")
        }
    }

    companion object {
        const val FORMAT_IMAGE = "image"
        const val FORMAT_ARCHIVE = "archive"
    }
}
