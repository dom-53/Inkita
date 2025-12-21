package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_volumes_v2")
data class CachedVolumeV2Entity(
    @PrimaryKey val id: Int,
    val seriesId: Int,
    val name: String?,
    val number: String?,
    val pages: Int?,
    val pagesRead: Int?,
    val wordCount: Long?,
    val minHoursToRead: Double?,
    val maxHoursToRead: Double?,
    val avgHoursToRead: Double?,
    val summary: String?,
    val releaseYear: Int?,
    val updatedAt: Long,
)
