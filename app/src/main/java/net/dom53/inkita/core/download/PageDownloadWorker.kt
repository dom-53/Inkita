package net.dom53.inkita.core.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.notification.AppNotificationManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern

/**
 * Foreground worker that downloads one or more chapter pages (HTML + images)
 * into the app sandbox at Android/data/<package>/files/Inkita/downloads/series_<id>/.../
 */
class PageDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val appPreferences = AppPreferences(appContext)
    private val db = InkitaDatabase.getInstance(appContext)
    private val downloadDao = db.downloadDao()
    private val httpClient = OkHttpClient()
    private var currentTaskId: Long = -1L

    /**
     * Fetch pages, rewrite image URLs, download assets, and persist HTML + metadata.
     * Updates task state/progress and retries on failure.
     */
    override suspend fun doWork(): Result {
        val seriesId = inputData.getInt(KEY_SERIES_ID, -1)
        val chapterId = inputData.getInt(KEY_CHAPTER_ID, -1)
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        currentTaskId = taskId
        if (seriesId <= 0 || chapterId <= 0) return Result.failure()

        val volumeId = inputData.getInt(KEY_VOLUME_ID, -1).takeIf { it > 0 }
        val pageStart = inputData.getInt(KEY_PAGE_START, 0)
        val pageEnd = inputData.getInt(KEY_PAGE_END, pageStart).let { if (it <= 0) pageStart else it }

        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) return Result.failure()
        AppNotificationManager.init(applicationContext)
        val networkStatus = NetworkMonitor.getInstance(applicationContext, appPreferences).status.value
        if (!networkStatus.isOnlineAllowed) {
            return if (networkStatus.offlineMode) Result.success() else Result.retry()
        }

        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)

        val baseDir =
            applicationContext.getExternalFilesDir("Inkita/downloads/series_$seriesId")
                ?: File(applicationContext.filesDir, "Inkita/downloads/series_$seriesId").apply { mkdirs() }
        if (!baseDir.exists()) baseDir.mkdirs()
        val assetsDir = File(baseDir, "assets").apply { if (!exists()) mkdirs() }

        return try {
            if (taskId > 0) {
                downloadDao.updateTaskState(
                    id = taskId,
                    state = net.dom53.inkita.data.local.db.entity.DownloadTaskEntity.STATE_RUNNING,
                    updatedAt = System.currentTimeMillis(),
                    error = null,
                    progress = 0,
                    total = 0,
                )
            }

            val totalPages = (pageEnd - pageStart + 1).coerceAtLeast(1)
            var completed = 0
            var bytesAccum = 0L
            var bytesTotal = 0L
            safeSetForeground(completed, totalPages, bytesAccum, bytesTotal)

            for (page in pageStart..pageEnd) {
                if (isStopped) {
                    markCanceled(taskId, completed, totalPages, bytesAccum, bytesTotal)
                    return Result.failure()
                }
                setProgress(workDataOf(KEY_PROGRESS_PAGE to page))
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
                val entity =
                    DownloadedPageEntity(
                        seriesId = seriesId,
                        volumeId = volumeId,
                        chapterId = chapterId,
                        page = page,
                        htmlPath = htmlFile.absolutePath,
                        assetsDir = assetsDir.absolutePath,
                        sizeBytes = size,
                        updatedAt = System.currentTimeMillis(),
                    )
                downloadDao.upsertDownloadedPage(entity)

                completed++
                bytesAccum += size
                if (bytesTotal < bytesAccum) bytesTotal = bytesAccum
                safeSetForeground(completed, totalPages, bytesAccum, bytesTotal)
                if (isStopped) {
                    markCanceled(taskId, completed, totalPages, bytesAccum, bytesTotal)
                    return Result.failure()
                }
                if (taskId > 0) {
                    downloadDao.updateTaskState(
                        id = taskId,
                        state = net.dom53.inkita.data.local.db.entity.DownloadTaskEntity.STATE_RUNNING,
                        updatedAt = System.currentTimeMillis(),
                        error = null,
                        progress = completed,
                        total = totalPages,
                        bytes = bytesAccum,
                        bytesTotal = bytesTotal,
                    )
                }
            }
            if (taskId > 0) {
                if (isStopped) {
                    markCanceled(taskId, totalPages, totalPages, bytesAccum, bytesTotal)
                    return Result.failure()
                } else {
                    downloadDao.updateTaskState(
                        id = taskId,
                        state = net.dom53.inkita.data.local.db.entity.DownloadTaskEntity.STATE_COMPLETED,
                        updatedAt = System.currentTimeMillis(),
                        error = null,
                        progress = totalPages,
                        total = totalPages,
                        bytes = bytesAccum,
                        bytesTotal = bytesTotal,
                    )
                }
            }
            DownloadManager(applicationContext).rebalanceQueue()
            Result.success()
        } catch (e: Exception) {
            LoggingManager.e("PageDownloadWorker", "Download failed", e)
            val retryEnabled = appPreferences.downloadRetryEnabledFlow.first()
            val maxAttempts = appPreferences.downloadRetryMaxAttemptsFlow.first().coerceAtLeast(1)
            val shouldRetry = retryEnabled && runAttemptCount < (maxAttempts - 1)
            if (taskId > 0) {
                downloadDao.updateTaskState(
                    id = taskId,
                    state = net.dom53.inkita.data.local.db.entity.DownloadTaskEntity.STATE_FAILED,
                    updatedAt = System.currentTimeMillis(),
                    error = e.message,
                    progress = 0,
                    total = 0,
                    bytes = 0,
                    bytesTotal = 0,
                )
            }
            if (shouldRetry) Result.retry() else Result.failure()
        }
    }

    /** Build the foreground notification info for the current download progress. */
    private fun createForegroundInfo(
        progress: Int,
        total: Int,
        bytes: Long,
        bytesTotal: Long,
    ): ForegroundInfo {
        val subtitle =
            buildString {
                append("$progress / $total")
                if (bytes > 0) {
                    append(" | ")
                    append(formatBytes(bytes))
                    if (bytesTotal > 0) {
                        append(" / ")
                        append(formatBytes(bytesTotal))
                    }
                }
            }
        val notification =
            AppNotificationManager.buildForegroundNotification(
                channel = AppNotificationManager.CHANNEL_DOWNLOADS,
                title = "Downloading pages",
                text = subtitle,
                progress = progress,
                max = total.coerceAtLeast(1),
            )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** Safely promote the worker to foreground; logs but ignores failures. */
    private suspend fun safeSetForeground(
        progress: Int,
        total: Int,
        bytes: Long,
        bytesTotal: Long,
    ) {
        try {
            if (!AppNotificationManager.canPostNotifications()) {
                LoggingManager.w("PageDownloadWorker", "Notification permission missing; skipping foreground promotion")
                return
            }
            setForeground(createForegroundInfo(progress, total, bytes, bytesTotal))
        } catch (e: Exception) {
            LoggingManager.w("PageDownloadWorker", "Failed to promote to foreground", e)
        }
    }

    /**
     * Rewrites image src URLs to local files and downloads them into assetsDir.
     * Returns rewritten HTML and total bytes of downloaded assets.
     */
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

    /** Download a binary asset if not already cached; returns uri/bytes/file info. */
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

    /** Generate a stable hashed filename stem. */
    private fun hashName(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Mark a task as canceled with the last known progress. */
    private suspend fun markCanceled(
        taskId: Long,
        progress: Int,
        total: Int,
        bytes: Long,
        bytesTotal: Long,
    ) {
        if (taskId <= 0) return
        downloadDao.updateTaskState(
            id = taskId,
            state = net.dom53.inkita.data.local.db.entity.DownloadTaskEntity.STATE_CANCELED,
            updatedAt = System.currentTimeMillis(),
            error = null,
            progress = progress,
            total = total,
            bytes = bytes,
            bytesTotal = bytesTotal,
        )
    }

    companion object {
        private val SRC_PATTERN = Pattern.compile("src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

        const val KEY_SERIES_ID = "seriesId"
        const val KEY_VOLUME_ID = "volumeId"
        const val KEY_CHAPTER_ID = "chapterId"
        const val KEY_PAGE_START = "pageStart"
        const val KEY_PAGE_END = "pageEnd"
        const val KEY_TASK_ID = "taskId"
        const val KEY_PROGRESS_PAGE = "progressPage"

        private const val NOTIFICATION_ID = 2001

        /** Human-readable formatter for byte sizes. */
        @Suppress("MagicNumber")
        private fun formatBytes(value: Long): String {
            if (value <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var v = value.toDouble()
            var idx = 0
            while (v >= 1024 && idx < units.lastIndex) {
                v /= 1024
                idx++
            }
            return String.format(Locale.getDefault(), "%.1f %s", v, units[idx])
        }

        /** Helper to build worker input data. */
        fun buildInputData(
            seriesId: Int,
            volumeId: Int?,
            chapterId: Int,
            pageStart: Int,
            pageEnd: Int = pageStart,
            taskId: Long = -1,
        ): Data =
            Data
                .Builder()
                .putInt(KEY_SERIES_ID, seriesId)
                .putInt(KEY_CHAPTER_ID, chapterId)
                .putInt(KEY_PAGE_START, pageStart)
                .putInt(KEY_PAGE_END, pageEnd)
                .putLong(KEY_TASK_ID, taskId)
                .apply { volumeId?.let { putInt(KEY_VOLUME_ID, it) } }
                .build()
    }
}
