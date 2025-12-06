package net.dom53.inkita.data.api.dto

data class RelatedSeriesDto(
    val sourceSeriesId: Int? = null,
    val sequels: List<SeriesDto>? = null,
    val prequels: List<SeriesDto>? = null,
    val spinOffs: List<SeriesDto>? = null,
    val adaptations: List<SeriesDto>? = null,
    val sideStories: List<SeriesDto>? = null,
    val characters: List<SeriesDto>? = null,
    val contains: List<SeriesDto>? = null,
    val others: List<SeriesDto>? = null,
    val alternativeSettings: List<SeriesDto>? = null,
    val alternativeVersions: List<SeriesDto>? = null,
    val doujinshis: List<SeriesDto>? = null,
    val parent: List<SeriesDto>? = null,
    val editions: List<SeriesDto>? = null,
    val annuals: List<SeriesDto>? = null,
)
