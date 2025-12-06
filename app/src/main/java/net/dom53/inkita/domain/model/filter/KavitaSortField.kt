package net.dom53.inkita.domain.model.filter

enum class KavitaSortField(
    val id: Int,
) {
    SortName(1),
    Created(2),
    LastModified(3),
    ItemAdded(4),
    TimeToRead(5),
    ReleaseYear(6),
    LastRead(7),
    AverageRating(8),
    Random(9),
}
