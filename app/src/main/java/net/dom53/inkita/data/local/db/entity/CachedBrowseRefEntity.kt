package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_browse_refs",
    primaryKeys = ["queryKey", "page", "seriesId"],
)
data class CachedBrowseRefEntity(
    val queryKey: String,
    val page: Int,
    val seriesId: Int,
    val updatedAt: Long,
)
