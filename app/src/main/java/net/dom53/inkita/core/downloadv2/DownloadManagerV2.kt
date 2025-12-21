package net.dom53.inkita.core.downloadv2

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao

class DownloadManagerV2(
    private val appContext: Context,
    private val downloadDao: DownloadV2Dao,
    private val strategies: Map<String, DownloadStrategyV2>,
) {
    suspend fun enqueue(request: DownloadRequestV2): Long {
        val strategy = strategies[request.format] ?: return -1L
        val jobId = strategy.enqueue(request)
        enqueueWorker()
        return jobId
    }

    fun enqueueWorker() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            OneTimeWorkRequestBuilder<DownloadWorkerV2>()
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(appContext).enqueue(request)
    }
}
