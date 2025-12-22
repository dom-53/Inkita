package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_collections_v2")
data class CachedCollectionV2Entity(
    @PrimaryKey val id: Int,
    val name: String,
    val updatedAt: Long,
)
