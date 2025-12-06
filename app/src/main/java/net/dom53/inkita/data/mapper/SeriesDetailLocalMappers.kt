package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.local.db.entity.CachedChapterEntity
import net.dom53.inkita.data.local.db.entity.CachedSeriesDetailEntity
import net.dom53.inkita.data.local.db.entity.CachedVolumeEntity
import net.dom53.inkita.domain.model.Chapter
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.SeriesDetail
import net.dom53.inkita.domain.model.SeriesMetadata
import net.dom53.inkita.domain.model.Tag
import net.dom53.inkita.domain.model.Volume

fun SeriesDetail.toCachedDetailEntity(updatedAt: Long = System.currentTimeMillis()): CachedSeriesDetailEntity =
    CachedSeriesDetailEntity(
        seriesId = series.id,
        unreadCount = unreadCount,
        totalCount = totalCount,
        readState = readState?.name ?: series.readState?.name,
        timeLeftMin = timeLeftMin,
        timeLeftMax = timeLeftMax,
        timeLeftAvg = timeLeftAvg,
        metadataSummary = metadata?.summary,
        metadataWriters = metadata?.writers?.joinToString(",") { person -> person.name.orEmpty() } ?: "",
        metadataTags = metadata?.tags?.joinToString(",") { tag -> tag.title.orEmpty() } ?: "",
        metadataPublicationStatus = metadata?.publicationStatus,
        specialsVolumeIds = this.specials.joinToString(",") { vol -> vol.id.toString() }.takeIf { it.isNotBlank() },
        updatedAt = updatedAt,
    )

fun Volume.toCachedVolumeEntity(
    seriesId: Int,
    updatedAt: Long = System.currentTimeMillis(),
): CachedVolumeEntity =
    CachedVolumeEntity(
        id = id,
        seriesId = seriesId,
        name = name,
        minNumber = minNumber,
        maxNumber = maxNumber,
        pages = null,
        pagesRead = null,
        readState = chapters.mapNotNull { it.status }.maxByOrNull { it.ordinal }?.name,
        minHoursToRead = minHoursToRead,
        maxHoursToRead = maxHoursToRead,
        avgHoursToRead = avgHoursToRead,
        bookId = bookId,
        updatedAt = updatedAt,
    )

fun Chapter.toCachedChapterEntity(
    volumeId: Int,
    updatedAt: Long = System.currentTimeMillis(),
): CachedChapterEntity =
    CachedChapterEntity(
        volumeId = volumeId,
        pageIndex = id % 1_000_000,
        title = title.orEmpty(),
        status = status?.name,
        isSpecial = isSpecial,
        updatedAt = updatedAt,
    )

fun CachedVolumeEntity.toDomain(chapters: List<Chapter>): Volume =
    Volume(
        id = id,
        name = name,
        minNumber = minNumber,
        maxNumber = maxNumber,
        chapters = chapters,
        minHoursToRead = minHoursToRead,
        maxHoursToRead = maxHoursToRead,
        avgHoursToRead = avgHoursToRead,
        bookId = bookId,
    )

fun buildCachedSeriesDetail(
    series: net.dom53.inkita.domain.model.Series,
    detail: CachedSeriesDetailEntity?,
    volumes: List<CachedVolumeEntity>,
    chapters: List<CachedChapterEntity>,
): SeriesDetail? {
    if (detail == null) return null
    val chaptersByVolume = chapters.groupBy { it.volumeId }
    val volDomains =
        volumes.map { vol ->
            val domainChapters =
                chaptersByVolume[vol.id].orEmpty().map { chapter ->
                    Chapter(
                        id = vol.id * 1_000_000 + chapter.pageIndex,
                        minNumber = null,
                        maxNumber = null,
                        title = chapter.title,
                        status = chapter.status?.let { runCatching { ReadState.valueOf(it) }.getOrNull() } ?: ReadState.Unread,
                        isSpecial = chapter.isSpecial,
                    )
                }
            vol.toDomain(domainChapters)
        }
    val specialsIds = detail.specialsVolumeIds?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    val specialsVolumes = volDomains.filter { specialsIds.contains(it.id) }
    val mainVolumes = volDomains.filterNot { specialsIds.contains(it.id) }
    val readState = detail.readState?.let { runCatching { ReadState.valueOf(it) }.getOrNull() } ?: series.readState
    val metadata =
        SeriesMetadata(
            summary = detail.metadataSummary ?: series.summary,
            tags =
                detail.metadataTags
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.map { Tag(id = 0, title = it.trim()) } ?: emptyList(),
            writers =
                detail.metadataWriters
                    ?.split(
                        ",",
                    )?.filter { it.isNotBlank() }
                    ?.map { Person(id = 0, name = it.trim()) } ?: emptyList(),
            publicationStatus = detail.metadataPublicationStatus,
        )
    return SeriesDetail(
        series = series.copy(readState = readState),
        metadata = metadata,
        volumes = mainVolumes,
        specials = specialsVolumes,
        unreadCount = detail.unreadCount,
        totalCount = detail.totalCount,
        readState = readState,
        minHoursToRead = series.minHoursToRead,
        maxHoursToRead = series.maxHoursToRead,
        avgHoursToRead = series.avgHoursToRead,
        timeLeftMin = detail.timeLeftMin,
        timeLeftMax = detail.timeLeftMax,
        timeLeftAvg = detail.timeLeftAvg,
    )
}
