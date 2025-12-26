package net.dom53.inkita.domain.repository

import net.dom53.inkita.domain.model.RecentlyUpdatedSeriesItem
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.filter.SeriesQuery

interface SeriesRepository {
    suspend fun getSeries(
        query: SeriesQuery,
        prefetchThumbnails: Boolean = true,
    ): List<Series>

    suspend fun getOnDeckSeries(
        pageNumber: Int,
        pageSize: Int,
        libraryId: Int = 0,
    ): List<Series>

    suspend fun getRecentlyUpdatedSeries(
        pageNumber: Int,
        pageSize: Int,
    ): List<RecentlyUpdatedSeriesItem>

    suspend fun getRecentlyAddedSeries(
        pageNumber: Int,
        pageSize: Int,
    ): List<Series>

    suspend fun getWantToReadSeries(
        pageNumber: Int,
        pageSize: Int,
    ): List<Series>

    suspend fun getSeriesForCollection(
        collectionId: Int,
        pageNumber: Int,
        pageSize: Int,
    ): List<Series>

    suspend fun getSeriesForLibrary(
        libraryId: Int,
        pageNumber: Int,
        pageSize: Int,
    ): List<Series>
}
