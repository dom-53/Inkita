package net.dom53.inkita.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.dom53.inkita.data.local.db.entity.CachedChapterV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailRelatedRefEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesVolumeRefEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeChapterRefEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeV2Entity

@Dao
interface SeriesDetailV2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesDetail(detail: CachedSeriesDetailV2Entity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelatedRefs(refs: List<CachedSeriesDetailRelatedRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVolumes(volumes: List<CachedVolumeV2Entity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesVolumeRefs(refs: List<CachedSeriesVolumeRefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapters(chapters: List<CachedChapterV2Entity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVolumeChapterRefs(refs: List<CachedVolumeChapterRefEntity>)

    @Query("SELECT * FROM cached_series_detail_v2 WHERE seriesId = :seriesId")
    suspend fun getSeriesDetail(seriesId: Int): CachedSeriesDetailV2Entity?

    @Query("SELECT updatedAt FROM cached_series_detail_v2 WHERE seriesId = :seriesId")
    suspend fun getSeriesDetailUpdatedAt(seriesId: Int): Long?

    @Query("SELECT COUNT(*) FROM cached_series_detail_v2")
    suspend fun getDetailsCount(): Int

    @Query("SELECT COUNT(*) FROM cached_series_detail_related_refs_v2")
    suspend fun getRelatedRefsCount(): Int

    @Query("SELECT COUNT(*) FROM cached_volumes_v2")
    suspend fun getVolumesCount(): Int

    @Query("SELECT COUNT(*) FROM cached_series_volume_refs_v2")
    suspend fun getSeriesVolumeRefsCount(): Int

    @Query("SELECT * FROM cached_volumes_v2 WHERE seriesId = :seriesId")
    suspend fun getSeriesVolumes(seriesId: Int): List<CachedVolumeV2Entity>?

    @Query("SELECT COUNT(*) FROM cached_chapters_v2")
    suspend fun getChaptersCount(): Int

    @Query("SELECT COUNT(*) FROM cached_volume_chapter_refs_v2")
    suspend fun getVolumeChapterRefsCount(): Int

    @Query("SELECT * FROM cached_chapters_v2 WHERE volumeId = :volumeId")
    suspend fun getSeriesVolumeChapters(volumeId: Int): List<CachedChapterV2Entity>?

    @Query("DELETE FROM cached_series_detail_v2")
    suspend fun clearSeriesDetails()

    @Query("DELETE FROM cached_series_detail_related_refs_v2")
    suspend fun clearRelatedRefs()

    @Query("DELETE FROM cached_volumes_v2")
    suspend fun clearVolumes()

    @Query("DELETE FROM cached_series_volume_refs_v2")
    suspend fun clearSeriesVolumeRefs()

    @Query("DELETE FROM cached_chapters_v2")
    suspend fun clearChapters()

    @Query("DELETE FROM cached_volume_chapter_refs_v2")
    suspend fun clearVolumeChapterRefs()
}
