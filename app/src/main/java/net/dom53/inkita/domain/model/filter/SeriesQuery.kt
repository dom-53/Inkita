package net.dom53.inkita.domain.model.filter

data class SeriesQuery(
    val libraryId: Int? = null,
    val collectionId: Int? = null,
    val clauses: List<FilterClause> = emptyList(),
    val combination: KavitaCombination = KavitaCombination.MatchAll,
    val sortField: KavitaSortField = KavitaSortField.SortName,
    val sortDescending: Boolean = false,
    val page: Int = 1,
    val pageSize: Int = 50,
)
