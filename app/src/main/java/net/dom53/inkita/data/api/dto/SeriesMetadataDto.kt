package net.dom53.inkita.data.api.dto

data class SeriesMetadataDto(
    val id: Int?,
    val summary: String?,
    val tags: List<TagDto>?,
    val writers: List<PersonDto>?,
    val publicationStatus: Int?,
)
