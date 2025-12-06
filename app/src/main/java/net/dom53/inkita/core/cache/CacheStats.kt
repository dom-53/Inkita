package net.dom53.inkita.core.cache

data class CacheStats(
    val seriesCount: Int = 0,
    val tabRefs: Int = 0,
    val browseRefs: Int = 0,
    val detailsCount: Int = 0,
    val volumesCount: Int = 0,
    val chaptersCount: Int = 0,
    val dbBytes: Long = 0L,
    val thumbnailsBytes: Long = 0L,
    val lastLibraryRefresh: Long = 0L,
    val lastBrowseRefresh: Long = 0L,
)
