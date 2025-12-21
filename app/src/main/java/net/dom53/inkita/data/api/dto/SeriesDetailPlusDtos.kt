package net.dom53.inkita.data.api.dto

data class SeriesDetailPlusDto(
    val recommendations: RecommendationDto? = null,
    val reviews: List<UserReviewDto>? = null,
    val ratings: List<RatingDto>? = null,
    val series: ExternalSeriesDetailDto? = null,
)

data class RecommendationDto(
    val ownedSeries: List<SeriesDto>? = null,
    val externalSeries: List<ExternalSeriesDto>? = null,
)

data class ExternalSeriesDto(
    val name: String? = null,
    val coverUrl: String? = null,
    val url: String? = null,
    val summary: String? = null,
    val aniListId: Int? = null,
    val malId: Long? = null,
    val provider: Int? = null,
)

data class ExternalSeriesDetailDto(
    val name: String? = null,
    val aniListId: Int? = null,
    val malId: Long? = null,
    val cbrId: Int? = null,
    val synonyms: List<String>? = null,
    val plusMediaFormat: Int? = null,
    val siteUrl: String? = null,
    val coverUrl: String? = null,
    val genres: List<String>? = null,
    val staff: List<SeriesStaffDto>? = null,
    val tags: List<MetadataTagDto>? = null,
    val summary: String? = null,
    val provider: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val averageScore: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val relations: List<SeriesRelationship>? = null,
    val characters: List<SeriesCharacter>? = null,
    val publisher: String? = null,
    val chapterDtos: List<ExternalChapterDto>? = null,
)

data class ExternalChapterDto(
    val title: String? = null,
    val issueNumber: String? = null,
    val criticRating: Double? = null,
    val userRating: Double? = null,
    val summary: String? = null,
    val writers: List<String>? = null,
    val artists: List<String>? = null,
    val releaseDate: String? = null,
    val publisher: String? = null,
    val coverImageUrl: String? = null,
    val issueUrl: String? = null,
    val criticReviews: List<UserReviewDto>? = null,
    val userReviews: List<UserReviewDto>? = null,
)

data class SeriesStaffDto(
    val name: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val url: String? = null,
    val role: String? = null,
    val imageUrl: String? = null,
    val gender: String? = null,
    val description: String? = null,
)

data class MetadataTagDto(
    val name: String? = null,
    val description: String? = null,
    val rank: Int? = null,
    val isGeneralSpoiler: Boolean? = null,
    val isMediaSpoiler: Boolean? = null,
    val isAdult: Boolean? = null,
)

data class SeriesRelationship(
    val aniListId: Int? = null,
    val malId: Int? = null,
    val seriesName: ALMediaTitle? = null,
    val relation: Int? = null,
    val provider: Int? = null,
    val plusMediaFormat: Int? = null,
)

data class ALMediaTitle(
    val englishTitle: String? = null,
    val romajiTitle: String? = null,
    val nativeTitle: String? = null,
    val preferredTitle: String? = null,
)

data class SeriesCharacter(
    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val role: Int? = null,
)

data class UserReviewDto(
    val tagline: String? = null,
    val body: String? = null,
    val bodyJustText: String? = null,
    val seriesId: Int? = null,
    val chapterId: Int? = null,
    val libraryId: Int? = null,
    val username: String? = null,
    val userId: Int? = null,
    val totalVotes: Int? = null,
    val rating: Float? = null,
    val rawBody: String? = null,
    val score: Int? = null,
    val siteUrl: String? = null,
    val isExternal: Boolean? = null,
    val provider: Int? = null,
    val authority: Int? = null,
)
