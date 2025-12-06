package net.dom53.inkita.data.api.dto

data class CollectionTagBulkAddDto(
    val collectionTagId: Int,
    val collectionTagTitle: String? = null,
    val seriesIds: List<Int> = emptyList(),
)
