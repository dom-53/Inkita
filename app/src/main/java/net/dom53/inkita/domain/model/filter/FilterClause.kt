package net.dom53.inkita.domain.model.filter

data class FilterClause(
    val field: KavitaField,
    val comparison: KavitaComparison,
    val value: String,
)
