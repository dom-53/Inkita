package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_progress")
data class LocalReaderProgressEntity(
    @PrimaryKey val chapterId: Int,
    val page: Int? = null,
    val bookScrollId: String? = null,
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val libraryId: Int? = null,
    val lastModifiedUtc: Long = 0L,
)
