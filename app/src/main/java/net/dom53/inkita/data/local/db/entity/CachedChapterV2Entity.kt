package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_chapters_v2")
data class CachedChapterV2Entity(
    @PrimaryKey val id: Int,
    val volumeId: Int,
    val title: String?,
    val pages: Int?,
    val pagesRead: Int?,
    val summary: String?,
    val releaseDate: String?,
    val updatedAt: Long,
)
