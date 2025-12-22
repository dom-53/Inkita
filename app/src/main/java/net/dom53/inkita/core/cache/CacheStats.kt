package net.dom53.inkita.core.cache

/**
 * Aggregated cache metrics for the stats/debug screen.
 *
 * Counts are stored per table or reference list, sizes are in bytes.
 */
data class CacheStats(
    val seriesCount: Int = 0,
    val seriesListRefsCount: Int = 0,
    val collectionsCount: Int = 0,
    val collectionRefsCount: Int = 0,
    val readingListsCount: Int = 0,
    val readingListRefsCount: Int = 0,
    val peopleCount: Int = 0,
    val personRefsCount: Int = 0,
    val detailsCount: Int = 0,
    val relatedRefsCount: Int = 0,
    val volumesCount: Int = 0,
    val seriesVolumeRefsCount: Int = 0,
    val chaptersCount: Int = 0,
    val volumeChapterRefsCount: Int = 0,
    val dbBytes: Long = 0L,
    val thumbnailsCount: Int = 0,
    val thumbnailsBytes: Long = 0L,
    val lastLibraryRefresh: Long = 0L,
    val lastBrowseRefresh: Long = 0L,
)
