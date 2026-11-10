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
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.DownloadStorageManager
import net.dom53.inkita.core.downloadv2.DownloadWorkerV2
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
        private val manager = DownloadManagerV2(preferences, mapOf(strategy.key to strategy))
        private val jobIds = mutableListOf<Long>()

        suspend fun enqueue(
            pageCount: Int,
            schedule: Boolean = true,
        ): Long {
            val chapterId = Random.nextInt(1_000_000, Int.MAX_VALUE)
            val request =
                DownloadRequestV2(
                    type = DownloadJobV2Entity.TYPE_CHAPTER,
                    format = strategy.key,
                    seriesId = chapterId,
                    chapterId = chapterId,
                    pageCount = pageCount,
                )
            val id = if (schedule) manager.enqueue(request) else strategy.enqueue(request)
            assertTrue(id > 0)
            jobIds += id
            return id
        }

        suspend fun awaitStatus(
            id: Long,
            status: String,
        ): DownloadJobV2Entity = await { dao.getJob(id)?.takeIf { it.status == status } }

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
