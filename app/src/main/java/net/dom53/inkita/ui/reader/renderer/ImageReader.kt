package net.dom53.inkita.ui.reader.renderer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import net.dom53.inkita.R

object ImageReader : BaseReader {
    override val supportsTextSettings: Boolean = false

    @Composable
    override fun Content(
        params: ReaderRenderParams,
        callbacks: ReaderRenderCallbacks,
    ) {
        val imageUrl = params.uiState.imageUrl
        when {
            params.uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            imageUrl.isNullOrBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = params.uiState.error ?: stringResource(R.string.general_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable { callbacks.onToggleOverlay() },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
