package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.api.dto.CollectionDto
import net.dom53.inkita.domain.model.Collection

fun CollectionDto.toDomain(): Collection =
    Collection(
        id = id,
        name = title,
    )
