package net.dom53.inkita.ui.seriesdetail.utils

internal fun cleanHtml(text: String?): String? {
    if (text == null) return null
    val replaced =
        text
            .replace("(?i)<br\\s*/?>".toRegex(), "\n")
            .replace("(?i)</p>".toRegex(), "\n\n")
    val stripped = replaced.replace("(?i)<[^>]*>".toRegex(), "")
    return stripped.trim()
}
