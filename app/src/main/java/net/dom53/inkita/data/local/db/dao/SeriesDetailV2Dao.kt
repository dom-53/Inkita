package net.dom53.inkita.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.dom53.inkita.data.local.db.entity.CachedChapterV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailChunkEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailRelatedRefEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesVolumeRefEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeChapterRefEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeV2Entity

@Dao
interface SeriesDetailV2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesDetail(detail: CachedSeriesDetailV2Entity)

    @Query("INSERT OR REPLACE INTO cached_series_detail_v2 (seriesId, updatedAt, chunkCount) VALUES (:seriesId, :updatedAt, :chunkCount)")
    suspend fun replaceChunkedDetailHeader(
        seriesId: Int,
        updatedAt: Long,
        chunkCount: Int,
    )

    @Insert
    suspend fun insertSeriesDetailChunks(chunks: List<CachedSeriesDetailChunkEntity>)

    @Transaction
    suspend fun replaceChunkedSeriesDetail(
        seriesId: Int,
        updatedAt: Long,
        chunks: List<CachedSeriesDetailChunkEntity>,
    ) {
        // Replacing the header removes its previous chunks via the foreign key.
        replaceChunkedDetailHeader(seriesId, updatedAt, chunks.size)
        insertSeriesDetailChunks(chunks)
    }

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

    @Query("SELECT payload FROM cached_series_detail_chunks WHERE seriesId = :seriesId ORDER BY chunkIndex")
    suspend fun getSeriesDetailChunks(seriesId: Int): List<ByteArray>

    @Transaction
    suspend fun getSeriesDetailWithChunks(seriesId: Int): Pair<CachedSeriesDetailV2Entity, List<ByteArray>>? {
        val detail = getSeriesDetail(seriesId) ?: return null
        val chunks = if (detail.chunkCount > 0) getSeriesDetailChunks(seriesId) else emptyList()
        return detail to chunks
    }

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

    @Query("SELECT COUNT(*) FROM cached_chapters_v2")
    suspend fun getChaptersCount(): Int

    @Query("SELECT COUNT(*) FROM cached_volume_chapter_refs_v2")
    suspend fun getVolumeChapterRefsCount(): Int

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
