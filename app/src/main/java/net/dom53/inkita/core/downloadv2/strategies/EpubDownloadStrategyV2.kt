package net.dom53.inkita.core.downloadv2.strategies

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.DownloadStrategyV2
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkLoggingInterceptor
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.regex.Pattern

class EpubDownloadStrategyV2(
    private val appContext: Context,
    private val downloadDao: DownloadV2Dao,
    private val appPreferences: AppPreferences,
) : DownloadStrategyV2 {
    override val key: String = FORMAT_EPUB

    private val httpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(NetworkLoggingInterceptor)
            .build()

    override suspend fun enqueue(request: DownloadRequestV2): Long {
        val seriesId = request.seriesId ?: return -1L
        val volumeId = request.volumeId
        val chapterId = request.chapterId ?: return -1L
        val pageCount = request.pageCount ?: return -1L
        val now = System.currentTimeMillis()
        val job =
            DownloadJobV2Entity(
                type = request.type,
                format = request.format,
                strategy = key,
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId,
                status = DownloadJobV2Entity.STATUS_PENDING,
                totalItems = pageCount,
                completedItems = 0,
                priority = request.priority,
                createdAt = now,
                updatedAt = now,
                error = null,
            )
        val jobId = downloadDao.insertJob(job)
        val items =
            (0 until pageCount).map { page ->
                DownloadedItemV2Entity(
                    jobId = jobId,
                    type = DownloadedItemV2Entity.TYPE_PAGE,
                    chapterId = chapterId,
                    page = page,
                    url = null,
                    localPath = null,
                    bytes = null,
                    checksum = null,
                    status = DownloadedItemV2Entity.STATUS_PENDING,
                    createdAt = now,
                    updatedAt = now,
                    error = null,
                )
            }
        downloadDao.upsertItems(items)
        return jobId
    }

    override suspend fun run(jobId: Long) {
        val job = downloadDao.getJob(jobId) ?: return
        if (job.status == DownloadJobV2Entity.STATUS_COMPLETED) return
        val offlineMode = appPreferences.offlineModeFlow.first()
        val networkStatus = NetworkMonitor.getInstance(appContext, appPreferences).status.value
        if (offlineMode || !networkStatus.isOnlineAllowed) {
            updateJob(
                job,
                status = DownloadJobV2Entity.STATUS_FAILED,
                error = "Offline mode",
            )
            return
        }
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) {
            updateJob(
                job,
                status = DownloadJobV2Entity.STATUS_FAILED,
                error = "Not configured",
            )
            return
        }
        val seriesId = job.seriesId ?: return
        val chapterId = job.chapterId ?: return
        val volumeId = job.volumeId
        val baseDir =
            appContext.getExternalFilesDir("Inkita/downloads/series_$seriesId")
                ?: File(appContext.filesDir, "Inkita/downloads/series_$seriesId").apply { mkdirs() }
        if (!baseDir.exists()) baseDir.mkdirs()
        val assetsDir = File(baseDir, "assets").apply { if (!exists()) mkdirs() }

        updateJob(job, DownloadJobV2Entity.STATUS_RUNNING, error = null)

        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
        val items = downloadDao.getItemsForJob(jobId)
        var completed = job.completedItems ?: 0
        try {
            for (item in items) {
                if (item.status == DownloadedItemV2Entity.STATUS_COMPLETED) continue
                val page = item.page ?: continue
                val htmlResp = api.getBookPage(chapterId, page)
                if (!htmlResp.isSuccessful) throw HttpException(htmlResp)
                val htmlRaw = htmlResp.body() ?: ""
                val (rewrittenHtml, assetsBytes) = rewriteAndDownloadImages(htmlRaw, assetsDir, config.serverUrl, config.apiKey)
                val htmlName = "page_${chapterId}_$page.html"
                val htmlFile = File(baseDir, htmlName)
                withContext(Dispatchers.IO) {
                    htmlFile.sink().buffer().use { sink -> sink.writeString(rewrittenHtml, Charsets.UTF_8) }
                }
                val htmlSize = htmlFile.length()
                val size = htmlSize + assetsBytes
                downloadDao.upsertItems(
                    listOf(
                        item.copy(
                            status = DownloadedItemV2Entity.STATUS_COMPLETED,
                            localPath = htmlFile.absolutePath,
                            bytes = size,
                            updatedAt = System.currentTimeMillis(),
                            error = null,
                        ),
                    ),
                )
                completed++
                updateJob(
                    job.copy(completedItems = completed),
                    status = DownloadJobV2Entity.STATUS_RUNNING,
                    error = null,
                )
            }
            updateJob(
                job.copy(completedItems = completed),
                status = DownloadJobV2Entity.STATUS_COMPLETED,
                error = null,
            )
        } catch (e: Exception) {
            LoggingManager.e("EpubDownloadV2", "Download failed", e)
            val failedItem =
                items.firstOrNull { it.status != DownloadedItemV2Entity.STATUS_COMPLETED }
            if (failedItem != null) {
                downloadDao.upsertItems(
                    listOf(
                        failedItem.copy(
                            status = DownloadedItemV2Entity.STATUS_FAILED,
                            updatedAt = System.currentTimeMillis(),
                            error = e.message,
                        ),
                    ),
                )
            }
            updateJob(
                job.copy(completedItems = completed),
                status = DownloadJobV2Entity.STATUS_FAILED,
                error = e.message,
            )
        }
    }

    private suspend fun updateJob(
        job: DownloadJobV2Entity,
        status: String,
        error: String?,
    ) {
        val updated =
            job.copy(
                status = status,
                updatedAt = System.currentTimeMillis(),
                error = error,
            )
        downloadDao.updateJob(updated)
    }

    @Suppress("LoopWithTooManyJumpStatements", "MagicNumber")
    private suspend fun rewriteAndDownloadImages(
        html: String,
        assetsDir: File,
        baseUrl: String,
        apiKey: String,
    ): Pair<String, Long> =
        withContext(Dispatchers.IO) {
            var result = html
            val matcher = SRC_PATTERN.matcher(html)
            val replacements = mutableMapOf<String, String>()
            var totalBytes = 0L

            while (matcher.find()) {
                val src = matcher.group(1) ?: continue
                if (replacements.containsKey(src)) continue

                val absoluteUrl =
                    when {
                        src.startsWith("//") -> "https:$src"
                        src.startsWith("http://") || src.startsWith("https://") -> src
                        src.startsWith("/") -> baseUrl.trimEnd('/') + src
                        else -> null
                    } ?: continue

                val ext =
                    absoluteUrl
                        .substringAfterLast('.', missingDelimiterValue = "")
                        .takeIf { it.length in 2..5 }
                        ?.let { ".$it" }
                        .orEmpty()
                val fileName = hashName(absoluteUrl) + ext
                try {
                    val (uri, bytes, legacyFile) = downloadBinary(absoluteUrl, fileName, assetsDir, apiKey)
                    totalBytes += bytes
                    val replacement =
                        when {
                            uri != null -> uri.toString()
                            legacyFile != null -> Uri.fromFile(legacyFile).toString()
                            else -> null
                        }
                    if (replacement != null) replacements[src] = replacement
                } catch (_: IOException) {
                    // ignore failed image
                }
            }

            for ((old, new) in replacements) {
                result = result.replace(old, new)
            }
            Pair(result, totalBytes)
        }

    private suspend fun downloadBinary(
        url: String,
        fileName: String,
        targetDir: File,
        apiKey: String,
    ): Triple<Uri?, Long, File?> =
        withContext(Dispatchers.IO) {
            val existing = File(targetDir, fileName)
            if (existing.exists()) {
                return@withContext Triple(null, existing.length(), existing)
            }

            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("x-api-key", apiKey)
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")

                val target = File(targetDir, fileName)
                val bytes =
                    target.sink().buffer().use { sink ->
                        body.source().use { source ->
                            sink.writeAll(source)
                            target.length()
                        }
                    }
                Triple(null, bytes, target)
            }
        }

    private fun hashName(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FORMAT_EPUB = "epub"
        private val SRC_PATTERN = Pattern.compile("src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    }
}
