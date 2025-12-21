@file:Suppress("MagicNumber")

package net.dom53.inkita.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.dom53.inkita.data.local.db.dao.DownloadDao
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.dao.LibraryV2Dao
import net.dom53.inkita.data.local.db.dao.ReaderDao
import net.dom53.inkita.data.local.db.dao.SeriesDetailV2Dao
import net.dom53.inkita.data.local.db.entity.CachedCollectionRefEntity
import net.dom53.inkita.data.local.db.entity.CachedCollectionV2Entity
import net.dom53.inkita.data.local.db.entity.CachedChapterV2Entity
import net.dom53.inkita.data.local.db.entity.CachedPageEntity
import net.dom53.inkita.data.local.db.entity.CachedPersonRefEntity
import net.dom53.inkita.data.local.db.entity.CachedPersonV2Entity
import net.dom53.inkita.data.local.db.entity.CachedReadingListRefEntity
import net.dom53.inkita.data.local.db.entity.CachedReadingListV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailRelatedRefEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesListRefEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesV2Entity
import net.dom53.inkita.data.local.db.entity.CachedSeriesVolumeRefEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeChapterRefEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadTaskEntity
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity

@Database(
    entities = [
        CachedSeriesV2Entity::class,
        CachedSeriesListRefEntity::class,
        CachedCollectionV2Entity::class,
        CachedCollectionRefEntity::class,
        CachedReadingListV2Entity::class,
        CachedReadingListRefEntity::class,
        CachedPersonV2Entity::class,
        CachedPersonRefEntity::class,
        CachedSeriesDetailV2Entity::class,
        CachedSeriesDetailRelatedRefEntity::class,
        CachedVolumeV2Entity::class,
        CachedSeriesVolumeRefEntity::class,
        CachedChapterV2Entity::class,
        CachedVolumeChapterRefEntity::class,
        CachedPageEntity::class,
        DownloadTaskEntity::class,
        DownloadedPageEntity::class,
        DownloadJobV2Entity::class,
        DownloadedItemV2Entity::class,
        net.dom53.inkita.data.local.db.entity.LocalReaderProgressEntity::class,
    ],
    version = 19,
    exportSchema = false,
)
abstract class InkitaDatabase : RoomDatabase() {
    abstract fun libraryV2Dao(): LibraryV2Dao

    abstract fun seriesDetailV2Dao(): SeriesDetailV2Dao

    abstract fun readerDao(): ReaderDao

    abstract fun downloadDao(): DownloadDao

    abstract fun downloadV2Dao(): DownloadV2Dao

    companion object {
        @Volatile
        @Suppress("ktlint:standard:property-naming")
        private var INSTANCE: InkitaDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_series_refs(
                            tabType TEXT NOT NULL,
                            collectionId INTEGER NOT NULL DEFAULT -1,
                            seriesId INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(tabType, collectionId, seriesId)
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_browse_refs(
                            queryKey TEXT NOT NULL,
                            page INTEGER NOT NULL,
                            seriesId INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(queryKey, page, seriesId)
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_series_detail(
                            seriesId INTEGER NOT NULL PRIMARY KEY,
                            unreadCount INTEGER,
                            totalCount INTEGER,
                            readState TEXT,
                            timeLeftMin REAL,
                            timeLeftMax REAL,
                            timeLeftAvg REAL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_volumes(
                            id INTEGER NOT NULL PRIMARY KEY,
                            seriesId INTEGER NOT NULL,
                            name TEXT,
                            minNumber REAL,
                            maxNumber REAL,
                            pages INTEGER,
                            pagesRead INTEGER,
                            readState TEXT,
                            minHoursToRead REAL,
                            maxHoursToRead REAL,
                            avgHoursToRead REAL,
                            bookId INTEGER,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_pages(
                            chapterId INTEGER NOT NULL,
                            page INTEGER NOT NULL,
                            html TEXT NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(chapterId, page)
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE cached_series_detail ADD COLUMN metadataSummary TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail ADD COLUMN metadataWriters TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail ADD COLUMN metadataTags TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail ADD COLUMN metadataPublicationStatus INTEGER")
                }
            }
        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_chapters(
                            volumeId INTEGER NOT NULL,
                            pageIndex INTEGER NOT NULL,
                            title TEXT NOT NULL,
                            status TEXT,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(volumeId, pageIndex)
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS download_tasks(
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            seriesId INTEGER NOT NULL,
                            volumeId INTEGER,
                            chapterId INTEGER,
                            pageStart INTEGER,
                            pageEnd INTEGER,
                            type TEXT NOT NULL,
                            priority INTEGER NOT NULL DEFAULT 0,
                            state TEXT NOT NULL DEFAULT 'pending',
                            createdAt INTEGER NOT NULL DEFAULT 0,
                            updatedAt INTEGER NOT NULL DEFAULT 0,
                            error TEXT
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS downloaded_pages(
                            seriesId INTEGER NOT NULL,
                            volumeId INTEGER,
                            chapterId INTEGER NOT NULL,
                            page INTEGER NOT NULL,
                            htmlPath TEXT NOT NULL,
                            assetsDir TEXT,
                            sizeBytes INTEGER NOT NULL DEFAULT 0,
                            status TEXT NOT NULL DEFAULT 'completed',
                            checksum TEXT,
                            updatedAt INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(chapterId, page)
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE download_tasks ADD COLUMN workId TEXT")
                }
            }
        private val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE download_tasks ADD COLUMN progress INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE download_tasks ADD COLUMN total INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE download_tasks ADD COLUMN bytes INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE download_tasks ADD COLUMN bytesTotal INTEGER NOT NULL DEFAULT 0")
                }
            }
        private val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS local_progress(
                            chapterId INTEGER NOT NULL PRIMARY KEY,
                            page INTEGER,
                            bookScrollId TEXT,
                            seriesId INTEGER,
                            volumeId INTEGER,
                            libraryId INTEGER,
                            lastModifiedUtc INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE cached_chapters ADD COLUMN isSpecial INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE cached_series_detail ADD COLUMN specialsVolumeIds TEXT")
                }
            }
        private val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_series_v2(
                            id INTEGER NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            summary TEXT,
                            libraryId INTEGER,
                            format TEXT,
                            pages INTEGER,
                            pagesRead INTEGER,
                            readState TEXT,
                            minHoursToRead REAL,
                            maxHoursToRead REAL,
                            avgHoursToRead REAL,
                            localThumbPath TEXT,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_series_list_refs_v2(
                            listType TEXT NOT NULL,
                            listKey TEXT NOT NULL,
                            seriesId INTEGER NOT NULL,
                            position INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(listType, listKey, seriesId)
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_collections_v2(
                            id INTEGER NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_collection_refs_v2(
                            listType TEXT NOT NULL,
                            collectionId INTEGER NOT NULL,
                            position INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(listType, collectionId)
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_reading_lists_v2(
                            id INTEGER NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            itemCount INTEGER,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_reading_list_refs_v2(
                            listType TEXT NOT NULL,
                            readingListId INTEGER NOT NULL,
                            position INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(listType, readingListId)
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_people_v2(
                            id INTEGER NOT NULL PRIMARY KEY,
                            name TEXT,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_person_refs_v2(
                            listType TEXT NOT NULL,
                            page INTEGER NOT NULL,
                            personId INTEGER NOT NULL,
                            position INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(listType, page, personId)
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("DROP TABLE IF EXISTS cached_series")
                    database.execSQL("DROP TABLE IF EXISTS cached_series_refs")
                    database.execSQL("DROP TABLE IF EXISTS cached_browse_refs")
                    database.execSQL("DROP TABLE IF EXISTS cached_series_detail")
                    database.execSQL("DROP TABLE IF EXISTS cached_volumes")
                    database.execSQL("DROP TABLE IF EXISTS cached_chapters")
                }
            }
        private val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_series_detail_v2(
                            seriesId INTEGER NOT NULL PRIMARY KEY,
                            summary TEXT,
                            publicationStatus INTEGER,
                            genres TEXT,
                            tags TEXT,
                            writers TEXT,
                            releaseYear INTEGER,
                            wordCount INTEGER,
                            timeLeftMin REAL,
                            timeLeftMax REAL,
                            timeLeftAvg REAL,
                            hasProgress INTEGER,
                            wantToRead INTEGER,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_series_detail_related_refs_v2(
                            seriesId INTEGER NOT NULL,
                            relationType TEXT NOT NULL,
                            targetType TEXT NOT NULL,
                            targetId INTEGER NOT NULL,
                            position INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(seriesId, relationType, targetType, targetId)
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_volumes_v2(
                            id INTEGER NOT NULL PRIMARY KEY,
                            seriesId INTEGER NOT NULL,
                            name TEXT,
                            number TEXT,
                            pages INTEGER,
                            pagesRead INTEGER,
                            wordCount INTEGER,
                            minHoursToRead REAL,
                            maxHoursToRead REAL,
                            avgHoursToRead REAL,
                            summary TEXT,
                            releaseYear INTEGER,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_series_volume_refs_v2(
                            seriesId INTEGER NOT NULL,
                            volumeId INTEGER NOT NULL,
                            position INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(seriesId, volumeId)
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_chapters_v2(
                            id INTEGER NOT NULL PRIMARY KEY,
                            volumeId INTEGER NOT NULL,
                            title TEXT,
                            pages INTEGER,
                            pagesRead INTEGER,
                            summary TEXT,
                            releaseDate TEXT,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS cached_volume_chapter_refs_v2(
                            volumeId INTEGER NOT NULL,
                            chapterId INTEGER NOT NULL,
                            position INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            PRIMARY KEY(volumeId, chapterId)
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN seriesJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN metadataJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN detailJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN relatedJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN ratingJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN continuePointJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN readerProgressJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN timeLeftJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN collectionsJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN readingListsJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN bookmarksJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN annotationsJson TEXT")
                    database.execSQL("ALTER TABLE cached_series_detail_v2 ADD COLUMN seriesDetailPlusJson TEXT")
                }
            }
        private val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS download_jobs_v2(
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            type TEXT NOT NULL,
                            format TEXT,
                            strategy TEXT,
                            seriesId INTEGER,
                            volumeId INTEGER,
                            chapterId INTEGER,
                            status TEXT NOT NULL,
                            totalItems INTEGER,
                            completedItems INTEGER,
                            retryCount INTEGER NOT NULL DEFAULT 0,
                            priority INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            error TEXT
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS download_items_v2(
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            jobId INTEGER NOT NULL,
                            type TEXT NOT NULL,
                            chapterId INTEGER,
                            page INTEGER,
                            url TEXT,
                            localPath TEXT,
                            bytes INTEGER,
                            checksum TEXT,
                            status TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            error TEXT
                        )
                        """.trimIndent(),
                    )
                }
            }
        private val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE download_items_v2 ADD COLUMN seriesId INTEGER")
                    database.execSQL("ALTER TABLE download_items_v2 ADD COLUMN volumeId INTEGER")
                }
            }
        private val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE download_jobs_v2 ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                }
            }

        fun getInstance(context: Context): InkitaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        InkitaDatabase::class.java,
                        "inkita.db",
                    ).addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                    ).build()
                    .also { INSTANCE = it }
            }
    }
}
