package net.dom53.inkita.domain.model

data class Volume(
    val id: Int,
    val name: String?,
    val minNumber: Float?,
    val maxNumber: Float?,
    val chapters: List<Chapter>,
    val minHoursToRead: Double? = null,
    val maxHoursToRead: Double? = null,
    val avgHoursToRead: Double? = null,
    val bookId: Int? = null,
)
