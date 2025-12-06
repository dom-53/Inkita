package net.dom53.inkita.data.api.dto

data class ReadHistoryEventDto(
    val userId: Int? = null,
    val userName: String? = null,
    val libraryId: Int? = null,
    val seriesId: Int? = null,
    val seriesName: String? = null,
    val readDate: String? = null,
    val readDateUtc: String? = null,
    val chapterId: Int? = null,
    val chapterNumber: Float? = null,
)
