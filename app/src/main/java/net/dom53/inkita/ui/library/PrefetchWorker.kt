package net.dom53.inkita.ui.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.notification.AppNotificationManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.CollectionsRepositoryImpl
import net.dom53.inkita.data.repository.SeriesRepositoryImpl
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.filter.FilterClause
import net.dom53.inkita.domain.model.filter.KavitaCombination
import net.dom53.inkita.domain.model.filter.KavitaComparison
import net.dom53.inkita.domain.model.filter.KavitaField
import net.dom53.inkita.domain.model.filter.KavitaSortField
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
import net.dom53.inkita.domain.model.library.LibraryTabType
import java.io.File

class PrefetchWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val notificationId = 4001
    private val tag = "PrefetchWorker"

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        AppNotificationManager.init(applicationContext)
        val monitor = NetworkMonitor.getInstance(applicationContext, prefs)
        val status = monitor.status.value
        if (!status.isOnlineAllowed) {
            LoggingManager.d(tag, "Offline or offline-mode, skipping prefetch run (offlineMode=${status.offlineMode})")
            return if (status.offlineMode) Result.success() else Result.retry()
        }
        LoggingManager.d(tag, "Starting prefetch worker")
        return runCatching {
            val cachePolicy =
                CacheManagerImpl(
                    prefs,
                    null,
                    null,
                ).policy()
            if (!cachePolicy.globalEnabled || !cachePolicy.libraryEnabled) {
                LoggingManager.d(tag, "Cache policy disabled, ending work")
                return Result.success()
            }

            val prefetchInProgress = prefs.prefetchInProgressFlow.first()
            val prefetchWant = prefs.prefetchWantFlow.first()
            val prefetchCollections = prefs.prefetchCollectionsFlow.first()
            val prefetchCollectionsAll = prefs.prefetchCollectionsAllFlow.first()
            val selectedCollectionIds = prefs.prefetchCollectionIdsFlow.first()
            val prefetchDetails = prefs.prefetchDetailsFlow.first()
            if (!prefetchInProgress && !prefetchWant && !prefetchCollections) {
                LoggingManager.d(tag, "No prefetch targets enabled, finishing")
                return Result.success()
            }
            LoggingManager.d(tag, "Flags inProgress=$prefetchInProgress want=$prefetchWant collections=$prefetchCollections details=$prefetchDetails")
            LoggingManager.d(
                tag,
                "Constraints met? metered=${status.isMetered} offlineAllowed=${status.isOnlineAllowed}",
            )

            val config = prefs.configFlow.first()
            if (!config.isConfigured) {
                LoggingManager.d(tag, "Config missing, finishing")
                return Result.success()
            }

            val db = InkitaDatabase.getInstance(applicationContext)
            val cacheManager =
                CacheManagerImpl(
                    prefs,
                    db.seriesDao(),
                    File(applicationContext.filesDir, "thumbnails"),
                    applicationContext.getDatabasePath("inkita.db"),
                )
            val seriesRepo = SeriesRepositoryImpl(applicationContext, prefs, cacheManager)
            val collectionsRepo = CollectionsRepositoryImpl(applicationContext, prefs)

            val targetCollections =
                if (prefetchCollections) {
                    val allCollections = runCatching { collectionsRepo.getCollections() }.getOrDefault(emptyList())
                    if (prefetchCollectionsAll) {
                        allCollections
                    } else {
                        val filtered = allCollections.filter { selectedCollectionIds.contains(it.id) }
                        if (filtered.isEmpty()) {
                            LoggingManager.d(tag, "No selected collections found, skipping collection prefetch")
                        }
                        filtered
                    }
                } else {
                    emptyList()
                }
            val tasks =
                buildList {
                    if (prefetchInProgress) add(0)
                    if (prefetchWant) add(1)
                    if (targetCollections.isNotEmpty()) {
                        targetCollections.forEachIndexed { idx, _ -> add(idx + 2) }
                    }
                }
            LoggingManager.d(tag, "Total prefetch tasks=${tasks.size}")
            if (tasks.isEmpty()) {
                LoggingManager.d(tag, "No tasks to run, finishing")
                return Result.success()
            }

            startForeground("Caching offline data")
            var completed = 0
            tasks.forEach { tabIndex ->
                runCatching {
                    prefetchTab(tabIndex, targetCollections, seriesRepo, collectionsRepo, cacheManager, prefetchDetails)
                    LoggingManager.d(tag, "Prefetched tab $tabIndex")
                }.onFailure { e ->
                    LoggingManager.e(tag, "Prefetch tab $tabIndex failed", e)
                }
                completed += 1
                val progressText = applicationContext.getString(R.string.library_prefetch_progress, completed, tasks.size)
                startForeground(progressText)
            }

            stopForeground()
            Result.success()
        }.getOrElse { e ->
            stopForeground()
            LoggingManager.e(tag, "Prefetch worker failed", e)
            Result.failure()
        }
    }

    private suspend fun prefetchTab(
        tabIndex: Int,
        collections: List<net.dom53.inkita.domain.model.Collection>,
        seriesRepo: SeriesRepositoryImpl,
        collectionsRepo: CollectionsRepositoryImpl,
        cacheManager: CacheManagerImpl,
        prefetchDetails: Boolean,
    ) {
        val allowedFormats = setOf(Format.Epub, Format.Pdf)

        fun tabKey(): LibraryTabCacheKey? =
            when (tabIndex) {
                0 -> LibraryTabCacheKey(LibraryTabType.InProgress, null)
                1 -> LibraryTabCacheKey(LibraryTabType.WantToRead, null)
                else -> {
                    val collection = collections.getOrNull(tabIndex - 2) ?: return null
                    LibraryTabCacheKey(LibraryTabType.Collection, collection.id)
                }
            }

        var page = 1
        val collected = mutableListOf<Series>()
        while (true) {
            val pageData = fetchSeriesPage(tabIndex, page, collections, seriesRepo, collectionsRepo)
            if (pageData.isEmpty()) break
            val filtered = pageData.filter { it.format in allowedFormats }
            collected += filtered
            if (prefetchDetails) {
                filtered.forEach { runCatching { seriesRepo.getSeriesDetail(it.id) } }
            }
            page++
        }
        val key = tabKey() ?: return
        if (collected.isNotEmpty()) {
            cacheManager.cacheTabResults(key, collected.distinctBy { it.id })
        }
    }

    private suspend fun fetchSeriesPage(
        selectedTabIndex: Int,
        page: Int,
        collections: List<net.dom53.inkita.domain.model.Collection>,
        seriesRepo: SeriesRepositoryImpl,
        collectionsRepo: CollectionsRepositoryImpl,
        pageSize: Int = 50,
    ): List<Series> {
        return when (selectedTabIndex) {
            0 -> {
                val baseClauses =
                    listOf(
                        FilterClause(
                            field = KavitaField.ReadingProgress,
                            comparison = KavitaComparison.GreaterThan,
                            value = "0",
                        ),
                        FilterClause(
                            field = KavitaField.ReadingProgress,
                            comparison = KavitaComparison.LessThan,
                            value = "100",
                        ),
                    )
                loadSeriesWithFormats(baseClauses, page, pageSize, seriesRepo)
            }
            1 -> {
                val baseClauses =
                    listOf(
                        FilterClause(
                            field = KavitaField.WantToRead,
                            comparison = KavitaComparison.Equal,
                            value = "true",
                        ),
                    )
                loadSeriesWithFormats(baseClauses, page, pageSize, seriesRepo)
            }
            else -> {
                val collection = collections.getOrNull(selectedTabIndex - 2) ?: return emptyList()
                collectionsRepo.getSeriesForCollection(collection.id, page, pageSize)
            }
        }
    }

    private suspend fun loadSeriesWithFormats(
        baseClauses: List<FilterClause>,
        page: Int,
        pageSize: Int,
        seriesRepo: SeriesRepositoryImpl,
    ): List<Series> {
        val formats = listOf(Format.Epub, Format.Pdf)
        return formats
            .flatMap { format ->
                val query =
                    net.dom53.inkita.domain.model.filter.SeriesQuery(
                        clauses =
                            baseClauses +
                                FilterClause(
                                    field = KavitaField.Formats,
                                    comparison = KavitaComparison.Equal,
                                    value = format.id.toString(),
                                ),
                        combination = KavitaCombination.MatchAll,
                        sortField = KavitaSortField.SortName,
                        page = page,
                        pageSize = pageSize,
                    )
                seriesRepo.getSeries(query)
            }.distinctBy { it.id }
    }

    private fun startForeground(text: String) {
        if (!AppNotificationManager.canPostNotifications()) {
            LoggingManager.w(tag, "Notification permission missing; running prefetch without foreground notification")
            return
        }
        val notif =
            AppNotificationManager.buildForegroundNotification(
                channel = AppNotificationManager.CHANNEL_PREFETCH,
                title = applicationContext.getString(R.string.library_prefetch_title),
                text = text,
            )
        val fgInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                androidx.work.ForegroundInfo(
                    notificationId,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                androidx.work.ForegroundInfo(notificationId, notif)
            }
        setForegroundAsync(fgInfo)
    }

    private fun stopForeground() {
        AppNotificationManager.cancel(notificationId)
    }

    companion object {
        fun enqueue(context: Context) {
            LoggingManager.d("PrefetchWorker", "enqueue called")
            val prefs = AppPreferences(context)
            val monitor = NetworkMonitor.getInstance(context, prefs)
            val status = monitor.status.value
            if (!status.isOnlineAllowed) {
                LoggingManager.d("PrefetchWorker", "Skipping enqueue, offline/offlineMode=${status.offlineMode}")
                return
            }
            val allowMetered = prefs.prefetchAllowMeteredFlow.firstBlocking()
            val allowLowBattery = prefs.prefetchAllowLowBatteryFlow.firstBlocking()
            val constraints =
                monitor.buildConstraints(
                    allowMetered = allowMetered,
                    requireBatteryNotLow = !allowLowBattery,
                )
            LoggingManager.d(
                "PrefetchWorker",
                "Constraints: network=${constraints.requiredNetworkType} batteryNotLow=${constraints.requiresBatteryNotLow()}",
            )
            val work =
                OneTimeWorkRequestBuilder<PrefetchWorker>()
                    .setConstraints(constraints)
                    .build()
            LoggingManager.d("PrefetchWorker", "enqueueUniqueWork inkita_prefetch")
            WorkManager.getInstance(context).enqueueUniqueWork("inkita_prefetch", ExistingWorkPolicy.REPLACE, work)
        }
    }
}

// Simple blocking helper for enqueue-time preference read
private fun <T> kotlinx.coroutines.flow.Flow<T>.firstBlocking(): T = kotlinx.coroutines.runBlocking { first() }
