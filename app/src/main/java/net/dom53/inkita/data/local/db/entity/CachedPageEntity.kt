package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_pages",
    primaryKeys = ["chapterId", "page"],
)
data class CachedPageEntity(
    val chapterId: Int,
    val page: Int,
    val html: String,
    val updatedAt: Long,
)
