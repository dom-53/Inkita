package net.dom53.inkita.ui.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.AnnotationDto
import net.dom53.inkita.data.api.dto.AppUserCollectionDto
import net.dom53.inkita.data.api.dto.BookmarkDto
import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.HourEstimateRangeDto
import net.dom53.inkita.data.api.dto.RatingDto
import net.dom53.inkita.data.api.dto.ReadingListDto
import net.dom53.inkita.data.api.dto.RelatedSeriesDto
import net.dom53.inkita.data.api.dto.SeriesDetailDto
import net.dom53.inkita.data.api.dto.SeriesDetailPlusDto
import net.dom53.inkita.data.api.dto.SeriesDto
import net.dom53.inkita.data.api.dto.SeriesMetadataDto
import retrofit2.Response

data class SeriesDetailUiStateV2(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: InkitaDetailV2? = null,
    val showLoadedToast: Boolean = false,
)

data class InkitaDetailV2(
    val series: SeriesDto?,
    val metadata: SeriesMetadataDto?,
    val wantToRead: Boolean?,
    val readingLists: List<ReadingListDto>?,
    val collections: List<AppUserCollectionDto>?,
    val bookmarks: List<BookmarkDto>?,
    val annotations: List<AnnotationDto>?,
    val timeLeft: HourEstimateRangeDto?,
    val hasProgress: Boolean?,
    val continuePoint: ChapterDto?,
    val seriesDetailPlus: SeriesDetailPlusDto?,
    val related: RelatedSeriesDto?,
    val detail: SeriesDetailDto?,
    val rating: RatingDto?,
)

class SeriesDetailViewModelV2(
    val seriesId: Int,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(SeriesDetailUiStateV2())
    val state: StateFlow<SeriesDetailUiStateV2> = _state

    init {
        load()
    }

    fun consumeLoadedToast() {
        _state.update { it.copy(showLoadedToast = false) }
    }

    fun toggleWantToRead() {
        viewModelScope.launch {
            val current = _state.value.detail ?: return@launch
            val currentSeriesId = current.series?.id ?: seriesId
            val config = appPreferences.configFlow.first()
            if (!config.isConfigured) {
                _state.update { it.copy(error = "Not configured") }
                return@launch
            }
            val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
            val want = current.wantToRead == true
            val response =
                if (want) {
                    api.removeWantToRead(
                        net.dom53.inkita.data.api.dto
                            .WantToReadDto(listOf(currentSeriesId)),
                    )
                } else {
                    api.addWantToRead(
                        net.dom53.inkita.data.api.dto
                            .WantToReadDto(listOf(currentSeriesId)),
                    )
                }
            if (!response.isSuccessful) {
                _state.update { it.copy(error = "Failed to update want-to-read") }
                return@launch
            }
            _state.update { state ->
                state.copy(
                    detail = state.detail?.copy(wantToRead = !want),
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val config = appPreferences.configFlow.first()
            if (!config.isConfigured) {
                _state.update { it.copy(isLoading = false, error = "Not configured") }
                return@launch
            }

            val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
            val errors = mutableListOf<String>()

            val seriesDeferred = async { api.getSeriesById(seriesId) }
            val metadataDeferred = async { api.getSeriesMetadata(seriesId) }
            val wantDeferred = async { api.hasWantToRead(seriesId) }
            val readingListsDeferred = async { api.getReadingListsForSeries(seriesId) }
            val collectionsDeferred = async { api.getCollectionsForSeriesOwned(seriesId, ownedOnly = false) }
            val bookmarksDeferred = async { api.getSeriesBookmarks(seriesId) }
            val annotationsDeferred = async { api.getAnnotationsForSeries(seriesId) }
            val timeLeftDeferred = async { api.getSeriesTimeLeft(seriesId) }
            val hasProgressDeferred = async { api.getHasProgress(seriesId) }
            val continuePointDeferred = async { api.getContinuePoint(seriesId) }
            val seriesDetailDeferred = async { api.getSeriesDetail(seriesId) }
            val relatedDeferred = async { api.getAllRelated(seriesId) }
            val ratingDeferred = async { api.getOverallSeriesRating(seriesId) }
            val librariesDeferred = async { api.getLibrariesFilter() }

            val seriesResponse = seriesDeferred.await()
            val series = seriesResponse.extract("series", errors)
            val metadata = metadataDeferred.await().extract("metadata", errors)
            val wantToRead = wantDeferred.await().extract("wantToRead", errors)
            val readingLists = readingListsDeferred.await().extractList("readingLists", errors)
            val collections = collectionsDeferred.await().extractList("collections", errors)
            val bookmarks = bookmarksDeferred.await().extractList("bookmarks", errors)
            val annotations = annotationsDeferred.await().extractList("annotations", errors)
            val timeLeft = timeLeftDeferred.await().extract("timeLeft", errors)
            val hasProgress = hasProgressDeferred.await().extract("hasProgress", errors)
            val continuePoint = continuePointDeferred.await().extract("continuePoint", errors)
            val seriesDetail = seriesDetailDeferred.await().extract("seriesDetail", errors)
            val related = relatedDeferred.await().extract("related", errors)
            val rating = ratingDeferred.await().extract("rating", errors)

            val libraries = librariesDeferred.await().extractList("libraries", errors)
            val libraryType =
                series?.libraryId?.let { id ->
                    libraries?.firstOrNull { it.id == id }?.type
                }
            val seriesDetailPlus =
                if (libraryType != null) {
                    api.getSeriesDetailPlus(seriesId, libraryType).extract("seriesDetailPlus", errors)
                } else {
                    null
                }

            val detail =
                InkitaDetailV2(
                    series = series,
                    metadata = metadata,
                    wantToRead = wantToRead,
                    readingLists = readingLists,
                    collections = collections,
                    bookmarks = bookmarks,
                    annotations = annotations,
                    timeLeft = timeLeft,
                    hasProgress = hasProgress,
                    continuePoint = continuePoint,
                    seriesDetailPlus = seriesDetailPlus,
                    related = related,
                    detail = seriesDetail,
                    rating = rating,
                )

            _state.update {
                it.copy(
                    isLoading = false,
                    error = errors.firstOrNull(),
                    detail = detail,
                    showLoadedToast = true,
                )
            }
        }
    }

    private fun <T> Response<T>.extract(
        label: String,
        errors: MutableList<String>,
    ): T? {
        if (!isSuccessful) {
            errors.add("$label: HTTP ${code()} ${message()}")
            return null
        }
        return body()
    }

    private fun <T> Response<List<T>>.extractList(
        label: String,
        errors: MutableList<String>,
    ): List<T>? {
        if (!isSuccessful) {
            errors.add("$label: HTTP ${code()} ${message()}")
            return null
        }
        return body() ?: emptyList()
    }

    companion object {
        fun provideFactory(
            seriesId: Int,
            appPreferences: AppPreferences,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SeriesDetailViewModelV2(seriesId, appPreferences) as T
                }
            }
    }
}
