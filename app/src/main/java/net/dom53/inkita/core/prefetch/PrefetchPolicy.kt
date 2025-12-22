package net.dom53.inkita.core.prefetch

data class PrefetchPolicy(
    val inProgressEnabled: Boolean,
    val wantEnabled: Boolean,
    val collectionsEnabled: Boolean,
    val detailsEnabled: Boolean,
    val allowMetered: Boolean,
    val allowLowBattery: Boolean,
    val collectionsAll: Boolean,
    val collectionIds: List<Int>,
) {
    val hasTargetsEnabled: Boolean
        get() = inProgressEnabled || wantEnabled || collectionsEnabled || detailsEnabled

    val selectedCollectionIds: List<Int>
        get() = if (collectionsEnabled && !collectionsAll) collectionIds else emptyList()
}
