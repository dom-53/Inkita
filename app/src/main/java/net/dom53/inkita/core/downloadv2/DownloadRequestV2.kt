package net.dom53.inkita.core.downloadv2

data class DownloadRequestV2(
    val type: String,
    val format: String,
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val pageCount: Int? = null,
    val pageIndex: Int? = null,
    val priority: Int = 0,
)
