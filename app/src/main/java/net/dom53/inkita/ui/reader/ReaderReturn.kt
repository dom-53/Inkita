package net.dom53.inkita.ui.reader

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReaderReturn(
    val volumeId: Int,
    val page: Int,
) : Parcelable
