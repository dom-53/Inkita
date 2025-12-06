package net.dom53.inkita.ui.browse.utils

import net.dom53.inkita.R

enum class PublicationState(
    val code: Int,
    val titleRes: Int,
) {
    Ongoing(0, R.string.general_pub_status_ongoing),
    Hiatus(1, R.string.general_pub_status_hiatus),
    Completed(2, R.string.general_pub_status_completed),
    Cancelled(3, R.string.general_pub_status_cancelled),
    Ended(4, R.string.general_pub_status_ended),
}
