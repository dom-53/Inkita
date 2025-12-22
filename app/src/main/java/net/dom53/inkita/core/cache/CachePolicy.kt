package net.dom53.inkita.core.cache

/**
 * Snapshot of cache feature toggles that drive read/write behavior.
 *
 * Keeps UI switches and derived access flags in one place.
 */
data class CachePolicy(
    val globalEnabled: Boolean,
    val libraryEnabled: Boolean,
    val browseEnabled: Boolean,
    val libraryHomeEnabled: Boolean,
    val libraryWantEnabled: Boolean,
    val libraryCollectionsEnabled: Boolean,
    val libraryReadingListsEnabled: Boolean,
    val libraryBrowsePeopleEnabled: Boolean,
    val libraryDetailsEnabled: Boolean,
) {
    /** True when library cache writes are allowed by global + library toggles. */
    val libraryWriteAllowed: Boolean get() = globalEnabled && libraryEnabled

    /** True when browse cache writes are allowed by global + browse toggles. */
    val browseWriteAllowed: Boolean get() = globalEnabled && browseEnabled
}
