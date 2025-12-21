package net.dom53.inkita.core.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkLoggingInterceptor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.LibraryV2Dao
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadingList
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class CacheManagerImpl(
    private val appPreferences: AppPreferences,
    private val libraryV2Dao: LibraryV2Dao? = null,
    private val thumbnailsDir: File?,
    private val dbFile: File? = null,
) : CacheManager {
    private val httpClient by lazy {
        OkHttpClient
            .Builder()
            .addInterceptor(NetworkLoggingInterceptor)
            .build()
    }

    companion object {
        const val MAX_DIM = 512
        const val THUMBNAIL_QUALITY = 85
    }

    override suspend fun policy(): CachePolicy {
        val global = appPreferences.cacheEnabledFlow.first()
        val library = appPreferences.libraryCacheEnabledFlow.first()
        val browse = appPreferences.browseCacheEnabledFlow.first()
        val libraryHome = appPreferences.libraryCacheHomeFlow.first()
        val libraryWant = appPreferences.libraryCacheWantToReadFlow.first()
        val libraryCollections = appPreferences.libraryCacheCollectionsFlow.first()
        val libraryReadingLists = appPreferences.libraryCacheReadingListsFlow.first()
        val libraryBrowsePeople = appPreferences.libraryCacheBrowsePeopleFlow.first()
        return CachePolicy(
            globalEnabled = global,
            libraryEnabled = library,
            browseEnabled = browse,
            libraryHomeEnabled = libraryHome,
            libraryWantEnabled = libraryWant,
            libraryCollectionsEnabled = libraryCollections,
            libraryReadingListsEnabled = libraryReadingLists,
            libraryBrowsePeopleEnabled = libraryBrowsePeople,
        )
    }

    override suspend fun enrichThumbnails(series: List<Series>): List<Series> {
        if (thumbnailsDir == null) return series
        val p = policy()
        if (!p.globalEnabled) return series
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) return series

        return series.map { item ->
            if (item.localThumbPath != null) {
                item
            } else {
                val thumb = downloadThumbnailIfNeeded(config.serverUrl, config.apiKey, item.id)
                if (thumb != null) item.copy(localThumbPath = thumb) else item
            }
        }
    }

    override suspend fun cacheTabResults(
        key: LibraryTabCacheKey,
        series: List<Series>,
    ) {
        // Legacy cache removed; no-op.
    }

    override suspend fun cacheBrowsePage(
        queryKey: String,
        page: Int,
        series: List<Series>,
    ) {
        // Legacy cache removed; no-op.
    }

    override suspend fun cacheSeriesDetail(detail: SeriesDetail) {
        // Legacy cache removed; no-op.
    }

    override suspend fun getCachedSeries(query: SeriesQuery): List<Series> {
        return emptyList()
    }

    override suspend fun getCachedSeriesForTab(key: LibraryTabCacheKey): List<Series> {
        return emptyList()
    }

    override suspend fun getCachedBrowsePage(
        queryKey: String,
        page: Int,
    ): List<Series> {
        return emptyList()
    }

    override suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail? {
        return null
    }

    override suspend fun cacheLibraryV2SeriesList(
        listType: String,
        listKey: String,
        series: List<Series>,
    ) {
        // Skeleton only; no-op.
    }

    override suspend fun getCachedLibraryV2SeriesList(
        listType: String,
        listKey: String,
    ): List<Series> {
        return emptyList()
    }

    override suspend fun cacheLibraryV2Collections(
        listType: String,
        collections: List<Collection>,
    ) {
        // Skeleton only; no-op.
    }

    override suspend fun getCachedLibraryV2Collections(listType: String): List<Collection> {
        return emptyList()
    }

    override suspend fun cacheLibraryV2ReadingLists(
        listType: String,
        readingLists: List<ReadingList>,
    ) {
        // Skeleton only; no-op.
    }

    override suspend fun getCachedLibraryV2ReadingLists(listType: String): List<ReadingList> {
        return emptyList()
    }

    override suspend fun cacheLibraryV2People(
        listType: String,
        page: Int,
        people: List<Person>,
    ) {
        // Skeleton only; no-op.
    }

    override suspend fun getCachedLibraryV2People(
        listType: String,
        page: Int,
    ): List<Person> {
        return emptyList()
    }

    override suspend fun clearAllCache() {
        val p = policy()
        if (!p.globalEnabled) return
        clearDatabaseInternal()
        clearThumbnailsInternal()
        LoggingManager.d("CacheManager", "Cleared all cached data and thumbnails")
    }

    override suspend fun clearThumbnails() {
        val p = policy()
        if (!p.globalEnabled) return
        clearThumbnailsInternal()
    }

    override suspend fun clearDatabase() {
        val p = policy()
        if (!p.globalEnabled) return
        clearDatabaseInternal()
    }

    override suspend fun clearDetails() {
        // Legacy cache removed; no-op.
    }

    override suspend fun getCacheSizeBytes(): Long =
        withContext(Dispatchers.IO) {
            var total = 0L
            dbFile?.takeIf { it.exists() }?.let { total += it.length() }
            total += dirSize(thumbnailsDir)
            total
        }

    override suspend fun getCacheStats(): CacheStats {
        val policy = policy()
        val dbSize = dbFile?.takeIf { it.exists() }?.length() ?: 0L
        val thumbs = dirSize(thumbnailsDir)
        val libTs = appPreferences.lastLibraryRefreshFlow.first()
        val browseTs = appPreferences.lastBrowseRefreshFlow.first()
        val stats =
            CacheStats(
                seriesCount = 0,
                tabRefs = 0,
                browseRefs = 0,
                detailsCount = 0,
                volumesCount = 0,
                chaptersCount = 0,
                dbBytes = dbSize,
                thumbnailsBytes = thumbs,
                lastLibraryRefresh = libTs,
                lastBrowseRefresh = browseTs,
            )
        LoggingManager.d("CacheManager", "Cache stats (policy=$policy): $stats")
        return stats
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir
            .walkBottomUp()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    private fun clearThumbnailsInternal() {
        thumbnailsDir?.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        LoggingManager.d("CacheManager", "Cleared thumbnails")
    }

    private suspend fun clearDatabaseInternal() {
        libraryV2Dao?.let { v2Dao ->
            v2Dao.clearAllSeriesRefs()
            v2Dao.clearAllSeries()
            v2Dao.clearAllCollectionRefs()
            v2Dao.clearAllCollections()
            v2Dao.clearAllReadingListRefs()
            v2Dao.clearAllReadingLists()
            v2Dao.clearAllPersonRefs()
            v2Dao.clearAllPeople()
        }
        LoggingManager.d("CacheManager", "Cleared database cache")
    }

    private suspend fun downloadThumbnailIfNeeded(
        serverUrl: String,
        apiKey: String,
        seriesId: Int,
    ): String? {
        val dir = thumbnailsDir ?: return null
        return withContext(Dispatchers.IO) {
            var result: String? = null
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "$seriesId.jpg")
                if (file.exists()) {
                    if (file.length() > 0) {
                        BitmapFactory.decodeFile(file.absolutePath)?.let { result = file.absolutePath }
                        if (result == null) runCatching { file.delete() }
                    } else {
                        runCatching { file.delete() }
                    }
                }

                if (result == null) {
                    val base = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl
                    val url = "$base/api/Image/series-cover?seriesId=$seriesId&apiKey=$apiKey"
                    val request = Request.Builder().url(url).build()
                    httpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body
                            if (body != null) {
                                val bytes = body.byteStream().use { it.readBytes() }
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    val scale = maxOf(1f, maxOf(bitmap.width, bitmap.height) / MAX_DIM.toFloat())
                                    val targetW = (bitmap.width / scale).toInt().coerceAtLeast(1)
                                    val targetH = (bitmap.height / scale).toInt().coerceAtLeast(1)
                                    val scaled =
                                        if (scale > 1f) Bitmap.createScaledBitmap(bitmap, targetW, targetH, true) else bitmap
                                    FileOutputStream(file).use { out ->
                                        scaled.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
                                    }
                                    if (scaled != bitmap) scaled.recycle()
                                    result = file.absolutePath
                                } else {
                                    runCatching { file.delete() }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                result = null
            }
            result
        }
    }
}
