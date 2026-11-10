package net.dom53.inkita.core.storage

import android.content.Context
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.prefetch.PrefetchPolicy
import net.dom53.inkita.domain.model.Collection
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "inkita_prefs")

enum class AppTheme { System, Light, Dark }

enum class ReaderThemeMode { Light, Dark, DarkHighContrast, Sepia, SepiaHighContrast, Gray }

enum class ImageReaderMode { LeftToRight, RightToLeft, Vertical, Webtoon }

data class AppConfig(
    val serverUrl: String,
    val apiKey: String,
    val imageApiKey: String,
    val userId: Int,
    val caCertificate: String? = null,
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
}

data class ReaderPrefs(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.4f,
    val paddingDp: Float = 5f,
    val textAlign: TextAlign = TextAlign.Justify,
    val useSerif: Boolean = true,
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    val readerTheme: ReaderThemeMode = ReaderThemeMode.Light,
    val imageReaderMode: ImageReaderMode = ImageReaderMode.LeftToRight,
    val imagePrefetchPages: Int = 8,
    val pdfRightToLeft: Boolean = false,
) {
    companion object {
        const val DEFAULT_FONT_FAMILY = "literata"
        const val DEFAULT_IMAGE_PREFETCH_PAGES = 8
    }
}

@Suppress("TooManyFunctions")
class AppPreferences(
    private val context: Context,
    private val session: KavitaSession? = null,
) {
    val appContext: Context
        get() = context.applicationContext
    private val secureStorage by lazy { SecureStorage(appContext) }
    val profile: KavitaProfile = session?.profile ?: KavitaProfile.Guest

    companion object {
        private val authMutex = Mutex()
        private val KEY_CREDENTIALS_REF = stringPreferencesKey("kavita_credentials_ref")
        private val KEY_LEGACY_PROFILE = stringPreferencesKey("legacy_kavita_profile")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_KAVITA_CA = stringPreferencesKey("kavita_ca")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val LEGACY_KEY_TOKEN = stringPreferencesKey("token")
        private val LEGACY_KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")

        private val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        private val KEY_PADDING = floatPreferencesKey("reader_padding")
        private val KEY_TEXT_ALIGN = stringPreferencesKey("reader_text_align")
        private val KEY_USE_SERIF = booleanPreferencesKey("reader_use_serif")
        private val KEY_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        private val KEY_READER_THEME = stringPreferencesKey("reader_theme")
        private val KEY_IMAGE_READER_MODE = stringPreferencesKey("image_reader_mode")
        private val KEY_IMAGE_PREFETCH_PAGES = intPreferencesKey("image_prefetch_pages")
        private val KEY_PDF_RIGHT_TO_LEFT = booleanPreferencesKey("pdf_right_to_left")

        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_APP_THEME = stringPreferencesKey("app_theme")
        private val KEY_CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
        private val KEY_LIBRARY_CACHE_ENABLED = booleanPreferencesKey("library_cache_enabled")
        private val KEY_BROWSE_CACHE_ENABLED = booleanPreferencesKey("browse_cache_enabled")
        private val KEY_CACHE_ALWAYS_REFRESH = booleanPreferencesKey("cache_always_refresh")
        private val KEY_CACHE_STALE_HOURS = intPreferencesKey("cache_stale_hours")
        private val KEY_CACHE_STALE_MINUTES = intPreferencesKey("cache_stale_minutes")
        private val KEY_DEBUG_TOASTS = booleanPreferencesKey("debug_toasts")
        private val KEY_LIBRARY_CACHE_HOME = booleanPreferencesKey("library_cache_home")
        private val KEY_LIBRARY_CACHE_WANT = booleanPreferencesKey("library_cache_want")
        private val KEY_LIBRARY_CACHE_COLLECTIONS = booleanPreferencesKey("library_cache_collections")
        private val KEY_LIBRARY_CACHE_READING_LISTS = booleanPreferencesKey("library_cache_reading_lists")
        private val KEY_LIBRARY_CACHE_BROWSE_PEOPLE = booleanPreferencesKey("library_cache_browse_people")
        private val KEY_LIBRARY_CACHE_DETAILS = booleanPreferencesKey("library_cache_details")
        private val KEY_NOTIF_PROMPT_SHOWN = booleanPreferencesKey("notifications_prompt_shown")
        private val KEY_CACHE_REFRESH_TTL_MIN = intPreferencesKey("cache_refresh_ttl_min")
        private val KEY_LAST_LIBRARY_REFRESH = longPreferencesKey("last_library_refresh")
        private val KEY_LAST_BROWSE_REFRESH = longPreferencesKey("last_browse_refresh")
        private val KEY_CACHED_COLLECTIONS = stringPreferencesKey("cached_collections")
        private val KEY_PREFETCH_IN_PROGRESS = booleanPreferencesKey("prefetch_in_progress")
        private val KEY_PREFETCH_WANT = booleanPreferencesKey("prefetch_want")
        private val KEY_PREFETCH_COLLECTIONS = booleanPreferencesKey("prefetch_collections")
        private val KEY_PREFETCH_DETAILS = booleanPreferencesKey("prefetch_details")
        private val KEY_PREFETCH_ALLOW_METERED = booleanPreferencesKey("prefetch_allow_metered")
        private val KEY_PREFETCH_ALLOW_LOW_BATTERY = booleanPreferencesKey("prefetch_allow_low_battery")
        private val KEY_PREFETCH_COLLECTIONS_ALL = booleanPreferencesKey("prefetch_collections_all")
        private val KEY_PREFETCH_COLLECTION_IDS = stringPreferencesKey("prefetch_collection_ids")
        private val KEY_DOWNLOAD_ALLOW_METERED = booleanPreferencesKey("download_allow_metered")
        private val KEY_DOWNLOAD_ALLOW_LOW_BATTERY = booleanPreferencesKey("download_allow_low_battery")
        private val KEY_DOWNLOAD_MAX_CONCURRENT = intPreferencesKey("download_max_concurrent")
        private val KEY_DOWNLOAD_LOCATION_URI = stringPreferencesKey("download_location_uri")
        private val KEY_PREFER_OFFLINE_PAGES = booleanPreferencesKey("prefer_offline_pages")
        private val KEY_DOWNLOAD_RETRY_ENABLED = booleanPreferencesKey("download_retry_enabled")
        private val KEY_DOWNLOAD_RETRY_MAX = intPreferencesKey("download_retry_max")
        private val KEY_IMPORTANT_INFO_VERSION = intPreferencesKey("important_info_version")
        private val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        private val KEY_DELETE_AFTER_MARK_READ = booleanPreferencesKey("delete_after_mark_read")
        private val KEY_DELETE_AFTER_READ_DEPTH = intPreferencesKey("delete_after_read_depth")
        private val KEY_VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
        private val KEY_BROWSE_PAGE_SIZE = intPreferencesKey("browse_page_size")
        private val KEY_MAX_THUMBNAILS_PARALLEL = intPreferencesKey("max_thumbnails_parallel")
        private val KEY_DISABLE_BROWSE_THUMBNAILS = booleanPreferencesKey("disable_browse_thumbnails")
        private val KEY_SHOW_DOWNLOAD_BADGES = booleanPreferencesKey("show_download_badges")

        private const val REFRESH_CACHE_DEFAULT = 720
        private const val STALE_CACHE_DEFAULT_MIN = 15

        private const val DEFAULT_MAX_RETRY_ATTEMPT = 3
    }

    val activeSessionFlow: Flow<KavitaSession> =
        context.dataStore.data
            .map { authMutex.withLock { readSession(context.dataStore.data.first()) } }
            .distinctUntilChanged()

    val configFlow: Flow<AppConfig> = session?.let { flowOf(it.config) } ?: activeSessionFlow.map { it.config }

    suspend fun isActiveSession(): Boolean = session == null || activeSessionFlow.first() == session

    private fun readConfig(prefs: Preferences): AppConfig {
        val reference = prefs[KEY_CREDENTIALS_REF]
        val legacyKey = if (reference == null) prefs[KEY_API_KEY].orEmpty() else ""
        val apiKey = secureStorage.getApiKey(reference).ifBlank { legacyKey }
        return AppConfig(
            serverUrl = prefs[KEY_SERVER_URL].orEmpty(),
            apiKey = apiKey,
            imageApiKey = secureStorage.getImageApiKey(reference).ifBlank { apiKey },
            userId = prefs[KEY_USER_ID]?.toIntOrNull() ?: 0,
            caCertificate = prefs[KEY_KAVITA_CA],
        )
    }

    private fun readSession(prefs: Preferences): KavitaSession {
        val config = readConfig(prefs)
        if (!config.isConfigured) return KavitaSession(config, KavitaProfile.Guest)
        val id = config.profileId()
        val owner = prefs[KEY_LEGACY_PROFILE]
        val usesLegacyStorage = owner == id || (owner == null && prefs[KEY_CREDENTIALS_REF] == null)
        return KavitaSession(config, if (usesLegacyStorage) KavitaProfile.Legacy else KavitaProfile(id))
    }

    val readerPrefsFlow: Flow<ReaderPrefs> =
        context.dataStore.data.map { prefs ->
            ReaderPrefs(
                fontSize = prefs[KEY_FONT_SIZE] ?: 18f,
                lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.4f,
                paddingDp = prefs[KEY_PADDING] ?: 5f,
                textAlign = prefs.toTextAlign(),
                useSerif = prefs[KEY_USE_SERIF] ?: true,
                fontFamily =
                    prefs[KEY_FONT_FAMILY]
                        ?: if ((prefs[KEY_USE_SERIF] ?: true)) ReaderPrefs.DEFAULT_FONT_FAMILY else "noto_sans",
                readerTheme = prefs.toReaderTheme(),
                imageReaderMode = prefs.toImageReaderMode(),
                imagePrefetchPages = prefs[KEY_IMAGE_PREFETCH_PAGES] ?: ReaderPrefs.DEFAULT_IMAGE_PREFETCH_PAGES,
                pdfRightToLeft = prefs[KEY_PDF_RIGHT_TO_LEFT] ?: false,
            )
        }

    val appLanguageFlow: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[KEY_APP_LANGUAGE] ?: "system" }

    val notificationsPromptShownFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_NOTIF_PROMPT_SHOWN] ?: false }

    val appThemeFlow: Flow<AppTheme> =
        context.dataStore.data.map { prefs ->
            when (prefs[KEY_APP_THEME]) {
                "light" -> AppTheme.Light
                "dark" -> AppTheme.Dark
                else -> AppTheme.System
            }
        }

    val cacheEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_CACHE_ENABLED] ?: true }
    val libraryCacheEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LIBRARY_CACHE_ENABLED] ?: true }
    val browseCacheEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_BROWSE_CACHE_ENABLED] ?: false }
    val cacheAlwaysRefreshFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_CACHE_ALWAYS_REFRESH] ?: true }
    val cacheStaleMinutesFlow: Flow<Int> =
        context.dataStore.data.map { prefs ->
            val minutes =
                prefs[KEY_CACHE_STALE_MINUTES]
                    ?: if (prefs.contains(KEY_CACHE_STALE_HOURS)) {
                        (prefs[KEY_CACHE_STALE_HOURS] ?: 1) * 60
                    } else {
                        STALE_CACHE_DEFAULT_MIN
                    }
            minutes.coerceIn(1, 10080)
        }
    val debugToastsFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_DEBUG_TOASTS] ?: false }
    val libraryCacheHomeFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LIBRARY_CACHE_HOME] ?: true }
    val libraryCacheWantToReadFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LIBRARY_CACHE_WANT] ?: true }
    val libraryCacheCollectionsFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LIBRARY_CACHE_COLLECTIONS] ?: true }
    val libraryCacheReadingListsFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LIBRARY_CACHE_READING_LISTS] ?: false }
    val libraryCacheBrowsePeopleFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LIBRARY_CACHE_BROWSE_PEOPLE] ?: false }
    val libraryCacheDetailsFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_LIBRARY_CACHE_DETAILS] ?: true }
    val offlineModeFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_OFFLINE_MODE] ?: false }
    val cacheRefreshTtlMinutesFlow: Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[KEY_CACHE_REFRESH_TTL_MIN] ?: REFRESH_CACHE_DEFAULT }
    val lastLibraryRefreshFlow: Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[KEY_LAST_LIBRARY_REFRESH] ?: 0L }
    val lastBrowseRefreshFlow: Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[KEY_LAST_BROWSE_REFRESH] ?: 0L }
    val prefetchInProgressFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFETCH_IN_PROGRESS] ?: true }
    val prefetchWantFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFETCH_WANT] ?: false }
    val prefetchCollectionsFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFETCH_COLLECTIONS] ?: false }
    val prefetchDetailsFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFETCH_DETAILS] ?: false }
    val prefetchAllowMeteredFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFETCH_ALLOW_METERED] ?: false }
    val prefetchAllowLowBatteryFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFETCH_ALLOW_LOW_BATTERY] ?: false }
    val downloadAllowMeteredFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_DOWNLOAD_ALLOW_METERED] ?: false }
    val downloadAllowLowBatteryFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_DOWNLOAD_ALLOW_LOW_BATTERY] ?: false }
    val downloadMaxConcurrentFlow: Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[KEY_DOWNLOAD_MAX_CONCURRENT] ?: 2 }
    private val downloadLocationKey =
        if (profile == KavitaProfile.Legacy) KEY_DOWNLOAD_LOCATION_URI else stringPreferencesKey("download_location_${profile.id}")

    val downloadLocationUriFlow: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[downloadLocationKey]?.takeIf { it.isNotBlank() } }
    val preferOfflinePagesFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFER_OFFLINE_PAGES] ?: true }
    val importantInfoVersionFlow: Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[KEY_IMPORTANT_INFO_VERSION] ?: 0 }
    val downloadRetryEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_DOWNLOAD_RETRY_ENABLED] ?: true }
    val downloadRetryMaxAttemptsFlow: Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[KEY_DOWNLOAD_RETRY_MAX] ?: DEFAULT_MAX_RETRY_ATTEMPT }
    val deleteAfterMarkReadFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_DELETE_AFTER_MARK_READ] ?: false }
    val deleteAfterReadDepthFlow: Flow<Int> =
        context.dataStore.data.map { prefs -> (prefs[KEY_DELETE_AFTER_READ_DEPTH] ?: 1).coerceIn(1, 5) }
    val verboseLoggingFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_VERBOSE_LOGGING] ?: false }
    val browsePageSizeFlow: Flow<Int> =
        context.dataStore.data.map { prefs -> (prefs[KEY_BROWSE_PAGE_SIZE] ?: 25).coerceIn(10, 50) }
    val maxThumbnailsParallelFlow: Flow<Int> =
        context.dataStore.data.map { prefs -> (prefs[KEY_MAX_THUMBNAILS_PARALLEL] ?: 4).coerceIn(2, 6) }
    val disableBrowseThumbnailsFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_DISABLE_BROWSE_THUMBNAILS] ?: false }
    val showDownloadBadgesFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_SHOW_DOWNLOAD_BADGES] ?: true }

    val prefetchCollectionsAllFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEY_PREFETCH_COLLECTIONS_ALL] ?: true }

    val prefetchCollectionIdsFlow: Flow<List<Int>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_PREFETCH_COLLECTION_IDS]
                ?.split(',')
                ?.mapNotNull { it.toIntOrNull() }
                ?.filter { it > 0 }
                ?: emptyList()
        }

    suspend fun prefetchPolicy(): PrefetchPolicy =
        PrefetchPolicy(
            inProgressEnabled = prefetchInProgressFlow.first(),
            wantEnabled = prefetchWantFlow.first(),
            collectionsEnabled = prefetchCollectionsFlow.first(),
            detailsEnabled = prefetchDetailsFlow.first(),
            allowMetered = prefetchAllowMeteredFlow.first(),
            allowLowBattery = prefetchAllowLowBatteryFlow.first(),
            collectionsAll = prefetchCollectionsAllFlow.first(),
            collectionIds = prefetchCollectionIdsFlow.first(),
        )

    suspend fun updateKavitaConfig(config: AppConfig) = changeKavitaConfig { config }

    suspend fun clearAuth(clearServer: Boolean = false) =
        changeKavitaConfig { current ->
            current.copy(
                serverUrl = if (clearServer) "" else current.serverUrl,
                apiKey = "",
                imageApiKey = "",
                userId = 0,
                caCertificate = if (clearServer) null else current.caCertificate,
            )
        }

    private suspend fun changeKavitaConfig(change: (AppConfig) -> AppConfig) {
        withContext(Dispatchers.IO) {
            authMutex.withLock {
                // Publish the matching credential reference and server in a single DataStore edit.
                withContext(NonCancellable) {
                    val prefs = context.dataStore.data.first()
                    val previousRef = prefs[KEY_CREDENTIALS_REF]
                    val previous = readConfig(prefs)
                    val config = change(previous)
                    val legacyOwner =
                        prefs[KEY_LEGACY_PROFILE]
                            ?: previous.takeIf { previousRef == null && it.isConfigured }?.profileId()
                    val verifiedLegacyOwner =
                        if (config.isConfigured && legacyOwner == config.profileId(useAccountId = false)) config.profileId() else legacyOwner
                    val reference = UUID.randomUUID().toString()
                    secureStorage.saveKavitaKeys(reference, config.apiKey, config.imageApiKey)
                    try {
                        context.dataStore.edit { next ->
                            next[KEY_CREDENTIALS_REF] = reference
                            next[KEY_SERVER_URL] = config.serverUrl
                            next[KEY_USER_ID] = config.userId.toString()
                            next.remove(KEY_API_KEY)
                            if (verifiedLegacyOwner != null) next[KEY_LEGACY_PROFILE] = verifiedLegacyOwner
                            val certificate = config.caCertificate
                            if (certificate == null) next.remove(KEY_KAVITA_CA) else next[KEY_KAVITA_CA] = certificate
                        }
                    } catch (e: Exception) {
                        secureStorage.removeKavitaKeys(reference)
                        throw e
                    }
                    secureStorage.removeKavitaKeys(previousRef)
                }
            }
        }
    }

    /**
     * One-time migration to move sensitive values from plain DataStore into encrypted prefs.
     */
    suspend fun migrateSensitiveIfNeeded() {
        val prefs = context.dataStore.data.first()
        secureStorage.clearLegacyTokens()
        val legacyApiKey = prefs[KEY_API_KEY] ?: ""
        val hasLegacyToken = prefs[LEGACY_KEY_TOKEN] != null
        val hasLegacyRefresh = prefs[LEGACY_KEY_REFRESH_TOKEN] != null
        val hasLegacyApi = prefs[KEY_API_KEY] != null

        val secureApiKey = secureStorage.getApiKey(null)

        if (secureApiKey.isBlank() && legacyApiKey.isNotBlank()) {
            secureStorage.setApiKey(legacyApiKey)
        }
        if (hasLegacyApi || hasLegacyToken || hasLegacyRefresh) {
            context.dataStore.edit { ds ->
                ds.remove(LEGACY_KEY_TOKEN)
                ds.remove(LEGACY_KEY_REFRESH_TOKEN)
                ds.remove(KEY_API_KEY)
            }
        }
    }

    suspend fun updateReaderPrefs(transform: ReaderPrefs.() -> ReaderPrefs) {
        context.dataStore.edit { prefs ->
            val current =
                ReaderPrefs(
                    fontSize = prefs[KEY_FONT_SIZE] ?: 18f,
                    lineHeight = prefs[KEY_LINE_HEIGHT] ?: 1.4f,
                    paddingDp = prefs[KEY_PADDING] ?: 5f,
                    textAlign = prefs.toTextAlign(),
                    useSerif = prefs[KEY_USE_SERIF] ?: true,
                    fontFamily =
                        prefs[KEY_FONT_FAMILY]
                            ?: if ((prefs[KEY_USE_SERIF] ?: true)) ReaderPrefs.DEFAULT_FONT_FAMILY else "noto_sans",
                    readerTheme = prefs.toReaderTheme(),
                    imageReaderMode = prefs.toImageReaderMode(),
                    imagePrefetchPages = prefs[KEY_IMAGE_PREFETCH_PAGES] ?: ReaderPrefs.DEFAULT_IMAGE_PREFETCH_PAGES,
                    pdfRightToLeft = prefs[KEY_PDF_RIGHT_TO_LEFT] ?: false,
                )
            val next = current.transform()
            prefs[KEY_FONT_SIZE] = next.fontSize
            prefs[KEY_LINE_HEIGHT] = next.lineHeight
            prefs[KEY_PADDING] = next.paddingDp
            prefs[KEY_TEXT_ALIGN] = encodeTextAlign(next.textAlign)
            prefs[KEY_USE_SERIF] = next.useSerif
            prefs[KEY_FONT_FAMILY] = next.fontFamily
            prefs[KEY_READER_THEME] = encodeReaderTheme(next.readerTheme)
            prefs[KEY_IMAGE_READER_MODE] = encodeImageReaderMode(next.imageReaderMode)
            prefs[KEY_IMAGE_PREFETCH_PAGES] = next.imagePrefetchPages.coerceAtLeast(0)
            prefs[KEY_PDF_RIGHT_TO_LEFT] = next.pdfRightToLeft
        }
    }

    suspend fun setAppLanguage(languageTag: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = languageTag
        }
    }

    suspend fun setNotificationsPromptShown(shown: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIF_PROMPT_SHOWN] = shown
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APP_THEME] =
                when (theme) {
                    AppTheme.System -> "system"
                    AppTheme.Light -> "light"
                    AppTheme.Dark -> "dark"
                }
        }
    }

    suspend fun setCacheEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHE_ENABLED] = enabled
        }
    }

    suspend fun setLibraryCacheEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIBRARY_CACHE_ENABLED] = enabled
        }
    }

    suspend fun setBrowseCacheEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BROWSE_CACHE_ENABLED] = enabled
        }
    }

    suspend fun setCacheAlwaysRefresh(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHE_ALWAYS_REFRESH] = enabled
        }
    }

    suspend fun setCacheStaleMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHE_STALE_MINUTES] = minutes.coerceIn(1, 10080)
        }
    }

    suspend fun setDebugToasts(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEBUG_TOASTS] = enabled
        }
    }

    suspend fun setLibraryCacheHomeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIBRARY_CACHE_HOME] = enabled
        }
    }

    suspend fun setLibraryCacheWantToReadEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIBRARY_CACHE_WANT] = enabled
        }
    }

    suspend fun setLibraryCacheCollectionsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIBRARY_CACHE_COLLECTIONS] = enabled
        }
    }

    suspend fun setLibraryCacheReadingListsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIBRARY_CACHE_READING_LISTS] = enabled
        }
    }

    suspend fun setLibraryCacheBrowsePeopleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIBRARY_CACHE_BROWSE_PEOPLE] = enabled
        }
    }

    suspend fun setLibraryCacheDetailsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LIBRARY_CACHE_DETAILS] = enabled
        }
    }

    suspend fun setCacheRefreshTtlMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CACHE_REFRESH_TTL_MIN] = minutes
        }
    }

    /**
     * Explicitly toggle offline mode. When true, the app behaves as offline even if connectivity is available
     * (NetworkMonitor reports isOnlineAllowed = false), so workers and network calls should defer/skip.
     */
    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OFFLINE_MODE] = enabled
        }
    }

    suspend fun setLastLibraryRefresh(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_LIBRARY_REFRESH] = timestamp
        }
    }

    suspend fun setLastBrowseRefresh(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_BROWSE_REFRESH] = timestamp
        }
    }

    suspend fun saveCollectionsCache(collections: List<Collection>) {
        context.dataStore.edit { prefs ->
            val encoded =
                collections.joinToString("||") { c ->
                    "${c.id}|${c.name.replace("|", " ")}"
                }
            prefs[KEY_CACHED_COLLECTIONS] = encoded
        }
    }

    suspend fun setPrefetchInProgress(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_IN_PROGRESS] = enabled
        }
    }

    suspend fun setPrefetchWant(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_WANT] = enabled
        }
    }

    suspend fun setPrefetchCollections(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_COLLECTIONS] = enabled
        }
    }

    suspend fun setPrefetchDetails(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_DETAILS] = enabled
        }
    }

    suspend fun setPrefetchAllowMetered(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_ALLOW_METERED] = enabled
        }
    }

    suspend fun setPrefetchAllowLowBattery(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_ALLOW_LOW_BATTERY] = enabled
        }
    }

    suspend fun setDownloadAllowMetered(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_ALLOW_METERED] = enabled
        }
    }

    suspend fun setDownloadAllowLowBattery(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_ALLOW_LOW_BATTERY] = enabled
        }
    }

    suspend fun setDownloadMaxConcurrent(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_MAX_CONCURRENT] = value
        }
    }

    suspend fun setDownloadLocationUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(downloadLocationKey)
            } else {
                prefs[downloadLocationKey] = uri
            }
        }
    }

    suspend fun setDownloadRetryEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_RETRY_ENABLED] = enabled
        }
    }

    suspend fun setDownloadRetryMaxAttempts(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_RETRY_MAX] = value
        }
    }

    suspend fun setDeleteAfterMarkRead(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DELETE_AFTER_MARK_READ] = enabled
        }
    }

    suspend fun setDeleteAfterReadDepth(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DELETE_AFTER_READ_DEPTH] = value.coerceIn(1, 5)
        }
    }

    suspend fun setPrefetchCollectionsAll(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_COLLECTIONS_ALL] = enabled
        }
    }

    suspend fun setPrefetchCollectionIds(ids: List<Int>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFETCH_COLLECTION_IDS] = ids.joinToString(",")
        }
    }

    suspend fun setPreferOfflinePages(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFER_OFFLINE_PAGES] = enabled
        }
    }

    suspend fun setImportantInfoVersion(versionCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IMPORTANT_INFO_VERSION] = versionCode
        }
    }

    suspend fun setVerboseLogging(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERBOSE_LOGGING] = enabled
        }
    }

    suspend fun setBrowsePageSize(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BROWSE_PAGE_SIZE] = value.coerceIn(10, 50)
        }
    }

    suspend fun setMaxThumbnailsParallel(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MAX_THUMBNAILS_PARALLEL] = value.coerceIn(2, 6)
        }
    }

    suspend fun setDisableBrowseThumbnails(disabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISABLE_BROWSE_THUMBNAILS] = disabled
        }
    }

    suspend fun setShowDownloadBadges(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOW_DOWNLOAD_BADGES] = enabled
        }
    }

    suspend fun loadCachedCollections(): List<Collection> {
        val prefs = context.dataStore.data.first()
        val encoded = prefs[KEY_CACHED_COLLECTIONS] ?: return emptyList()
        return encoded
            .split("||")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val name = parts.getOrNull(1) ?: return@mapNotNull null
                Collection(id = id, name = name)
            }
    }

    private fun Preferences.toTextAlign(): TextAlign = decodeTextAlign(this[KEY_TEXT_ALIGN])

    private fun encodeTextAlign(align: TextAlign): String =
        when (align) {
            TextAlign.Left -> "left"
            TextAlign.Right -> "right"
            TextAlign.Center -> "center"
            TextAlign.Justify -> "justify"
            TextAlign.Start -> "start"
            TextAlign.End -> "end"
            else -> "start"
        }

    private fun decodeTextAlign(value: String?): TextAlign =
        when (value) {
            "left" -> TextAlign.Left
            "right" -> TextAlign.Right
            "center" -> TextAlign.Center
            "justify" -> TextAlign.Justify
            "end" -> TextAlign.End
            else -> TextAlign.Start
        }

    private fun Preferences.toReaderTheme(): ReaderThemeMode =
        when (this[KEY_READER_THEME]) {
            "dark" -> ReaderThemeMode.Dark
            "dark_hc" -> ReaderThemeMode.DarkHighContrast
            "sepia" -> ReaderThemeMode.Sepia
            "sepia_hc" -> ReaderThemeMode.SepiaHighContrast
            "gray" -> ReaderThemeMode.Gray
            else -> ReaderThemeMode.Light
        }

    private fun encodeReaderTheme(theme: ReaderThemeMode): String =
        when (theme) {
            ReaderThemeMode.Light -> "light"
            ReaderThemeMode.Dark -> "dark"
            ReaderThemeMode.DarkHighContrast -> "dark_hc"
            ReaderThemeMode.Sepia -> "sepia"
            ReaderThemeMode.SepiaHighContrast -> "sepia_hc"
            ReaderThemeMode.Gray -> "gray"
        }

    private fun Preferences.toImageReaderMode(): ImageReaderMode =
        when (this[KEY_IMAGE_READER_MODE]) {
            "rtl" -> ImageReaderMode.RightToLeft
            "vertical" -> ImageReaderMode.Vertical
            "webtoon" -> ImageReaderMode.Webtoon
            else -> ImageReaderMode.LeftToRight
        }

    private fun encodeImageReaderMode(mode: ImageReaderMode): String =
        when (mode) {
            ImageReaderMode.LeftToRight -> "ltr"
            ImageReaderMode.RightToLeft -> "rtl"
            ImageReaderMode.Vertical -> "vertical"
            ImageReaderMode.Webtoon -> "webtoon"
        }
}
