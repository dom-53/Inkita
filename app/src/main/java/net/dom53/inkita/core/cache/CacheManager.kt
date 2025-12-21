package net.dom53.inkita.core.cache

import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadingList
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2

interface CacheManager {
    suspend fun policy(): CachePolicy

    suspend fun enrichThumbnails(series: List<Series>): List<Series>

    suspend fun cacheTabResults(
        key: LibraryTabCacheKey,
        series: List<Series>,
    )

    suspend fun cacheBrowsePage(
        queryKey: String,
        page: Int,
        series: List<Series>,
    )

    suspend fun cacheSeriesDetail(detail: SeriesDetail)

    suspend fun getCachedSeries(query: SeriesQuery): List<Series>

    suspend fun getCachedSeriesForTab(key: LibraryTabCacheKey): List<Series>

    suspend fun getCachedBrowsePage(
        queryKey: String,
        page: Int,
    ): List<Series>

    suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail?

    suspend fun cacheLibraryV2SeriesList(
        listType: String,
        listKey: String,
        series: List<Series>,
    )

    suspend fun getCachedLibraryV2SeriesList(
        listType: String,
        listKey: String,
    ): List<Series>

    suspend fun getLibraryV2SeriesListUpdatedAt(
        listType: String,
        listKey: String,
    ): Long?

    suspend fun getLibraryV2CollectionsUpdatedAt(listType: String): Long?

    suspend fun getLibraryV2ReadingListsUpdatedAt(listType: String): Long?

    suspend fun getLibraryV2PeopleUpdatedAt(
        listType: String,
        page: Int,
    ): Long?

    suspend fun cacheLibraryV2Collections(
        listType: String,
        collections: List<Collection>,
    )

    suspend fun getCachedLibraryV2Collections(listType: String): List<Collection>

    suspend fun cacheLibraryV2ReadingLists(
        listType: String,
        readingLists: List<ReadingList>,
    )

    suspend fun getCachedLibraryV2ReadingLists(listType: String): List<ReadingList>

    suspend fun cacheLibraryV2People(
        listType: String,
        page: Int,
        people: List<Person>,
    )

    suspend fun getCachedLibraryV2People(
        listType: String,
        page: Int,
    ): List<Person>

    suspend fun cacheSeriesDetailV2(
        seriesId: Int,
        detail: InkitaDetailV2,
    )

    suspend fun getCachedSeriesDetailV2(seriesId: Int): InkitaDetailV2?

    suspend fun getSeriesDetailV2UpdatedAt(seriesId: Int): Long?

    suspend fun clearAllCache()

    suspend fun clearThumbnails()

    suspend fun clearDatabase()

    suspend fun clearDetails()

    suspend fun getCacheSizeBytes(): Long

    suspend fun getCacheStats(): CacheStats
}
