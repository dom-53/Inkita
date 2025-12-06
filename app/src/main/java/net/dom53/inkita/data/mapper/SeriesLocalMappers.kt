package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.local.db.entity.CachedSeriesEntity
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.ReadState
import net.dom53.inkita.domain.model.Series

fun Series.toCachedEntity(
    coverUrl: String? = null,
    localThumbPath: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
): CachedSeriesEntity =
    CachedSeriesEntity(
        id = id,
        name = name,
        summary = summary,
        libraryId = libraryId,
        formatId = format?.id,
        pages = pages,
        pagesRead = pagesRead,
        readState = readState?.name,
        minHoursToRead = minHoursToRead,
        maxHoursToRead = maxHoursToRead,
        avgHoursToRead = avgHoursToRead,
        coverUrl = coverUrl,
        localThumbPath = localThumbPath ?: this.localThumbPath,
        updatedAt = updatedAt,
    )

fun CachedSeriesEntity.toDomain(): Series =
    Series(
        id = id,
        name = name,
        summary = summary,
        libraryId = libraryId,
        format = Format.fromId(formatId),
        pages = pages,
        pagesRead = pagesRead,
        readState = readState?.let { runCatching { ReadState.valueOf(it) }.getOrNull() },
        minHoursToRead = minHoursToRead,
        maxHoursToRead = maxHoursToRead,
        avgHoursToRead = avgHoursToRead,
        localThumbPath = localThumbPath,
    )
