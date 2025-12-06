package net.dom53.inkita.ui.seriesdetail.model

import net.dom53.inkita.data.api.dto.FilterV2Dto
import net.dom53.inkita.data.api.dto.SortOptionDto
import net.dom53.inkita.domain.model.ReaderTimeLeft
import net.dom53.inkita.domain.model.Series

enum class RelatedFilter(
    val label: String,
) {
    All("All"),
    Sequels("Sequel"),
    Prequels("Prequel"),
    SpinOffs("Spin-off"),
    Adaptations("Adaptation"),
    SideStories("Side story"),
    AlternativeSettings("Alternative setting"),
    AlternativeVersions("Alternative version"),
    Parent("Parent"),
    Contains("Contains"),
    Others("Other"),
    Doujinshis("Doujinshi"),
    Editions("Edition"),
    Annuals("Annuals"),
}

data class RelatedGroup(
    val type: RelatedFilter,
    val title: String,
    val series: List<Series>,
)

data class VolumeProgressUi(
    val page: Int? = null,
    val totalPages: Int? = null,
    val timeLeft: ReaderTimeLeft? = null,
)

enum class SwipeDirection { Left, Right }

fun emptyFilter(): FilterV2Dto =
    FilterV2Dto(
        id = null,
        name = null,
        statements = null,
        combination = 1,
        sortOptions = SortOptionDto(sortField = 1, isAscending = true),
        limitTo = 0,
    )
