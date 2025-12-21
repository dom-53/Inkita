package net.dom53.inkita.data.api.dto

data class BookmarkDto(
    val id: Int? = null,
    val page: Int? = null,
    val volumeId: Int? = null,
    val seriesId: Int? = null,
    val chapterId: Int? = null,
    val imageOffset: Int? = null,
    val xPath: String? = null,
    val series: SeriesDto? = null,
    val chapterTitle: String? = null,
)
