package net.dom53.inkita.domain.repository

import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey

interface SeriesRepository {
    suspend fun getSeries(
        query: SeriesQuery,
        prefetchThumbnails: Boolean = true,
    ): List<Series>

    suspend fun getSeriesDetail(seriesId: Int): SeriesDetail

    /**
     Cached series stored locally (best-effort).
     */
    suspend fun getCachedSeries(query: SeriesQuery): List<Series>

    suspend fun getCachedSeriesForTab(key: LibraryTabCacheKey): List<Series>

    suspend fun cacheTabResults(
        key: LibraryTabCacheKey,
        series: List<Series>,
    )

    suspend fun getCachedBrowsePage(
        queryKey: String,
        page: Int,
    ): List<Series>

    suspend fun cacheBrowsePage(
        queryKey: String,
        page: Int,
        series: List<Series>,
    )

    suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail?
}
