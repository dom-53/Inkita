package net.dom53.inkita.data.api.dto

data class BookInfoDto(
    val pages: Int?,
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val libraryId: Int? = null,
    val bookTitle: String? = null,
    val chapterTitle: String? = null,
    val seriesName: String? = null,
)
