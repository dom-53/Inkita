package net.dom53.inkita.data.api.dto

data class ReadingListDto(
    val id: Int,
    val title: String? = null,
    val summary: String? = null,
    val promoted: Boolean = false,
    val coverImageLocked: Boolean = false,
    val coverImage: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val itemCount: Int? = null,
    val startingYear: Int? = null,
    val startingMonth: Int? = null,
    val endingYear: Int? = null,
    val endingMonth: Int? = null,
    val ageRating: Int,
    val ownerUserName: String? = null,
)
