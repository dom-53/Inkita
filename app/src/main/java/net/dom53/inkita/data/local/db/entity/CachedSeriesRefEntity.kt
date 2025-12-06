package net.dom53.inkita.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "cached_series_refs",
    primaryKeys = ["tabType", "collectionId", "seriesId"],
)
data class CachedSeriesRefEntity(
    val tabType: String,
    /**
     * Use NO_COLLECTION for non-collection tabs (in-progress, want-to-read).
     */
    val collectionId: Int = NO_COLLECTION,
    val seriesId: Int,
    val updatedAt: Long,
) {
    companion object {
        const val NO_COLLECTION = -1
    }
}
