package net.dom53.inkita.ui.common

import net.dom53.inkita.core.storage.AppConfig

fun seriesCoverUrl(
    config: AppConfig,
    seriesId: Int,
): String? {
    if (config.serverUrl.isBlank() || config.apiKey.isBlank()) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/api/Image/series-cover?seriesId=$seriesId&apiKey=${config.apiKey}"
}
