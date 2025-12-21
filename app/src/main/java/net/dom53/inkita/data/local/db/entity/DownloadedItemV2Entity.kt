package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items_v2")
data class DownloadedItemV2Entity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val type: String,
    val chapterId: Int?,
    val page: Int?,
    val url: String?,
    val localPath: String?,
    val bytes: Long?,
    val checksum: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?,
) {
    companion object {
        const val TYPE_PAGE = "page"
        const val TYPE_FILE = "file"
        const val TYPE_ASSET = "asset"

        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELED = "canceled"
    }
}
