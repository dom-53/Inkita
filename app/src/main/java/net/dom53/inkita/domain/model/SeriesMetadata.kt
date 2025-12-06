package net.dom53.inkita.domain.model

data class SeriesMetadata(
    val summary: String?,
    val tags: List<Tag>,
    val writers: List<Person>,
    val publicationStatus: Int?,
)
