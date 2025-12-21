package net.dom53.inkita.core.downloadv2

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity

class DownloadWorkerV2(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = InkitaDatabase.getInstance(applicationContext)
        val dao = db.downloadV2Dao()
        val appPreferences = AppPreferences(applicationContext)
        while (true) {
            val job = dao.getJobsByStatus(DownloadJobV2Entity.STATUS_PENDING).firstOrNull() ?: break
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
                continue
            }
            strategy.run(job.id)
        }
        return Result.success()
    }
}
