package net.dom53.inkita.core.downloadv2.strategies

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.DownloadStrategyV2
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import java.io.File

class PdfDownloadStrategyV2(
    private val appContext: Context,
    private val downloadDao: DownloadV2Dao,
    private val appPreferences: AppPreferences,
) : DownloadStrategyV2 {
    override val key: String = FORMAT_PDF

    override suspend fun enqueue(request: DownloadRequestV2): Long {
        val chapterId = request.chapterId ?: return -1L
        // If we already have a completed item for this chapter, reuse it.
        val existing =
            downloadDao.getDownloadedFileForChapter(
                chapterId = chapterId,
                type = DownloadedItemV2Entity.TYPE_FILE,
            )
        if (existing != null && existing.localPath?.let { File(it).exists() } == true) {
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("PdfDownloadV2", "Skip enqueue; already downloaded chapter=$chapterId")
            }
            return existing.jobId ?: -1L
        }

        val now = System.currentTimeMillis()
        val job =
            DownloadJobV2Entity(
                type = DownloadJobV2Entity.TYPE_CHAPTER,
                format = FORMAT_PDF,
                strategy = FORMAT_PDF,
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
        val jobId = downloadDao.insertJob(job)
        val item =
            DownloadedItemV2Entity(
                jobId = jobId,
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
                error = null,
            )
        downloadDao.upsertItems(listOf(item))
        return jobId
    }

    override suspend fun run(jobId: Long) {
        val job = downloadDao.getJob(jobId) ?: return
        if (job.status == DownloadJobV2Entity.STATUS_COMPLETED) return
        val chapterId = job.chapterId ?: return
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) {
            failJob(job, "Not configured")
            return
        }
        val target = pdfPath(chapterId)
        target.parentFile?.mkdirs()

        try {
            val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
            val resp = api.getPdf(chapterId, apiKey = config.apiKey, extractPdf = true)
            if (!resp.isSuccessful) {
                failJob(job, "HTTP ${resp.code()}")
                return
            }
            val body =
                resp.body() ?: run {
                    failJob(job, "Empty body")
                    return
                }
            withContext(Dispatchers.IO) {
                body.byteStream().use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
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
                LoggingManager.d("PdfDownloadV2", "Completed job=$jobId chapter=$chapterId bytes=$bytes")
            }
        } catch (e: Exception) {
            failJob(job, e.message ?: "Download failed")
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
            LoggingManager.e("PdfDownloadV2", "Job failed id=${job.id}: $message")
        }
    }

    private fun pdfPath(chapterId: Int): File = File(appContext.getExternalFilesDir("Inkita/downloads/pdfs"), "pdf-$chapterId.pdf")

    companion object {
        const val FORMAT_PDF = "pdf"
    }
}
