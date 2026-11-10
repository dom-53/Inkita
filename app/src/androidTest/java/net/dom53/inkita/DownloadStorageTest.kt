package net.dom53.inkita

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.downloadv2.DownloadPaths
import net.dom53.inkita.core.downloadv2.DownloadStorageManager
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.KavitaProfile
import net.dom53.inkita.core.storage.KavitaSession
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadStorageTest {
    @Test
    fun failedLocationSaveKeepsOriginalDownloadsAndAllowsRetry() =
        withStorage { fixture ->
            val source = File(fixture.sourceDir, "page.html")
            val asset =
                File(fixture.sourceDir, "assets/cover image.png").apply {
                    parentFile?.mkdirs()
                    writeText("image data")
                }
            val html = """<img src="${Uri.fromFile(asset)}">"""
            source.writeText(html)
            val item = download(1, source.path, format = Format.Epub)
            fixture.dao.upsertItems(listOf(item))
            fixture.preferences.setDownloadLocationUri("content://old-location")
            val occupied =
                File(fixture.destinationDir, DownloadPaths.epubPageRelativePath(1, 2, 3, 0)).apply {
                    parentFile?.mkdirs()
                    writeText("pre-existing file")
                }
            val failure = IOException("DataStore write failed")
            val failingStorage = DownloadStorageManager(fixture.context, fixture.preferences) { throw failure }

            val result = runCatching { failingStorage.migrateDownloadLocation(fixture.database, null) }

            assertEquals(failure, result.exceptionOrNull())
            assertEquals(item, fixture.dao.getItemById(item.id))
            assertEquals("content://old-location", fixture.preferences.downloadLocationUriFlow.first())
            assertEquals(html, source.readText())
            assertEquals("image data", asset.readText())
            assertEquals(
                listOf(occupied),
                fixture.destinationDir
                    .walkTopDown()
                    .filter { it.isFile }
                    .toList(),
            )
            assertEquals("pre-existing file", occupied.readText())

            fixture.storage.migrateDownloadLocation(fixture.database, null)

            val path = requireNotNull(fixture.dao.getItemById(item.id)?.localPath)
            assertNotEquals(occupied.path, path)
            val migratedHtml = File(path).readText()
            val migratedAsset = File(requireNotNull(Uri.parse(migratedHtml.substringAfter("src=\"").substringBefore('"')).path))
            assertEquals("image data", migratedAsset.readText())
            assertEquals("pre-existing file", occupied.readText())
            assertNull(fixture.preferences.downloadLocationUriFlow.first())
            assertFalse(source.exists())
            assertFalse(asset.exists())
        }

    @Test
    fun deletesSharedFileOnlyAfterLastDownloadIsRemoved() =
        withStorage { fixture ->
            val file = File(fixture.destinationDir, "shared file.pdf").apply { writeText("downloaded") }
            fixture.dao.upsertItems(listOf(download(1, file.path), download(2, Uri.fromFile(file).toString())))

            assertTrue(fixture.storage.deleteDownload(fixture.database, 1))
            assertNull(fixture.dao.getItemById(1))
            assertTrue(file.exists())

            assertTrue(fixture.storage.deleteDownload(fixture.database, 2))
            assertNull(fixture.dao.getItemById(2))
            assertFalse(file.exists())
        }

    @Test
    fun keepsDownloadListedIfFileCannotBeDeleted() =
        withStorage { fixture ->
            val directory = File(fixture.destinationDir, "unexpected-directory").apply { mkdir() }
            val item = download(1, directory.path)
            fixture.dao.upsertItems(listOf(item))

            assertFalse(fixture.storage.deleteDownload(fixture.database, item.id))
            assertEquals(item, fixture.dao.getItemById(item.id))
        }

    private fun download(
        id: Long,
        path: String,
        format: Format = Format.Pdf,
    ) = DownloadedItemV2Entity(
        id = id,
        jobId = 1,
        type = if (format == Format.Epub) DownloadedItemV2Entity.TYPE_PAGE else DownloadedItemV2Entity.TYPE_FILE,
        seriesId = 1,
        volumeId = 2,
        chapterId = 3,
        page = 0,
        url = null,
        localPath = path,
        bytes = null,
        checksum = null,
        status = DownloadedItemV2Entity.STATUS_COMPLETED,
        createdAt = 0,
        updatedAt = 0,
        format = format,
        error = null,
    )

    private fun withStorage(block: suspend (Fixture) -> Unit) =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val profile = KavitaProfile("storage-test-${UUID.randomUUID()}")
            val preferences = AppPreferences(context, KavitaSession(AppConfig("", "", "", 0), profile))
            val database = Room.inMemoryDatabaseBuilder(context, InkitaDatabase::class.java).build()
            val fixture = Fixture(preferences, database)
            try {
                block(fixture)
            } finally {
                database.close()
                preferences.setDownloadLocationUri(null)
                fixture.sourceDir.deleteRecursively()
                fixture.destinationDir.deleteRecursively()
            }
        }

    private class Fixture(
        val preferences: AppPreferences,
        val database: InkitaDatabase,
    ) {
        val context = preferences.appContext
        val dao = database.downloadV2Dao()
        val storage = DownloadStorageManager(context, preferences)
        val sourceDir = File(context.cacheDir, preferences.profile.id).apply { mkdirs() }
        val destinationDir = DownloadPaths.baseDir(context, preferences.profile)
    }
}
