package net.dom53.inkita

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.data.repository.ReaderRepositoryImpl
import net.dom53.inkita.data.repository.comicPageUrl
import net.dom53.inkita.data.repository.fetchPdfForReading
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderImageResult
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.repository.ReaderRepository
import net.dom53.inkita.ui.reader.screen.ReaderScreen
import net.dom53.inkita.ui.settings.screens.SettingsReaderScreen
import net.dom53.inkita.ui.theme.InkitaTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun opensSettingsForOnlineCbz() = opensSettings(Format.Archive, downloaded = false)

    @Test
    fun opensSettingsForDownloadedCbz() = opensSettings(Format.Archive, downloaded = true)

    @Test
    fun opensSettingsForOnlinePdf() = opensSettings(Format.Pdf, downloaded = false)

    @Test
    fun opensSettingsForDownloadedPdf() = opensSettings(Format.Pdf, downloaded = true)

    @Test
    fun readerPreferencesCanScrollToTheThemeAndChangeTheFont() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val context = compose.activity
        val preferences = AppPreferences(context.applicationContext)
        val original = runBlocking { preferences.readerPrefsFlow.first() }
        try {
            runBlocking { preferences.updateReaderPrefs { copy(fontFamily = "literata", useSerif = true) } }
            compose.setContent {
                InkitaTheme(darkTheme = false, dynamicColor = false) {
                    SettingsReaderScreen(appPreferences = preferences, onBack = {})
                }
            }

            compose.onNodeWithText(context.getString(R.string.reader_theme_header)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Literata").performScrollTo().performTouchInput { click() }
            compose.onNodeWithText("Noto Sans").performScrollTo().performClick()

            compose.onNodeWithText("Noto Sans").assertIsDisplayed()
            runBlocking {
                withTimeout(5000) { preferences.readerPrefsFlow.first { it.fontFamily == "noto_sans" && !it.useSerif } }
            }
        } finally {
            runBlocking { preferences.updateReaderPrefs { copy(fontFamily = original.fontFamily, useSerif = original.useSerif) } }
        }
    }

    private fun opensSettings(
        format: Format,
        downloaded: Boolean,
    ) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        val context = compose.activity.applicationContext
        val prefs = AppPreferences(context)
        val originalPdfRightToLeft = runBlocking { prefs.readerPrefsFlow.first().pdfRightToLeft }
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "reader-settings-").toFile()
        val content = if (format == Format.Pdf) pdf() else png()
        val chapterId = if (format == Format.Pdf) 2_420_001 else 2_420_002

        try {
            MockWebServer().use { server ->
                server.dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse =
                            when (request.requestUrl?.encodedPath) {
                                "/api/Reader/pdf", "/api/reader/image" ->
                                    MockResponse().setBody(Buffer().write(content))
                                else -> MockResponse().setResponseCode(404)
                            }
                    }
                val config = AppConfig(server.url("/").toString(), "test-key", "", 0)
                val database = Room.inMemoryDatabaseBuilder(context, InkitaDatabase::class.java).build()
                try {
                    runBlocking {
                        if (format == Format.Pdf) {
                            prefs.updateReaderPrefs { copy(pdfRightToLeft = false) }
                        }
                        if (downloaded) {
                            val file =
                                if (format == Format.Pdf) {
                                    directory.resolve("chapter.pdf").apply { writeBytes(content) }
                                } else {
                                    cbz(directory.resolve("chapter.cbz"), content)
                                }
                            database.downloadV2Dao().upsertItems(listOf(downloadedFile(chapterId, format, file)))
                        }
                    }
                    val localRepository = ReaderRepositoryImpl(context, prefs, database.readerDao(), database.downloadV2Dao())
                    val repository = settingsTestRepository(localRepository, config, downloaded, directory)
                    try {
                        compose.setContent {
                            InkitaTheme(darkTheme = false, dynamicColor = false) {
                                ReaderScreen(
                                    chapterId = chapterId,
                                    initialPage = 0,
                                    readerRepository = repository,
                                    appPreferences = prefs,
                                    seriesId = null,
                                    volumeId = null,
                                    serverUrl = config.serverUrl,
                                    apiKey = config.apiKey,
                                    formatId = format.id,
                                )
                            }
                        }
                        waitForPage(Color.Green)
                        if (downloaded) assertEquals(0, server.requestCount)
                        val title = context.getString(R.string.reader_reader_settings)
                        compose.onNodeWithContentDescription(title).assertIsEnabled().performClick()
                        compose.onNodeWithText(title).assertIsDisplayed()
                        if (format == Format.Pdf) verifyPdfReadingDirection(title)
                    } finally {
                        compose.activityRule.scenario.close()
                    }
                } finally {
                    database.close()
                }
            }
        } finally {
            runBlocking {
                if (format == Format.Pdf) {
                    prefs.updateReaderPrefs { copy(pdfRightToLeft = originalPdfRightToLeft) }
                }
            }
            directory.listFiles()?.forEach(File::delete)
            directory.delete()
        }
    }

    private fun settingsTestRepository(
        localRepository: ReaderRepository,
        config: AppConfig,
        downloaded: Boolean,
        directory: File,
    ): ReaderRepository =
        object : ReaderRepository by localRepository {
            override suspend fun getBookInfo(chapterId: Int): ReaderBookInfo =
                ReaderBookInfo(
                    pages = if (downloaded) 1 else 2,
                    seriesId = null,
                    volumeId = null,
                    libraryId = null,
                    title = "Reader test",
                    pageTitle = "Chapter 1",
                )

            override suspend fun getProgress(chapterId: Int): ReaderProgress? = null

            override suspend fun setProgress(
                progress: ReaderProgress,
                totalPages: Int?,
            ) = Unit

            override suspend fun getImageResult(
                chapterId: Int,
                page: Int,
            ): ReaderImageResult =
                if (downloaded) {
                    localRepository.getImageResult(chapterId, page).also { check(it.fromOffline) }
                } else {
                    ReaderImageResult(config.comicPageUrl(chapterId, page), fromOffline = false)
                }

            override suspend fun getPdfFile(chapterId: Int): File? =
                if (downloaded) {
                    localRepository.getPdfFile(chapterId)
                } else {
                    KavitaApiFactory.createAuthenticated(config).fetchPdfForReading(chapterId, config.apiKey, directory.resolve("online.pdf"))
                }
        }

    private fun verifyPdfReadingDirection(settingsTitle: String) {
        val context = compose.activity
        val settings = compose.onNodeWithContentDescription(settingsTitle)
        val leftToRight = compose.onNodeWithText(context.getString(R.string.reader_image_mode_ltr))
        val rightToLeftLabel = context.getString(R.string.reader_image_mode_rtl)
        val rightToLeft = compose.onNodeWithText(rightToLeftLabel)
        val page = compose.onRoot()

        leftToRight.assertIsSelected()
        settings.performClick()
        page.performTouchInput { swipeLeft() }
        waitForPage(Color.Blue)
        page.performTouchInput { swipeRight() }
        waitForPage(Color.Green)

        settings.performClick()
        rightToLeft.performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(rightToLeftLabel).fetchSemanticsNodes().any {
                it.config.getOrNull(SemanticsProperties.Selected) == true
            }
        }
        rightToLeft.assertIsSelected()
        settings.performClick()
        page.performTouchInput { click(center) }
        settings.assertDoesNotExist()
        page.performTouchInput { swipeRight() }
        waitForPage(Color.Blue)
        page.performTouchInput { click(center) }
        settings.assertIsEnabled().performClick()
        rightToLeft.assertIsSelected()
    }

    private fun waitForPage(color: Color) {
        compose.waitUntil(8000) {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2] == color
        }
    }

    private fun downloadedFile(
        chapterId: Int,
        format: Format,
        file: File,
    ) = DownloadedItemV2Entity(
        jobId = 0,
        type = DownloadedItemV2Entity.TYPE_FILE,
        seriesId = null,
        volumeId = null,
        chapterId = chapterId,
        page = null,
        url = null,
        localPath = file.absolutePath,
        bytes = file.length(),
        checksum = null,
        status = DownloadedItemV2Entity.STATUS_COMPLETED,
        createdAt = 0,
        updatedAt = 0,
        format = format,
        error = null,
    )

    private fun cbz(
        file: File,
        png: ByteArray,
    ): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("page1.png"))
            zip.write(png)
            zip.closeEntry()
        }
        return file
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(android.graphics.Color.GREEN)
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun pdf(): ByteArray {
        val document = PdfDocument()
        return try {
            repeat(2) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(200, 100, index + 1).create())
                page.canvas.drawColor(if (index == 0) android.graphics.Color.GREEN else android.graphics.Color.BLUE)
                document.finishPage(page)
            }
            ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }
}
