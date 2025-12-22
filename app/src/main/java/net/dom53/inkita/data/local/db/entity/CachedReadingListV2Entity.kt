package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_reading_lists_v2")
data class CachedReadingListV2Entity(
    @PrimaryKey val id: Int,
    val title: String,
    val itemCount: Int?,
    val updatedAt: Long,
)
