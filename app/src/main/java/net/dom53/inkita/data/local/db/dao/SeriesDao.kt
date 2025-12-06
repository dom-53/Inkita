package net.dom53.inkita.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.dom53.inkita.data.local.db.entity.CachedBrowseRefEntity
import net.dom53.inkita.data.local.db.entity.CachedChapterEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesRefEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeEntity

@Dao
interface SeriesDao {
    @Query("SELECT * FROM cached_series ORDER BY name COLLATE NOCASE")
    suspend fun getAllOnce(): List<CachedSeriesEntity>

    @Query("SELECT * FROM cached_series WHERE id = :id")
    suspend fun getById(id: Int): CachedSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CachedSeriesEntity>)

    @Query("DELETE FROM cached_series")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRefs(refs: List<CachedSeriesRefEntity>)

    @Query(
        """
        DELETE FROM cached_series_refs
        WHERE tabType = :tabType AND collectionId = :collectionId
        """,
    )
    suspend fun clearRefsForTab(
        tabType: String,
        collectionId: Int,
    )

    @Query(
        """
        SELECT cs.* FROM cached_series cs
        INNER JOIN cached_series_refs ref ON cs.id = ref.seriesId
        WHERE ref.tabType = :tabType AND ref.collectionId = :collectionId
        ORDER BY cs.name COLLATE NOCASE
        """,
    )
    suspend fun getSeriesForTab(
        tabType: String,
        collectionId: Int,
    ): List<CachedSeriesEntity>

    suspend fun replaceTabRefs(
        tabType: String,
        collectionId: Int,
        refs: List<CachedSeriesRefEntity>,
    ) {
        clearRefsForTab(tabType, collectionId)
        if (refs.isNotEmpty()) {
            upsertRefs(refs)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrowseRefs(refs: List<CachedBrowseRefEntity>)

    @Query("DELETE FROM cached_browse_refs WHERE queryKey = :queryKey AND page = :page")
    suspend fun clearBrowsePage(
        queryKey: String,
        page: Int,
    )

    @Query(
        """
        SELECT cs.* FROM cached_series cs
        INNER JOIN cached_browse_refs ref ON cs.id = ref.seriesId
        WHERE ref.queryKey = :queryKey AND ref.page = :page
        ORDER BY cs.name COLLATE NOCASE
        """,
    )
    suspend fun getBrowsePage(
        queryKey: String,
        page: Int,
    ): List<CachedSeriesEntity>

    suspend fun replaceBrowsePage(
        queryKey: String,
        page: Int,
        refs: List<CachedBrowseRefEntity>,
    ) {
        clearBrowsePage(queryKey, page)
        if (refs.isNotEmpty()) {
            upsertBrowseRefs(refs)
        }
    }

    // Series detail + volumes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesDetail(detail: CachedSeriesDetailEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVolumes(volumes: List<CachedVolumeEntity>)

    @Query("DELETE FROM cached_volumes WHERE seriesId = :seriesId")
    suspend fun clearVolumes(seriesId: Int)

    @Query("SELECT * FROM cached_series_detail WHERE seriesId = :seriesId LIMIT 1")
    suspend fun getSeriesDetail(seriesId: Int): CachedSeriesDetailEntity?

    @Query("SELECT * FROM cached_volumes WHERE seriesId = :seriesId ORDER BY minNumber ASC, maxNumber ASC, id ASC")
    suspend fun getVolumes(seriesId: Int): List<CachedVolumeEntity>

    // Chapters (pages)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapters(chapters: List<CachedChapterEntity>)

    @Query("DELETE FROM cached_chapters WHERE volumeId IN (SELECT id FROM cached_volumes WHERE seriesId = :seriesId)")
    suspend fun clearChaptersForSeries(seriesId: Int)

    @Query("SELECT * FROM cached_chapters WHERE volumeId IN (:volumeIds) ORDER BY pageIndex ASC")
    suspend fun getChaptersForVolumes(volumeIds: List<Int>): List<CachedChapterEntity>

    @Query("DELETE FROM cached_series_refs")
    suspend fun clearAllTabRefs()

    @Query("DELETE FROM cached_browse_refs")
    suspend fun clearAllBrowseRefs()

    @Query("DELETE FROM cached_series_detail")
    suspend fun clearAllSeriesDetail()

    @Query("DELETE FROM cached_volumes")
    suspend fun clearAllVolumes()

    @Query("DELETE FROM cached_chapters")
    suspend fun clearAllChapters()

    @Query("DELETE FROM cached_series")
    suspend fun clearAllSeries()

    @Query("SELECT COUNT(*) FROM cached_series")
    suspend fun countSeries(): Int

    @Query("SELECT COUNT(*) FROM cached_series_refs")
    suspend fun countTabRefs(): Int

    @Query("SELECT COUNT(*) FROM cached_browse_refs")
    suspend fun countBrowseRefs(): Int

    @Query("SELECT COUNT(*) FROM cached_series_detail")
    suspend fun countSeriesDetail(): Int

    @Query("SELECT COUNT(*) FROM cached_volumes")
    suspend fun countVolumes(): Int

    @Query("SELECT COUNT(*) FROM cached_chapters")
    suspend fun countChapters(): Int
}
