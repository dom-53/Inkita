package net.dom53.inkita.data.api.dto

data class VolumeDto(
    val id: Int,
    val name: String?,
    val title: String? = null,
    val minNumber: Float?,
    val maxNumber: Float?,
    val chapters: List<ChapterDto>?,
    val minHoursToRead: Double? = null,
    val maxHoursToRead: Double? = null,
    val avgHoursToRead: Double? = null,
)
