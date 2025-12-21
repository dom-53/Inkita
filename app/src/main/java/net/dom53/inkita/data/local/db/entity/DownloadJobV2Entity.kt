package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_jobs_v2")
data class DownloadJobV2Entity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val format: String?,
    val strategy: String?,
    val seriesId: Int?,
    val volumeId: Int?,
    val chapterId: Int?,
    val status: String,
    val totalItems: Int?,
    val completedItems: Int?,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?,
) {
    companion object {
        const val TYPE_SERIES = "series"
        const val TYPE_VOLUME = "volume"
        const val TYPE_CHAPTER = "chapter"

        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELED = "canceled"
    }
}
