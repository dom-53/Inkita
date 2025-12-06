package net.dom53.inkita.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.dom53.inkita.data.local.db.entity.CachedPageEntity
import net.dom53.inkita.data.local.db.entity.LocalReaderProgressEntity

@Dao
interface ReaderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: CachedPageEntity)

    @Query("SELECT * FROM cached_pages WHERE chapterId = :chapterId AND page = :page LIMIT 1")
    suspend fun getPage(
        chapterId: Int,
        page: Int,
    ): CachedPageEntity?

    @Query("DELETE FROM cached_pages WHERE chapterId = :chapterId")
    suspend fun clearChapter(chapterId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalProgress(progress: LocalReaderProgressEntity)

    @Query("SELECT * FROM local_progress WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getLocalProgress(chapterId: Int): LocalReaderProgressEntity?

    @Query("SELECT * FROM local_progress")
    suspend fun getAllLocalProgress(): List<LocalReaderProgressEntity>

    @Query("DELETE FROM local_progress WHERE chapterId = :chapterId")
    suspend fun clearLocalProgress(chapterId: Int)
}
