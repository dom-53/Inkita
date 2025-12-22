package net.dom53.inkita.data.api.dto

data class BrowsePersonDto(
    val id: Int,
    val name: String? = null,
    val coverImageLocked: Boolean = false,
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
    val seriesCount: Int? = null,
    val chapterCount: Int? = null,
)

data class PersonFilterStatementDto(
    val comparison: Int,
    val field: Int,
    val value: String?,
)

data class PersonSortOptionsDto(
    val sortField: Int,
    val isAscending: Boolean,
)

data class BrowsePersonFilterDto(
    val id: Int? = null,
    val name: String? = null,
    val statements: List<PersonFilterStatementDto>? = null,
    val combination: Int,
    val sortOptions: PersonSortOptionsDto,
    val limitTo: Int = 0,
)
