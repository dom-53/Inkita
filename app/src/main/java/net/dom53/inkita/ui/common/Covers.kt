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

fun collectionCoverUrl(
    config: AppConfig,
    collectionId: Int,
): String? {
    if (config.serverUrl.isBlank() || config.imageApiKey.isBlank()) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/api/Image/collection-cover?collectionTagId=$collectionId&apiKey=${config.imageApiKey}"
}

fun readingListCoverUrl(
    config: AppConfig,
    readingListId: Int,
): String? {
    if (config.serverUrl.isBlank() || config.imageApiKey.isBlank()) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/api/Image/readinglist-cover?readingListId=$readingListId&apiKey=${config.imageApiKey}"
}

fun personCoverUrl(
    config: AppConfig,
    personId: Int,
): String? {
    if (config.serverUrl.isBlank() || config.imageApiKey.isBlank()) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/api/Image/person-cover?personId=$personId&apiKey=${config.imageApiKey}"
}

fun volumeCoverUrl(
    config: AppConfig,
    volumeId: Int,
): String? {
    if (config.serverUrl.isBlank() || config.imageApiKey.isBlank()) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/api/Image/volume-cover?volumeId=$volumeId&apiKey=${config.imageApiKey}"
}
