package net.dom53.inkita.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity
import net.dom53.inkita.data.repository.DownloadRepositoryImpl
import net.dom53.inkita.domain.repository.DownloadRepository

class DownloadQueueViewModel(
    private val repo: DownloadRepository,
) : ViewModel() {
    val tasks =
        repo
            .observeTasks()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    val downloaded =
        repo
            .observeDownloadedPages()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    fun cancelTask(id: Long) {
        viewModelScope.launch {
            repo.cancelTask(id)
        }
    }

    fun pauseTask(id: Long) {
        viewModelScope.launch {
            repo.pauseTask(id)
        }
    }

    fun resumeTask(id: Long) {
        viewModelScope.launch {
            repo.resumeTask(id)
        }
    }

    fun retryTask(id: Long) {
        viewModelScope.launch {
            repo.retryTask(id)
        }
    }

    fun deleteDownloaded(
        chapterId: Int,
        page: Int,
    ) {
        viewModelScope.launch { repo.deleteDownloadedPage(chapterId, page) }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { repo.deleteTask(id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repo.clearCompletedTasks() }
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
        val manager =
            net.dom53.inkita.core.download
                .DownloadManager(context)
        val repo: DownloadRepository = DownloadRepositoryImpl(db.downloadDao(), manager, context.applicationContext)
        return DownloadQueueViewModel(repo)
    }
}
