package net.dom53.inkita.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.dom53.inkita.data.local.db.entity.DownloadTaskEntity
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity

@Dao
interface DownloadDao {
    // Tasks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<DownloadTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity): Long

    @Query(
        """
        SELECT * FROM download_tasks
        WHERE state IN (:states)
        ORDER BY priority DESC, createdAt ASC
        """,
    )
    suspend fun getTasksByState(states: List<String>): List<DownloadTaskEntity>

    @Query(
        """
        UPDATE download_tasks
        SET state = :state, updatedAt = :updatedAt, error = :error, progress = :progress, total = :total, bytes = :bytes, bytesTotal = :bytesTotal
        WHERE id = :id
        """,
    )
    suspend fun updateTaskState(
        id: Long,
        state: String,
        updatedAt: Long,
        error: String?,
        progress: Int = 0,
        total: Int = 0,
        bytes: Long = 0L,
        bytesTotal: Long = 0L,
    )

    @Query(
        """
        UPDATE download_tasks
        SET workId = :workId, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateTaskWorkId(
        id: Long,
        workId: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM download_tasks WHERE state IN (:states)")
    suspend fun deleteTasksByStates(states: List<String>)

    @Query("DELETE FROM download_tasks")
    suspend fun clearAllTasks()

    @Query("SELECT * FROM download_tasks ORDER BY priority DESC, createdAt ASC")
    suspend fun getAllTasks(): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks ORDER BY priority DESC, createdAt ASC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): DownloadTaskEntity?

    @Query("SELECT COUNT(*) FROM download_tasks WHERE state IN (:states) AND workId IS NOT NULL")
    suspend fun countEnqueuedStates(states: List<String>): Int

    @Query(
        """
        SELECT * FROM download_tasks
        WHERE state = :state AND (workId IS NULL OR workId = '')
        ORDER BY priority DESC, createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingUnenqueued(
        state: String = DownloadTaskEntity.STATE_PENDING,
        limit: Int = 1,
    ): List<DownloadTaskEntity>

    @Query(
        """
        SELECT * FROM download_tasks
        WHERE seriesId = :seriesId
          AND (volumeId IS :volumeId OR volumeId = :volumeId)
          AND (chapterId IS :chapterId OR chapterId = :chapterId)
          AND (pageStart IS :pageStart OR pageStart = :pageStart)
          AND (pageEnd IS :pageEnd OR pageEnd = :pageEnd)
          AND type = :type
          AND state IN (:states)
        LIMIT 1
        """,
    )
    suspend fun findDuplicateTask(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int?,
        pageStart: Int?,
        pageEnd: Int?,
        type: String,
        states: List<String>,
    ): DownloadTaskEntity?

    // Downloaded pages
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadedPage(page: DownloadedPageEntity)

    @Query(
        """
        SELECT * FROM downloaded_pages
        WHERE chapterId = :chapterId AND page = :page
        LIMIT 1
        """,
    )
    suspend fun getDownloadedPage(
        chapterId: Int,
        page: Int,
    ): DownloadedPageEntity?

    @Query("SELECT * FROM downloaded_pages ORDER BY updatedAt DESC")
    fun observeDownloadedPages(): Flow<List<DownloadedPageEntity>>

    @Query("SELECT * FROM downloaded_pages")
    suspend fun getAllDownloadedPages(): List<DownloadedPageEntity>

    @Query("SELECT * FROM downloaded_pages WHERE volumeId = :volumeId")
    suspend fun getDownloadedPagesByVolume(volumeId: Int): List<DownloadedPageEntity>

    @Query("SELECT * FROM downloaded_pages WHERE seriesId = :seriesId")
    suspend fun getDownloadedPagesBySeries(seriesId: Int): List<DownloadedPageEntity>

    @Query("SELECT * FROM downloaded_pages WHERE chapterId = :chapterId AND page < :pageCutoff")
    suspend fun getDownloadedPagesBefore(
        chapterId: Int,
        pageCutoff: Int,
    ): List<DownloadedPageEntity>

    @Query("DELETE FROM downloaded_pages WHERE chapterId = :chapterId AND page = :page")
    suspend fun deleteDownloadedPage(
        chapterId: Int,
        page: Int,
    )

    @Query("DELETE FROM downloaded_pages WHERE volumeId = :volumeId")
    suspend fun deleteDownloadedVolume(volumeId: Int)

    @Query("DELETE FROM downloaded_pages WHERE seriesId = :seriesId")
    suspend fun clearDownloadedSeries(seriesId: Int)

    @Query("DELETE FROM downloaded_pages")
    suspend fun clearAllDownloadedPages()
}
