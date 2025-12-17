package net.dom53.inkita.ui.seriesdetail.utils

import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.data.api.dto.RelatedSeriesDto
import net.dom53.inkita.data.api.dto.WantToReadDto
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.ui.seriesdetail.model.RelatedFilter
import net.dom53.inkita.ui.seriesdetail.model.RelatedGroup
import java.util.Locale

internal suspend fun toggleWantToRead(
    add: Boolean,
    seriesId: Int,
    config: AppConfig,
) {
    val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
    val body = WantToReadDto(seriesIds = listOf(seriesId))
    if (add) api.addWantToRead(body) else api.removeWantToRead(body)
}

internal suspend fun loadRelated(
    seriesId: Int,
    config: AppConfig,
    onLoading: (Boolean) -> Unit = {},
    onError: (String?) -> Unit = {},
    onResult: (List<RelatedGroup>) -> Unit,
) {
    onLoading(true)
    val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
    runCatching { api.getAllRelated(seriesId) }
        .onSuccess { resp ->
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) onResult(body.toGroups()) else onError("Empty response")
            } else {
                onError("HTTP ${resp.code()} ${resp.message()}")
            }
        }.onFailure { e -> onError(e.message) }
    onLoading(false)
}

internal fun RelatedSeriesDto.toGroups(): List<RelatedGroup> =
    buildList {
        if (sequels?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Sequels,
                    "Sequel",
                    sequels.map { it.toDomain() },
                ),
            )
        }
        if (prequels?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Prequels,
                    "Prequel",
                    prequels.map { it.toDomain() },
                ),
            )
        }
        if (spinOffs?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.SpinOffs,
                    "Spin-off",
                    spinOffs.map { it.toDomain() },
                ),
            )
        }
        if (adaptations?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Adaptations,
                    "Adaptation",
                    adaptations.map { it.toDomain() },
                ),
            )
        }
        if (sideStories?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.SideStories,
                    "Side story",
                    sideStories.map { it.toDomain() },
                ),
            )
        }
        if (alternativeSettings?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.AlternativeSettings,
                    "Alternative setting",
                    alternativeSettings.map { it.toDomain() },
                ),
            )
        }
        if (alternativeVersions?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.AlternativeVersions,
                    "Alternative version",
                    alternativeVersions.map { it.toDomain() },
                ),
            )
        }
        if (parent?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Parent,
                    "Parent",
                    parent.map { it.toDomain() },
                ),
            )
        }
        if (contains?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Contains,
                    "Contains",
                    contains.map { it.toDomain() },
                ),
            )
        }
        if (others?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Others,
                    "Other",
                    others.map { it.toDomain() },
                ),
            )
        }
        if (doujinshis?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Doujinshis,
                    "Doujinshi",
                    doujinshis.map { it.toDomain() },
                ),
            )
        }
        if (editions?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Editions,
                    "Edition",
                    editions.map { it.toDomain() },
                ),
            )
        }
        if (annuals?.isNotEmpty() == true) {
            add(
                RelatedGroup(
                    RelatedFilter.Annuals,
                    "Annuals",
                    annuals.map { it.toDomain() },
                ),
            )
        }
    }

internal fun cleanHtml(text: String?): String? {
    if (text == null) return null
    val replaced =
        text
            .replace("(?i)<br\\s*/?>".toRegex(), "\n")
            .replace("(?i)</p>".toRegex(), "\n\n")
    val stripped = replaced.replace("(?i)<[^>]*>".toRegex(), "")
    return stripped.trim()
}

internal fun formatHours(
    min: Double?,
    max: Double?,
    avg: Double?,
): String? {
    val parts = mutableListOf<String>()
    val formatter: (Double) -> String = { value ->
        if (value >= 10) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
    }
    val avgText = avg?.let { formatter(it) }?.let { "~$it h" }
    if (avgText != null) parts.add(avgText)
    val rangeText =
        when {
            min != null && max != null -> "${formatter(min)}–${formatter(max)} h"
            min != null -> "min ${formatter(min)} h"
            max != null -> "max ${formatter(max)} h"
            else -> null
        }
    if (rangeText != null) parts.add(rangeText)
    if (parts.isEmpty()) return null
    return parts.joinToString(" ")
}
