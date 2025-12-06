package net.dom53.inkita.data.api.dto

data class UpdateSeriesForTagDto(
    val tag: AppUserCollectionDto,
    val seriesIdsToRemove: List<Int> = emptyList(),
)
