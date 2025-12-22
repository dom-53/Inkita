package net.dom53.inkita.core.downloadv2

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Suppress("UnusedPrivateProperty")
class DownloadManagerV2(
    private val appContext: Context,
    private val downloadDao: DownloadV2Dao,
    private val strategies: Map<String, DownloadStrategyV2>,
) {
    suspend fun enqueue(request: DownloadRequestV2): Long {
        val strategy = strategies[request.format] ?: return -1L
        val jobId = strategy.enqueue(request)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadManagerV2",
                "Enqueued job=$jobId type=${request.type} fmt=${request.format} series=${request.seriesId} " +
                    "volume=${request.volumeId} chapter=${request.chapterId} page=${request.pageIndex} count=${request.pageCount}",
            )
        }
        enqueueWorker()
        return jobId
    }

    fun enqueueWorker() {
        val prefs = AppPreferences(appContext)
        val monitor = NetworkMonitor.getInstance(appContext, prefs)
        if (monitor.shouldDeferNetworkWork()) {
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("DownloadManagerV2", "Skipping worker enqueue (offline mode)")
            }
            return
        }
        val (allowMetered, allowLowBattery) =
            runBlocking {
                prefs.downloadAllowMeteredFlow.first() to prefs.downloadAllowLowBatteryFlow.first()
            }
        val constraints =
            monitor.buildConstraints(
                allowMetered = allowMetered,
                requireBatteryNotLow = !allowLowBattery,
            )
        val request =
            OneTimeWorkRequestBuilder<DownloadWorkerV2>()
                .setConstraints(constraints)
                .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("DownloadManagerV2", "Worker enqueued (unique=$WORK_NAME)")
        }
    }

    companion object {
        private const val WORK_NAME = "download_v2_worker"
    }
}
