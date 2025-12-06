package net.dom53.inkita.data.api.dto

data class BookChapterItemDto(
    val title: String?,
    val part: String?,
    val page: Int?,
    val children: List<BookChapterItemDto>?,
)
