package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_series_detail")
data class CachedSeriesDetailEntity(
    @PrimaryKey val seriesId: Int,
    val unreadCount: Int?,
    val totalCount: Int?,
    val readState: String?,
    val timeLeftMin: Double?,
    val timeLeftMax: Double?,
    val timeLeftAvg: Double?,
    val metadataSummary: String?,
    val metadataWriters: String?, // comma-separated names
    val metadataTags: String?, // comma-separated titles
    val metadataPublicationStatus: Int?,
    val specialsVolumeIds: String? = null, // comma-separated synthetic volume ids for specials
    val updatedAt: Long,
)
