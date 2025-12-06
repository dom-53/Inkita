package net.dom53.inkita.domain.usecase

import net.dom53.inkita.data.api.dto.FilterStatementDto
import net.dom53.inkita.data.api.dto.FilterV2Dto
import net.dom53.inkita.data.api.dto.SortOptionDto
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.filter.FilterClause
import net.dom53.inkita.domain.model.filter.KavitaCombination
import net.dom53.inkita.domain.model.filter.KavitaComparison
import net.dom53.inkita.domain.model.filter.KavitaField
import net.dom53.inkita.domain.model.filter.KavitaSortField
import net.dom53.inkita.domain.model.filter.SeriesQuery
import net.dom53.inkita.domain.model.filter.TriState

data class AppliedFilter(
    val combination: KavitaCombination = KavitaCombination.MatchAll,
    val sortField: KavitaSortField = KavitaSortField.SortName,
    val sortDesc: Boolean = false,
    val statusFilter: ReadStatusFilter = ReadStatusFilter.Any,
    val minYear: String = "",
    val maxYear: String = "",
    val genres: Map<Int, TriState> = emptyMap(),
    val tags: Map<Int, TriState> = emptyMap(),
    val languages: Map<String, TriState> = emptyMap(),
    val ageRatings: Map<Int, TriState> = emptyMap(),
    val publication: Map<Int, Boolean> = emptyMap(),
    val collections: Map<Int, TriState> = emptyMap(),
    val libraries: Map<Int, TriState> = emptyMap(),
    val special: SpecialFilter? = null,
    val smartFilterId: Int? = null,
    val decodedSmartFilter: FilterV2Dto? = null,
)

enum class SpecialFilter { WantToRead }

enum class ReadStatusFilter { Any, Unread, InProgress, Completed }

fun buildQueries(
    appliedFilter: AppliedFilter,
    search: String,
    page: Int,
    pageSize: Int,
): List<SeriesQuery> {
    val formats = listOf(Format.Epub, Format.Pdf)
    val publicationTargets =
        appliedFilter.publication
            .filterValues { it }
            .keys
            .toList()
            .ifEmpty { listOf<Int?>(null) }
    val baseClauses = buildBaseClauses(appliedFilter, search)
    val effectiveCombination =
        if (appliedFilter.statusFilter == ReadStatusFilter.InProgress) {
            KavitaCombination.MatchAll
        } else {
            appliedFilter.combination
        }

    val results = mutableListOf<SeriesQuery>()
    val smart = appliedFilter.decodedSmartFilter
    if (appliedFilter.smartFilterId != null && smart != null) {
        val smartClauses = smart.statements.orEmpty().mapNotNull { it.toFilterClause() }
        val smartCombination =
            KavitaCombination.values().firstOrNull { it.id == smart.combination }
                ?: KavitaCombination.MatchAll
        val smartSortField =
            KavitaSortField.values().firstOrNull { it.id == smart.sortOptions.sortField }
                ?: KavitaSortField.SortName
        val smartSortDesc = !smart.sortOptions.isAscending
        val searchClause =
            if (search.isNotBlank()) {
                listOf(FilterClause(KavitaField.SeriesName, KavitaComparison.BeginsWith, search))
            } else {
                emptyList()
            }
        formats.forEach { format ->
            val clauses = mutableListOf<FilterClause>()
            clauses += smartClauses
            clauses += searchClause
            clauses += FilterClause(KavitaField.Formats, KavitaComparison.Equal, format.id.toString())
            results +=
                SeriesQuery(
                    clauses = clauses,
                    combination = smartCombination,
                    sortField = smartSortField,
                    sortDescending = smartSortDesc,
                    page = page,
                    pageSize = pageSize,
                )
        }
        return results.distinctBy { it.hashCode() }
    }

    if (appliedFilter.special != null) {
        formats.forEach { format ->
            val clauses = mutableListOf<FilterClause>()
            if (search.isNotBlank()) {
                clauses += FilterClause(KavitaField.SeriesName, KavitaComparison.BeginsWith, search)
            }
            clauses += FilterClause(KavitaField.Formats, KavitaComparison.Equal, format.id.toString())
            when (appliedFilter.special) {
                SpecialFilter.WantToRead ->
                    clauses +=
                        FilterClause(
                            KavitaField.WantToRead,
                            KavitaComparison.Equal,
                            "true",
                        )
            }
            results +=
                SeriesQuery(
                    clauses = clauses,
                    combination = KavitaCombination.MatchAll,
                    sortField = appliedFilter.sortField,
                    sortDescending = appliedFilter.sortDesc,
                    page = page,
                    pageSize = pageSize,
                )
        }
        return results.distinctBy { it.hashCode() }
    }

    formats.forEach { format ->
        publicationTargets.forEach { pub ->
            val clauses = mutableListOf<FilterClause>()
            clauses += baseClauses
            clauses += FilterClause(KavitaField.Formats, KavitaComparison.Equal, format.id.toString())
            if (pub != null) {
                clauses += FilterClause(KavitaField.PublicationStatus, KavitaComparison.Equal, pub.toString())
            }
            results +=
                SeriesQuery(
                    clauses = clauses,
                    combination = effectiveCombination,
                    sortField = appliedFilter.sortField,
                    sortDescending = appliedFilter.sortDesc,
                    page = page,
                    pageSize = pageSize,
                )
        }
    }

    return results.distinctBy { it.hashCode() }
}

private fun buildBaseClauses(
    appliedFilter: AppliedFilter,
    search: String,
): List<FilterClause> {
    val clauses = mutableListOf<FilterClause>()
    if (search.isNotBlank()) {
        clauses +=
            FilterClause(
                field = KavitaField.SeriesName,
                comparison = KavitaComparison.Matches,
                value = search,
            )
    }
    when (appliedFilter.statusFilter) {
        ReadStatusFilter.Unread ->
            clauses +=
                FilterClause(
                    field = KavitaField.ReadingProgress,
                    comparison = KavitaComparison.Equal,
                    value = "0",
                )
        ReadStatusFilter.Completed ->
            clauses +=
                FilterClause(
                    field = KavitaField.ReadingProgress,
                    comparison = KavitaComparison.Equal,
                    value = "100",
                )
        ReadStatusFilter.InProgress -> {
            clauses +=
                FilterClause(
                    field = KavitaField.ReadingProgress,
                    comparison = KavitaComparison.GreaterThan,
                    value = "0",
                )
            clauses +=
                FilterClause(
                    field = KavitaField.ReadingProgress,
                    comparison = KavitaComparison.LessThan,
                    value = "100",
                )
        }
        ReadStatusFilter.Any -> {}
    }
    appliedFilter.minYear.toIntOrNull()?.let {
        clauses +=
            FilterClause(
                field = KavitaField.ReleaseYear,
                comparison = KavitaComparison.GreaterThanEqual,
                value = it.toString(),
            )
    }
    appliedFilter.maxYear.toIntOrNull()?.let {
        clauses +=
            FilterClause(
                field = KavitaField.ReleaseYear,
                comparison = KavitaComparison.LessThanEqual,
                value = it.toString(),
            )
    }
    addTriStateClauses(appliedFilter.genres, KavitaField.Genres, { it.toString() }, clauses)
    addTriStateClauses(appliedFilter.tags, KavitaField.Tags, { it.toString() }, clauses)
    addTriStateClauses(appliedFilter.languages, KavitaField.Languages, { it }, clauses)
    addTriStateClauses(appliedFilter.ageRatings, KavitaField.AgeRating, { it.toString() }, clauses)
    addTriStateClauses(appliedFilter.collections, KavitaField.CollectionTags, { it.toString() }, clauses)
    addTriStateClauses(appliedFilter.libraries, KavitaField.Libraries, { it.toString() }, clauses)
    return clauses
}

private fun <T> addTriStateClauses(
    entries: Map<T, TriState>,
    field: KavitaField,
    mapValue: (T) -> String,
    target: MutableList<FilterClause>,
) {
    entries.forEach { (id, state) ->
        when (state) {
            TriState.Include -> target += FilterClause(field, KavitaComparison.Contains, mapValue(id))
            TriState.Exclude -> target += FilterClause(field, KavitaComparison.NotContains, mapValue(id))
            TriState.None -> {}
        }
    }
}

private fun FilterStatementDto.toFilterClause(): FilterClause? {
    val fieldEnum = KavitaField.values().firstOrNull { it.id == field } ?: return null
    val comparisonEnum = KavitaComparison.values().firstOrNull { it.id == comparison } ?: return null
    val valueStr = value ?: return null
    return FilterClause(fieldEnum, comparisonEnum, valueStr)
}

fun emptyFilter(): FilterV2Dto =
    FilterV2Dto(
        id = null,
        name = null,
        statements = null,
        combination = 1,
        sortOptions = SortOptionDto(sortField = 1, isAscending = true),
        limitTo = 0,
    )
