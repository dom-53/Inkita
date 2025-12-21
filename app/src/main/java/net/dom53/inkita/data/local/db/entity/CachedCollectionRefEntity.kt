package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_collection_refs_v2",
    primaryKeys = ["listType", "collectionId"],
)
data class CachedCollectionRefEntity(
    val listType: String,
    val collectionId: Int,
    val position: Int,
    val updatedAt: Long,
)
