package net.dom53.inkita.data.api.dto

data class RatingDto(
    val averageScore: Int? = null,
    val favoriteCount: Int? = null,
    val provider: Int? = null,
    val authority: Int? = null,
    val providerUrl: String? = null,
)
