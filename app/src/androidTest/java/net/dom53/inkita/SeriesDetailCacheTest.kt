package net.dom53.inkita

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.SeriesDetailDto
import net.dom53.inkita.data.api.dto.SeriesMetadataDto
import net.dom53.inkita.data.api.dto.VolumeDto
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailChunkEntity
import net.dom53.inkita.ui.seriesdetail.InkitaDetailV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesDetailCacheTest {
    @Test
    fun readableLegacyDetailRemainsAvailable() =
        withCache { cache, database ->
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO cached_series_detail_v2 (seriesId, metadataJson, updatedAt) VALUES (?, ?, ?)",
                arrayOf<Any>(1, """{"summary":"Legacy summary"}""", 1L),
            )

            assertEquals(detailWithSummary("Legacy summary"), cache.getCachedSeriesDetailV2(1))
        }

    @Test
    fun oversizedLegacyDetailIsACacheMiss() =
        withCache { cache, database ->
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO cached_series_detail_v2 (seriesId, detailJson, updatedAt) VALUES (?, ?, ?)",
                arrayOf<Any>(1, """{"padding":"${"x".repeat(3 * 1024 * 1024)}"}""", 1L),
            )

            assertNull(cache.getCachedSeriesDetailV2(1))

            val refreshedDetail = detailWithSummary("ž".repeat(600_000))
            cache.cacheSeriesDetailV2(1, refreshedDetail)
            assertEquals(refreshedDetail, cache.getCachedSeriesDetailV2(1))
        }

    @Test
    fun largeDetailRoundTripsWithAllChapterGroupsAndOrder() =
        withCache { cache, database ->
            val first = ChapterDto(id = 20, title = "First", volumeId = 7, summary = "č😀".repeat(220_000))
            val second = ChapterDto(id = 10, title = "Second", volumeId = 7)
            val special = ChapterDto(id = 30, title = "Special", isSpecial = true)
            val detail =
                detailWithSummary("Large series").copy(
                    detail =
                        SeriesDetailDto(
                            volumes = listOf(VolumeDto(id = 7, name = "Volume", chapters = listOf(first, second))),
                            unreadCount = 3,
                            totalCount = 3,
                            chapters = listOf(first, second),
                            specials = listOf(special),
                            storylineChapters = listOf(first, special, second),
                        ),
                )
            cache.cacheSeriesDetailV2(1, detail)

            val dao = database.seriesDetailV2Dao()
            val chunks = dao.getSeriesDetailChunks(1)
            assertTrue(chunks.sumOf { it.size } > 3 * 1024 * 1024)
            assertTrue(chunks.all { it.size <= 256 * 1024 })
            assertTrue((cache.getSeriesDetailV2UpdatedAt(1) ?: 0) > 0)
            assertEquals(detail, cache.getCachedSeriesDetailV2(1))
        }

    @Test
    fun replacingOrClearingLargeDetailRemovesItsChunks() =
        withCache { cache, database ->
            val dao = database.seriesDetailV2Dao()
            val regularDetail = detailWithSummary("Cached summary")
            val largeDetail = detailWithSummary("ž".repeat(600_000))
            val replacement = detailWithSummary("文".repeat(380_000))
            cache.cacheSeriesDetailV2(1, regularDetail)
            assertEquals(regularDetail, cache.getCachedSeriesDetailV2(1))

            cache.cacheSeriesDetailV2(1, largeDetail)
            assertEquals(largeDetail, cache.getCachedSeriesDetailV2(1))
            cache.cacheSeriesDetailV2(1, replacement)
            assertEquals(replacement, cache.getCachedSeriesDetailV2(1))

            cache.cacheSeriesDetailV2(1, regularDetail)
            assertEquals(regularDetail, cache.getCachedSeriesDetailV2(1))
            assertTrue(dao.getSeriesDetailChunks(1).isEmpty())

            cache.cacheSeriesDetailV2(2, largeDetail)
            cache.clearDetails()
            assertNull(cache.getCachedSeriesDetailV2(2))
            assertTrue(dao.getSeriesDetailChunks(2).isEmpty())
        }

    @Test
    fun failedChunkWritePreservesPreviousDetail() =
        withCache { cache, database ->
            val previous = detailWithSummary("ž".repeat(600_000))
            cache.cacheSeriesDetailV2(1, previous)
            val duplicate = CachedSeriesDetailChunkEntity(1, 0, byteArrayOf(1))
            val failure =
                runCatching {
                    database.seriesDetailV2Dao().replaceChunkedSeriesDetail(1, 1L, listOf(duplicate, duplicate))
                }.exceptionOrNull()

            assertTrue(failure is SQLiteConstraintException)
            assertEquals(previous, cache.getCachedSeriesDetailV2(1))
        }

    private fun detailWithSummary(summary: String): InkitaDetailV2 =
        InkitaDetailV2(
            series = null,
            metadata = SeriesMetadataDto(summary = summary),
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
