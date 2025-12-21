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
import net.dom53.inkita.data.mapper.buildChaptersFromPages
import net.dom53.inkita.data.mapper.computeSeriesReadState
import net.dom53.inkita.data.mapper.flattenToc
import net.dom53.inkita.data.mapper.mergeWith
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.data.mapper.toFilterV2Dto
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.RecentlyUpdatedSeriesItem
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.Volume
import net.dom53.inkita.domain.model.filter.KavitaSortField
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.library.LibraryTabCacheKey
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

    override suspend fun getSeriesDetail(seriesId: Int): SeriesDetail {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            val cached = cacheManager.getCachedSeriesDetail(seriesId)
            LoggingManager.d("SeriesRepo", "Detail offline-only (not configured), cached=${cached != null}")
            if (cached != null) return cached
            throw Exception("The app is not configured, API key is missing.")
        }

        if (!NetworkUtils.isOnline(context)) {
            val cached = cacheManager.getCachedSeriesDetail(seriesId)
            LoggingManager.w("SeriesRepo", "Offline; using cached detail for $seriesId: ${cached != null}")
            if (cached != null) return cached
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val cachedFallback = cacheManager.getCachedSeriesDetail(seriesId).also { LoggingManager.d("SeriesRepo", "Cached detail fallback present=${it != null}") }

        return try {
            val seriesResponse = api.getSeriesById(seriesId)
            if (!seriesResponse.isSuccessful) {
                LoggingManager.w("SeriesRepo", "Series response HTTP ${seriesResponse.code()}")
                return cachedFallback ?: throw Exception("Failed to load series (HTTP ${seriesResponse.code()})")
            }
            val seriesDto =
                seriesResponse.body()
                    ?: return cachedFallback ?: throw Exception("Empty response when loading series")

            val metadataResponse = api.getSeriesMetadata(seriesId)
            val metadata =
                if (metadataResponse.isSuccessful) {
                    metadataResponse.body()?.toDomain()
                } else {
                    null
                }

            val timeLeft = api.getTimeLeft(seriesId).takeIf { it.isSuccessful }?.body()

            val detailResponse = api.getSeriesDetail(seriesId)
            if (!detailResponse.isSuccessful) {
                LoggingManager.w("SeriesRepo", "Detail response HTTP ${detailResponse.code()}")
                return cachedFallback ?: throw Exception("Failed to load series detail (HTTP ${detailResponse.code()})")
            }

            val detailBody =
                detailResponse.body()
                    ?: return cachedFallback ?: throw Exception("Empty detail response")

            val seriesDomain = seriesDto.toDomain()
            val computedReadState =
                computeSeriesReadState(
                    unreadCount = detailBody.unreadCount,
                    totalCount = detailBody.totalCount,
                )
            val finalReadState =
                when {
                    seriesDomain.readState == ReadState.Completed || seriesDomain.readState == ReadState.InProgress -> seriesDomain.readState
                    computedReadState != null -> computedReadState
                    else -> seriesDomain.readState
                }

            val volumes =
                detailBody.volumes.orEmpty().map { volumeDto ->
                    val enrichedVolumeDto =
                        api
                            .getVolumeById(volumeDto.id)
                            .takeIf { it.isSuccessful }
                            ?.body() ?: volumeDto

                    val mergedVolume = volumeDto.mergeWith(enrichedVolumeDto)
                    val topChapter = enrichedVolumeDto.chapters?.firstOrNull()
                    val tocItems =
                        topChapter?.let { chapter ->
                            val tocResponse = api.getBookChapters(chapter.id)
                            if (tocResponse.isSuccessful) {
                                tocResponse.body().orEmpty().flatMap { flattenToc(it) }
                            } else {
                                emptyList()
                            }
                        } ?: emptyList()

                    val pagesCountFromBook =
                        topChapter?.let { chapter ->
                            val infoResponse = api.getBookInfo(chapter.id)
                            if (infoResponse.isSuccessful) {
                                infoResponse.body()?.pages ?: 0
                            } else {
                                0
                            }
                        } ?: 0
                    val pagesCount =
                        if (pagesCountFromBook > 0) {
                            pagesCountFromBook
                        } else {
                            topChapter?.pages ?: 0
                        }

                    val pagesRead = if (finalReadState == ReadState.Unread) 0 else (topChapter?.pagesRead ?: 0)
                    val bookChapters =
                        buildChaptersFromPages(
                            volumeId = mergedVolume.id,
                            topChapterId = topChapter?.id,
                            pagesCount = pagesCount,
                            pagesRead = pagesRead,
                            tocItems = tocItems,
                        )

                    mergedVolume.toDomain(
                        chapters = bookChapters,
                        bookId = topChapter?.id,
                    )
                }

            val specials =
                detailBody.specials.orEmpty().map { chapterDto ->
                    val pagesCount = chapterDto.pages ?: 0
                    val bookChapters =
                        buildChaptersFromPages(
                            volumeId = chapterDto.id,
                            topChapterId = chapterDto.id,
                            pagesCount = pagesCount,
                            pagesRead = chapterDto.pagesRead ?: 0,
                            tocItems = emptyList(),
                        ).map { it.copy(isSpecial = true) }
                    Volume(
                        id = chapterDto.id,
                        name = chapterDto.title ?: chapterDto.range,
                        minNumber = null,
                        maxNumber = null,
                        chapters = bookChapters,
                        minHoursToRead = null,
                        maxHoursToRead = null,
                        avgHoursToRead = null,
                        bookId = chapterDto.id,
                    )
                }

            val detail =
                SeriesDetail(
                    series = seriesDomain.copy(readState = finalReadState),
                    metadata = metadata,
                    volumes = volumes,
                    specials = specials,
                    unreadCount = detailBody.unreadCount,
                    totalCount = detailBody.totalCount,
                    readState = finalReadState,
                    minHoursToRead = seriesDomain.minHoursToRead,
                    maxHoursToRead = seriesDomain.maxHoursToRead,
                    avgHoursToRead = seriesDomain.avgHoursToRead,
                    timeLeftMin = timeLeft?.minHours,
                    timeLeftMax = timeLeft?.maxHours,
                    timeLeftAvg = timeLeft?.avgHours,
                )
            cacheSeriesDetail(detail)
            LoggingManager.d("SeriesRepo", "Cached detail stored for series $seriesId (volumes=${volumes.size})")
            detail
        } catch (e: Exception) {
            LoggingManager.e("SeriesRepo", "Detail fetch failed: ${e.message}")
            cachedFallback ?: throw e
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

    override suspend fun getCachedSeries(query: SeriesQuery): List<Series> = cacheManager.getCachedSeries(query)

    override suspend fun getCachedSeriesForTab(key: LibraryTabCacheKey): List<Series> = cacheManager.getCachedSeriesForTab(key)

    override suspend fun cacheTabResults(
        key: LibraryTabCacheKey,
        series: List<Series>,
    ) {
        cacheManager.cacheTabResults(key, series)
    }

    override suspend fun getCachedBrowsePage(
        queryKey: String,
        page: Int,
    ): List<Series> = cacheManager.getCachedBrowsePage(queryKey, page)

    override suspend fun cacheBrowsePage(
        queryKey: String,
        page: Int,
        series: List<Series>,
    ) {
        cacheManager.cacheBrowsePage(queryKey, page, series)
    }

    override suspend fun getCachedSeriesDetail(seriesId: Int): SeriesDetail? = cacheManager.getCachedSeriesDetail(seriesId)

    private suspend fun cacheSeriesDetail(detail: SeriesDetail) {
        cacheManager.cacheSeriesDetail(detail)
    }
}
