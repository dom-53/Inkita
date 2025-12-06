package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_chapters",
    primaryKeys = ["volumeId", "pageIndex"],
)
data class CachedChapterEntity(
    val volumeId: Int,
    val pageIndex: Int,
    val title: String,
    val status: String?,
    val isSpecial: Boolean = false,
    val updatedAt: Long,
)
