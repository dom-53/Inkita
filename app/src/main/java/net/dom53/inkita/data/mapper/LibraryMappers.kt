package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.api.dto.LibraryDto
import net.dom53.inkita.domain.model.Library

fun LibraryDto.toDomain(): Library =
    Library(
        id = id,
        name = name.orEmpty(),
    )
