package net.dom53.inkita.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity

@Dao
interface DownloadV2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: DownloadJobV2Entity): Long

    @Update
    suspend fun updateJob(job: DownloadJobV2Entity)

    @Query("SELECT * FROM download_jobs_v2 WHERE id = :jobId")
    suspend fun getJob(jobId: Long): DownloadJobV2Entity?

    @Query("SELECT * FROM download_jobs_v2 ORDER BY updatedAt DESC")
    fun observeJobs(): Flow<List<DownloadJobV2Entity>>

    @Query("SELECT * FROM download_jobs_v2 WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun getJobsByStatus(status: String): List<DownloadJobV2Entity>

    @Query("DELETE FROM download_jobs_v2 WHERE status = :status")
    suspend fun deleteJobsByStatus(status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<DownloadedItemV2Entity>)

    @Query("SELECT * FROM download_items_v2 WHERE jobId = :jobId ORDER BY id ASC")
    suspend fun getItemsForJob(jobId: Long): List<DownloadedItemV2Entity>

    @Query("SELECT * FROM download_items_v2 WHERE status = :status ORDER BY updatedAt DESC")
    fun observeItemsByStatus(status: String): Flow<List<DownloadedItemV2Entity>>

    @Query("DELETE FROM download_items_v2 WHERE id = :itemId")
    suspend fun deleteItemById(itemId: Long)

    @Query("DELETE FROM download_items_v2 WHERE jobId IN (SELECT id FROM download_jobs_v2 WHERE seriesId = :seriesId)")
    suspend fun deleteItemsForSeries(seriesId: Int)

    @Query("DELETE FROM download_jobs_v2 WHERE seriesId = :seriesId")
    suspend fun deleteJobsForSeries(seriesId: Int)

    @Query(
        """
        SELECT COUNT(*) FROM download_items_v2
        WHERE jobId IN (SELECT id FROM download_jobs_v2 WHERE volumeId = :volumeId)
          AND status = :status
        """,
    )
    suspend fun countCompletedItemsForVolume(
        volumeId: Int,
        status: String = DownloadedItemV2Entity.STATUS_COMPLETED,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM download_items_v2
        WHERE jobId IN (SELECT id FROM download_jobs_v2 WHERE volumeId = :volumeId)
        """,
    )
    suspend fun countItemsForVolume(volumeId: Int): Int

    @Query("DELETE FROM download_items_v2 WHERE jobId IN (SELECT id FROM download_jobs_v2 WHERE volumeId = :volumeId)")
    suspend fun deleteItemsForVolume(volumeId: Int)

    @Query("DELETE FROM download_jobs_v2 WHERE volumeId = :volumeId")
    suspend fun deleteJobsForVolume(volumeId: Int)

    @Query(
        """
        SELECT COUNT(*) FROM download_items_v2
        WHERE chapterId = :chapterId
          AND status = :status
        """,
    )
    suspend fun countCompletedItemsForChapter(
        chapterId: Int,
        status: String = DownloadedItemV2Entity.STATUS_COMPLETED,
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM download_items_v2
        WHERE chapterId = :chapterId
        """,
    )
    suspend fun countItemsForChapter(chapterId: Int): Int

    @Query("DELETE FROM download_items_v2 WHERE jobId = :jobId")
    suspend fun clearItemsForJob(jobId: Long)

    @Query("DELETE FROM download_jobs_v2 WHERE id = :jobId")
    suspend fun deleteJob(jobId: Long)

    @Query("DELETE FROM download_items_v2")
    suspend fun clearAllItems()

    @Query("DELETE FROM download_jobs_v2")
    suspend fun clearAllJobs()
}
