package net.dom53.inkita.core.downloadv2

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.downloadv2.strategies.ChapterImageArchiveDownloadStrategyV2
import net.dom53.inkita.core.downloadv2.strategies.DownloadApiStrategyV2
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
import net.dom53.inkita.core.downloadv2.strategies.PdfDownloadStrategyV2
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.KavitaProfile
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity

/**
 * WorkManager worker that processes queued Download V2 jobs with concurrency limits and retry logic.
 */
class DownloadWorkerV2(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    /**
     * Pull pending jobs and execute them via their strategy until the queue is empty.
     */
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(KavitaProfile.WORK_INPUT) ?: return Result.success()
        val activeSession = AppPreferences(applicationContext).activeSessionFlow
        val session = activeSession.first()
        if (session.profile.id != profileId || !session.config.isConfigured) return Result.success()
        val db = InkitaDatabase.getInstance(applicationContext, session.profile)
        val dao = db.downloadV2Dao()
        val appPreferences = AppPreferences(applicationContext, session)
        val networkMonitor = NetworkMonitor.getInstance(applicationContext, appPreferences)
        val maxConcurrent = appPreferences.downloadMaxConcurrentFlow.first().coerceAtLeast(1)
        val retryEnabled = appPreferences.downloadRetryEnabledFlow.first()
        val maxRetries = appPreferences.downloadRetryMaxAttemptsFlow.first().coerceAtLeast(1)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadWorkerV2",
                "Start (maxConcurrent=$maxConcurrent retry=$retryEnabled maxRetries=$maxRetries)",
            )
        }
        val semaphore = Semaphore(maxConcurrent)
        // Unique WorkManager requests run in order. Any RUNNING rows here belong to an interrupted run.
        dao.recoverInterruptedJobs()
        while (true) {
            val pending = dao.getJobsByStatus(DownloadJobV2Entity.STATUS_PENDING)
            if (pending.isEmpty()) {
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d("DownloadWorkerV2", "No pending jobs")
                }
                break
            }
            if (appPreferences.offlineModeFlow.first()) return Result.success()
            if (!networkMonitor.status.value.isOnlineAllowed) return Result.retry()
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("DownloadWorkerV2", "Processing pending=${pending.size}")
            }
            coroutineScope {
                pending.forEach { job ->
                    launch {
                        semaphore.withPermit {
                            if (activeSession.first() == session) {
                                runJob(job, dao, appPreferences, retryEnabled, maxRetries)
                            }
                        }
                    }
                }
            }
            if (activeSession.first() != session) return Result.success()
        }
        return Result.success()
    }

    /**
     * Execute a single job using its strategy and apply retry rules if needed.
     */
    private suspend fun runJob(
        job: DownloadJobV2Entity,
        dao: DownloadV2Dao,
        appPreferences: AppPreferences,
        retryEnabled: Boolean,
        maxRetries: Int,
    ) {
        val claimed =
            dao.transitionJob(
                job.id,
                listOf(DownloadJobV2Entity.STATUS_PENDING),
                DownloadJobV2Entity.STATUS_RUNNING,
            )
        if (claimed == 0) return

        val strategy =
            when (job.strategy) {
                EpubDownloadStrategyV2.FORMAT_EPUB ->
                    EpubDownloadStrategyV2(
                        appContext = applicationContext,
                        downloadDao = dao,
                        appPreferences = appPreferences,
                    )
                DownloadApiStrategyV2.FORMAT_DOWNLOAD ->
                    DownloadApiStrategyV2(
                        appContext = applicationContext,
                        downloadDao = dao,
                        appPreferences = appPreferences,
                    )
                PdfDownloadStrategyV2.FORMAT_PDF ->
                    PdfDownloadStrategyV2(
                        appContext = applicationContext,
                        downloadDao = dao,
                        appPreferences = appPreferences,
                    )
                ChapterImageArchiveDownloadStrategyV2.FORMAT_IMAGE ->
                    ChapterImageArchiveDownloadStrategyV2(
                        appContext = applicationContext,
                        downloadDao = dao,
                        appPreferences = appPreferences,
                        key = ChapterImageArchiveDownloadStrategyV2.FORMAT_IMAGE,
                    )
                ChapterImageArchiveDownloadStrategyV2.FORMAT_ARCHIVE ->
                    ChapterImageArchiveDownloadStrategyV2(
                        appContext = applicationContext,
                        downloadDao = dao,
                        appPreferences = appPreferences,
                        key = ChapterImageArchiveDownloadStrategyV2.FORMAT_ARCHIVE,
                    )
                else -> null
            }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadWorkerV2",
                "Run job=${job.id} type=${job.type} series=${job.seriesId} volume=${job.volumeId} chapter=${job.chapterId}",
            )
        }
        val error =
            try {
                if (strategy == null) {
                    "No strategy: ${job.strategy}"
                } else {
                    withContext(Dispatchers.IO) { strategy.run(job.id) }
                    "Download ended before completion"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Download failed"
            }
        val updated = dao.getJob(job.id) ?: return
        if (updated.status == DownloadJobV2Entity.STATUS_RUNNING) {
            dao.updateRunningJob(
                updated.copy(
                    status = DownloadJobV2Entity.STATUS_FAILED,
                    updatedAt = System.currentTimeMillis(),
                    error = error,
                ),
            )
        }
        if (retryEnabled && strategy != null) dao.retryFailedJob(job.id, maxRetries)
    }
}
