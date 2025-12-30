package net.dom53.inkita.ui.reader.renderer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.ImageReaderMode
import kotlin.math.abs

object ImageReader : BaseReader {
    override val supportsTextSettings: Boolean = false

    @Composable
    override fun Content(
        params: ReaderRenderParams,
        callbacks: ReaderRenderCallbacks,
    ) {
        val imageUrl = params.uiState.imageUrl
        val isRtl = params.imageReaderMode == ImageReaderMode.RightToLeft
        val isVertical = params.imageReaderMode == ImageReaderMode.Vertical
        val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(params.uiState.pageIndex) {
                        var totalDrag = 0f
                        if (isVertical) {
                            detectVerticalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    if (abs(totalDrag) > swipeThresholdPx) {
                                        if (totalDrag < 0) {
                                            callbacks.onSwipeNext()
                                        } else {
                                            callbacks.onSwipePrev()
                                        }
                                    }
                                    totalDrag = 0f
                                },
                                onDragCancel = { totalDrag = 0f },
                            )
                        } else {
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    if (abs(totalDrag) > swipeThresholdPx) {
                                        val next =
                                            if (isRtl) {
                                                totalDrag > 0
                                            } else {
                                                totalDrag < 0
                                            }
                                        if (next) {
                                            callbacks.onSwipeNext()
                                        } else {
                                            callbacks.onSwipePrev()
                                        }
                                    }
                                    totalDrag = 0f
                                },
                                onDragCancel = { totalDrag = 0f },
                            )
                        }
                    }.clickable { callbacks.onToggleOverlay() },
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl.isNullOrBlank()) {
                if (params.uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = params.uiState.error ?: stringResource(R.string.general_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                AnimatedContent(
                    targetState = ImagePageState(pageIndex = params.uiState.pageIndex, imageUrl = imageUrl),
                    transitionSpec = {
                        val direction =
                            when {
                                targetState.pageIndex > initialState.pageIndex ->
                                    if (isVertical) {
                                        1
                                    } else if (isRtl) {
                                        -1
                                    } else {
                                        1
                                    }
                                targetState.pageIndex < initialState.pageIndex ->
                                    if (isVertical) {
                                        -1
                                    } else if (isRtl) {
                                        1
                                    } else {
                                        -1
                                    }
                                else -> 0
                            }
                        if (direction == 0) {
                            fadeIn() togetherWith fadeOut()
                        } else if (isVertical) {
                            (slideInVertically { fullHeight -> fullHeight * direction } + fadeIn()) togetherWith
                                (slideOutVertically { fullHeight -> -fullHeight * direction } + fadeOut())
                        } else {
                            (slideInHorizontally { fullWidth -> fullWidth * direction } + fadeIn()) togetherWith
                                (slideOutHorizontally { fullWidth -> -fullWidth * direction } + fadeOut())
                        }
                    },
                    label = "ImageReaderTransition",
                ) { state ->
                    AsyncImage(
                        model = state.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                if (params.uiState.isLoading) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

private data class ImagePageState(
    val pageIndex: Int,
    val imageUrl: String,
)
