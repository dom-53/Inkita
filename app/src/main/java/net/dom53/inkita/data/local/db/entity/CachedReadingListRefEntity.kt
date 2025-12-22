package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_reading_list_refs_v2",
    primaryKeys = ["listType", "readingListId"],
)
data class CachedReadingListRefEntity(
    val listType: String,
    val readingListId: Int,
    val position: Int,
    val updatedAt: Long,
)
