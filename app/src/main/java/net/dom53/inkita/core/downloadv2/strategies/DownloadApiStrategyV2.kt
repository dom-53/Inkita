package net.dom53.inkita.core.downloadv2.strategies

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.DownloadStrategyV2
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkLoggingInterceptor
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File

class DownloadApiStrategyV2(
    private val appContext: Context,
    private val downloadDao: DownloadV2Dao,
    private val appPreferences: AppPreferences,
) : DownloadStrategyV2 {
    override val key: String = FORMAT_DOWNLOAD

    private val httpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(NetworkLoggingInterceptor)
            .build()

    override suspend fun enqueue(request: DownloadRequestV2): Long {
        val now = System.currentTimeMillis()
        val (seriesId, volumeId, chapterId, endpoint) = resolveEndpoint(request) ?: return -1L
        val existing =
            when (request.type) {
                DownloadJobV2Entity.TYPE_SERIES ->
                    seriesId?.let { downloadDao.getDownloadedFileForSeries(it) }
                DownloadJobV2Entity.TYPE_VOLUME ->
                    volumeId?.let { downloadDao.getDownloadedFileForVolume(it) }
                else ->
                    chapterId?.let { downloadDao.getDownloadedFileForChapter(it) }
            }
        if (existing != null && existing.localPath?.let { File(it).exists() } == true) {
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("DownloadApiV2", "Skip enqueue; already downloaded ${request.type}=$endpoint")
            }
            return existing.jobId
        }
        val job =
            DownloadJobV2Entity(
                type = request.type,
                format = request.format,
                strategy = key,
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId,
                status = DownloadJobV2Entity.STATUS_PENDING,
                totalItems = 1,
                completedItems = 0,
                priority = request.priority,
                createdAt = now,
                updatedAt = now,
                error = null,
            )
        val jobId = downloadDao.insertJob(job)
        val item =
            DownloadedItemV2Entity(
                jobId = jobId,
                type = DownloadedItemV2Entity.TYPE_FILE,
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId,
                page = null,
                url = endpoint,
                localPath = null,
                bytes = null,
                checksum = null,
                status = DownloadedItemV2Entity.STATUS_PENDING,
                createdAt = now,
                updatedAt = now,
                format = translateFormatFromRequest(request.format),
                error = null,
            )
        downloadDao.upsertItems(listOf(item))
        return jobId
    }

    override suspend fun run(jobId: Long) {
        val job = downloadDao.getJob(jobId) ?: return
        if (job.status == DownloadJobV2Entity.STATUS_COMPLETED) return
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
        val (seriesId, volumeId, chapterId, endpoint) =
            resolveEndpoint(
                DownloadRequestV2(
                    type = job.type,
                    format = job.format ?: key,
                    seriesId = job.seriesId,
                    volumeId = job.volumeId,
                    chapterId = job.chapterId,
                ),
            ) ?: run {
                failJob(job, "Invalid request")
                return
            }
        val fileName =
            when (job.type) {
                DownloadJobV2Entity.TYPE_SERIES -> "series_${seriesId ?: job.id}.zip"
                DownloadJobV2Entity.TYPE_VOLUME -> "volume_${volumeId ?: job.id}.zip"
                else -> "chapter_${chapterId ?: job.id}.zip"
            }
        val baseDir =
            appContext.getExternalFilesDir("Inkita/downloads/archives")
                ?: File(appContext.filesDir, "Inkita/downloads/archives").apply { mkdirs() }
        if (!baseDir.exists()) baseDir.mkdirs()
        val target = File(baseDir, fileName)
        val url =
            buildString {
                val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
                append(base)
                append(endpoint)
            }

        try {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("x-api-key", config.apiKey)
                    .get()
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    failJob(job, "HTTP ${response.code}")
                    return
                }
                val body =
                    response.body ?: run {
                        failJob(job, "Empty body")
                        return
                    }
                withContext(Dispatchers.IO) {
                    body.byteStream().use { input ->
                        target.sink().buffer().use { sink -> sink.writeAll(input.source()) }
                    }
                }
            }
            val bytes = target.length()
            val item =
                downloadDao
                    .getItemsForJob(jobId)
                    .firstOrNull()
                    ?.copy(
                        status = DownloadedItemV2Entity.STATUS_COMPLETED,
                        localPath = target.absolutePath,
                        bytes = bytes,
                        updatedAt = System.currentTimeMillis(),
                        error = null,
                    )
            if (item != null) {
                downloadDao.upsertItems(listOf(item))
            }
            downloadDao.updateJob(
                job.copy(
                    status = DownloadJobV2Entity.STATUS_COMPLETED,
                    completedItems = 1,
                    updatedAt = System.currentTimeMillis(),
                    error = null,
                ),
            )
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("DownloadApiV2", "Completed job=$jobId path=${target.absolutePath} bytes=$bytes")
            }
        } catch (e: Exception) {
            failJob(job, e.message ?: "Download failed")
        }
    }

    private fun resolveEndpoint(request: DownloadRequestV2): Quad? {
        val seriesId = request.seriesId
        val volumeId = request.volumeId
        val chapterId = request.chapterId
        return when (request.type) {
            DownloadJobV2Entity.TYPE_SERIES ->
                seriesId?.let { Quad(seriesId, null, null, "/api/Download/series?seriesId=$it") }
            DownloadJobV2Entity.TYPE_VOLUME ->
                volumeId?.let { Quad(seriesId, volumeId, null, "/api/Download/volume?volumeId=$it") }
            DownloadJobV2Entity.TYPE_CHAPTER ->
                chapterId?.let { Quad(seriesId, volumeId, chapterId, "/api/Download/chapter?chapterId=$it") }
            else -> null
        }
    }

    private suspend fun failJob(
        job: DownloadJobV2Entity,
        message: String,
    ) {
        downloadDao.updateJob(
            job.copy(
                status = DownloadJobV2Entity.STATUS_FAILED,
                updatedAt = System.currentTimeMillis(),
                error = message,
            ),
        )
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.e("DownloadApiV2", "Job failed id=${job.id}: $message")
        }
    }

    private data class Quad(
        val seriesId: Int?,
        val volumeId: Int?,
        val chapterId: Int?,
        val endpoint: String,
    )

    companion object {
        const val FORMAT_DOWNLOAD = "download"
    }
}

private fun translateFormatFromRequest(incomingFormat: String): Format {
    return when(incomingFormat) {
        PdfDownloadStrategyV2.FORMAT_PDF -> Format.Pdf
        EpubDownloadStrategyV2.FORMAT_EPUB -> Format.Epub
        ChapterImageArchiveDownloadStrategyV2.FORMAT_IMAGE -> Format.Image
        ChapterImageArchiveDownloadStrategyV2.FORMAT_ARCHIVE -> Format.Archive
        else -> Format.Unknown
    }
}
