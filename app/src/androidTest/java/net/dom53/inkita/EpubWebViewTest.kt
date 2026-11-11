package net.dom53.inkita

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.ReaderThemeMode
import net.dom53.inkita.domain.model.ReaderPageResult
import net.dom53.inkita.ui.reader.model.ReaderFontOption
import net.dom53.inkita.ui.reader.screen.ReaderWebView
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EpubWebViewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun onlineEpubFollowsFootnoteLinksWithABookResourceBaseUrl() {
        MockWebServer().use { server ->
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(404)
                }
            server.start()
            val config = AppConfig(server.url("/kavita/").toString(), "reader-test-key", "", 1)
            assertFootnoteRoundTrip(config, fromOffline = false)
            val paths = List(server.requestCount) { server.takeRequest(1, TimeUnit.SECONDS)?.requestUrl?.encodedPath }
            assertEquals(emptyList<String>(), paths.filterNot { it == "/favicon.ico" })
        }
    }

    @Test
    fun offlineEpubFollowsFootnoteLinks() {
        assertFootnoteRoundTrip(config = null, fromOffline = true)
    }

    @Test
    fun onlineEpubLoadsAuthenticatedImagesAndKeepsReaderControls() {
        MockWebServer().use { kavita ->
            MockWebServer().use { unrelated ->
                unrelated.start()
                kavita.dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse =
                            if (request.requestUrl?.encodedPath == "/api/Book/book-resources") {
                                MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(testImage()))
                            } else {
                                MockResponse().setResponseCode(404)
                            }
                    }
                kavita.start()
                val config = AppConfig(kavita.url("/").toString(), "reader-test-key", "", 1)
                val html =
                    """
                    <p id="title">First page</p>
                    <img id="cover" src="/api/Book/book-resources?file=cover&amp;apiKey=stale">
                    <img id="outside" src="${unrelated.url("/outside.png")}">
                    <script>document.body.dataset.bookScript = 'ran';</script>
                    <iframe src="${unrelated.url("/frame")}"></iframe>
                    <div style="height: 1600px"></div><p id='bookmark"1'>Bookmark</p><div style="height: 1300px"></div>
                    """.trimIndent()
                val reader = mount(ReaderPageResult(html, false), config, """id("bookmark"1")""")
                awaitJs(reader, "document.getElementById('cover')?.naturalWidth === 2")
                awaitJs(reader, "window.scrollY > 500")
                val request = kavita.takeRequest(5, TimeUnit.SECONDS)
                assertNotNull(request)
                assertEquals("reader-test-key", request?.getHeader("x-api-key"))
                assertEquals(listOf("reader-test-key"), request?.requestUrl?.queryParameterValues("apiKey"))
                assertEquals("true", evaluate(reader, "document.body.dataset.bookScript === undefined && !document.querySelector('iframe')"))
                assertEquals("true", evaluate(reader, "!document.getElementById('cover').src.includes('apiKey')"))
                assertEquals(0, unrelated.requestCount)
                compose.runOnIdle {
                    assertFalse(reader.webView.settings.allowFileAccess)
                    assertFalse(reader.webView.settings.allowContentAccess)
                    reader.font.value = notoSans
                }
                awaitJs(reader, "getComputedStyle(document.body).fontFamily.includes('Noto Sans')")
                awaitJs(reader, "Array.from(document.fonts).some(font => font.family === 'Noto Sans' && font.status === 'loaded')")
                compose.runOnIdle { reader.page.value = ReaderPageResult("<p id='next'>Next page</p>", false) }
                awaitJs(reader, "document.getElementById('next')?.textContent === 'Next page'")
            }
        }
    }

    @Test
    fun offlineEpubRendersLegacyFileReferencesOnlyFromTheBook() {
        val directory =
            java.nio.file.Files
                .createTempDirectory(compose.activity.cacheDir.toPath(), "epub-web-test")
                .toFile()
        try {
            val allowed =
                File(directory, "cover.png")
                    .apply { writeBytes(testImage()) }
                    .toURI()
                    .toString()
                    .replace("file:/", "file:///")
            val outside =
                File(directory, "outside.png")
                    .apply { writeBytes(testImage()) }
                    .toURI()
                    .toString()
                    .replace("file:/", "file:///")
            val html = """<img id="cover" src="$allowed"><img id="outside" src="$outside">"""
            val reader = mount(ReaderPageResult(html, true, setOf(allowed)), null)
            awaitJs(reader, "document.getElementById('cover')?.naturalWidth === 2")
            assertEquals("true", evaluate(reader, "!document.getElementById('outside').hasAttribute('src')"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun mount(
        page: ReaderPageResult,
        config: AppConfig?,
        initialScrollId: String? = null,
    ): ReaderFixture {
        val fixture = ReaderFixture(page, initialScrollId)
        compose.setContent {
            val currentPage = fixture.page.value
            ReaderWebView(
                html = currentPage.html,
                fontSize = 18f,
                lineHeight = 1.4f,
                paddingPx = 8f,
                fontOption = fixture.font.value,
                textAlignCss = "left",
                themeMode = ReaderThemeMode.Light,
                config = config,
                fromOffline = currentPage.fromOffline,
                offlineAssetPaths = currentPage.offlineAssetPaths,
                onToggleOverlay = {},
                pendingScrollY = null,
                onWebViewReady = { fixture.webView = it },
                pendingScrollId = fixture.scrollId.value,
                onConsumeScrollId = { fixture.scrollId.value = null },
            )
        }
        compose.waitForIdle()
        return fixture
    }

    private fun assertFootnoteRoundTrip(
        config: AppConfig?,
        fromOffline: Boolean,
    ) {
        val html =
            """
            <p id="start"><a id="note-link" href="#pozn%C3%A1mka">Read the note</a></p>
            <div style="height: 1600px"></div>
            <p id="poznámka"><a id="back-link" href="#start">Return to the text</a></p>
            <div style="height: 1300px"></div>
            """.trimIndent()
        val reader = mount(ReaderPageResult(html, fromOffline), config)
        awaitJs(reader, "document.readyState === 'complete' && document.getElementById('note-link') !== null")

        clickLink(reader, "note-link")
        awaitJs(reader, "decodeURIComponent(location.hash) === '#poznámka' && window.scrollY > 500")

        clickLink(reader, "back-link")
        awaitJs(reader, "location.hash === '#start' && window.scrollY < 100")
    }

    private fun clickLink(
        reader: ReaderFixture,
        id: String,
    ) {
        compose.runOnUiThread {
            reader.webView.evaluateJavascript("document.getElementById('$id').click()", null)
        }
    }

    private fun awaitJs(
        reader: ReaderFixture,
        expression: String,
    ) {
        compose.waitUntil(10_000) { evaluate(reader, expression) == "true" }
    }

    private fun evaluate(
        reader: ReaderFixture,
        expression: String,
    ): String? {
        val result = ArrayBlockingQueue<String>(1)
        compose.runOnUiThread {
            reader.webView.evaluateJavascript("Boolean($expression)") { value -> result.offer(value ?: "null") }
        }
        return result.poll(5, TimeUnit.SECONDS)
    }

    private class ReaderFixture(
        page: ReaderPageResult,
        initialScrollId: String?,
    ) {
        val page = mutableStateOf(page)
        val font = mutableStateOf(ReaderFontOption("default", "Default", "serif"))
        val scrollId = mutableStateOf(initialScrollId)
        lateinit var webView: WebView
    }

    private val notoSans =
        ReaderFontOption(
            "noto_sans",
            "Noto Sans",
            "Noto Sans",
            "file:///android_asset/fonts/Noto_Sans/NotoSans-VariableFont_wdth,wght.ttf",
        )
}

private fun testImage(): ByteArray =
    ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
            compress(Bitmap.CompressFormat.PNG, 100, output)
            recycle()
        }
        output.toByteArray()
    }
