package net.dom53.inkita.core.downloadv2

import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity

interface DownloadStrategyV2 {
    val format: String

    suspend fun enqueue(job: DownloadJobV2Entity): DownloadJobV2Entity
}
