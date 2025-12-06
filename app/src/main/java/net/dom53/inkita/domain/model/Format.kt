package net.dom53.inkita.domain.model

enum class Format(
    val id: Int,
) {
    Image(0),
    Archive(1),
    Unknown(2),
    Epub(3),
    Pdf(4),
    ;

    companion object {
        fun fromId(id: Int?): Format? =
            when (id) {
                Image.id -> Image
                Archive.id -> Archive
                Epub.id -> Epub
                Pdf.id -> Pdf
                Unknown.id -> Unknown
                else -> null
            }
    }
}
