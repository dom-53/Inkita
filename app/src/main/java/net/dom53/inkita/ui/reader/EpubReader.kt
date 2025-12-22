package net.dom53.inkita.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import net.dom53.inkita.R

object EpubReader : BaseReader {
    override val supportsTextSettings: Boolean = true

    @Composable
    override fun Content(
        params: ReaderRenderParams,
        callbacks: ReaderRenderCallbacks,
    ) {
        val html = params.uiState.content
        if (html == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (params.uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        params.uiState.error ?: stringResource(R.string.general_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            return
        }
        ReaderWebView(
            html = html,
            fontSize = params.fontSize,
            lineHeight = params.lineHeight,
            paddingPx = with(LocalDensity.current) { params.padding.toPx() },
            fontOption = params.fontOption,
            textAlignCss =
                when (params.textAlign) {
                    TextAlign.Center -> "center"
                    TextAlign.End, TextAlign.Right -> "right"
                    TextAlign.Justify -> "justify"
                    else -> "left"
                },
            themeMode = params.themeMode,
            serverUrl = params.serverUrl,
            apiKey = params.apiKey,
            onToggleOverlay = callbacks.onToggleOverlay,
            pendingScrollY = params.pendingScrollY,
            onConsumePendingScroll = callbacks.onConsumePendingScroll,
            onWebViewReady = callbacks.onWebViewReady,
            onSwipeNext = callbacks.onSwipeNext,
            onSwipePrev = callbacks.onSwipePrev,
            pendingScrollId = params.pendingScrollId,
            onConsumeScrollId = callbacks.onConsumeScrollId,
            onScrollIdle = callbacks.onScrollIdle,
        )
    }
}
