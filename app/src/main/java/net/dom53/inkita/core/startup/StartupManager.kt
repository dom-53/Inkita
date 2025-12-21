package net.dom53.inkita.core.startup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.notification.AppNotificationManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.sync.ProgressSyncWorker
import net.dom53.inkita.core.update.UpdateChecker
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.AuthRepositoryImpl
import net.dom53.inkita.data.repository.CollectionsRepositoryImpl
import net.dom53.inkita.data.repository.LibraryRepositoryImpl
import net.dom53.inkita.data.repository.PersonRepositoryImpl
import net.dom53.inkita.data.repository.ReaderRepositoryImpl
import net.dom53.inkita.data.repository.ReadingListRepositoryImpl
import net.dom53.inkita.data.repository.SeriesRepositoryImpl
import net.dom53.inkita.domain.repository.AuthRepository
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.LibraryRepository
import net.dom53.inkita.domain.repository.PersonRepository
import net.dom53.inkita.domain.repository.ReaderRepository
import net.dom53.inkita.domain.repository.ReadingListRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import java.io.File

/**
 * Central place to build and expose app-wide dependencies and to trigger startup jobs.
 */
object StartupManager {
    data class Components(
        val preferences: AppPreferences,
        val cacheManager: CacheManager,
        val libraryRepository: LibraryRepository,
        val seriesRepository: SeriesRepository,
        val collectionsRepository: CollectionsRepository,
        val readingListRepository: ReadingListRepository,
        val personRepository: PersonRepository,
        val authRepository: AuthRepository,
        val readerRepository: ReaderRepository,
    )

    @Volatile
    private var cached: Components? = null
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initialise singletons used across the app. Safe to call multiple times; returns cached instance.
     */
    @Suppress("MaxLineLength")
    fun init(context: Context): Components {
        cached?.let { return it }

        val appContext = context.applicationContext
        val preferences = AppPreferences(appContext)
        LoggingManager.init(appContext)
        // Migrate legacy API key storage into encrypted prefs.
        runCatching { kotlinx.coroutines.runBlocking { preferences.migrateSensitiveIfNeeded() } }
        AppNotificationManager.init(appContext)
        val database = InkitaDatabase.getInstance(appContext)
        val thumbsDir = File(appContext.filesDir, "thumbnails")
        val cacheManager =
            CacheManagerImpl(
                preferences,
                database.seriesDao(),
                database.libraryV2Dao(),
                thumbsDir,
                appContext.getDatabasePath("inkita.db"),
            )
        val libraryRepository: LibraryRepository = LibraryRepositoryImpl(appContext, preferences)
        val seriesRepository: SeriesRepository = SeriesRepositoryImpl(appContext, preferences, cacheManager)
        val collectionsRepository: CollectionsRepository = CollectionsRepositoryImpl(appContext, preferences)
        val readingListRepository: ReadingListRepository = ReadingListRepositoryImpl(appContext, preferences)
        val personRepository: PersonRepository = PersonRepositoryImpl(appContext, preferences)
        val authRepository: AuthRepository = AuthRepositoryImpl(preferences)
        val networkMonitor = NetworkMonitor.getInstance(appContext, preferences)
        val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        LoggingManager.setErrorsEnabled(true)
        // Log uncaught exceptions to file/logcat for post-mortem debugging.
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            LoggingManager.e("Uncaught", "Thread=${thread.name}", ex)
            prevHandler?.uncaughtException(thread, ex)
        }
        preferences.configFlow
            .onEach { config ->
                val host =
                    kotlin
                        .runCatching {
                            val raw = config.serverUrl
                            val parsed = java.net.URI(if (raw.startsWith("http")) raw else "https://$raw")
                            parsed.host ?: raw
                        }.getOrDefault("")
                LoggingManager.updateHostMask(host)
            }.launchIn(logScope)
        preferences.verboseLoggingFlow
            .onEach { enabled ->
                LoggingManager.setEnabled(enabled || (isDebuggable && Debug.isDebuggerConnected()))
            }.launchIn(logScope)
        networkMonitor.status
            .onEach { status ->
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d(
                        "NetworkMonitor",
                        "online=${status.isOnline} allowed=${status.isOnlineAllowed} type=${status.connectionType} metered=${status.isMetered} roaming=${status.isRoaming} offlineMode=${status.offlineMode}",
                    )
                }
            }.launchIn(logScope)
        val readerRepository: ReaderRepository =
            ReaderRepositoryImpl(
                appContext,
                preferences,
                database.readerDao(),
                database.downloadDao(),
            )

        // Kick off sync of offline reader progress; worker will only run when online.
        ProgressSyncWorker.enqueue(appContext)
        // Check for app updates and post a notification if available (best-effort).
        appScope.launch { UpdateChecker.checkForUpdate(appContext) }

        val components =
            Components(
                preferences = preferences,
                cacheManager = cacheManager,
                libraryRepository = libraryRepository,
                seriesRepository = seriesRepository,
                collectionsRepository = collectionsRepository,
                readingListRepository = readingListRepository,
                personRepository = personRepository,
                authRepository = authRepository,
                readerRepository = readerRepository,
            )
        cached = components
        return components
    }

    /**
     * Hook for foreground transitions to reschedule important one-off jobs.
     */
    fun onResume(context: Context) {
        ProgressSyncWorker.enqueue(context.applicationContext)
    }
}
