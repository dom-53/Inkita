package net.dom53.inkita.domain.model

data class Chapter(
    val id: Int,
    val minNumber: Float?,
    val maxNumber: Float?,
    val title: String?,
    val status: ReadState = ReadState.Unread,
    val isSpecial: Boolean = false,
    val chapters: List<Chapter>? = null,
)
