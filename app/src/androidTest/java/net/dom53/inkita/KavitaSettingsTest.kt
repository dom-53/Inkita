package net.dom53.inkita

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.forKavita
import net.dom53.inkita.core.network.readKavitaCaCertificate
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.data.repository.AuthRepositoryImpl
import net.dom53.inkita.ui.reader.screen.KavitaWebResources
import net.dom53.inkita.ui.settings.screens.SettingsKavitaScreen
import net.dom53.inkita.ui.theme.InkitaTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

@RunWith(AndroidJUnit4::class)
class KavitaSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val original = AppConfig("https://old.example", "old-key", "old-image-key", 1)
    private val saved = MutableStateFlow(original)

    @Test
    fun privateCaServerFailsBeforeCertificateIsConfigured() {
        val authority = HeldCertificate.Builder().certificateAuthority(1).build()
        MockWebServer().use { server ->
            server.usePrivateCa(authority)
            server.enqueue(MockResponse().setBody("""{"id":2}"""))
            server.start()
            openSettings()
            enterConnection(server.url("/").toString(), "valid-key")
            compose.onNodeWithText(label(R.string.settings_kavita_button_login)).performClick()

            assertSaveError(label(R.string.settings_kavita_tls_failed))
            assertEquals(original, saved.value)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun importedCaIsUsedToSaveAndReadFromKavita() {
        val authority = HeldCertificate.Builder().certificateAuthority(1).build()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("kavita-ca", ".pem", context.cacheDir)
        try {
            file.writeText(authority.certificatePem())
            MockWebServer().use { server ->
                server.usePrivateCa(authority)
                repeat(2) { server.enqueue(MockResponse().setBody("""{"id":2}""")) }
                server.enqueue(MockResponse().setBody("cover"))
                server.enqueue(MockResponse().addHeader("Content-Type", "image/png").setBody("epub-image"))
                val url = server.url("/kavita/").toString()
                openSettings(Uri.fromFile(file))
                enterConnection(url, "valid-key")
                compose.onNodeWithText(label(R.string.settings_kavita_ca_import)).performScrollTo().performClick()
                compose.waitUntil(timeoutMillis = 5000) {
                    compose.onAllNodesWithText(label(R.string.settings_kavita_ca_selected)).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(label(R.string.settings_kavita_button_login)).performScrollTo().performClick()
                compose.waitUntil(timeoutMillis = 5000) { saved.value != original }

                val expectedCa = readKavitaCaCertificate(authority.certificate.encoded.inputStream())
                assertEquals(AppConfig(url.trimEnd('/'), "valid-key", "old-image-key", 2, expectedCa), saved.value)
                assertEquals(
                    2,
                    runBlocking {
                        KavitaApiFactory
                            .createAuthenticated(saved.value)
                            .getAccount()
                            .body()
                            ?.id
                    },
                )
                val request = Request.Builder().url(server.url("/cover")).build()
                OkHttpClient().forKavita(saved.value).newCall(request).execute().use { response ->
                    assertEquals("cover", response.body?.string())
                }
                val resources = KavitaWebResources(saved.value)
                val embedded = resources.intercept(webResource(server.url("/epub-image").toString()))
                assertEquals("image/png", embedded?.mimeType)
                assertEquals("epub-image", embedded?.data?.bufferedReader()?.use { it.readText() })
                assertNull(resources.intercept(webResource("https://elsewhere.example/epub-image")))
                assertEquals(4, server.requestCount)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun privateCaStillRequiresTheCorrectHostname() {
        val authority = HeldCertificate.Builder().certificateAuthority(1).build()
        MockWebServer().use { server ->
            server.usePrivateCa(authority, "wrong.example")
            val url = server.url("/")
            val calls = OkHttpClient().forKavita(configWithCa(url.toString(), authority))

            assertThrows(SSLPeerUnverifiedException::class.java) {
                calls.newCall(Request.Builder().url(url).build()).execute().close()
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun privateCaDoesNotApplyToAnotherPortOrRedirect() {
        val authority = HeldCertificate.Builder().certificateAuthority(1).build()
        MockWebServer().use { server ->
            MockWebServer().use { other ->
                server.usePrivateCa(authority)
                other.usePrivateCa(authority)
                val url = server.url("/")
                val otherUrl = other.url("/")
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", otherUrl))
                val calls = OkHttpClient().forKavita(configWithCa(url.toString(), authority))

                assertThrows(SSLPeerUnverifiedException::class.java) {
                    calls.newCall(Request.Builder().url(url).build()).execute().close()
                }
                assertThrows(SSLException::class.java) {
                    calls.newCall(Request.Builder().url(otherUrl).build()).execute().close()
                }
                assertEquals(0, other.requestCount)
            }
        }
    }

    @Test
    fun invalidHttpKeyDoesNotReplaceSavedConfigInDarkMode() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.start()
            openSettings()
            saveOverHttp(server.url("/").toString(), "invalid-key")

            assertSaveError(label(R.string.settings_kavita_rejected_key))
            assertEquals(original, saved.value)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun validHttpConnectionIsSavedAfterValidation() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":2,"username":"reader","email":null}"""))
            server.start()
            val url = server.url("/kavita/").toString()
            openSettings()
            saveOverHttp(url, "valid-key")

            compose.waitUntil(timeoutMillis = 5000) { saved.value != original }

            assertEquals(AppConfig(url.trimEnd('/'), "valid-key", "old-image-key", 2), saved.value)
            val request = server.takeRequest()
            assertEquals("/kavita/api/Account", request.path)
            assertEquals("valid-key", request.getHeader("x-api-key"))
        }
    }

    @Test
    fun unreachableHttpServerShowsNetworkErrorAndKeepsConfig() {
        val url =
            MockWebServer().use { server ->
                server.start()
                server.url("/").toString()
            }
        openSettings()
        saveOverHttp(url, "valid-key")

        assertSaveError(label(R.string.settings_kavita_unreachable))
        assertEquals(original, saved.value)
    }

    @Test
    fun validationDoesNotSendKeyToRedirectTarget() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/other")))
            openSettings()
            saveOverHttp(server.url("/").toString(), "test-key")

            assertSaveError(label(R.string.settings_kavita_http_error, 302))
            assertEquals(original, saved.value)
            assertEquals(1, server.requestCount)
        }
    }

    private fun openSettings(certificateUri: Uri? = null) {
        val testRegistry =
            object : ActivityResultRegistry(), ActivityResultRegistryOwner {
                override val activityResultRegistry = this

                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    dispatchResult(requestCode, certificateUri)
                }
            }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides testRegistry) {
                InkitaTheme(darkTheme = true, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        SettingsKavitaScreen(
                            configFlow = saved,
                            authRepository = AuthRepositoryImpl { saved.value = it },
                            onBack = {},
                            onLogout = {},
                        )
                    }
                }
            }
        }
    }

    private fun MockWebServer.usePrivateCa(
        authority: HeldCertificate,
        hostname: String = "localhost",
    ) {
        val intermediate =
            HeldCertificate
                .Builder()
                .certificateAuthority(0)
                .signedBy(authority)
                .build()
        val certificate =
            HeldCertificate
                .Builder()
                .addSubjectAlternativeName(hostname)
                .signedBy(intermediate)
                .build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate, intermediate.certificate).build()
        useHttps(tls.sslSocketFactory(), false)
    }

    private fun configWithCa(
        url: String,
        authority: HeldCertificate,
    ): AppConfig = original.copy(serverUrl = url, caCertificate = readKavitaCaCertificate(authority.certificate.encoded.inputStream()))

    private fun webResource(url: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame(): Boolean = false

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = false

            override fun getMethod(): String = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

    private fun saveOverHttp(
        url: String,
        apiKey: String,
    ) {
        enterConnection(url, apiKey)
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText(label(R.string.settings_kavita_http_warning_confirm)).performClick()
        compose.onNodeWithText(label(R.string.settings_kavita_button_login)).performClick()
        compose.onNodeWithText(label(R.string.settings_kavita_http_warning_confirm)).performClick()
    }

    private fun enterConnection(
        url: String,
        apiKey: String,
    ) {
        compose.onNode(hasSetTextAction() and hasText(label(R.string.settings_kavita_server))).performTextReplacement(url)
        compose.onNode(hasSetTextAction() and hasText(label(R.string.settings_kavita_api_key))).performTextReplacement(apiKey)
    }

    private fun assertSaveError(message: String) {
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(message).assertIsDisplayed()
    }

    private fun label(
        id: Int,
        vararg args: Any,
    ): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
}
