package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_series")
data class CachedSeriesEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val summary: String?,
    val libraryId: Int?,
    val formatId: Int?,
    val pages: Int?,
    val pagesRead: Int?,
    val readState: String?,
    val minHoursToRead: Double?,
    val maxHoursToRead: Double?,
    val avgHoursToRead: Double?,
    val coverUrl: String?,
    val localThumbPath: String?,
    val updatedAt: Long,
)
