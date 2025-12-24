package net.dom53.inkita.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.data.api.dto.SeriesDetailDto
import net.dom53.inkita.data.api.dto.VolumeDto
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2

class DownloadQueueViewModel(
    private val downloadDao: DownloadV2Dao,
    private val cacheManager: CacheManager,
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
    private val _lookup = MutableStateFlow(DownloadLookup())
    val lookup: StateFlow<DownloadLookup> = _lookup
    private var lastTasks: List<DownloadJobV2Entity> = emptyList()
    private var lastDownloaded: List<DownloadedItemV2Entity> = emptyList()

    init {
        viewModelScope.launch {
            tasks.collectLatest { list ->
                lastTasks = list
                updateLookup()
            }
        }
        viewModelScope.launch {
            downloaded.collectLatest { list ->
                lastDownloaded = list
                updateLookup()
            }
        }
    }

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

    fun deleteDownloaded(itemId: Long) {
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

    private suspend fun updateJobStatus(
        id: Long,
        status: String,
    ) {
        val job = downloadDao.getJob(id) ?: return
        downloadDao.updateJob(
            job.copy(
                status = status,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun updateLookup() {
        val seriesIds =
            buildSet {
                lastTasks.mapNotNullTo(this) { it.seriesId }
                lastDownloaded.mapNotNullTo(this) { it.seriesId }
            }
        if (seriesIds.isEmpty()) {
            _lookup.update { DownloadLookup() }
            return
        }
        val details =
            withContext(Dispatchers.IO) {
                seriesIds.mapNotNull { id ->
                    cacheManager.getCachedSeriesDetailV2(id)?.let { id to it }
                }.toMap()
            }
        val seriesNames = mutableMapOf<Int, String>()
        val volumeNames = mutableMapOf<Int, String>()
        val chapterTitles = mutableMapOf<Int, String>()
        details.forEach { (seriesId, detail) ->
            val name =
                detail.series
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: detail.series
                        ?.localizedName
                        ?.takeIf { it.isNotBlank() }
            if (name != null) {
                seriesNames[seriesId] = name
            }
            val detailDto = detail.detail
            detailDto
                ?.volumes
                ?.forEach { volume ->
                    volumeNames[volume.id] = volumeLabel(volume)
                    volume.chapters?.forEach { chapter ->
                        chapterTitles[chapter.id] = chapterLabel(chapter)
                    }
                }
            detailDto
                ?.chapters
                ?.forEach { chapter ->
                    chapterTitles.putIfAbsent(chapter.id, chapterLabel(chapter))
                }
            detailDto
                ?.specials
                ?.forEach { chapter ->
                    chapterTitles.putIfAbsent(chapter.id, chapterLabel(chapter))
                }
            detailDto
                ?.storylineChapters
                ?.forEach { chapter ->
                    chapterTitles.putIfAbsent(chapter.id, chapterLabel(chapter))
                }
        }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadQueue",
                "Lookup updated series=${seriesNames.size} volumes=${volumeNames.size} chapters=${chapterTitles.size}",
            )
        }
        _lookup.update {
            DownloadLookup(
                seriesNames = seriesNames,
                volumeNames = volumeNames,
                chapterTitles = chapterTitles,
            )
        }
    }

    private fun volumeLabel(volume: VolumeDto): String {
        val name =
            volume.name?.takeIf { it.isNotBlank() }
                ?: volume.title?.takeIf { it.isNotBlank() }
        if (name != null) return name
        val number =
            volume.number
                ?: volume.minNumber?.takeIf { it > 0 }?.toInt()
        return number?.let { "Vol. $it" } ?: "Volume ${volume.id}"
    }

    private fun chapterLabel(chapter: net.dom53.inkita.data.api.dto.ChapterDto): String =
        chapter.titleName?.takeIf { it.isNotBlank() }
            ?: chapter.title?.takeIf { it.isNotBlank() }
            ?: chapter.range?.takeIf { it.isNotBlank() }
            ?: "Chapter ${chapter.id}"
}

data class DownloadLookup(
    val seriesNames: Map<Int, String> = emptyMap(),
    val volumeNames: Map<Int, String> = emptyMap(),
    val chapterTitles: Map<Int, String> = emptyMap(),
)

/**
 * Jednoduchý factory pro manuální vytvoření (bez DI).
 */
object DownloadQueueViewModelFactory {
    fun create(
        context: android.content.Context,
        cacheManager: CacheManager,
    ): DownloadQueueViewModel {
        val db =
            net.dom53.inkita.data.local.db.InkitaDatabase
                .getInstance(context)
        return DownloadQueueViewModel(db.downloadV2Dao(), cacheManager)
    }
}
