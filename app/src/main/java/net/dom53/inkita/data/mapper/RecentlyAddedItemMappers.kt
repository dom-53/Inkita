package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.api.dto.RecentlyAddedItemDto
import net.dom53.inkita.domain.model.RecentlyUpdatedSeriesItem

fun RecentlyAddedItemDto.toDomain(): RecentlyUpdatedSeriesItem =
    RecentlyUpdatedSeriesItem(
        seriesName = seriesName,
        seriesId = seriesId,
        libraryId = libraryId,
        libraryType = libraryType,
        title = title,
        created = created,
        chapterId = chapterId,
        volumeId = volumeId,
        id = id,
        format = format,
    )
