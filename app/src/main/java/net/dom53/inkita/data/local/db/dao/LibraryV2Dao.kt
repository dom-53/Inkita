package net.dom53.inkita.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.dom53.inkita.data.local.db.entity.CachedCollectionRefEntity
import net.dom53.inkita.data.local.db.entity.CachedCollectionV2Entity
import net.dom53.inkita.data.local.db.entity.CachedPersonRefEntity
import net.dom53.inkita.data.local.db.entity.CachedPersonV2Entity
import net.dom53.inkita.data.local.db.entity.CachedReadingListRefEntity
import net.dom53.inkita.data.local.db.entity.CachedReadingListV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesListRefEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesV2Entity

@Dao
interface LibraryV2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeries(items: List<CachedSeriesV2Entity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeriesRefs(refs: List<CachedSeriesListRefEntity>)

    @Query("DELETE FROM cached_series_list_refs_v2 WHERE listType = :listType AND listKey = :listKey")
    suspend fun clearSeriesRefs(listType: String, listKey: String)

    @Query("DELETE FROM cached_series_list_refs_v2")
    suspend fun clearAllSeriesRefs()

    @Query("DELETE FROM cached_series_v2")
    suspend fun clearAllSeries()

    @Query(
        """
        SELECT cs.* FROM cached_series_v2 cs
        INNER JOIN cached_series_list_refs_v2 ref ON cs.id = ref.seriesId
        WHERE ref.listType = :listType AND ref.listKey = :listKey
        ORDER BY ref.position ASC
        """,
    )
    suspend fun getSeriesForList(listType: String, listKey: String): List<CachedSeriesV2Entity>

    @Query(
        """
        SELECT MAX(updatedAt) FROM cached_series_list_refs_v2
        WHERE listType = :listType AND listKey = :listKey
        """,
    )
    suspend fun getSeriesListUpdatedAt(listType: String, listKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollections(items: List<CachedCollectionV2Entity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollectionRefs(refs: List<CachedCollectionRefEntity>)

    @Query("DELETE FROM cached_collection_refs_v2 WHERE listType = :listType")
    suspend fun clearCollectionRefs(listType: String)

    @Query("DELETE FROM cached_collection_refs_v2")
    suspend fun clearAllCollectionRefs()

    @Query("DELETE FROM cached_collections_v2")
    suspend fun clearAllCollections()

    @Query(
        """
        SELECT c.* FROM cached_collections_v2 c
        INNER JOIN cached_collection_refs_v2 ref ON c.id = ref.collectionId
        WHERE ref.listType = :listType
        ORDER BY ref.position ASC
        """,
    )
    suspend fun getCollectionsForList(listType: String): List<CachedCollectionV2Entity>

    @Query("SELECT MAX(updatedAt) FROM cached_collection_refs_v2 WHERE listType = :listType")
    suspend fun getCollectionsUpdatedAt(listType: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingLists(items: List<CachedReadingListV2Entity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingListRefs(refs: List<CachedReadingListRefEntity>)

    @Query("DELETE FROM cached_reading_list_refs_v2 WHERE listType = :listType")
    suspend fun clearReadingListRefs(listType: String)

    @Query("DELETE FROM cached_reading_list_refs_v2")
    suspend fun clearAllReadingListRefs()

    @Query("DELETE FROM cached_reading_lists_v2")
    suspend fun clearAllReadingLists()

    @Query(
        """
        SELECT r.* FROM cached_reading_lists_v2 r
        INNER JOIN cached_reading_list_refs_v2 ref ON r.id = ref.readingListId
        WHERE ref.listType = :listType
        ORDER BY ref.position ASC
        """,
    )
    suspend fun getReadingListsForList(listType: String): List<CachedReadingListV2Entity>

    @Query("SELECT MAX(updatedAt) FROM cached_reading_list_refs_v2 WHERE listType = :listType")
    suspend fun getReadingListsUpdatedAt(listType: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeople(items: List<CachedPersonV2Entity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonRefs(refs: List<CachedPersonRefEntity>)

    @Query("DELETE FROM cached_person_refs_v2 WHERE listType = :listType AND page = :page")
    suspend fun clearPersonRefs(listType: String, page: Int)

    @Query("DELETE FROM cached_person_refs_v2")
    suspend fun clearAllPersonRefs()

    @Query("DELETE FROM cached_people_v2")
    suspend fun clearAllPeople()

    @Query(
        """
        SELECT p.* FROM cached_people_v2 p
        INNER JOIN cached_person_refs_v2 ref ON p.id = ref.personId
        WHERE ref.listType = :listType AND ref.page = :page
        ORDER BY ref.position ASC
        """,
    )
    suspend fun getPeopleForList(listType: String, page: Int): List<CachedPersonV2Entity>

    @Query(
        """
        SELECT MAX(updatedAt) FROM cached_person_refs_v2
        WHERE listType = :listType AND page = :page
        """,
    )
    suspend fun getPeopleUpdatedAt(listType: String, page: Int): Long?
}
