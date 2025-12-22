package net.dom53.inkita.core.downloadv2

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
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
        val db = InkitaDatabase.getInstance(applicationContext)
        val dao = db.downloadV2Dao()
        val appPreferences = AppPreferences(applicationContext)
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
        while (true) {
            val pending = dao.getJobsByStatus(DownloadJobV2Entity.STATUS_PENDING)
            if (pending.isEmpty()) {
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d("DownloadWorkerV2", "No pending jobs")
                }
                break
            }
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("DownloadWorkerV2", "Processing pending=${pending.size}")
            }
            coroutineScope {
                pending.forEach { job ->
                    launch {
                        semaphore.withPermit {
                            runJob(job, dao, appPreferences, retryEnabled, maxRetries)
                        }
                    }
                }
            }
        }
        return Result.success()
    }

    /**
     * Execute a single job using its strategy and apply retry rules if needed.
     */
    private suspend fun runJob(
        job: DownloadJobV2Entity,
        dao: net.dom53.inkita.data.local.db.dao.DownloadV2Dao,
        appPreferences: AppPreferences,
        retryEnabled: Boolean,
        maxRetries: Int,
    ) {
        val strategy =
            when (job.strategy) {
                EpubDownloadStrategyV2.FORMAT_EPUB ->
                    EpubDownloadStrategyV2(
                        appContext = applicationContext,
                        downloadDao = dao,
                        appPreferences = appPreferences,
                    )
                else -> null
            }
        if (strategy == null) {
            dao.updateJob(
                job.copy(
                    status = DownloadJobV2Entity.STATUS_FAILED,
                    updatedAt = System.currentTimeMillis(),
                    error = "No strategy: ${job.strategy}",
                ),
            )
            LoggingManager.w("DownloadWorkerV2", "No strategy for job=${job.id} strategy=${job.strategy}")
            return
        }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadWorkerV2",
                "Run job=${job.id} type=${job.type} series=${job.seriesId} volume=${job.volumeId} chapter=${job.chapterId}",
            )
        }
        strategy.run(job.id)
        val updated = dao.getJob(job.id) ?: return
        if (updated.status == DownloadJobV2Entity.STATUS_FAILED && retryEnabled) {
            if (updated.retryCount < maxRetries) {
                dao.updateJob(
                    updated.copy(
                        status = DownloadJobV2Entity.STATUS_PENDING,
                        retryCount = updated.retryCount + 1,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d(
                        "DownloadWorkerV2",
                        "Retrying job=${updated.id} attempt=${updated.retryCount + 1}/$maxRetries",
                    )
                }
            }
        } else if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("DownloadWorkerV2", "Job done id=${updated.id} status=${updated.status}")
        }
    }
}
