package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "downloaded_pages",
    primaryKeys = ["chapterId", "page"],
)
data class DownloadedPageEntity(
    val seriesId: Int,
    val volumeId: Int?,
    val chapterId: Int,
    val page: Int,
    val htmlPath: String,
    val assetsDir: String? = null,
    val sizeBytes: Long = 0,
    val status: String = STATUS_COMPLETED,
    val checksum: String? = null,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_PARTIAL = "partial"
        const val STATUS_FAILED = "failed"
    }
}
