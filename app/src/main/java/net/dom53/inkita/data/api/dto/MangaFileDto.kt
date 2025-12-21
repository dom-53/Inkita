package net.dom53.inkita.data.api.dto

data class MangaFileDto(
    val id: Int,
    val filePath: String? = null,
    val pages: Int? = null,
    val bytes: Long? = null,
    val format: Int? = null,
    val created: String? = null,
    val extension: String? = null,
)
