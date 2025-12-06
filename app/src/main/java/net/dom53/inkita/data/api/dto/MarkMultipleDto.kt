package net.dom53.inkita.data.api.dto

data class MarkMultipleDto(
    val seriesId: Int,
    val volumeIds: List<Int> = emptyList(),
    val chapterIds: List<Int> = emptyList(),
)
