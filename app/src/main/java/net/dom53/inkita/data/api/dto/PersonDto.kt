package net.dom53.inkita.data.api.dto

data class PersonDto(
    val id: Int? = null,
    val name: String? = null,
    val coverImageLocked: Boolean? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val coverImage: String? = null,
    val aliases: List<String>? = null,
    val description: String? = null,
    val asin: String? = null,
    val aniListId: Int? = null,
    val malId: Long? = null,
    val hardcoverId: String? = null,
    val webLinks: List<String>? = null,
    val roles: List<Int>? = null,
)
