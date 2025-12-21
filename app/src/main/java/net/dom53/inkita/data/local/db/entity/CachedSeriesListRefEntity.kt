package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_series_list_refs_v2",
    primaryKeys = ["listType", "listKey", "seriesId"],
)
data class CachedSeriesListRefEntity(
    val listType: String,
    val listKey: String,
    val seriesId: Int,
    val position: Int,
    val updatedAt: Long,
)
