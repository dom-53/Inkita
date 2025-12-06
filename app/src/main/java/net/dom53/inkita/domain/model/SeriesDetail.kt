package net.dom53.inkita.domain.model

data class SeriesDetail(
    val series: Series,
    val metadata: SeriesMetadata?,
    val volumes: List<Volume>,
    val specials: List<Volume> = emptyList(),
    val unreadCount: Int?,
    val totalCount: Int?,
    val readState: ReadState?,
    val minHoursToRead: Double? = null,
    val maxHoursToRead: Double? = null,
    val avgHoursToRead: Double? = null,
    val timeLeftMin: Double? = null,
    val timeLeftMax: Double? = null,
    val timeLeftAvg: Double? = null,
)
