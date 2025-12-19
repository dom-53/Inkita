package net.dom53.inkita.core.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkLoggingInterceptor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.SeriesDao
import net.dom53.inkita.data.local.db.entity.CachedBrowseRefEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesRefEntity
import net.dom53.inkita.data.mapper.toCachedChapterEntity
import net.dom53.inkita.data.mapper.toCachedDetailEntity
import net.dom53.inkita.data.mapper.toCachedEntity
import net.dom53.inkita.data.mapper.toCachedVolumeEntity
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import net.dom53.inkita.data.mapper.toDomain as toDomainLocal

class CacheManagerImpl(
    private val appPreferences: AppPreferences,
    private val seriesDao: SeriesDao?,
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
        return CachePolicy(globalEnabled = global, libraryEnabled = library, browseEnabled = browse)
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
        val dao = seriesDao ?: return
        val p = policy()
        if (!p.libraryWriteAllowed) return
        val enriched = enrichThumbnails(series)
        val now = System.currentTimeMillis()
        val collectionId = key.collectionId ?: CachedSeriesRefEntity.NO_COLLECTION
        if (enriched.isEmpty()) {
            dao.clearRefsForTab(key.type.name, collectionId)
            LoggingManager.d("CacheManager", "Cleared cached tab ${key.type} col=$collectionId (empty)")
            return
        }
        dao.upsertAll(enriched.map { it.toCachedEntity(localThumbPath = it.localThumbPath, updatedAt = now) })
        val refs =
            enriched.map {
                CachedSeriesRefEntity(
                    tabType = key.type.name,
                    collectionId = collectionId,
                    seriesId = it.id,
                    updatedAt = now,
                )
            }
        dao.replaceTabRefs(key.type.name, collectionId, refs)
        LoggingManager.d("CacheManager", "Cached tab ${key.type} col=$collectionId size=${enriched.size}")
    }

    override suspend fun cacheBrowsePage(
        queryKey: String,
        page: Int,
        series: List<Series>,
    ) {
        val dao = seriesDao ?: return
        val p = policy()
        if (!p.browseWriteAllowed) return
        val enriched = enrichThumbnails(series)
        val now = System.currentTimeMillis()
        if (enriched.isEmpty()) {
            dao.clearBrowsePage(queryKey, page)
            LoggingManager.d("CacheManager", "Cleared cached browse key=$queryKey page=$page (empty)")
            return
        }
        dao.upsertAll(enriched.map { it.toCachedEntity(localThumbPath = it.localThumbPath, updatedAt = now) })
        val refs =
            enriched.map {
                CachedBrowseRefEntity(
                    queryKey = queryKey,
                    page = page,
                    seriesId = it.id,
                    updatedAt = now,
                )
            }
        dao.replaceBrowsePage(queryKey, page, refs)
        LoggingManager.d("CacheManager", "Cached browse key=$queryKey page=$page size=${enriched.size}")
    }

    override suspend fun cacheSeriesDetail(detail: SeriesDetail) {
        val dao = seriesDao ?: return
        val p = policy()
        if (!p.libraryWriteAllowed) return
        val now = System.currentTimeMillis()
        dao.upsertAll(listOf(detail.series.toCachedEntity(localThumbPath = detail.series.localThumbPath, updatedAt = now)))
        dao.upsertSeriesDetail(detail.toCachedDetailEntity(now))
        dao.clearVolumes(detail.series.id)
        val allVolumes = detail.volumes + detail.specials
        val volumeEntities = allVolumes.map { it.toCachedVolumeEntity(detail.series.id, now) }
        if (volumeEntities.isNotEmpty()) dao.upsertVolumes(volumeEntities)
        dao.clearChaptersForSeries(detail.series.id)
        val chapterEntities =
            allVolumes.flatMap { volume ->
                volume.chapters.map { chapter -> chapter.toCachedChapterEntity(volume.id, now) }
            }
        if (chapterEntities.isNotEmpty()) dao.upsertChapters(chapterEntities)
    }

    override suspend fun getCachedSeries(query: SeriesQuery): List<Series> {
        val dao = seriesDao ?: return emptyList()
        val all = dao.getAllOnce().map { it.toDomainLocal() }
        return all
    }

    override suspend fun getCachedSeriesForTab(key: LibraryTabCacheKey): List<Series> {
        val dao = seriesDao ?: return emptyList()
        val collectionId = key.collectionId ?: CachedSeriesRefEntity.NO_COLLECTION
        return dao.getSeriesForTab(key.type.name, collectionId).map { it.toDomainLocal() }
    }

    override suspend fun getCachedBrowsePage(
        queryKey: String,
        page: Int,
    ): List<Series> {
        val dao = seriesDao ?: return emptyList()
        return dao.getBrowsePage(queryKey, page).map { it.toDomainLocal() }
    }

    override suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail? {
        val dao = seriesDao ?: return null
        val series = dao.getById(seriesId)?.toDomainLocal() ?: return null
        val cachedDetail = dao.getSeriesDetail(seriesId)
        val cachedVolumes = dao.getVolumes(seriesId)
        val cachedChapters =
            if (cachedVolumes.isNotEmpty()) {
                dao.getChaptersForVolumes(cachedVolumes.map { it.id })
            } else {
                emptyList()
            }

        return if (cachedDetail != null) {
            net.dom53.inkita.data.mapper
                .buildCachedSeriesDetail(series, cachedDetail, cachedVolumes, cachedChapters)
        } else {
            null
        }
    }

    override suspend fun clearAllCache() {
        val dao = seriesDao ?: return
        val p = policy()
        if (!p.globalEnabled) return
        clearDatabaseInternal(dao)
        clearThumbnailsInternal()
        LoggingManager.d("CacheManager", "Cleared all cached data and thumbnails")
    }

    override suspend fun clearThumbnails() {
        val p = policy()
        if (!p.globalEnabled) return
        clearThumbnailsInternal()
    }

    override suspend fun clearDatabase() {
        val dao = seriesDao ?: return
        val p = policy()
        if (!p.globalEnabled) return
        clearDatabaseInternal(dao)
    }

    override suspend fun clearDetails() {
        val dao = seriesDao ?: return
        val p = policy()
        if (!p.globalEnabled) return
        dao.clearAllChapters()
        dao.clearAllVolumes()
        dao.clearAllSeriesDetail()
        LoggingManager.d("CacheManager", "Cleared cached details/volumes/chapters")
    }

    override suspend fun getCacheSizeBytes(): Long =
        withContext(Dispatchers.IO) {
            var total = 0L
            dbFile?.takeIf { it.exists() }?.let { total += it.length() }
            total += dirSize(thumbnailsDir)
            total
        }

    override suspend fun getCacheStats(): CacheStats {
        val dao = seriesDao ?: return CacheStats()
        val policy = policy()
        val dbSize = dbFile?.takeIf { it.exists() }?.length() ?: 0L
        val thumbs = dirSize(thumbnailsDir)
        val libTs = appPreferences.lastLibraryRefreshFlow.first()
        val browseTs = appPreferences.lastBrowseRefreshFlow.first()
        val stats =
            CacheStats(
                seriesCount = dao.countSeries(),
                tabRefs = dao.countTabRefs(),
                browseRefs = dao.countBrowseRefs(),
                detailsCount = dao.countSeriesDetail(),
                volumesCount = dao.countVolumes(),
                chaptersCount = dao.countChapters(),
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

    private suspend fun clearDatabaseInternal(daoRaw: SeriesDao? = seriesDao) {
        val dao = daoRaw ?: return

        dao.clearAllTabRefs()
        dao.clearAllBrowseRefs()
        dao.clearAllChapters()
        dao.clearAllVolumes()
        dao.clearAllSeriesDetail()
        dao.clearAllSeries()
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
