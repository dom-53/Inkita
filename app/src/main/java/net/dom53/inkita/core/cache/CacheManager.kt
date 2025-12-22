package net.dom53.inkita.core.cache

import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadingList
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2

/**
 * Cache interface covering legacy and V2 cache layers.
 *
 * V2 cache focuses on Library V2 lists and Series Detail V2 payloads.
 */
interface CacheManager {
    /** Returns the current cache policy (feature toggles + limits). */
    suspend fun policy(): CachePolicy

    /** Enrich series with locally cached thumbnails when available. */
    suspend fun enrichThumbnails(series: List<Series>): List<Series>

    /** Legacy: cache tab results. */
    suspend fun cacheTabResults(
        key: LibraryTabCacheKey,
        series: List<Series>,
    )

    /** Legacy: cache a browse page. */
    suspend fun cacheBrowsePage(
        queryKey: String,
        page: Int,
        series: List<Series>,
    )

    /** Legacy: cache Series Detail V1. */
    suspend fun cacheSeriesDetail(detail: SeriesDetail)

    /** Legacy: read cached series for a query. */
    suspend fun getCachedSeries(query: SeriesQuery): List<Series>

    /** Legacy: read cached series for a tab. */
    suspend fun getCachedSeriesForTab(key: LibraryTabCacheKey): List<Series>

    /** Legacy: read cached browse page. */
    suspend fun getCachedBrowsePage(
        queryKey: String,
        page: Int,
    ): List<Series>

    /** Legacy: read cached Series Detail V1. */
    suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail?

    /** Cache a list of series for Library V2. */
    suspend fun cacheLibraryV2SeriesList(
        listType: String,
        listKey: String,
        series: List<Series>,
    )

    /** Read cached Library V2 series list. */
    suspend fun getCachedLibraryV2SeriesList(
        listType: String,
        listKey: String,
    ): List<Series>

    /** Return the last update timestamp for a Library V2 series list. */
    suspend fun getLibraryV2SeriesListUpdatedAt(
        listType: String,
        listKey: String,
    ): Long?

    /** Return the last update timestamp for Library V2 collections. */
    suspend fun getLibraryV2CollectionsUpdatedAt(listType: String): Long?

    /** Return the last update timestamp for Library V2 reading lists. */
    suspend fun getLibraryV2ReadingListsUpdatedAt(listType: String): Long?

    /** Return the last update timestamp for Library V2 people list. */
    suspend fun getLibraryV2PeopleUpdatedAt(
        listType: String,
        page: Int,
    ): Long?

    /** Cache Library V2 collections. */
    suspend fun cacheLibraryV2Collections(
        listType: String,
        collections: List<Collection>,
    )

    /** Read cached Library V2 collections. */
    suspend fun getCachedLibraryV2Collections(listType: String): List<Collection>

    /** Cache Library V2 reading lists. */
    suspend fun cacheLibraryV2ReadingLists(
        listType: String,
        readingLists: List<ReadingList>,
    )

    /** Read cached Library V2 reading lists. */
    suspend fun getCachedLibraryV2ReadingLists(listType: String): List<ReadingList>

    /** Cache Library V2 people list. */
    suspend fun cacheLibraryV2People(
        listType: String,
        page: Int,
        people: List<Person>,
    )

    /** Read cached Library V2 people list. */
    suspend fun getCachedLibraryV2People(
        listType: String,
        page: Int,
    ): List<Person>

    /** Cache Series Detail V2 aggregate payload. */
    suspend fun cacheSeriesDetailV2(
        seriesId: Int,
        detail: InkitaDetailV2,
    )

    /** Read cached Series Detail V2 aggregate payload. */
    suspend fun getCachedSeriesDetailV2(seriesId: Int): InkitaDetailV2?

    /** Return the last update timestamp for Series Detail V2. */
    suspend fun getSeriesDetailV2UpdatedAt(seriesId: Int): Long?

    /** Clear all cache data (DB + thumbnails). */
    suspend fun clearAllCache()

    /** Clear cached thumbnails only. */
    suspend fun clearThumbnails()

    /** Clear cached database data only. */
    suspend fun clearDatabase()

    /** Clear cached detail payloads only. */
    suspend fun clearDetails()

    /** Return total cache size in bytes. */
    suspend fun getCacheSizeBytes(): Long

    /** Return cache stats snapshot for the stats screen. */
    suspend fun getCacheStats(): CacheStats
}
