package net.dom53.inkita.data.api.dto

/**
 * Jedna podmínka filtru pro /api/Series/v2.
 * Odpovídá FilterStatementDto z OpenAPI:
 * - comparison: int (KavitaComparison.id)
 * - field: int (KavitaField.id)
 * - value: string
 */
data class FilterStatementDto(
    val comparison: Int,
    val field: Int,
    val value: String?,
)

/**
 * FilterV2Dto podle OpenAPI:
 *
 * {
 *   "id": 0,
 *   "name": "string",
 *   "statements": [ FilterStatementDto ],
 *   "combination": 0,
 *   "sortOptions": SortOptionsDto,
 *   "limitTo": 0
 * }
 */
data class FilterV2Dto(
    val id: Int? = null,
    val name: String? = null,
    val statements: List<FilterStatementDto>?,
    val combination: Int,
    val sortOptions: SortOptionDto,
    val limitTo: Int = 0,
)
