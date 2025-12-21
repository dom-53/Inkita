package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_series_detail_related_refs_v2",
    primaryKeys = ["seriesId", "relationType", "targetType", "targetId"],
)
data class CachedSeriesDetailRelatedRefEntity(
    val seriesId: Int,
    val relationType: String,
    val targetType: String,
    val targetId: Int,
    val position: Int,
    val updatedAt: Long,
)
