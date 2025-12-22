package net.dom53.inkita.core.downloadv2

/**
 * Strategy interface for Download V2.
 *
 * Implementations define how to enqueue and execute downloads for a specific format.
 */
interface DownloadStrategyV2 {
    /** Key used to match [DownloadRequestV2.format]. */
    val key: String

    /**
     * Enqueue a new download request and return the created job id, or -1 if rejected.
     */
    suspend fun enqueue(request: DownloadRequestV2): Long

    /**
     * Execute a queued job by its id.
     */
    suspend fun run(jobId: Long)
}
