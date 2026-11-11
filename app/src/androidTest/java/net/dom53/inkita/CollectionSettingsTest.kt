package net.dom53.inkita

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.KavitaProfile
import net.dom53.inkita.core.storage.KavitaSession
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.ui.settings.screens.SettingsAdvancedScreen
import net.dom53.inkita.ui.theme.InkitaTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CollectionSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun switchingProfilesKeepsTheirOwnCacheAndClearsItOnAnEmptyResponse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MockWebServer().use { server ->
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.getHeader("x-api-key") == "original") {
                            MockResponse().setResponseCode(503)
                        } else {
                            MockResponse().setHeader("Content-Type", "application/json").setBody("[]")
                        }
                }
            val config = AppConfig(server.url("/").toString(), "original", "", 1)
            val original = AppPreferences(context, KavitaSession(config, KavitaProfile.Legacy))
            val secondConfig = config.copy(apiKey = "second", userId = 2)
            val second = AppPreferences(context, KavitaSession(secondConfig, KavitaProfile(UUID.randomUUID().toString())))
            val active = mutableStateOf(original)
            val originalCollection = Collection(1, "Original account collection")
            val staleCollection = Collection(1, "Stale second account collection")
            val savedCollections = runBlocking { original.loadCachedCollections() }
            val savedPolicy = runBlocking { original.prefetchPolicy() }
            val savedOfflineMode = runBlocking { original.offlineModeFlow.first() }
            try {
                runBlocking {
                    original.setOfflineMode(false)
                    original.saveCollectionsCache(listOf(originalCollection))
                    original.setPrefetchCollections(true)
                    original.setPrefetchCollectionsAll(false)
                    original.setPrefetchCollectionIds(listOf(originalCollection.id))

                    assertTrue(second.loadCachedCollections().isEmpty())
                    val secondPolicy = second.prefetchPolicy()
                    assertFalse(secondPolicy.collectionsEnabled)
                    assertTrue(secondPolicy.collectionsAll)
                    assertTrue(secondPolicy.collectionIds.isEmpty())

                    second.saveCollectionsCache(listOf(staleCollection))
                    second.setPrefetchCollections(true)
                    second.setPrefetchCollectionsAll(false)
                }
                compose.setContent {
                    InkitaTheme(darkTheme = false, dynamicColor = false) {
                        val prefs = active.value
                        val cache = remember(prefs) { CacheManagerImpl(prefs, thumbnailsDir = null) }
                        SettingsAdvancedScreen(prefs, cache, onBack = {})
                    }
                }
                waitForText(originalCollection.name)
                assertEquals("original", server.takeRequest(5, TimeUnit.SECONDS)?.getHeader("x-api-key"))

                compose.runOnIdle { active.value = second }
                waitForText(context.getString(R.string.settings_cache_prefetch_collections_empty))
                compose.onNodeWithText(originalCollection.name).assertDoesNotExist()
                compose.onNodeWithText(staleCollection.name).assertDoesNotExist()
                compose.waitUntil(timeoutMillis = 5_000) { runBlocking { second.loadCachedCollections().isEmpty() } }

                compose.runOnIdle { active.value = original }
                waitForText(originalCollection.name)
                assertEquals(listOf(originalCollection.id), runBlocking { original.prefetchPolicy().selectedCollectionIds })
            } finally {
                runBlocking {
                    original.saveCollectionsCache(savedCollections)
                    original.setPrefetchCollections(savedPolicy.collectionsEnabled)
                    original.setPrefetchCollectionsAll(savedPolicy.collectionsAll)
                    original.setPrefetchCollectionIds(savedPolicy.collectionIds)
                    original.setOfflineMode(savedOfflineMode)
                }
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
