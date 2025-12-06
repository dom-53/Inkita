package net.dom53.inkita.data.api.dto

data class AppUserCollectionDto(
    val id: Int,
    val title: String? = null,
    val summary: String? = null,
    val promoted: Boolean = false,
    val ageRating: Int? = null,
    val coverImage: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val coverImageLocked: Boolean = false,
    val itemCount: Int? = null,
    val owner: String? = null,
    val lastSyncUtc: String? = null,
    val source: Int? = null,
    val sourceUrl: String? = null,
    val totalSourceCount: Int? = null,
    val missingSeriesFromSource: String? = null,
)
