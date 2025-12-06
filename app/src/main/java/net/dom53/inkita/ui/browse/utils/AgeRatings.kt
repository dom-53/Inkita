package net.dom53.inkita.ui.browse.utils

import net.dom53.inkita.R

enum class AgeRatings(
    val code: Int,
    val titleRes: Int,
) {
    Unknown(0, R.string.general_age_rating_unknown),
    RatingPending(1, R.string.general_age_rating_rating_pending),
    EarlyChildhood(2, R.string.general_age_rating_early_childhood),
    Everyone(3, R.string.general_age_rating_everyone),
    G(4, R.string.general_age_rating_g),
    Everyone10plus(5, R.string.general_age_rating_everyone_10p),
    PG(6, R.string.general_age_rating_pg),
    KidsToAdults(7, R.string.general_age_rating_kids_to_adults),
    Teen(8, R.string.general_age_rating_teen),
    MA15plus(9, R.string.general_age_rating_ma15p),
    Mature17plus(10, R.string.general_age_rating_m17p),
    M(11, R.string.general_age_rating_m),
    R18plus(12, R.string.general_age_rating_r18p),
    AdultsOnly18plus(13, R.string.general_age_rating_adults_only_18p),
    X18plus(14, R.string.general_age_rating_x18p),
}
