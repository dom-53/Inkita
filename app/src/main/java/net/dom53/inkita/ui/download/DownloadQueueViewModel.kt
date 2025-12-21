package net.dom53.inkita.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity

class DownloadQueueViewModel(
    private val downloadDao: DownloadV2Dao,
) : ViewModel() {
    val tasks =
        downloadDao
            .observeJobs()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    val downloaded =
        downloadDao
            .observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    fun cancelTask(id: Long) {
        viewModelScope.launch {
            updateJobStatus(id, DownloadJobV2Entity.STATUS_CANCELED)
        }
    }

    fun pauseTask(id: Long) {
        viewModelScope.launch {
            updateJobStatus(id, DownloadJobV2Entity.STATUS_PAUSED)
        }
    }

    fun resumeTask(id: Long) {
        viewModelScope.launch {
            updateJobStatus(id, DownloadJobV2Entity.STATUS_PENDING)
        }
    }

    fun retryTask(id: Long) {
        viewModelScope.launch {
            updateJobStatus(id, DownloadJobV2Entity.STATUS_PENDING)
        }
    }

    fun deleteDownloaded(
        itemId: Long,
    ) {
        viewModelScope.launch { downloadDao.deleteItemById(itemId) }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            downloadDao.clearItemsForJob(id)
            downloadDao.deleteJob(id)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch { downloadDao.deleteJobsByStatus(DownloadJobV2Entity.STATUS_COMPLETED) }
    }

    private suspend fun updateJobStatus(id: Long, status: String) {
        val job = downloadDao.getJob(id) ?: return
        downloadDao.updateJob(
            job.copy(
                status = status,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

/**
 * Jednoduchý factory pro manuální vytvoření (bez DI).
 */
object DownloadQueueViewModelFactory {
    fun create(context: android.content.Context): DownloadQueueViewModel {
        val db =
            net.dom53.inkita.data.local.db.InkitaDatabase
                .getInstance(context)
        return DownloadQueueViewModel(db.downloadV2Dao())
    }
}
