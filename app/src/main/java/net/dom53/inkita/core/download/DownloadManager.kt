package net.dom53.inkita.core.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadTaskEntity
import java.io.File
import java.util.UUID

/** Lightweight orchestrator around WorkManager for downloading chapter pages. */
class DownloadManager(
    appContext: Context,
) {
    private val appContext = appContext.applicationContext
    private val downloadDao = InkitaDatabase.getInstance(this.appContext).downloadDao()
    private val workManager = WorkManager.getInstance(this.appContext)
    private val appPreferences = AppPreferences(this.appContext)
    private val networkMonitor = NetworkMonitor.getInstance(this.appContext, appPreferences)

    /**
     * Enqueue a download task for one or more pages of a chapter.
     * Handles deduplication against pending/running tasks.
     */
    suspend fun enqueuePages(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
        pageStart: Int,
        pageEnd: Int = pageStart,
    ) {
        // Dedup: if a pending or running job with the same range already exists, do not start another one.
        val dup =
            downloadDao.findDuplicateTask(
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId,
                pageStart = pageStart,
                pageEnd = pageEnd,
                type = DownloadTaskEntity.TYPE_PAGES,
                states =
                    listOf(
                        DownloadTaskEntity.STATE_PENDING,
                        DownloadTaskEntity.STATE_RUNNING,
                    ),
            )
        if (dup != null) return

        val now = System.currentTimeMillis()
        val task =
            DownloadTaskEntity(
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId,
                pageStart = pageStart,
                pageEnd = pageEnd,
                type = DownloadTaskEntity.TYPE_PAGES,
                createdAt = now,
                updatedAt = now,
            )
        val taskId = downloadDao.upsertTask(task)
        enqueueTask(task.copy(id = taskId))
    }

    suspend fun enqueuePdf(
        seriesId: Int,
        volumeId: Int?,
        bookId: Int,
        title: String?,
    ) {
        // pokud už soubor existuje, považuj za hotové
        if (pdfFileFor(bookId).exists()) return
        val dup =
            downloadDao.findDuplicateTask(
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = bookId,
                pageStart = null,
                pageEnd = null,
                type = DownloadTaskEntity.TYPE_PDF,
                states =
                    listOf(
                        DownloadTaskEntity.STATE_PENDING,
                        DownloadTaskEntity.STATE_RUNNING,
                    ),
            )
        if (dup != null) return

        val now = System.currentTimeMillis()
        val task =
            DownloadTaskEntity(
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = bookId,
                type = DownloadTaskEntity.TYPE_PDF,
                createdAt = now,
                updatedAt = now,
            )
        val taskId = downloadDao.upsertTask(task)
        // Download PDFs directly via WorkManager? For now, enqueue immediate download via DownloadDao tracking.
        enqueuePdfDirect(task.copy(id = taskId), title)
    }

    private suspend fun enqueuePdfDirect(
        task: DownloadTaskEntity,
        title: String?,
    ) {
        val request =
            buildPdfRequest(
                bookId = task.chapterId ?: return,
                title = title,
            )
        enqueueRaw(request)
        downloadDao.updateTaskState(
            id = task.id,
            state = DownloadTaskEntity.STATE_COMPLETED,
            updatedAt = System.currentTimeMillis(),
            error = null,
        )
    }

    private suspend fun buildPdfRequest(
        bookId: Int,
        title: String?,
    ): android.app.DownloadManager.Request {
        val config = appPreferences.configFlow.first()
        check(config.isConfigured) { "Not configured" }
        val base = config.serverUrl.trimEnd('/')
        val apiKeyParam = config.apiKey.takeIf { it.isNotBlank() }?.let { "&apiKey=$it" } ?: ""
        val extractParam = "&extractPdf=true"
        val file = pdfFileFor(bookId)
        val fileName = file.name
        val uri = Uri.parse("$base/api/Reader/pdf?chapterId=$bookId$apiKeyParam$extractParam")
        return android.app.DownloadManager
            .Request(uri)
            .setTitle(title ?: fileName)
            .setDescription("Inkita PDF")
            .addRequestHeader("x-api-key", config.apiKey)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { root ->
                    file.relativeTo(root).path
                } ?: "Inkita/pdfs/$fileName",
            )
    }

    fun enqueueRaw(request: android.app.DownloadManager.Request) {
        val sysManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        sysManager.enqueue(request)
    }

    /** Resolve the on-disk target file for a PDF download. */
    fun pdfFileFor(bookId: Int): File =
        File(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "Inkita/pdfs/inkita-pdf-$bookId.pdf",
        )

    /** Returns all tracked download tasks (any state). */
    suspend fun listTasks(): List<DownloadTaskEntity> = downloadDao.getAllTasks()

    /** Cancel a running/pending task and mark it as canceled. */
    suspend fun cancelTask(taskId: Long) {
        val task = downloadDao.getTaskById(taskId) ?: return
        task.workId?.let { workManager.cancelWorkById(UUID.fromString(it)) }
        downloadDao.updateTaskState(
            id = taskId,
            state = DownloadTaskEntity.STATE_CANCELED,
            updatedAt = System.currentTimeMillis(),
            error = null,
            progress = task.progress,
            total = task.total,
            bytes = task.bytes,
            bytesTotal = task.bytesTotal,
        )
        maybeEnqueuePending()
    }

    /** Pause a running task and release the worker slot. */
    suspend fun pauseTask(taskId: Long) {
        val task = downloadDao.getTaskById(taskId) ?: return
        task.workId?.let { workManager.cancelWorkById(UUID.fromString(it)) }
        downloadDao.updateTaskState(
            id = taskId,
            state = DownloadTaskEntity.STATE_PAUSED,
            updatedAt = System.currentTimeMillis(),
            error = null,
            progress = task.progress,
            total = task.total,
            bytes = task.bytes,
            bytesTotal = task.bytesTotal,
        )
        maybeEnqueuePending()
    }

    /** Resume a paused/failed task by re-enqueuing it. */
    suspend fun resumeTask(taskId: Long) {
        val task = downloadDao.getTaskById(taskId) ?: return
        if (task.state != DownloadTaskEntity.STATE_PAUSED && task.state != DownloadTaskEntity.STATE_FAILED) return
        enqueueTask(
            task.copy(
                state = DownloadTaskEntity.STATE_PENDING,
                updatedAt = System.currentTimeMillis(),
                error = null,
            ),
        )
        maybeEnqueuePending()
    }

    /** Retry a failed or canceled task by re-enqueuing it. */
    suspend fun retryTask(taskId: Long) {
        val task = downloadDao.getTaskById(taskId) ?: return
        if (task.state != DownloadTaskEntity.STATE_FAILED && task.state != DownloadTaskEntity.STATE_CANCELED) return
        enqueueTask(
            task.copy(
                state = DownloadTaskEntity.STATE_PENDING,
                updatedAt = System.currentTimeMillis(),
                error = null,
            ),
        )
        maybeEnqueuePending()
    }

    /** Permanently delete a task and cancel its work if needed. */
    suspend fun deleteTask(taskId: Long) {
        val task = downloadDao.getTaskById(taskId) ?: return
        task.workId?.let { workManager.cancelWorkById(UUID.fromString(it)) }
        downloadDao.deleteTask(taskId)
    }

    companion object {
        private const val TAG_DOWNLOAD = "download_pages"
    }

    /**
     * Called after a task finishes or a slot is freed; enqueues pending tasks
     * while respecting the user-defined parallel download limit.
     */
    suspend fun rebalanceQueue() {
        maybeEnqueuePending()
    }

    /** Build WorkManager constraints based on user settings (metered / battery). */
    private suspend fun buildConstraints(): Constraints {
        val allowMetered = appPreferences.prefetchAllowMeteredFlow.first()
        val allowLowBattery = appPreferences.prefetchAllowLowBatteryFlow.first()
        return networkMonitor.buildConstraints(
            allowMetered = allowMetered,
            requireBatteryNotLow = !allowLowBattery,
        )
    }

    /**
     * Save the task as pending and trigger scheduling.
     * This does not enqueue WorkManager immediately (handled by maybeEnqueuePending).
     */
    private suspend fun enqueueTask(task: DownloadTaskEntity) {
        // Ulož jako pending bez WorkManager requestu, limit zpracujeme níže
        val now = System.currentTimeMillis()
        val pending = task.copy(workId = null, state = DownloadTaskEntity.STATE_PENDING, updatedAt = now)
        val newId = downloadDao.upsertTask(pending)
        if (pending.id == 0L) {
            // Pokud bylo vloženo nové ID, uložíme ho zpět (kvůli dedupu/observingu)
            downloadDao.upsertTask(pending.copy(id = newId))
        }
        maybeEnqueuePending()
    }

    /** Enqueue pending tasks up to the configured concurrency limit. */
    private suspend fun maybeEnqueuePending() {
        if (networkMonitor.shouldDeferNetworkWork()) return
        val limit = appPreferences.downloadMaxConcurrentFlow.first().coerceAtLeast(1)
        val enqueued =
            downloadDao.countEnqueuedStates(
                listOf(
                    DownloadTaskEntity.STATE_PENDING,
                    DownloadTaskEntity.STATE_RUNNING,
                ),
            )
        val freeSlots = (limit - enqueued).coerceAtLeast(0)
        if (freeSlots <= 0) return

        val toEnqueue = downloadDao.getPendingUnenqueued(limit = freeSlots)
        toEnqueue.forEach { task ->
            actuallyEnqueue(task)
        }
    }

    /** Create and enqueue the WorkManager request for a single task. */
    private suspend fun actuallyEnqueue(task: DownloadTaskEntity) {
        val workId = UUID.randomUUID()
        val updatedTask =
            task.copy(
                workId = workId.toString(),
                updatedAt = System.currentTimeMillis(),
            )
        downloadDao.upsertTask(updatedTask)

        val workData =
            PageDownloadWorker.buildInputData(
                seriesId = updatedTask.seriesId,
                volumeId = updatedTask.volumeId,
                chapterId = updatedTask.chapterId ?: return,
                pageStart = updatedTask.pageStart ?: return,
                pageEnd = updatedTask.pageEnd ?: updatedTask.pageStart ?: return,
                taskId = updatedTask.id,
            )

        val request =
            OneTimeWorkRequestBuilder<PageDownloadWorker>()
                .setId(workId)
                .setInputData(workData)
                .addTag(TAG_DOWNLOAD)
                .setConstraints(buildConstraints())
                .build()

        workManager.enqueue(request)
    }
}
