package net.dom53.inkita

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.SeriesMetadataDto
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesDetailCacheTest {
    @Test
    fun oversizedLegacyDetailIsACacheMiss() =
        withCache { cache, database ->
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO cached_series_detail_v2 (seriesId, detailJson, updatedAt) VALUES (?, ?, ?)",
                arrayOf<Any>(1, """{"padding":"${"x".repeat(3 * 1024 * 1024)}"}""", 1L),
            )

            assertNull(cache.getCachedSeriesDetailV2(1))
        }

    @Test
    fun oversizedDetailIsNotWrittenOrAllowedToReplaceCachedDetail() =
        withCache { cache, database ->
            val regularDetail =
                InkitaDetailV2(
                    series = null,
                    metadata = SeriesMetadataDto(summary = "Cached summary"),
                    wantToRead = null,
                    readingLists = null,
                    collections = null,
                    bookmarks = null,
                    annotations = null,
                    timeLeft = null,
                    hasProgress = null,
                    continuePoint = null,
                    seriesDetailPlus = null,
                    related = null,
                    detail = null,
                    rating = null,
                    readerProgress = null,
                )
            // The summary is stored twice. Only the combined UTF-8 byte size exceeds the limit.
            val oversizedDetail = regularDetail.copy(metadata = SeriesMetadataDto(summary = "ž".repeat(300_000)))

            cache.cacheSeriesDetailV2(1, regularDetail)
            cache.cacheSeriesDetailV2(1, oversizedDetail)
            cache.cacheSeriesDetailV2(2, oversizedDetail)

            assertEquals(regularDetail, cache.getCachedSeriesDetailV2(1))
            assertNull(database.seriesDetailV2Dao().getSeriesDetailUpdatedAt(2))
        }

    private fun withCache(block: suspend (CacheManagerImpl, InkitaDatabase) -> Unit) =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(context, InkitaDatabase::class.java).build()
            try {
                val cache =
                    CacheManagerImpl(
                        appPreferences = AppPreferences(context),
                        seriesDetailV2Dao = database.seriesDetailV2Dao(),
                        thumbnailsDir = null,
                    )
                block(cache, database)
            } finally {
                database.close()
            }
        }
}
