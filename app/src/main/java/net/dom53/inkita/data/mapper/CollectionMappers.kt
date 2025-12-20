package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.api.dto.CollectionDto
import net.dom53.inkita.data.api.dto.AppUserCollectionDto
import net.dom53.inkita.data.api.dto.ReadingListDto
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.ReadingList

fun CollectionDto.toDomain(): Collection =
    Collection(
        id = id,
        name = title,
    )

fun AppUserCollectionDto.toDomain(): Collection =
    Collection(
        id = id,
        name = title.orEmpty(),
    )

fun ReadingListDto.toDomain(): ReadingList =
    ReadingList(
        id = id,
        title = title.orEmpty(),
        itemCount = itemCount,
    )
