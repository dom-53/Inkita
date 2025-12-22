package net.dom53.inkita.core.downloadv2

/**
 * Request used by Download V2 to enqueue a new job.
 *
 * @param type Semantic job type (series/volume/chapter).
 * @param format Format key that selects a DownloadStrategyV2 (e.g., EPUB).
 * @param seriesId Optional series id for the job.
 * @param volumeId Optional volume id for the job.
 * @param chapterId Optional chapter id for the job.
 * @param pageCount Optional total pages (used for chapter/page fan-out).
 * @param pageIndex Optional single page index to download.
 * @param priority Higher value means higher priority (strategy-defined).
 */
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
