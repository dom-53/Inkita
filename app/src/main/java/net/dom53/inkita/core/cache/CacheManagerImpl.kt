package net.dom53.inkita.core.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.cache.LibraryV2CacheKeys
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.NetworkLoggingInterceptor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.AnnotationDto
import net.dom53.inkita.data.api.dto.AppUserCollectionDto
import net.dom53.inkita.data.api.dto.BookmarkDto
import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.HourEstimateRangeDto
import net.dom53.inkita.data.api.dto.RatingDto
import net.dom53.inkita.data.api.dto.ReaderProgressDto
import net.dom53.inkita.data.api.dto.ReadingListDto
import net.dom53.inkita.data.api.dto.RelatedSeriesDto
import net.dom53.inkita.data.api.dto.SeriesDetailDto
import net.dom53.inkita.data.api.dto.SeriesDetailPlusDto
import net.dom53.inkita.data.api.dto.SeriesDto
import net.dom53.inkita.data.api.dto.SeriesMetadataDto
import net.dom53.inkita.data.local.db.dao.LibraryV2Dao
import net.dom53.inkita.data.local.db.dao.SeriesDetailV2Dao
import net.dom53.inkita.data.local.db.entity.CachedCollectionRefEntity
import net.dom53.inkita.data.local.db.entity.CachedCollectionV2Entity
import net.dom53.inkita.data.local.db.entity.CachedPersonRefEntity
import net.dom53.inkita.data.local.db.entity.CachedPersonV2Entity
import net.dom53.inkita.data.local.db.entity.CachedReadingListRefEntity
import net.dom53.inkita.data.local.db.entity.CachedReadingListV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesListRefEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesV2Entity
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.ReadingList
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Default cache implementation for legacy (no-op) and V2 cache flows.
 *
 * Uses Room DAOs for structured lists and details, and stores thumbnails on disk.
 */
class CacheManagerImpl(
    private val appPreferences: AppPreferences,
    private val libraryV2Dao: LibraryV2Dao? = null,
    private val seriesDetailV2Dao: SeriesDetailV2Dao? = null,
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

    private val moshi: Moshi by lazy {
        Moshi
            .Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val seriesAdapter = moshi.adapter(SeriesDto::class.java)
    private val metadataAdapter = moshi.adapter(SeriesMetadataDto::class.java)
    private val detailAdapter = moshi.adapter(SeriesDetailDto::class.java)
    private val relatedAdapter = moshi.adapter(RelatedSeriesDto::class.java)
    private val ratingAdapter = moshi.adapter(RatingDto::class.java)
    private val chapterAdapter = moshi.adapter(ChapterDto::class.java)
    private val readerProgressAdapter = moshi.adapter(ReaderProgressDto::class.java)
    private val timeLeftAdapter = moshi.adapter(HourEstimateRangeDto::class.java)
    private val seriesDetailPlusAdapter = moshi.adapter(SeriesDetailPlusDto::class.java)

    private val readingListAdapter =
        moshi.adapter<List<ReadingListDto>>(
            Types.newParameterizedType(List::class.java, ReadingListDto::class.java),
        )
    private val stringListAdapter =
        moshi.adapter<List<String>>(
            Types.newParameterizedType(List::class.java, String::class.java),
        )
    private val collectionsAdapter =
        moshi.adapter<List<AppUserCollectionDto>>(
            Types.newParameterizedType(List::class.java, AppUserCollectionDto::class.java),
        )
    private val bookmarkAdapter =
        moshi.adapter<List<BookmarkDto>>(
            Types.newParameterizedType(List::class.java, BookmarkDto::class.java),
        )
    private val annotationAdapter =
        moshi.adapter<List<AnnotationDto>>(
            Types.newParameterizedType(List::class.java, AnnotationDto::class.java),
        )

    /** Read cache-related preferences and build a policy snapshot. */
    override suspend fun policy(): CachePolicy {
        val global = appPreferences.cacheEnabledFlow.first()
        val library = appPreferences.libraryCacheEnabledFlow.first()
        val browse = appPreferences.browseCacheEnabledFlow.first()
        val libraryHome = appPreferences.libraryCacheHomeFlow.first()
        val libraryWant = appPreferences.libraryCacheWantToReadFlow.first()
        val libraryCollections = appPreferences.libraryCacheCollectionsFlow.first()
        val libraryReadingLists = appPreferences.libraryCacheReadingListsFlow.first()
        val libraryBrowsePeople = appPreferences.libraryCacheBrowsePeopleFlow.first()
        val libraryDetails = appPreferences.libraryCacheDetailsFlow.first()
        return CachePolicy(
            globalEnabled = global,
            libraryEnabled = library,
            browseEnabled = browse,
            libraryHomeEnabled = libraryHome,
            libraryWantEnabled = libraryWant,
            libraryCollectionsEnabled = libraryCollections,
            libraryReadingListsEnabled = libraryReadingLists,
            libraryBrowsePeopleEnabled = libraryBrowsePeople,
            libraryDetailsEnabled = libraryDetails,
        )
    }

    /** Resolve and populate local thumbnail paths for a series list. */
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

    /** Legacy cache entry point kept as no-op for compatibility. */
    override suspend fun cacheTabResults(
        key: LibraryTabCacheKey,
        series: List<Series>,
    ) {
        // Legacy cache removed; no-op.
    }

    /** Legacy cache entry point kept as no-op for compatibility. */
    override suspend fun cacheBrowsePage(
        queryKey: String,
        page: Int,
        series: List<Series>,
    ) {
        // Legacy cache removed; no-op.
    }

    /** Legacy cache entry point kept as no-op for compatibility. */
    override suspend fun cacheSeriesDetail(detail: SeriesDetail) {
        // Legacy cache removed; no-op.
    }

    /** Legacy cache read kept as empty for compatibility. */
    override suspend fun getCachedSeries(query: SeriesQuery): List<Series> = emptyList()

    /** Legacy cache read kept as empty for compatibility. */
    override suspend fun getCachedSeriesForTab(key: LibraryTabCacheKey): List<Series> = emptyList()

    /** Legacy cache read kept as empty for compatibility. */
    override suspend fun getCachedBrowsePage(
        queryKey: String,
        page: Int,
    ): List<Series> = emptyList()

    /** Legacy cache read kept as null for compatibility. */
    override suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail? = null

    /** Store a Library V2 series list along with reference ordering. */
    override suspend fun cacheLibraryV2SeriesList(
        listType: String,
        listKey: String,
        series: List<Series>,
    ) {
        val dao = libraryV2Dao ?: return
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return
        val now = System.currentTimeMillis()
        val unique = series.distinctBy { it.id }
        val enriched = enrichThumbnails(unique)
        dao.upsertSeries(enriched.map { it.toCachedSeriesV2(now) })
        dao.clearSeriesRefs(listType, listKey)
        val refs =
            enriched.mapIndexed { index, item ->
                CachedSeriesListRefEntity(
                    listType = listType,
                    listKey = listKey,
                    seriesId = item.id,
                    position = index,
                    updatedAt = now,
                )
            }
        dao.upsertSeriesRefs(refs)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Store series list type=$listType key=$listKey count=${refs.size}")
        }
    }

    /** Read a cached Library V2 series list by list type and key. */
    override suspend fun getCachedLibraryV2SeriesList(
        listType: String,
        listKey: String,
    ): List<Series> {
        val dao = libraryV2Dao ?: return emptyList()
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return emptyList()
        val result = dao.getSeriesForList(listType, listKey).map { it.toDomainSeries() }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Read series list type=$listType key=$listKey count=${result.size}")
        }
        return result
    }

    /** Read the last update timestamp for a Library V2 series list. */
    override suspend fun getLibraryV2SeriesListUpdatedAt(
        listType: String,
        listKey: String,
    ): Long? {
        val dao = libraryV2Dao ?: return null
        return dao.getSeriesListUpdatedAt(listType, listKey)
    }

    /** Read the last update timestamp for cached collections list. */
    override suspend fun getLibraryV2CollectionsUpdatedAt(listType: String): Long? {
        val dao = libraryV2Dao ?: return null
        return dao.getCollectionsUpdatedAt(listType)
    }

    /** Read the last update timestamp for cached reading lists. */
    override suspend fun getLibraryV2ReadingListsUpdatedAt(listType: String): Long? {
        val dao = libraryV2Dao ?: return null
        return dao.getReadingListsUpdatedAt(listType)
    }

    /** Read the last update timestamp for cached people list. */
    override suspend fun getLibraryV2PeopleUpdatedAt(
        listType: String,
        page: Int,
    ): Long? {
        val dao = libraryV2Dao ?: return null
        return dao.getPeopleUpdatedAt(listType, page)
    }

    /** Store cached collection list and references. */
    override suspend fun cacheLibraryV2Collections(
        listType: String,
        collections: List<Collection>,
    ) {
        val dao = libraryV2Dao ?: return
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return
        val now = System.currentTimeMillis()
        val unique = collections.distinctBy { it.id }
        dao.upsertCollections(unique.map { CachedCollectionV2Entity(it.id, it.name, now) })
        dao.clearCollectionRefs(listType)
        val refs =
            unique.mapIndexed { index, item ->
                CachedCollectionRefEntity(
                    listType = listType,
                    collectionId = item.id,
                    position = index,
                    updatedAt = now,
                )
            }
        dao.upsertCollectionRefs(refs)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Store collections type=$listType count=${refs.size}")
        }
    }

    /** Read cached collection list. */
    override suspend fun getCachedLibraryV2Collections(listType: String): List<Collection> {
        val dao = libraryV2Dao ?: return emptyList()
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return emptyList()
        val result = dao.getCollectionsForList(listType).map { Collection(it.id, it.name) }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Read collections type=$listType count=${result.size}")
        }
        return result
    }

    /** Store cached reading lists and references. */
    override suspend fun cacheLibraryV2ReadingLists(
        listType: String,
        readingLists: List<ReadingList>,
    ) {
        val dao = libraryV2Dao ?: return
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return
        val now = System.currentTimeMillis()
        val unique = readingLists.distinctBy { it.id }
        dao.upsertReadingLists(unique.map { CachedReadingListV2Entity(it.id, it.title, it.itemCount, now) })
        dao.clearReadingListRefs(listType)
        val refs =
            unique.mapIndexed { index, item ->
                CachedReadingListRefEntity(
                    listType = listType,
                    readingListId = item.id,
                    position = index,
                    updatedAt = now,
                )
            }
        dao.upsertReadingListRefs(refs)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Store reading lists type=$listType count=${refs.size}")
        }
    }

    /** Read cached reading lists. */
    override suspend fun getCachedLibraryV2ReadingLists(listType: String): List<ReadingList> {
        val dao = libraryV2Dao ?: return emptyList()
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return emptyList()
        val result = dao.getReadingListsForList(listType).map { ReadingList(it.id, it.title, it.itemCount) }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Read reading lists type=$listType count=${result.size}")
        }
        return result
    }

    /** Store cached people list and references for a page. */
    override suspend fun cacheLibraryV2People(
        listType: String,
        page: Int,
        people: List<Person>,
    ) {
        val dao = libraryV2Dao ?: return
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return
        val now = System.currentTimeMillis()
        val unique =
            people
                .mapNotNull { person ->
                    val id = person.id ?: return@mapNotNull null
                    id to person
                }.distinctBy { it.first }
        dao.upsertPeople(unique.map { CachedPersonV2Entity(it.first, it.second.name, now) })
        dao.clearPersonRefs(listType, page)
        val refs =
            unique.mapIndexed { index, item ->
                CachedPersonRefEntity(
                    listType = listType,
                    page = page,
                    personId = item.first,
                    position = index,
                    updatedAt = now,
                )
            }
        dao.upsertPersonRefs(refs)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Store people type=$listType page=$page count=${refs.size}")
        }
    }

    /** Read cached people list for a page. */
    override suspend fun getCachedLibraryV2People(
        listType: String,
        page: Int,
    ): List<Person> {
        val dao = libraryV2Dao ?: return emptyList()
        val p = policy()
        if (!isLibraryV2CacheAllowed(p, listType)) return emptyList()
        val result = dao.getPeopleForList(listType, page).map { Person(it.id, it.name) }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Read people type=$listType page=$page count=${result.size}")
        }
        return result
    }

    /** Store the aggregated Series Detail V2 payload. */
    override suspend fun cacheSeriesDetailV2(
        seriesId: Int,
        detail: InkitaDetailV2,
    ) {
        val dao = seriesDetailV2Dao ?: return
        val p = policy()
        if (!p.globalEnabled || !p.libraryEnabled || !p.libraryDetailsEnabled) return
        val now = System.currentTimeMillis()
        val metadata = detail.metadata
        val entity =
            net.dom53.inkita.data.local.db.entity.CachedSeriesDetailV2Entity(
                seriesId = seriesId,
                summary = metadata?.summary,
                publicationStatus = metadata?.publicationStatus,
                genres = metadata?.genres?.mapNotNull { it.title }?.let { toJsonList(it) },
                tags = metadata?.tags?.mapNotNull { it.title }?.let { toJsonList(it) },
                writers = metadata?.writers?.mapNotNull { it.name }?.let { toJsonList(it) },
                releaseYear = metadata?.releaseYear,
                wordCount = detail.series?.wordCount,
                timeLeftMin = detail.timeLeft?.minHours?.toDouble(),
                timeLeftMax = detail.timeLeft?.maxHours?.toDouble(),
                timeLeftAvg = detail.timeLeft?.avgHours?.toDouble(),
                hasProgress = detail.hasProgress,
                wantToRead = detail.wantToRead,
                seriesJson = detail.series?.let { seriesAdapter.toJson(it) },
                metadataJson = metadata?.let { metadataAdapter.toJson(it) },
                detailJson = detail.detail?.let { detailAdapter.toJson(it) },
                relatedJson = detail.related?.let { relatedAdapter.toJson(it) },
                ratingJson = detail.rating?.let { ratingAdapter.toJson(it) },
                continuePointJson = detail.continuePoint?.let { chapterAdapter.toJson(it) },
                readerProgressJson = detail.readerProgress?.let { readerProgressAdapter.toJson(it) },
                timeLeftJson = detail.timeLeft?.let { timeLeftAdapter.toJson(it) },
                collectionsJson = detail.collections?.let { collectionsAdapter.toJson(it) },
                readingListsJson = detail.readingLists?.let { readingListAdapter.toJson(it) },
                bookmarksJson = detail.bookmarks?.let { bookmarkAdapter.toJson(it) },
                annotationsJson = detail.annotations?.let { annotationAdapter.toJson(it) },
                seriesDetailPlusJson = detail.seriesDetailPlus?.let { seriesDetailPlusAdapter.toJson(it) },
                updatedAt = now,
            )
        dao.upsertSeriesDetail(entity)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Store series detail series=$seriesId")
        }
    }

    /** Read cached Series Detail V2 payload. */
    override suspend fun getCachedSeriesDetailV2(seriesId: Int): InkitaDetailV2? {
        val dao = seriesDetailV2Dao ?: return null
        val p = policy()
        if (!p.globalEnabled || !p.libraryEnabled || !p.libraryDetailsEnabled) return null
        val entity = dao.getSeriesDetail(seriesId) ?: return null
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("CacheV2", "Read series detail series=$seriesId")
        }
        val series = entity.seriesJson?.let { fromJson(seriesAdapter, it) }
        val metadata = entity.metadataJson?.let { fromJson(metadataAdapter, it) }
        val detail = entity.detailJson?.let { fromJson(detailAdapter, it) }
        val related = entity.relatedJson?.let { fromJson(relatedAdapter, it) }
        val rating = entity.ratingJson?.let { fromJson(ratingAdapter, it) }
        val continuePoint = entity.continuePointJson?.let { fromJson(chapterAdapter, it) }
        val readerProgress = entity.readerProgressJson?.let { fromJson(readerProgressAdapter, it) }
        val timeLeft =
            entity.timeLeftJson?.let { fromJson(timeLeftAdapter, it) }
                ?: if (
                    entity.timeLeftMin != null ||
                    entity.timeLeftMax != null ||
                    entity.timeLeftAvg != null
                ) {
                    HourEstimateRangeDto(
                        minHours = entity.timeLeftMin?.toInt(),
                        maxHours = entity.timeLeftMax?.toInt(),
                        avgHours = entity.timeLeftAvg?.toFloat(),
                    )
                } else {
                    null
                }
        val collections = entity.collectionsJson?.let { fromJsonList(collectionsAdapter, it) }
        val readingLists = entity.readingListsJson?.let { fromJsonList(readingListAdapter, it) }
        val bookmarks = entity.bookmarksJson?.let { fromJsonList(bookmarkAdapter, it) }
        val annotations = entity.annotationsJson?.let { fromJsonList(annotationAdapter, it) }
        val seriesDetailPlus = entity.seriesDetailPlusJson?.let { fromJson(seriesDetailPlusAdapter, it) }
        val fallbackMetadata =
            metadata ?: buildFallbackMetadata(entity)

        return InkitaDetailV2(
            series = series,
            metadata = fallbackMetadata,
            wantToRead = entity.wantToRead,
            readingLists = readingLists,
            collections = collections,
            bookmarks = bookmarks,
            annotations = annotations,
            timeLeft = timeLeft,
            hasProgress = entity.hasProgress,
            continuePoint = continuePoint,
            seriesDetailPlus = seriesDetailPlus,
            related = related,
            detail = detail,
            rating = rating,
            readerProgress = readerProgress,
        )
    }

    /** Read the last update timestamp for Series Detail V2. */
    override suspend fun getSeriesDetailV2UpdatedAt(seriesId: Int): Long? {
        val dao = seriesDetailV2Dao ?: return null
        return dao.getSeriesDetailUpdatedAt(seriesId)
    }

    /** Clear all cached data, including thumbnails and DB records. */
    override suspend fun clearAllCache() {
        val p = policy()
        if (!p.globalEnabled) return
        clearDatabaseInternal()
        clearThumbnailsInternal()
        LoggingManager.d("CacheManager", "Cleared all cached data and thumbnails")
    }

    /** Clear only cached thumbnails. */
    override suspend fun clearThumbnails() {
        val p = policy()
        if (!p.globalEnabled) return
        clearThumbnailsInternal()
    }

    /** Clear only cached database data. */
    override suspend fun clearDatabase() {
        val p = policy()
        if (!p.globalEnabled) return
        clearDatabaseInternal()
    }

    /** Clear only cached Series Detail V2 payloads. */
    override suspend fun clearDetails() {
        val p = policy()
        if (!p.globalEnabled) return
        seriesDetailV2Dao?.let { detailDao ->
            detailDao.clearVolumeChapterRefs()
            detailDao.clearChapters()
            detailDao.clearSeriesVolumeRefs()
            detailDao.clearVolumes()
            detailDao.clearRelatedRefs()
            detailDao.clearSeriesDetails()
        }
    }

    /** Return total cache size in bytes (DB + thumbnails). */
    override suspend fun getCacheSizeBytes(): Long =
        withContext(Dispatchers.IO) {
            var total = 0L
            dbFile?.takeIf { it.exists() }?.let { total += it.length() }
            total += dirSize(thumbnailsDir)
            total
        }

    /** Build a snapshot of cached counts and sizes for diagnostics. */
    override suspend fun getCacheStats(): CacheStats {
        val policy = policy()
        val dbSize = dbFile?.takeIf { it.exists() }?.length() ?: 0L
        val thumbs = dirSize(thumbnailsDir)
        val thumbsCount = dirFileCount(thumbnailsDir)
        val libTs = appPreferences.lastLibraryRefreshFlow.first()
        val browseTs = appPreferences.lastBrowseRefreshFlow.first()
        val libraryStats =
            libraryV2Dao?.let { dao ->
                listOf(
                    dao.getSeriesCount(),
                    dao.getSeriesRefsCount(),
                    dao.getCollectionsCount(),
                    dao.getCollectionRefsCount(),
                    dao.getReadingListsCount(),
                    dao.getReadingListRefsCount(),
                    dao.getPeopleCount(),
                    dao.getPersonRefsCount(),
                )
            }
        val detailStats =
            seriesDetailV2Dao?.let { dao ->
                listOf(
                    dao.getDetailsCount(),
                    dao.getRelatedRefsCount(),
                    dao.getVolumesCount(),
                    dao.getSeriesVolumeRefsCount(),
                    dao.getChaptersCount(),
                    dao.getVolumeChapterRefsCount(),
                )
            }
        val stats =
            CacheStats(
                seriesCount = libraryStats?.getOrNull(0) ?: 0,
                seriesListRefsCount = libraryStats?.getOrNull(1) ?: 0,
                collectionsCount = libraryStats?.getOrNull(2) ?: 0,
                collectionRefsCount = libraryStats?.getOrNull(3) ?: 0,
                readingListsCount = libraryStats?.getOrNull(4) ?: 0,
                readingListRefsCount = libraryStats?.getOrNull(5) ?: 0,
                peopleCount = libraryStats?.getOrNull(6) ?: 0,
                personRefsCount = libraryStats?.getOrNull(7) ?: 0,
                detailsCount = detailStats?.getOrNull(0) ?: 0,
                relatedRefsCount = detailStats?.getOrNull(1) ?: 0,
                volumesCount = detailStats?.getOrNull(2) ?: 0,
                seriesVolumeRefsCount = detailStats?.getOrNull(3) ?: 0,
                chaptersCount = detailStats?.getOrNull(4) ?: 0,
                volumeChapterRefsCount = detailStats?.getOrNull(5) ?: 0,
                dbBytes = dbSize,
                thumbnailsCount = thumbsCount,
                thumbnailsBytes = thumbs,
                lastLibraryRefresh = libTs,
                lastBrowseRefresh = browseTs,
            )
        LoggingManager.d("CacheManager", "Cache stats (policy=$policy): $stats")
        return stats
    }

    /** Compute the total byte size of a directory tree. */
    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir
            .walkBottomUp()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /** Count the number of files inside a directory tree. */
    private fun dirFileCount(dir: File?): Int {
        if (dir == null || !dir.exists()) return 0
        return dir
            .walkBottomUp()
            .count { it.isFile }
    }

    /** Remove all cached thumbnails from disk. */
    private fun clearThumbnailsInternal() {
        thumbnailsDir?.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
        LoggingManager.d("CacheManager", "Cleared thumbnails")
    }

    /** Remove all cached rows from V2 cache tables. */
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
        seriesDetailV2Dao?.let { detailDao ->
            detailDao.clearVolumeChapterRefs()
            detailDao.clearChapters()
            detailDao.clearSeriesVolumeRefs()
            detailDao.clearVolumes()
            detailDao.clearRelatedRefs()
            detailDao.clearSeriesDetails()
        }
        LoggingManager.d("CacheManager", "Cleared database cache")
    }

    /** Gate Library V2 cache reads/writes by policy and list type. */
    private fun isLibraryV2CacheAllowed(
        policy: CachePolicy,
        listType: String,
    ): Boolean {
        if (!policy.globalEnabled || !policy.libraryEnabled) return false
        return when (listType) {
            LibraryV2CacheKeys.HOME_ON_DECK,
            LibraryV2CacheKeys.HOME_RECENTLY_UPDATED,
            LibraryV2CacheKeys.HOME_RECENTLY_ADDED,
            -> policy.libraryHomeEnabled
            LibraryV2CacheKeys.WANT_TO_READ -> policy.libraryWantEnabled
            LibraryV2CacheKeys.COLLECTIONS,
            LibraryV2CacheKeys.COLLECTION_SERIES,
            -> policy.libraryCollectionsEnabled
            LibraryV2CacheKeys.READING_LISTS -> policy.libraryReadingListsEnabled
            LibraryV2CacheKeys.BROWSE_PEOPLE -> policy.libraryBrowsePeopleEnabled
            else -> false
        }
    }

    /** Serialize a list of strings for storage. */
    private fun toJsonList(value: List<String>): String = stringListAdapter.toJson(value)

    /** Recreate minimal metadata when cached JSON payload is missing. */
    private fun buildFallbackMetadata(entity: net.dom53.inkita.data.local.db.entity.CachedSeriesDetailV2Entity): SeriesMetadataDto? {
        if (
            entity.summary == null &&
            entity.publicationStatus == null &&
            entity.releaseYear == null &&
            entity.genres == null &&
            entity.tags == null &&
            entity.writers == null
        ) {
            return null
        }
        val genres =
            entity.genres
                ?.let { fromJsonList(stringListAdapter, it) }
                ?.mapIndexed { index, name ->
                    net.dom53.inkita.data.api.dto
                        .GenreTagDto(-(index + 1), name)
                }
        val tags =
            entity.tags
                ?.let { fromJsonList(stringListAdapter, it) }
                ?.map { name ->
                    net.dom53.inkita.data.api.dto
                        .TagDto(null, name)
                }
        val writers =
            entity.writers
                ?.let { fromJsonList(stringListAdapter, it) }
                ?.map { name ->
                    net.dom53.inkita.data.api.dto
                        .PersonDto(name = name)
                }
        return SeriesMetadataDto(
            summary = entity.summary,
            publicationStatus = entity.publicationStatus,
            releaseYear = entity.releaseYear,
            genres = genres,
            tags = tags,
            writers = writers,
        )
    }

    /** Deserialize a JSON string into an object. */
    private fun <T> fromJson(
        adapter: com.squareup.moshi.JsonAdapter<T>,
        value: String,
    ): T? = runCatching { adapter.fromJson(value) }.getOrNull()

    /** Deserialize a JSON string into a list. */
    private fun <T> fromJsonList(
        adapter: com.squareup.moshi.JsonAdapter<List<T>>,
        value: String,
    ): List<T>? = runCatching { adapter.fromJson(value) }.getOrNull()

    /** Convert a domain series into a cached entity row. */
    private fun Series.toCachedSeriesV2(updatedAt: Long): CachedSeriesV2Entity =
        CachedSeriesV2Entity(
            id = id,
            name = name,
            summary = summary,
            libraryId = libraryId,
            format = format?.name,
            pages = pages,
            pagesRead = pagesRead,
            readState = readState?.name,
            minHoursToRead = minHoursToRead,
            maxHoursToRead = maxHoursToRead,
            avgHoursToRead = avgHoursToRead,
            localThumbPath = localThumbPath,
            updatedAt = updatedAt,
        )

    /** Convert a cached entity row into a domain series. */
    private fun CachedSeriesV2Entity.toDomainSeries(): Series =
        Series(
            id = id,
            name = name,
            summary = summary,
            libraryId = libraryId,
            format = format?.let { runCatching { Format.valueOf(it) }.getOrNull() },
            pages = pages,
            pagesRead = pagesRead,
            readState = readState?.let { runCatching { ReadState.valueOf(it) }.getOrNull() },
            minHoursToRead = minHoursToRead,
            maxHoursToRead = maxHoursToRead,
            avgHoursToRead = avgHoursToRead,
            localThumbPath = localThumbPath,
        )

    /** Download and store a local series thumbnail if missing. */
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
