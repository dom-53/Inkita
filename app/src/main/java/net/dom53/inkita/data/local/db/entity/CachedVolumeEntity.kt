package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_volumes")
data class CachedVolumeEntity(
    @PrimaryKey val id: Int,
    val seriesId: Int,
    val name: String?,
    val minNumber: Float?,
    val maxNumber: Float?,
    val pages: Int?,
    val pagesRead: Int?,
    val readState: String?,
    val minHoursToRead: Double?,
    val maxHoursToRead: Double?,
    val avgHoursToRead: Double?,
    val bookId: Int?,
    val updatedAt: Long,
)
