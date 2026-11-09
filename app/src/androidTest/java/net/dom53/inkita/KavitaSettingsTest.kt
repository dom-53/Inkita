package net.dom53.inkita

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.data.repository.AuthRepositoryImpl
import net.dom53.inkita.ui.settings.screens.SettingsKavitaScreen
import net.dom53.inkita.ui.theme.InkitaTheme
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KavitaSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val original = AppConfig("https://old.example", "old-key", "old-image-key", 1)
    private val saved = MutableStateFlow(original)

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

    private fun openSettings() {
        compose.setContent {
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

    private fun saveOverHttp(
        url: String,
        apiKey: String,
    ) {
        compose.onNode(hasSetTextAction() and hasText(label(R.string.settings_kavita_server))).performTextReplacement(url)
        compose.onNode(hasSetTextAction() and hasText(label(R.string.settings_kavita_api_key))).performTextReplacement(apiKey)
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText(label(R.string.settings_kavita_http_warning_confirm)).performClick()
        compose.onNodeWithText(label(R.string.settings_kavita_button_login)).performClick()
        compose.onNodeWithText(label(R.string.settings_kavita_http_warning_confirm)).performClick()
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
