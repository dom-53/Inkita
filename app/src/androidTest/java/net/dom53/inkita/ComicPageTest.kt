package net.dom53.inkita

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
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
import net.dom53.inkita.ui.reader.renderer.ReaderPageImage
import net.dom53.inkita.ui.theme.InkitaTheme
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ComicPageTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pagedReaderRetriesFailedPage() = retriesFailedPage(isWebtoon = false)

    @Test
    fun webtoonReaderRetriesFailedPage() = retriesFailedPage(isWebtoon = true)

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

            compose.waitUntil(timeoutMillis = 5000) {
                if (server.requestCount < 2) return@waitUntil false
                val pixels = compose.onNodeWithTag("comic-page").captureToImage().toPixelMap()
                pixels.width > 0 && pixels.height > 0 && pixels[pixels.width / 2, pixels.height / 2] == Color.Green
            }
            assertEquals(2, server.requestCount)
        }
    }

    private fun pngPage(): Buffer {
        val bytes = ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        return Buffer().write(bytes.toByteArray())
    }
}
