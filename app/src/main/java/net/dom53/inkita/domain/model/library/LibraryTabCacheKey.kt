package net.dom53.inkita.domain.model.library

enum class LibraryTabType {
    InProgress,
    WantToRead,
    Collection,
}

data class LibraryTabCacheKey(
    val type: LibraryTabType,
    val collectionId: Int? = null,
)
