package net.dom53.inkita.core.cache

data class CachePolicy(
    val globalEnabled: Boolean,
    val libraryEnabled: Boolean,
    val browseEnabled: Boolean,
) {
    val libraryWriteAllowed: Boolean get() = globalEnabled && libraryEnabled
    val browseWriteAllowed: Boolean get() = globalEnabled && browseEnabled
}
