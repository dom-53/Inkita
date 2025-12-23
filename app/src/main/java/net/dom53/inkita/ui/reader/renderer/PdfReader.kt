package net.dom53.inkita.ui.reader.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.dom53.inkita.R
import net.dom53.inkita.ui.reader.screen.PdfPageViewer

object PdfReader : BaseReader {
    override val supportsTextSettings: Boolean = false

    @Composable
    override fun Content(
        params: ReaderRenderParams,
        callbacks: ReaderRenderCallbacks,
    ) {
        when {
            params.uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            params.uiState.pdfPath == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.general_error))
                }
            }
            else -> {
                PdfPageViewer(
                    pdfPath = params.uiState.pdfPath,
                    currentPage = params.uiState.pageIndex,
                    onPageChanged = callbacks.onPdfPageChanged,
                )
            }
        }
    }
}
