package net.dom53.inkita.domain.model

data class ReadingList(
    val id: Int,
    val title: String,
    val itemCount: Int? = null,
)
