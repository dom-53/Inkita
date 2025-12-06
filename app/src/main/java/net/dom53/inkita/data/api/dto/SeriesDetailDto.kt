package net.dom53.inkita.data.api.dto

data class SeriesDetailDto(
    val volumes: List<VolumeDto>?,
    val unreadCount: Int?,
    val totalCount: Int?,
    val chapters: List<ChapterDto>?,
    val specials: List<ChapterDto>?,
    val storylineChapters: List<ChapterDto>?,
)
