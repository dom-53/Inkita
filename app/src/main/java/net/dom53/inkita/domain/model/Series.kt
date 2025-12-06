package net.dom53.inkita.domain.model

data class Series(
    val id: Int,
    val name: String,
    val summary: String?,
    val libraryId: Int?,
    val format: Format?,
    val pages: Int?,
    val pagesRead: Int?,
    val readState: ReadState?,
    val minHoursToRead: Double? = null,
    val maxHoursToRead: Double? = null,
    val avgHoursToRead: Double? = null,
    val localThumbPath: String? = null,
)
