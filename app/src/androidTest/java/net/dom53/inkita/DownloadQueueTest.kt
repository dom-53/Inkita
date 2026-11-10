package net.dom53.inkita

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.downloadv2.DownloadManagerV2
import net.dom53.inkita.core.downloadv2.DownloadPaths
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.DownloadStorageManager
import net.dom53.inkita.core.downloadv2.DownloadWorkerV2
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
import net.dom53.inkita.core.downloadv2.strategies.PdfDownloadStrategyV2
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity.Companion.STATUS_CANCELED
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity.Companion.STATUS_COMPLETED
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity.Companion.STATUS_FAILED
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity.Companion.STATUS_PAUSED
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity.Companion.STATUS_PENDING
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity.Companion.STATUS_RUNNING
import net.dom53.inkita.ui.download.DownloadQueueViewModel
import net.dom53.inkita.ui.download.DownloadQueueViewModelFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class DownloadQueueTest {
    @Test
    fun manualRetryStartsAnIdleWorker() =
        withQueue { queue ->
            queue.server.enqueue(MockResponse().setResponseCode(503))
            queue.server.enqueue(pageResponse())
            val id = queue.enqueue(pageCount = 1)
            queue.awaitStatus(id, STATUS_FAILED)
            queue.awaitWorkerIdle()

            queue.viewModel.retryTask(id)

            assertEquals(1, queue.awaitStatus(id, STATUS_COMPLETED).completedItems)
            assertEquals(2, queue.server.requestCount)
        }

    @Test
    fun pausedDownloadResumesWithoutRepeatingCompletedPages() =
        withQueue { queue ->
            val id = stopDuringSecondPage(queue, cancel = false)

            queue.viewModel.resumeTask(id)

            assertEquals(2, queue.awaitStatus(id, STATUS_COMPLETED).completedItems)
            assertEquals("1", queue.nextRequestedPage())
            assertEquals(3, queue.server.requestCount)
        }

    @Test
    fun canceledDownloadStaysCanceledWhenResponseArrives() =
        withQueue { queue ->
            stopDuringSecondPage(queue, cancel = true)
            assertEquals(2, queue.server.requestCount)
        }

    @Test
    fun cancelDuringPdfWriteRemovesOnlyTheUnfinishedCopy() =
        withQueue { queue ->
            queue.server.enqueue(MockResponse().setBody("{}"))
            queue.server.enqueue(MockResponse().setBody("New PDF").setBodyDelay(4, TimeUnit.SECONDS))
            val id = queue.enqueue(pageCount = 1, schedule = false, format = PdfDownloadStrategyV2.FORMAT_PDF)
            val job = requireNotNull(queue.dao.getJob(id))
            val chapterId = requireNotNull(job.chapterId)
            val path = DownloadPaths.archiveDownloadRelativePath(job.seriesId, job.volumeId, chapterId, "pdf-$chapterId.pdf")
            val existing =
                File(DownloadPaths.baseDir(queue.preferences.appContext, queue.preferences.profile), path).apply {
                    parentFile?.mkdirs()
                    writeText("Previously downloaded PDF")
                }
            try {
                DownloadManagerV2.enqueueWorker(queue.preferences)
                queue.awaitFileCount(id, 2)

                queue.viewModel.cancelTask(id)
                queue.awaitStatus(id, STATUS_CANCELED)
                queue.awaitWorkerIdle()

                assertEquals(listOf(existing), queue.downloadedFiles(id))
                assertEquals("Previously downloaded PDF", existing.readText())
                val canceled = queue.dao.getItemsForJob(id).single()
                assertNull(canceled.localPath)
            } finally {
                existing.delete()
            }
        }

    @Test
    fun cancelDuringEpubAssetDownloadKeepsOnlyTheCompletedPageAndItsAssets() =
        withQueue { queue ->
            val lastAssetRequested = CountDownLatch(1)
            val releaseLastAsset = CountDownLatch(1)
            queue.server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when (request.requestUrl?.encodedPath) {
                            "/cover.png" -> MockResponse().setBody("Cover data")
                            "/later.png" -> {
                                lastAssetRequested.countDown()
                                if (releaseLastAsset.await(15, TimeUnit.SECONDS)) {
                                    MockResponse().setBody("Later image data")
                                } else {
                                    MockResponse().setResponseCode(504)
                                }
                            }
                            else -> {
                                val laterImage = if (request.requestUrl?.queryParameter("page") == "1") """<img src="/later.png">""" else ""
                                MockResponse().setBody("""<img src="/cover.png">$laterImage""")
                            }
                        }
                }
            val id = queue.enqueue(pageCount = 2)
            try {
                assertTrue(lastAssetRequested.await(15, TimeUnit.SECONDS))
                queue.viewModel.cancelTask(id)
                queue.awaitStatus(id, STATUS_CANCELED)
            } finally {
                releaseLastAsset.countDown()
            }
            queue.awaitWorkerIdle()

            val (completed, canceled) = queue.dao.getItemsForJob(id)
            val files = queue.downloadedFiles(id)
            val page = File(requireNotNull(completed.localPath))
            assertEquals(2, files.size)
            assertTrue(page in files)
            assertEquals("Cover data", files.single { it != page }.readText())
            assertNull(canceled.localPath)
        }

    @Test
    fun workerRecoversPersistedRunningJob() =
        withQueue { queue ->
            queue.server.enqueue(pageResponse())
            val id = queue.enqueue(pageCount = 1, schedule = false)
            assertEquals(1, queue.dao.transitionJob(id, listOf(STATUS_PENDING), STATUS_RUNNING))

            DownloadManagerV2.enqueueWorker(queue.preferences)

            assertEquals(1, queue.awaitStatus(id, STATUS_COMPLETED).completedItems)
        }

    private suspend fun stopDuringSecondPage(
        queue: Queue,
        cancel: Boolean,
    ): Long {
        val releaseResponse = CountDownLatch(1)
        queue.server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.requestUrl?.queryParameter("page") == "1") {
                        if (!releaseResponse.await(15, TimeUnit.SECONDS)) return MockResponse().setResponseCode(504)
                    }
                    return pageResponse()
                }
            }
        val id = queue.enqueue(pageCount = 2)
        val stoppedStatus = if (cancel) STATUS_CANCELED else STATUS_PAUSED
        try {
            assertEquals("0", queue.nextRequestedPage())
            assertEquals("1", queue.nextRequestedPage())
            if (cancel) queue.viewModel.cancelTask(id) else queue.viewModel.pauseTask(id)
            queue.awaitStatus(id, stoppedStatus)
        } finally {
            releaseResponse.countDown()
        }
        queue.awaitWorkerIdle()
        val stopped = queue.dao.getJob(id)
        assertEquals(stoppedStatus, stopped?.status)
        assertEquals(1, stopped?.completedItems)
        return id
    }

    private fun withQueue(block: suspend (Queue) -> Unit) =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val preferences = AppPreferences(context)
                val config = preferences.configFlow.first()
                val retry = preferences.downloadRetryEnabledFlow.first()
                val metered = preferences.downloadAllowMeteredFlow.first()
                val offline = preferences.offlineModeFlow.first()
                try {
                    preferences.updateKavitaConfig(AppConfig(server.url("/").toString(), "test-key", "", 1))
                    preferences.setDownloadRetryEnabled(false)
                    preferences.setDownloadAllowMetered(true)
                    preferences.setOfflineMode(false)
                    val queue = Queue(server, AppPreferences(context, preferences.activeSessionFlow.first()))
                    try {
                        block(queue)
                        queue.awaitWorkerIdle()
                    } finally {
                        queue.cleanup()
                    }
                } finally {
                    preferences.updateKavitaConfig(config)
                    preferences.setDownloadRetryEnabled(retry)
                    preferences.setDownloadAllowMetered(metered)
                    preferences.setOfflineMode(offline)
                }
            }
        }

    private class Queue(
        val server: MockWebServer,
        val preferences: AppPreferences,
    ) {
        private val context = preferences.appContext
        val dao = InkitaDatabase.getInstance(context, preferences.profile).downloadV2Dao()
        val viewModel =
            DownloadQueueViewModelFactory
                .create(preferences, CacheManagerImpl(preferences, thumbnailsDir = null))
                .create(DownloadQueueViewModel::class.java)
        private val strategy = EpubDownloadStrategyV2(context, dao, preferences)
        private val pdfStrategy = PdfDownloadStrategyV2(context, dao, preferences)
        private val strategies = listOf(strategy, pdfStrategy).associateBy { it.key }
        private val manager = DownloadManagerV2(preferences, strategies)
        private val jobIds = mutableListOf<Long>()

        suspend fun enqueue(
            pageCount: Int,
            schedule: Boolean = true,
            format: String = strategy.key,
        ): Long {
            val chapterId = Random.nextInt(1_000_000, Int.MAX_VALUE)
            val request =
                DownloadRequestV2(
                    type = DownloadJobV2Entity.TYPE_CHAPTER,
                    format = format,
                    seriesId = chapterId,
                    chapterId = chapterId,
                    pageCount = pageCount,
                )
            val id = if (schedule) manager.enqueue(request) else strategies.getValue(format).enqueue(request)
            assertTrue(id > 0)
            jobIds += id
            return id
        }

        suspend fun awaitStatus(
            id: Long,
            status: String,
        ): DownloadJobV2Entity = await { dao.getJob(id)?.takeIf { it.status == status } }

        suspend fun downloadedFiles(id: Long): List<File> {
            val seriesId = requireNotNull(dao.getJob(id)?.seriesId)
            val directory = DownloadPaths.seriesDir(context, preferences.profile, seriesId)
            return directory.walkTopDown().filter { it.isFile }.toList()
        }

        suspend fun awaitFileCount(
            id: Long,
            count: Int,
        ) {
            await { Unit.takeIf { downloadedFiles(id).size == count } }
        }

        suspend fun awaitWorkerIdle() {
            val workManager = WorkManager.getInstance(context)
            await {
                val jobs =
                    workManager.getWorkInfosByTag(DownloadWorkerV2::class.java.name).get(5, TimeUnit.SECONDS)
                Unit.takeIf { jobs.isNotEmpty() && jobs.all { job -> job.state.isFinished } }
            }
        }

        fun nextRequestedPage(): String? = server.takeRequest(15, TimeUnit.SECONDS)?.requestUrl?.queryParameter("page")

        suspend fun cleanup() {
            viewModel.viewModelScope.cancel()
            val storage = DownloadStorageManager(context, preferences)
            for (id in jobIds) {
                dao.deleteJob(id)
                dao.getItemsForJob(id).forEach { storage.delete(it.localPath) }
                dao.clearItemsForJob(id)
            }
        }

        private suspend fun <T : Any> await(read: suspend () -> T?): T =
            withTimeout(20_000) {
                var result = read()
                while (result == null) {
                    delay(50)
                    result = read()
                }
                result
            }
    }

    private fun pageResponse(): MockResponse = MockResponse().setHeader("Content-Type", "text/html").setBody("<p>Inkita queue test</p>")
}
