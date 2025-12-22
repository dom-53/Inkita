package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_person_refs_v2",
    primaryKeys = ["listType", "page", "personId"],
)
data class CachedPersonRefEntity(
    val listType: String,
    val page: Int,
    val personId: Int,
    val position: Int,
    val updatedAt: Long,
)
