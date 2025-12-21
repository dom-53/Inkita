package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_volume_chapter_refs_v2",
    primaryKeys = ["volumeId", "chapterId"],
)
data class CachedVolumeChapterRefEntity(
    val volumeId: Int,
    val chapterId: Int,
    val position: Int,
    val updatedAt: Long,
)
