package net.dom53.inkita.data.api.dto

data class UserReadStatisticsDto(
    val totalPagesRead: Long? = null,
    val totalWordsRead: Long? = null,
    val timeSpentReading: Long? = null,
    val chaptersRead: Long? = null,
    val lastActive: String? = null,
    val avgHoursPerWeekSpentReading: Double? = null,
)
