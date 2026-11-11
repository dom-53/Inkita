package net.dom53.inkita

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.update.UpdateChecker
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class UpdateCheckerTest {
    @Test
    fun sharedCheckFindsNumberedBetaUpdate() =
        runBlocking {
            MockWebServer().use { server ->
                server.enqueue(feed("0.3.3-beta.10", "preview"))
                val info = UpdateChecker.findUpdate("0.3.3-beta.9", 11, "preview", server.url("/updates.json").toString())
                assertEquals("0.3.3-beta.10", info?.versionName)
                assertEquals("https://example.com/inkita.apk", info?.url)
                assertEquals("/updates.json", server.takeRequest().path)
            }
        }

    @Test
    fun stableAndPreviewUseTheSameChannelRules() =
        runBlocking {
            MockWebServer().use { server ->
                val url = server.url("/updates.json").toString()
                server.enqueue(feed("0.3.3-beta.10", "preview"))
                assertNull(UpdateChecker.findUpdate("0.3.2", 11, "stable", url))
                server.enqueue(feed("0.3.3", "stable"))
                assertEquals("0.3.3", UpdateChecker.findUpdate("0.3.3-beta.10", 11, "preview", url)?.versionName)
                server.enqueue(feed("0.3.3-beta.9", "preview"))
                assertNull(UpdateChecker.findUpdate("0.3.3-beta.10", 11, "preview", url))
            }
        }

    @Test
    fun ignoresInvalidManifestAndReportsHttpErrors() {
        MockWebServer().use { server ->
            val url = server.url("/updates.json").toString()
            server.enqueue(feed("invalid", "preview"))
            assertNull(runBlocking { UpdateChecker.findUpdate("0.3.3-beta.9", 11, "preview", url) })
            server.enqueue(MockResponse().setBody("""{"version":"0.3.3-beta.10","channel":"preview"}"""))
            assertThrows(IOException::class.java) {
                runBlocking { UpdateChecker.findUpdate("0.3.3-beta.9", 11, "preview", url) }
            }
            server.enqueue(MockResponse().setResponseCode(503))
            assertThrows(IOException::class.java) {
                runBlocking { UpdateChecker.findUpdate("0.3.3-beta.9", 11, "preview", url) }
            }
        }
    }

    private fun feed(
        version: String,
        channel: String,
    ): MockResponse =
        MockResponse().setBody(
            """{"version":"$version","versionCode":11,"channel":"$channel","url":"https://example.com/inkita.apk"}""",
        )
}
