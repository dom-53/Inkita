package net.dom53.inkita.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import net.dom53.inkita.core.di.InkitaServices
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.domain.repository.ReaderRepository

/**
 * Syncs locally cached reader progress to the server once network is available.
 * This is a lightweight job: it simply calls ReaderRepository.syncLocalProgress().
 */
class ProgressSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val monitor = NetworkMonitor.getInstance(applicationContext)
        val status = monitor.status.value
        if (!status.isOnlineAllowed) {
            return if (status.offlineMode) Result.success() else Result.retry()
        }
        val services = InkitaServices.get(applicationContext)
        val repo: ReaderRepository = services.readerRepository
        return runCatching {
            repo.syncLocalProgress()
            Result.success()
        }.getOrElse { e ->
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "inkita_progress_sync"

        fun enqueue(context: Context) {
            val monitor = NetworkMonitor.getInstance(context)
            val status = monitor.status.value
            if (!status.isOnlineAllowed) return
            val request =
                OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                    .setConstraints(
                        monitor.buildConstraints(
                            allowMetered = true,
                        ),
                    ).build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
