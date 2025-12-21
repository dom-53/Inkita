package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_series_detail_v2")
data class CachedSeriesDetailV2Entity(
    @PrimaryKey val seriesId: Int,
    val summary: String?,
    val publicationStatus: Int?,
    val genres: String?,
    val tags: String?,
    val writers: String?,
    val releaseYear: Int?,
    val wordCount: Long?,
    val timeLeftMin: Double?,
    val timeLeftMax: Double?,
    val timeLeftAvg: Double?,
    val hasProgress: Boolean?,
    val wantToRead: Boolean?,
    val seriesJson: String?,
    val metadataJson: String?,
    val detailJson: String?,
    val relatedJson: String?,
    val ratingJson: String?,
    val continuePointJson: String?,
    val readerProgressJson: String?,
    val timeLeftJson: String?,
    val collectionsJson: String?,
    val readingListsJson: String?,
    val bookmarksJson: String?,
    val annotationsJson: String?,
    val seriesDetailPlusJson: String?,
    val updatedAt: Long,
)
