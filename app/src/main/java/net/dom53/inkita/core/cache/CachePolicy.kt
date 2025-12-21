package net.dom53.inkita.core.cache

data class CachePolicy(
    val globalEnabled: Boolean,
    val libraryEnabled: Boolean,
    val browseEnabled: Boolean,
    val libraryHomeEnabled: Boolean,
    val libraryWantEnabled: Boolean,
    val libraryCollectionsEnabled: Boolean,
    val libraryReadingListsEnabled: Boolean,
    val libraryBrowsePeopleEnabled: Boolean,
) {
    val libraryWriteAllowed: Boolean get() = globalEnabled && libraryEnabled
    val browseWriteAllowed: Boolean get() = globalEnabled && browseEnabled
}
