package net.dom53.inkita

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dom53.inkita.core.di.InkitaServices
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.KavitaProfile
import net.dom53.inkita.core.sync.ProgressSyncWorker
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.LocalReaderProgressEntity
import net.dom53.inkita.domain.model.ReaderProgress
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReaderProgressRetryTest {
    @Test
    fun failedSaveQueuesAnotherSyncBehindRunningWork() =
        withProfile { fixture ->
            val firstSyncStarted = CountDownLatch(1)
            val releaseFirstSync = CountDownLatch(1)
            val failedChapterPosts = AtomicInteger()
            fixture.server.dispatcher =
                ProgressDispatcher(
                    beforeRead = { chapterId ->
                        if (chapterId == FIRST_CHAPTER) {
                            firstSyncStarted.countDown()
                            check(releaseFirstSync.await(15, TimeUnit.SECONDS))
                        }
                    },
                    postStatus = { chapterId ->
                        if (chapterId == SECOND_CHAPTER && failedChapterPosts.incrementAndGet() == 1) 429 else 204
                    },
                )
            fixture.storeProgress(FIRST_CHAPTER)
            ProgressSyncWorker.enqueue(fixture.context, fixture.profile)
            try {
                assertTrue(firstSyncStarted.await(10, TimeUnit.SECONDS))

                fixture.repository.setProgress(ReaderProgress(chapterId = SECOND_CHAPTER, page = PAGE, seriesId = SECOND_CHAPTER))

                assertEquals(PAGE, fixture.dao.getLocalProgress(SECOND_CHAPTER)?.page)
            } finally {
                releaseFirstSync.countDown()
            }
            await {
                val workers = fixture.workManager.getWorkInfosForUniqueWork(fixture.uniqueWork).get(5, TimeUnit.SECONDS)
                workers.size == 2 && workers.all { it.state == WorkInfo.State.SUCCEEDED }
            }
            assertEquals(2, failedChapterPosts.get())
            assertNull(fixture.dao.getLocalProgress(SECOND_CHAPTER))
        }

    @Test
    fun workerRetriesFailedPostAndKeepsProcessingOtherChapters() =
        withProfile { fixture ->
            val failedChapterPosts = AtomicInteger()
            fixture.server.dispatcher =
                ProgressDispatcher(postStatus = { chapterId ->
                    if (chapterId == FIRST_CHAPTER && failedChapterPosts.incrementAndGet() == 1) 503 else 204
                })
            fixture.storeProgress(FIRST_CHAPTER)
            fixture.storeProgress(SECOND_CHAPTER)
            val request =
                OneTimeWorkRequestBuilder<ProgressSyncWorker>()
                    .setInputData(workDataOf(KavitaProfile.WORK_INPUT to fixture.profile.id))
                    .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .build()
            fixture.workManager
                .enqueueUniqueWork(fixture.uniqueWork, ExistingWorkPolicy.KEEP, request)
                .result
                .get(5, TimeUnit.SECONDS)

            await {
                val work = fixture.workManager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)
                work.state == WorkInfo.State.ENQUEUED && work.runAttemptCount == 1
            }
            assertEquals(PAGE, fixture.dao.getLocalProgress(FIRST_CHAPTER)?.page)
            assertNull(fixture.dao.getLocalProgress(SECOND_CHAPTER))

            await {
                fixture.workManager
                    .getWorkInfoById(request.id)
                    .get(5, TimeUnit.SECONDS)
                    .state == WorkInfo.State.SUCCEEDED
            }
            assertEquals(2, failedChapterPosts.get())
            assertNull(fixture.dao.getLocalProgress(FIRST_CHAPTER))
        }

    @Test
    fun workerFinishesWhenAChapterIsPermanentlyRejected() =
        withProfile { fixture ->
            val rejectedPosts = AtomicInteger()
            fixture.server.dispatcher =
                ProgressDispatcher(postStatus = { chapterId ->
                    if (chapterId == FIRST_CHAPTER) {
                        rejectedPosts.incrementAndGet()
                        404
                    } else {
                        204
                    }
                })
            fixture.storeProgress(FIRST_CHAPTER)
            fixture.storeProgress(SECOND_CHAPTER)
            ProgressSyncWorker.enqueue(fixture.context, fixture.profile)

            await {
                val workers = fixture.workManager.getWorkInfosForUniqueWork(fixture.uniqueWork).get(5, TimeUnit.SECONDS)
                workers.singleOrNull()?.state == WorkInfo.State.SUCCEEDED
            }
            assertEquals(1, rejectedPosts.get())
            assertEquals(PAGE, fixture.dao.getLocalProgress(FIRST_CHAPTER)?.page)
            assertNull(fixture.dao.getLocalProgress(SECOND_CHAPTER))
        }

    @Test
    fun permanentlyRejectedSaveDoesNotQueueFollowUp() =
        withProfile { fixture ->
            val rejectedPosts = AtomicInteger()
            fixture.server.dispatcher =
                ProgressDispatcher(postStatus = {
                    rejectedPosts.incrementAndGet()
                    403
                })

            fixture.repository.setProgress(ReaderProgress(chapterId = FIRST_CHAPTER, page = PAGE, seriesId = FIRST_CHAPTER))

            val workers = fixture.workManager.getWorkInfosForUniqueWork(fixture.uniqueWork).get(5, TimeUnit.SECONDS)
            assertTrue(workers.isEmpty())
            assertEquals(1, rejectedPosts.get())
            assertEquals(PAGE, fixture.dao.getLocalProgress(FIRST_CHAPTER)?.page)
        }

    private fun withProfile(block: suspend (Fixture) -> Unit) =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val preferences = AppPreferences(context)
                val original = preferences.configFlow.first()
                val offline = preferences.offlineModeFlow.first()
                try {
                    preferences.updateKavitaConfig(AppConfig(server.url("/").toString(), "test-key", "test-key", 1))
                    preferences.setOfflineMode(false)
                    val fixture = Fixture(server, AppPreferences(context, preferences.activeSessionFlow.first()))
                    try {
                        block(fixture)
                    } finally {
                        fixture.workManager
                            .cancelUniqueWork(fixture.uniqueWork)
                            .result
                            .get(5, TimeUnit.SECONDS)
                        fixture.dao.clearLocalProgress(FIRST_CHAPTER)
                        fixture.dao.clearLocalProgress(SECOND_CHAPTER)
                    }
                } finally {
                    preferences.updateKavitaConfig(original)
                    preferences.setOfflineMode(offline)
                }
            }
        }

    private class Fixture(
        val server: MockWebServer,
        preferences: AppPreferences,
    ) {
        val context = preferences.appContext
        val profile = preferences.profile
        val workManager = WorkManager.getInstance(context)
        val uniqueWork = "inkita_progress_sync-${profile.id}"
        val dao = InkitaDatabase.getInstance(context, profile).readerDao()
        val repository = InkitaServices.get(context, preferences).readerRepository

        suspend fun storeProgress(chapterId: Int) {
            dao.upsertLocalProgress(LocalReaderProgressEntity(chapterId = chapterId, page = PAGE, seriesId = chapterId))
        }
    }

    private class ProgressDispatcher(
        private val beforeRead: (Int) -> Unit = {},
        private val postStatus: (Int) -> Int,
    ) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = checkNotNull(request.requestUrl)
            return when (url.encodedPath.lowercase()) {
                "/api/reader/get-progress" -> {
                    val chapterId = checkNotNull(url.queryParameter("chapterId")).toInt()
                    beforeRead(chapterId)
                    MockResponse().setBody("""{"chapterId":$chapterId,"seriesId":$chapterId,"pageNum":$PAGE}""")
                }
                "/api/reader/progress" -> {
                    val chapterId = JSONObject(request.body.readUtf8()).getInt("chapterId")
                    MockResponse().setResponseCode(postStatus(chapterId))
                }
                "/api/reader/continue-point" -> {
                    val chapterId = checkNotNull(url.queryParameter("seriesId")).toInt()
                    MockResponse().setBody("""{"id":$chapterId,"pagesRead":$PAGE}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private suspend fun await(condition: suspend () -> Boolean) {
        withTimeout(30_000) {
            while (!condition()) delay(50)
        }
    }

    private companion object {
        const val FIRST_CHAPTER = 901
        const val SECOND_CHAPTER = 902
        const val PAGE = 5
    }
}
