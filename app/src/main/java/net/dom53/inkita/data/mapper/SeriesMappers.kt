package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.api.dto.BookChapterItemDto
import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.FilterStatementDto
import net.dom53.inkita.data.api.dto.FilterV2Dto
import net.dom53.inkita.data.api.dto.PersonDto
import net.dom53.inkita.data.api.dto.SeriesDto
import net.dom53.inkita.data.api.dto.SeriesMetadataDto
import net.dom53.inkita.data.api.dto.SortOptionDto
import net.dom53.inkita.data.api.dto.TagDto
import net.dom53.inkita.data.api.dto.VolumeDto
import net.dom53.inkita.domain.model.Chapter
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.model.SeriesMetadata
import net.dom53.inkita.domain.model.Tag
import net.dom53.inkita.domain.model.Volume
import net.dom53.inkita.domain.model.filter.SeriesQuery
import kotlin.math.max

fun SeriesQuery.toFilterV2Dto(): FilterV2Dto {
    val statements =
        if (clauses.isEmpty()) {
            null
        } else {
            clauses.map { clause ->
                FilterStatementDto(
                    field = clause.field.id,
                    comparison = clause.comparison.id,
                    value = clause.value,
                )
            }
        }

    return FilterV2Dto(
        id = null,
        name = null,
        statements = statements,
        combination = combination.id,
        sortOptions =
            SortOptionDto(
                sortField = sortField.id,
                isAscending = !sortDescending,
            ),
        // 0 = Without a limit, pagination is handled via PageNumber/PageSize in the query parameters.
        limitTo = 0,
    )
}

fun SeriesDto.toDomain(): Series {
    val readState = computeReadState(pagesRead, pages)
    return Series(
        id = id,
        name = name,
        summary = summary,
        libraryId = libraryId,
        format = Format.fromId(format),
        pages = pages,
        pagesRead = pagesRead,
        readState = readState,
        minHoursToRead = minHoursToRead,
        maxHoursToRead = maxHoursToRead,
        avgHoursToRead = avgHoursToRead,
        localThumbPath = localThumbPath,
    )
}

private fun computeReadState(
    pagesRead: Int?,
    pages: Int?,
): ReadState? {
    val total = pages ?: return null
    if (total <= 0) return null
    val read = pagesRead ?: 0
    return when {
        read >= total -> ReadState.Completed
        read > 0 -> ReadState.InProgress
        else -> ReadState.Unread
    }
}

fun SeriesMetadataDto.toDomain(): SeriesMetadata =
    SeriesMetadata(
        summary = summary,
        tags = tags?.map { it.toDomain() } ?: emptyList(),
        writers = writers?.map { it.toDomain() } ?: emptyList(),
        publicationStatus = publicationStatus,
    )

fun TagDto.toDomain(): Tag = Tag(id = id, title = title)

fun PersonDto.toDomain(): Person = Person(id = id, name = name)

fun VolumeDto.toDomain(): Volume =
    Volume(
        id = id,
        name = name ?: title,
        minNumber = minNumber,
        maxNumber = maxNumber,
        chapters = chapters?.map { it.toDomain() } ?: emptyList(),
        minHoursToRead = minHoursToRead,
        maxHoursToRead = maxHoursToRead,
        avgHoursToRead = avgHoursToRead,
        bookId = chapters?.firstOrNull()?.id,
    )

fun ChapterDto.toDomain(): Chapter =
    Chapter(
        id = id,
        minNumber = minNumber,
        maxNumber = maxNumber,
        title = title ?: range,
        isSpecial = isSpecial ?: false,
    )

fun sanitizeVolumeName(name: String): String {
    val patterns =
        listOf(
            "(?i)\\bvolume\\s*\\d+",
            "(?i)\\bvol\\.?\\s*\\d+",
            "(?i)\\bln\\b",
            "(?i)\\(ln\\)",
            "(?i)\\(light\\s*novel\\)",
            "(?i)light\\s*novel",
        )
    var result = name
    patterns.forEach { regex ->
        result = result.replace(regex.toRegex(), "")
    }
    return result
        .replace("\\s{2,}".toRegex(), " ")
        .trim()
        .trimEnd('-', ',')
        .trim()
}

fun VolumeDto.mergeWith(enriched: VolumeDto?): VolumeDto {
    val mergedName =
        when {
            !this.name.isNullOrBlank() -> this.name
            !this.title.isNullOrBlank() -> this.title
            !enriched?.name.isNullOrBlank() -> enriched?.name
            !enriched?.title.isNullOrBlank() -> enriched?.title
            else -> null
        }?.let { sanitizeVolumeName(it) }

    return (enriched ?: this).copy(
        name = mergedName,
        minNumber = enriched?.minNumber ?: this.minNumber,
        maxNumber = enriched?.maxNumber ?: this.maxNumber,
    )
}

fun flattenToc(item: BookChapterItemDto): List<TocItem> {
    val current =
        listOf(
            TocItem(
                title = item.title.orEmpty(),
                page = item.page ?: 0,
            ),
        )
    val children = item.children.orEmpty().flatMap { flattenToc(it) }
    return (current + children).sortedBy { it.page }
}

fun buildChaptersFromPages(
    volumeId: Int,
    topChapterId: Int?,
    pagesCount: Int,
    pagesRead: Int,
    tocItems: List<TocItem>,
): List<Chapter> {
    val clampedRead = pagesRead.coerceIn(0, max(pagesCount, 0))
    return if (pagesCount > 0 && topChapterId != null) {
        (0 until pagesCount).map { pageIndex ->
            val title =
                tocItems.lastOrNull { it.page <= pageIndex }?.title
                    ?: "Page ${pageIndex + 1}"
            val status =
                when {
                    clampedRead >= pagesCount -> ReadState.Completed
                    pageIndex < clampedRead -> ReadState.Completed
                    clampedRead in 1 until pagesCount && pageIndex == clampedRead -> ReadState.InProgress
                    else -> ReadState.Unread
                }
            Chapter(
                id = volumeId * 1_000_000 + pageIndex,
                minNumber = null,
                maxNumber = null,
                title = title,
                status = status,
            )
        }
    } else {
        emptyList()
    }
}

fun VolumeDto.toDomain(
    chapters: List<Chapter>,
    bookId: Int?,
): Volume =
    Volume(
        id = id,
        name = name ?: title,
        minNumber = minNumber,
        maxNumber = maxNumber,
        chapters = chapters,
        minHoursToRead = minHoursToRead,
        maxHoursToRead = maxHoursToRead,
        avgHoursToRead = avgHoursToRead,
        bookId = bookId,
    )

fun computeSeriesReadState(
    unreadCount: Int?,
    totalCount: Int?,
): ReadState? {
    if (totalCount == null || totalCount <= 0 || unreadCount == null) return null
    return when {
        unreadCount == 0 -> ReadState.Completed
        unreadCount == totalCount -> ReadState.Unread
        else -> ReadState.InProgress
    }
}

data class TocItem(
    val title: String,
    val page: Int,
)
