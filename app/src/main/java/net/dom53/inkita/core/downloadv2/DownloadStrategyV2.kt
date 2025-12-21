package net.dom53.inkita.core.downloadv2

interface DownloadStrategyV2 {
    val key: String

    suspend fun enqueue(request: DownloadRequestV2): Long

    suspend fun run(jobId: Long)
}
