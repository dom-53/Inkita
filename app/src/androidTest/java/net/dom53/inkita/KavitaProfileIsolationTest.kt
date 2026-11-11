package net.dom53.inkita

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dom53.inkita.core.di.InkitaServices
import net.dom53.inkita.core.downloadv2.DownloadPaths
import net.dom53.inkita.core.downloadv2.DownloadRequestV2
import net.dom53.inkita.core.downloadv2.DownloadStorageManager
import net.dom53.inkita.core.downloadv2.DownloadWorkerV2
import net.dom53.inkita.core.downloadv2.strategies.EpubDownloadStrategyV2
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.KavitaProfile
import net.dom53.inkita.core.sync.ProgressSyncWorker
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.data.local.db.entity.LocalReaderProgressEntity
import net.dom53.inkita.data.repository.AuthRepositoryImpl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class KavitaProfileIsolationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = AppPreferences(context)
    private val chapterId = 17342

    @Test
    fun keepsDownloadsAndProgressSeparateAcrossAccountsAndServers() =
        withServers { server, otherServer ->
            val owner = signIn(server, accountId = 1, key = "first")
            val ownerDao = database(owner).downloadV2Dao()
            val storage = DownloadStorageManager(context, owner)
            val jobId = enqueuePage(owner)
            val item = ownerDao.getItemsForJob(jobId).single()
            val relativePath = DownloadPaths.epubPageRelativePath(chapterId, null, chapterId, 0)
            val stored = storage.writeText(relativePath, "<p>First account</p>")
            try {
                ownerDao.upsertItems(listOf(item.copy(status = DownloadedItemV2Entity.STATUS_COMPLETED, localPath = stored.localPath)))
                database(owner).readerDao().upsertLocalProgress(LocalReaderProgressEntity(chapterId, page = 12))

                val otherAccount = signIn(server, accountId = 2, key = "second")
                assertIsolated(owner, otherAccount)
                val sameIdsOnAnotherServer = signIn(otherServer, accountId = 1, key = "third")
                assertIsolated(owner, sameIdsOnAnotherServer)

                preferences.clearAuth(clearServer = true)
                assertEquals(KavitaProfile.Guest, preferences.activeSessionFlow.first().profile)

                val signedBackIn = signIn(server, accountId = 1, key = "rotated")
                assertEquals(owner.profile, signedBackIn.profile)
                assertEquals(12, database(signedBackIn).readerDao().getLocalProgress(chapterId)?.page)
                assertTrue(InkitaServices.get(context, signedBackIn).readerRepository.isPageDownloaded(chapterId, 0))
                assertEquals("<p>First account</p>", storage.readText(stored.localPath))
                assertEquals("first", owner.configFlow.first().apiKey)
                assertEquals("rotated", signedBackIn.configFlow.first().apiKey)
            } finally {
                database(owner).readerDao().clearLocalProgress(chapterId)
                ownerDao.clearItemsForJob(jobId)
                ownerDao.deleteJob(jobId)
                storage.delete(stored.localPath)
            }
        }

    @Test
    fun backgroundWorkFromAnInactiveProfileDoesNotReachTheNewServer() =
        withServers { firstServer, secondServer ->
            val owner = signIn(firstServer, accountId = 1, key = "first")
            val ownerDao = database(owner).downloadV2Dao()
            val jobId = enqueuePage(owner)
            try {
                database(owner).readerDao().upsertLocalProgress(LocalReaderProgressEntity(chapterId, page = 12))
                signIn(secondServer, accountId = 1, key = "second")

                val input = workDataOf(KavitaProfile.WORK_INPUT to owner.profile.id)
                val sync = OneTimeWorkRequestBuilder<ProgressSyncWorker>().setInputData(input).build()
                val download = OneTimeWorkRequestBuilder<DownloadWorkerV2>().setInputData(input).build()
                val workManager = WorkManager.getInstance(context)
                workManager.enqueue(listOf(sync, download)).result.get(5, TimeUnit.SECONDS)

                assertEquals(WorkInfo.State.SUCCEEDED, awaitWork(workManager, sync.id))
                assertEquals(WorkInfo.State.SUCCEEDED, awaitWork(workManager, download.id))
                assertEquals(1, firstServer.requestCount)
                assertEquals(1, secondServer.requestCount)
                assertEquals(DownloadJobV2Entity.STATUS_PENDING, ownerDao.getJob(jobId)?.status)
                assertEquals(12, database(owner).readerDao().getLocalProgress(chapterId)?.page)
            } finally {
                database(owner).readerDao().clearLocalProgress(chapterId)
                ownerDao.clearItemsForJob(jobId)
                ownerDao.deleteJob(jobId)
            }
        }

    private suspend fun assertIsolated(
        owner: AppPreferences,
        active: AppPreferences,
    ) {
        assertNotEquals(owner.profile, active.profile)
        assertNotEquals(DownloadPaths.baseDir(context, owner.profile), DownloadPaths.baseDir(context, active.profile))
        assertNull(database(active).readerDao().getLocalProgress(chapterId))
        assertFalse(InkitaServices.get(context, active).readerRepository.isPageDownloaded(chapterId, 0))
        val otherPath = File(DownloadPaths.baseDir(context, active.profile), DownloadPaths.epubPageRelativePath(chapterId, null, chapterId, 0))
        assertFalse(otherPath.exists())
    }

    private fun database(appPreferences: AppPreferences): InkitaDatabase = InkitaDatabase.getInstance(context, appPreferences.profile)

    private suspend fun enqueuePage(appPreferences: AppPreferences): Long {
        val strategy = EpubDownloadStrategyV2(context, database(appPreferences).downloadV2Dao(), appPreferences)
        return strategy.enqueue(
            DownloadRequestV2(
                type = DownloadJobV2Entity.TYPE_CHAPTER,
                format = strategy.key,
                seriesId = chapterId,
                chapterId = chapterId,
                pageCount = 1,
            ),
        )
    }

    private suspend fun signIn(
        server: MockWebServer,
        accountId: Int,
        key: String,
    ): AppPreferences {
        server.enqueue(MockResponse().setBody("""{"id":$accountId,"username":"reader","email":null}"""))
        val verified = AuthRepositoryImpl(preferences).configure(AppConfig(server.url("/").toString(), key, key, 99))
        assertEquals(accountId, verified.userId)
        val session = preferences.activeSessionFlow.first()
        assertEquals(verified, session.config)
        return AppPreferences(context, session)
    }

    private suspend fun awaitWork(
        manager: WorkManager,
        id: UUID,
    ): WorkInfo.State =
        withTimeout(20_000) {
            var state = manager.getWorkInfoById(id).get(5, TimeUnit.SECONDS).state
            while (!state.isFinished) {
                delay(50)
                state = manager.getWorkInfoById(id).get(5, TimeUnit.SECONDS).state
            }
            state
        }

    private fun withServers(block: suspend (MockWebServer, MockWebServer) -> Unit) =
        runBlocking {
            val original = preferences.configFlow.first()
            MockWebServer().use { first ->
                MockWebServer().use { second ->
                    first.start()
                    second.start()
                    try {
                        block(first, second)
                    } finally {
                        preferences.updateKavitaConfig(original)
                    }
                }
            }
        }
}
