package net.dom53.inkita.ui.reader.renderer

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import net.dom53.inkita.core.storage.ImageReaderMode
import net.dom53.inkita.core.storage.ReaderThemeMode
import net.dom53.inkita.ui.reader.model.ReaderFontOption
import net.dom53.inkita.ui.reader.viewmodel.ReaderUiState

interface BaseReader {
    val supportsTextSettings: Boolean

    @Composable
    fun Content(
        params: ReaderRenderParams,
        callbacks: ReaderRenderCallbacks,
    )
}

data class ReaderRenderParams(
    val uiState: ReaderUiState,
    val serverUrl: String?,
    val apiKey: String?,
    val fontSize: Float,
    val lineHeight: Float,
    val padding: Dp,
    val fontOption: ReaderFontOption,
    val textAlign: TextAlign,
    val themeMode: ReaderThemeMode,
    val pendingScrollY: Int?,
    val pendingScrollId: String?,
    val imageReaderMode: ImageReaderMode,
)

data class ReaderRenderCallbacks(
    val onToggleOverlay: () -> Unit,
    val onSwipeNext: () -> Unit,
    val onSwipePrev: () -> Unit,
    val onConsumePendingScroll: () -> Unit,
    val onConsumeScrollId: () -> Unit,
    val onWebViewReady: (WebView) -> Unit,
    val onScrollIdle: (String?) -> Unit,
    val onPdfPageChanged: (Int, Int) -> Unit,
)
