package net.dom53.inkita.data.api.dto

/**
 * Zjednodušené DTO pro sérii.
 * Pokud openapi obsahuje víc polí, můžeš je doplnit.
 */
data class SeriesDto(
    val id: Int,
    val name: String,
    val summary: String?,
    val libraryId: Int?,
    val format: Int?,
    val pages: Int? = null,
    val pagesRead: Int? = null,
    val minHoursToRead: Double? = null,
    val maxHoursToRead: Double? = null,
    val avgHoursToRead: Double? = null,
    val localThumbPath: String? = null,
)
