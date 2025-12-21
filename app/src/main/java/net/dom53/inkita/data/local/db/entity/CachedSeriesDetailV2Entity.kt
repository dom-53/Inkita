package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_series_detail_v2")
data class CachedSeriesDetailV2Entity(
    @PrimaryKey val seriesId: Int,
    val summary: String?,
    val publicationStatus: Int?,
    val genres: String?,
    val tags: String?,
    val writers: String?,
    val releaseYear: Int?,
    val wordCount: Long?,
    val timeLeftMin: Double?,
    val timeLeftMax: Double?,
    val timeLeftAvg: Double?,
    val hasProgress: Boolean?,
    val wantToRead: Boolean?,
    val updatedAt: Long,
)
