package net.dom53.inkita.core.cache

import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey

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

    suspend fun clearAllCache()

    suspend fun clearThumbnails()

    suspend fun clearDatabase()

    suspend fun clearDetails()

    suspend fun getCacheSizeBytes(): Long

    suspend fun getCacheStats(): CacheStats
}
