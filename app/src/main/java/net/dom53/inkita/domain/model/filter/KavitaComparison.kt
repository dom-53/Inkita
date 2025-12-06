package net.dom53.inkita.domain.model.filter

enum class KavitaComparison(
    val id: Int,
) {
    Equal(0),
    GreaterThan(1),
    GreaterThanEqual(2),
    LessThan(3),
    LessThanEqual(4),
    Contains(5),
    MustContains(6),
    Matches(7),
    NotContains(8),
    NotEqual(9),
    BeginsWith(10),
    EndsWith(11),
    IsBefore(12),
    IsAfter(13),
    IsInLast(14),
    IsNotInLast(15),
    IsEmpty(16),
}
