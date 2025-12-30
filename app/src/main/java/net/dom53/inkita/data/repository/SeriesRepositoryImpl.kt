package net.dom53.inkita.data.repository

import android.content.Context
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.FilterV2Dto
import net.dom53.inkita.data.api.dto.SortOptionDto
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.data.mapper.toFilterV2Dto
import net.dom53.inkita.domain.model.RecentlyUpdatedSeriesItem
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.filter.KavitaSortField
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.repository.SeriesRepository
import java.io.IOException

class SeriesRepositoryImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val cacheManager: CacheManager,
) : SeriesRepository {
    override suspend fun getSeries(
        query: SeriesQuery,
        prefetchThumbnails: Boolean,
    ): List<Series> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            // No API key configured, skip network call.
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            LoggingManager.w("SeriesRepo", "Offline; skipping network fetch for query=$query")
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val filterDto = query.toFilterV2Dto()
        val response =
            api.getSeriesV2(
                filter = filterDto,
                pageNumber = query.page,
                pageSize = query.pageSize,
            )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw Exception(
                "Error loading series: HTTP ${response.code()} ${response.message()} body=$errorBody",
            )
        }

        val body = response.body() ?: emptyList()
        val domain = body.map { it.toDomain() }
        return if (prefetchThumbnails) {
            cacheManager.enrichThumbnails(domain)
        } else {
            domain
        }
    }

    override suspend fun getOnDeckSeries(
        pageNumber: Int,
        pageSize: Int,
        libraryId: Int,
    ): List<Series> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val response =
            api.getOnDeckSeries(
                libraryId = libraryId,
                pageNumber = pageNumber,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            throw Exception("Error loading on-deck series: HTTP ${response.code()} ${response.message()}")
        }

        val body = response.body().orEmpty()
        val domain = body.map { it.toDomain() }
        return cacheManager.enrichThumbnails(domain)
    }

    override suspend fun getRecentlyUpdatedSeries(
        pageNumber: Int,
        pageSize: Int,
    ): List<RecentlyUpdatedSeriesItem> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val response =
            api.getRecentlyUpdatedSeries(
                pageNumber = pageNumber,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            throw Exception("Error loading recently updated series: HTTP ${response.code()} ${response.message()}")
        }

        return response.body().orEmpty().map { it.toDomain() }
    }

    override suspend fun getRecentlyAddedSeries(
        pageNumber: Int,
        pageSize: Int,
    ): List<Series> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val filter =
            FilterV2Dto(
                id = null,
                name = null,
                statements = null,
                combination = 0,
                sortOptions =
                    SortOptionDto(
                        sortField = KavitaSortField.ItemAdded.id,
                        isAscending = false,
                    ),
                limitTo = 0,
            )

        val response =
            api.getRecentlyAddedSeries(
                filter = filter,
                pageNumber = pageNumber,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            throw Exception("Error loading recently added series: HTTP ${response.code()} ${response.message()}")
        }

        val body = response.body().orEmpty()
        val domain = body.map { it.toDomain() }
        return cacheManager.enrichThumbnails(domain)
    }

    override suspend fun getWantToReadSeries(
        pageNumber: Int,
        pageSize: Int,
    ): List<Series> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val filter =
            FilterV2Dto(
                id = null,
                name = null,
                statements = null,
                combination = 1,
                sortOptions =
                    SortOptionDto(
                        sortField = KavitaSortField.SortName.id,
                        isAscending = true,
                    ),
                limitTo = 0,
            )

        val response =
            api.getWantToRead(
                filter = filter,
                pageNumber = pageNumber,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            throw Exception("Error loading want-to-read: HTTP ${response.code()} ${response.message()}")
        }

        val body = response.body().orEmpty()
        val domain = body.map { it.toDomain() }
        return cacheManager.enrichThumbnails(domain)
    }

    override suspend fun getSeriesForCollection(
        collectionId: Int,
        pageNumber: Int,
        pageSize: Int,
    ): List<Series> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val filter =
            FilterV2Dto(
                id = null,
                name = null,
                statements =
                    listOf(
                        net.dom53.inkita.data.api.dto.FilterStatementDto(
                            field = 7,
                            value = collectionId.toString(),
                            comparison = 0,
                        ),
                    ),
                combination = 1,
                sortOptions =
                    SortOptionDto(
                        sortField = KavitaSortField.SortName.id,
                        isAscending = true,
                    ),
                limitTo = 0,
            )

        val response =
            api.getAllSeriesV2(
                filter = filter,
                pageNumber = pageNumber,
                pageSize = pageSize,
                context = 1,
            )

        if (!response.isSuccessful) {
            throw Exception("Error loading collection series: HTTP ${response.code()} ${response.message()}")
        }

        val body = response.body().orEmpty()
        val domain = body.map { it.toDomain() }
        return cacheManager.enrichThumbnails(domain)
    }

    override suspend fun getSeriesForLibrary(
        libraryId: Int,
        pageNumber: Int,
        pageSize: Int,
    ): List<Series> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val filter =
            FilterV2Dto(
                id = null,
                name = null,
                statements =
                    listOf(
                        net.dom53.inkita.data.api.dto.FilterStatementDto(
                            field = 19,
                            value = libraryId.toString(),
                            comparison = 0,
                        ),
                    ),
                combination = 1,
                sortOptions =
                    SortOptionDto(
                        sortField = KavitaSortField.SortName.id,
                        isAscending = true,
                    ),
                limitTo = 0,
            )

        val response =
            api.getSeriesV2(
                filter = filter,
                pageNumber = pageNumber,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            throw Exception("Error loading library series: HTTP ${response.code()} ${response.message()}")
        }

        val body = response.body().orEmpty()
        val domain = body.map { it.toDomain() }
        return cacheManager.enrichThumbnails(domain)
    }
}
