package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_series_volume_refs_v2",
    primaryKeys = ["seriesId", "volumeId"],
)
data class CachedSeriesVolumeRefEntity(
    val seriesId: Int,
    val volumeId: Int,
    val position: Int,
    val updatedAt: Long,
)
