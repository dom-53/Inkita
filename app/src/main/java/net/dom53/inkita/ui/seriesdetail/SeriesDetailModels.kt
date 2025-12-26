package net.dom53.inkita.ui.seriesdetail

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

data class SeriesDetailUiStateV2(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: InkitaDetailV2? = null,
    val showLoadedToast: Boolean = false,
    val collections: List<net.dom53.inkita.domain.model.Collection> = emptyList(),
    val isLoadingCollections: Boolean = false,
    val collectionError: String? = null,
    val collectionsWithSeries: Set<Int> = emptySet(),
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
    val readerProgress: ReaderProgressDto?,
)
