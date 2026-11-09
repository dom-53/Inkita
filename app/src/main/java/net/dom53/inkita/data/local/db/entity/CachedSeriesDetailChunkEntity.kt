package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "cached_series_detail_chunks",
    primaryKeys = ["seriesId", "chunkIndex"],
    foreignKeys = [
        ForeignKey(
            entity = CachedSeriesDetailV2Entity::class,
            parentColumns = ["seriesId"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class CachedSeriesDetailChunkEntity(
    val seriesId: Int,
    val chunkIndex: Int,
    val payload: ByteArray,
)
