package net.dom53.inkita.data.api.dto

data class ChapterDto(
    val id: Int,
    val minNumber: Float?,
    val maxNumber: Float?,
    val title: String?,
    val range: String? = null,
    val isSpecial: Boolean? = null,
    val pages: Int? = null,
    val pagesRead: Int? = null,
)
