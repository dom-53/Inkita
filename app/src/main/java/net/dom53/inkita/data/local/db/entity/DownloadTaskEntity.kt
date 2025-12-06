package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Int,
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val pageStart: Int? = null,
    val pageEnd: Int? = null,
    val type: String,
    val priority: Int = 0,
    val state: String = STATE_PENDING,
    val workId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val error: String? = null,
    val progress: Int = 0,
    val total: Int = 0,
    val bytes: Long = 0L,
    val bytesTotal: Long = 0L,
) {
    companion object {
        const val TYPE_SERIES = "series"
        const val TYPE_VOLUME = "volume"
        const val TYPE_PAGES = "pages"
        const val TYPE_PDF = "pdf"

        const val STATE_PENDING = "pending"
        const val STATE_RUNNING = "running"
        const val STATE_PAUSED = "paused"
        const val STATE_FAILED = "failed"
        const val STATE_COMPLETED = "completed"
        const val STATE_CANCELED = "canceled"
    }
}
