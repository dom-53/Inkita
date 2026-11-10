package net.dom53.inkita.core.startup

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.di.InkitaServices
import net.dom53.inkita.core.downloadv2.DownloadManagerV2
import net.dom53.inkita.core.downloadv2.DownloadPaths
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
    private var cachedPreferences: AppPreferences? = null
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Initialise singletons used across the app. Safe to call multiple times; returns cached instance.
     */
    @Suppress("MaxLineLength")
    @Synchronized
    fun init(context: Context): AppPreferences {
        cachedPreferences?.let { return it }

        val appContext = context.applicationContext
        val preferences = AppPreferences(appContext)
        LoggingManager.init(appContext)
        // Migrate legacy API key storage into encrypted prefs.
        runCatching { kotlinx.coroutines.runBlocking { preferences.migrateSensitiveIfNeeded() } }
        AppNotificationManager.init(appContext)
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
        combine(networkMonitor.status, preferences.activeSessionFlow, ::Pair)
            .onEach { (status, session) ->
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d(
                        "NetworkMonitor",
                        "online=${status.isOnline} allowed=${status.isOnlineAllowed} type=${status.connectionType} metered=${status.isMetered} roaming=${status.isRoaming} offlineMode=${status.offlineMode}",
                    )
                }
                if (status.isOnlineAllowed && session.config.isConfigured) {
                    ProgressSyncWorker.enqueue(appContext, session.profile)
                    if (InkitaDatabase.getInstance(appContext, session.profile).downloadV2Dao().hasUnfinishedJobs()) {
                        DownloadManagerV2.enqueueWorker(AppPreferences(appContext, session))
                    }
                }
            }.launchIn(logScope)
        // Check for app updates and post a notification if available (best-effort).
        appScope.launch {
            val hasPermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            if (hasPermission) {
                runCatching { UpdateChecker.checkForUpdate(appContext) }
                    .onFailure { e ->
                        if (LoggingManager.isDebugEnabled()) {
                            LoggingManager.w("UpdateChecker", "Missing notification permission", e)
                        }
                    }
            } else if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("UpdateChecker", "Skipping update check: notifications permission not granted")
            }
        }
        appScope.launch(Dispatchers.IO) {
            val tempDir = DownloadPaths.pdfTempDir(appContext)
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d(
                        "PdfReader",
                        "Cleared temp PDF cache on startup: ${tempDir.absolutePath}",
                    )
                }
            }
        }

        cachedPreferences = preferences
        return preferences
    }

    fun components(preferences: AppPreferences): Components {
        val context = preferences.appContext
        val profile = preferences.profile
        val database = InkitaDatabase.getInstance(context, profile)
        val cacheManager =
            CacheManagerImpl(
                preferences,
                database.libraryV2Dao(),
                database.seriesDetailV2Dao(),
                File(context.filesDir, profile.thumbnailFolder),
                context.getDatabasePath(profile.databaseName),
            )
        return Components(
            preferences = preferences,
            cacheManager = cacheManager,
            libraryRepository = LibraryRepositoryImpl(context, preferences),
            seriesRepository = SeriesRepositoryImpl(context, preferences, cacheManager),
            collectionsRepository = CollectionsRepositoryImpl(context, preferences),
            readingListRepository = ReadingListRepositoryImpl(context, preferences),
            personRepository = PersonRepositoryImpl(context, preferences),
            authRepository = AuthRepositoryImpl(preferences),
            readerRepository = InkitaServices.get(context, preferences).readerRepository,
        )
    }

    /**
     * Hook for foreground transitions to reschedule important one-off jobs.
     */
    fun onResume(context: Context) {
        val appContext = context.applicationContext
        val preferences = init(appContext)
        appScope.launch {
            val session = preferences.activeSessionFlow.first()
            if (session.config.isConfigured) ProgressSyncWorker.enqueue(appContext, session.profile)
        }
    }
}
