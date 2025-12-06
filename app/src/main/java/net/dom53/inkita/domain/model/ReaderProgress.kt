package net.dom53.inkita.domain.model

data class ReaderProgress(
    val chapterId: Int,
    val page: Int? = null,
    val bookScrollId: String? = null,
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val libraryId: Int? = null,
    val lastModifiedUtcMillis: Long = 0L,
)

data class ReaderBookInfo(
    val pages: Int? = null,
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val libraryId: Int? = null,
    val title: String? = null,
    val pageTitle: String? = null,
)

data class ReaderTimeLeft(
    val minHours: Double? = null,
    val maxHours: Double? = null,
    val avgHours: Double? = null,
)

data class ReaderChapterNav(
    val seriesId: Int? = null,
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val pagesRead: Int? = null,
)
