package net.dom53.inkita

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.data.repository.ComicPageFallback
import net.dom53.inkita.data.repository.comicPageUrl
import net.dom53.inkita.ui.reader.renderer.ReaderPageImage
import net.dom53.inkita.ui.theme.InkitaTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ComicPageTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pagedReaderRetriesFailedPage() = retriesFailedPage(isWebtoon = false)

    @Test
    fun webtoonReaderRetriesFailedPage() = retriesFailedPage(isWebtoon = true)

    @Test
    @Suppress("DEPRECATION")
    fun readerDisplaysBothPagesFromOneArchiveWhenPageEndpointFails() {
        MockWebServer().use { server ->
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when (request.requestUrl?.encodedPath) {
                            "/kavita/api/reader/image" -> MockResponse().setResponseCode(500)
                            "/kavita/api/Download/chapter" -> MockResponse().setBody(comicArchive())
                            else -> MockResponse().setResponseCode(404)
                        }
                }
            val config = config(server)
            val loader = fallbackLoader(config)
            val page = mutableIntStateOf(0)
            try {
                compose.setContent {
                    CompositionLocalProvider(LocalImageLoader provides loader) {
                        InkitaTheme(darkTheme = true, dynamicColor = false) {
                            ReaderPageImage(config.comicPageUrl(1, page.intValue), Modifier.fillMaxWidth().testTag("comic-page"))
                        }
                    }
                }

                waitForPage(Color.Green)
                compose.runOnIdle { page.intValue = 1 }
                waitForPage(Color.Blue)
                assertEquals(3, server.requestCount)
                val first = server.takeRequest()
                val archive = server.takeRequest()
                val next = server.takeRequest()
                assertEquals("0", first.requestUrl?.queryParameter("page"))
                assertEquals("/kavita/api/Download/chapter", archive.requestUrl?.encodedPath)
                assertEquals("1", archive.requestUrl?.queryParameter("chapterId"))
                assertEquals(config.apiKey, archive.getHeader("x-api-key"))
                assertEquals("1", next.requestUrl?.queryParameter("page"))
            } finally {
                loader.shutdown()
            }
        }
    }

    @Test
    fun forbiddenComicPageDoesNotRequestArchive() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().setBody(comicArchive()))
            val config = config(server)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val loader = fallbackLoader(config)
            try {
                val request = ImageRequest.Builder(context).data(config.comicPageUrl(1, 0)).build()
                assertTrue(runBlocking { loader.execute(request) } is ErrorResult)
                assertEquals(1, server.requestCount)
            } finally {
                loader.shutdown()
            }
        }
    }

    private fun retriesFailedPage(isWebtoon: Boolean) {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().addHeader("Content-Type", "image/png").setBody(pngPage()))
            val url = server.url("/api/reader/image?chapterId=1&page=0").toString()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val message = context.getString(R.string.reader_image_http_error, 500)
            val retry = context.getString(R.string.download_action_retry)

            compose.setContent {
                InkitaTheme(darkTheme = true, dynamicColor = false) {
                    ReaderPageImage(url, Modifier.fillMaxWidth().testTag("comic-page"), isWebtoon)
                }
            }

            compose.waitUntil(timeoutMillis = 5000) {
                compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithText(retry).performClick()

            waitForPage(Color.Green)
            assertEquals(2, server.requestCount)
        }
    }

    private fun waitForPage(color: Color) {
        compose.waitUntil(timeoutMillis = 5000) {
            runCatching {
                val pixels = compose.onNodeWithTag("comic-page").captureToImage().toPixelMap()
                pixels.width > 0 && pixels.height > 0 && pixels[pixels.width / 2, pixels.height / 2] == color
            }.getOrDefault(false)
        }
    }

    private fun config(server: MockWebServer): AppConfig = AppConfig(server.url("/kavita/").toString(), "test-key", "", 1)

    private fun fallbackLoader(config: AppConfig): ImageLoader {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return ImageLoader.Builder(context).components { add(ComicPageFallback(context, config) { true }) }.build()
    }

    private fun comicArchive(): Buffer {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("pages/page10.png"))
            zip.write(pngPage(android.graphics.Color.BLUE).readByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("pages/page2.png"))
            zip.write(pngPage().readByteArray())
            zip.closeEntry()
        }
        return Buffer().write(bytes.toByteArray())
    }

    private fun pngPage(color: Int = android.graphics.Color.GREEN): Buffer {
        val bytes = ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        return Buffer().write(bytes.toByteArray())
    }
}
