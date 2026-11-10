package net.dom53.inkita

import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.downloadv2.DownloadPaths
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.KavitaProfile
import net.dom53.inkita.core.storage.KavitaSession
import net.dom53.inkita.data.api.KavitaApi
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.ReaderRepositoryImpl
import net.dom53.inkita.data.repository.fetchPdfForReading
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class OnlinePdfTest {
    @Test
    fun historicalPdfIsOnlyAvailableToItsOriginalProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chapterId = 1_930_000_097
        val config = AppConfig("", "", "", 0)
        val legacy = AppPreferences(context, KavitaSession(config, KavitaProfile.Legacy))
        val other = AppPreferences(context, KavitaSession(config, KavitaProfile("pdf-isolation-test")))
        val legacyPdf = checkNotNull(context.getExternalFilesDir("Inkita/downloads/pdfs")).resolve("pdf-$chapterId.pdf")
        val otherPdf = DownloadPaths.pdfTempFile(context, other.profile, chapterId)
        val database = Room.inMemoryDatabaseBuilder(context, InkitaDatabase::class.java).build()
        try {
            val pdf = createPdf()
            legacyPdf.writeBytes(pdf)
            otherPdf.parentFile?.mkdirs()
            otherPdf.writeBytes(pdf)

            runBlocking {
                val downloads = database.downloadV2Dao()
                val otherReader = ReaderRepositoryImpl(context, other, database.readerDao(), downloads)
                assertEquals(otherPdf, otherReader.getPdfFile(chapterId))
                assertTrue(downloads.getItemsForChapter(chapterId).isEmpty())

                val legacyReader = ReaderRepositoryImpl(context, legacy, database.readerDao(), downloads)
                assertEquals(legacyPdf, legacyReader.getPdfFile(chapterId))
                assertEquals(legacyPdf.absolutePath, downloads.getItemsForChapter(chapterId).single().localPath)
            }
        } finally {
            otherPdf.delete()
            legacyPdf.delete()
            database.close()
        }
    }

    @Test
    fun opensPdfWhenReaderEndpointFailsButDownloadWorks() =
        withServer { server, api, target ->
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(pdfResponse())

            val result = api.fetchPdfForReading(42, "test-key", target)

            PdfRenderer(ParcelFileDescriptor.open(result, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertEquals(1, renderer.pageCount)
            }
            val readerRequest = server.takeRequest()
            val downloadRequest = server.takeRequest()
            assertEquals("/api/Reader/pdf", readerRequest.requestUrl?.encodedPath)
            assertEquals("/api/Download/chapter", downloadRequest.requestUrl?.encodedPath)
            assertEquals("42", downloadRequest.requestUrl?.queryParameter("chapterId"))
            assertEquals("test-key", downloadRequest.getHeader("x-api-key"))
        }

    @Test
    fun readsFromReaderEndpointWhenItReturnsPdf() =
        withServer { server, api, target ->
            server.enqueue(pdfResponse())

            assertEquals(target, api.fetchPdfForReading(42, "test-key", target))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun doesNotUseDownloadEndpointAfterAccessIsDenied() =
        withServer { server, api, target ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(pdfResponse())

            val failure = runCatching { api.fetchPdfForReading(42, "test-key", target) }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertEquals("Unable to open PDF (reader HTTP 403)", failure?.message)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun ignoresNonPdfDownloadResponse() =
        withServer { server, api, target ->
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody("Not a PDF"))

            val failure = runCatching { api.fetchPdfForReading(42, "test-key", target) }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertEquals("Unable to open PDF (reader HTTP 500, download did not return a PDF)", failure?.message)
            assertTrue(!target.exists())
        }

    @Test
    fun interruptedResponseDoesNotLeaveAPartialPdf() =
        withServer { server, api, target ->
            server.enqueue(pdfResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

            val failure = runCatching { api.fetchPdfForReading(42, "test-key", target) }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertTrue(
                target.parentFile
                    ?.listFiles()
                    .orEmpty()
                    .isEmpty(),
            )
            server.enqueue(pdfResponse())
            assertEquals(target, api.fetchPdfForReading(42, "test-key", target))
        }

    private fun withServer(block: suspend (MockWebServer, KavitaApi, File) -> Unit) =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = Files.createTempDirectory(context.cacheDir.toPath(), "online-pdf-test-").toFile()
            try {
                MockWebServer().use { server ->
                    server.start()
                    val api = KavitaApiFactory.createAuthenticated(AppConfig(server.url("/").toString(), "test-key", "", 0))
                    block(server, api, directory.resolve("pdf-42.pdf"))
                }
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun pdfResponse(): MockResponse = MockResponse().setBody(Buffer().write(createPdf()))

    private fun createPdf(): ByteArray {
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(100, 100, 1).create())
            pdf.finishPage(page)
            return ByteArrayOutputStream().also { pdf.writeTo(it) }.toByteArray()
        } finally {
            pdf.close()
        }
    }
}
