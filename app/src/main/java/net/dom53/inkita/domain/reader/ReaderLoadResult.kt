package net.dom53.inkita.domain.reader

data class ReaderLoadResult(
    val content: String? = null,
    val fromOffline: Boolean = false,
    val pdfPath: String? = null,
)
