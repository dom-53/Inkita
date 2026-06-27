package net.dom53.inkita.ui.reader.renderer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.ImageReaderMode
import kotlin.math.roundToInt

object ImageReader : BaseReader {
    override val supportsTextSettings: Boolean = false

    @Composable
    override fun Content(
        params: ReaderRenderParams,
        callbacks: ReaderRenderCallbacks,
    ) {
        val imageUrl = params.uiState.imageUrl
        val previousImageUrl = params.uiState.previousImageUrl
        val nextImageUrl = params.uiState.nextImageUrl
        val isRtl = params.imageReaderMode == ImageReaderMode.RightToLeft
        val isVertical = params.imageReaderMode == ImageReaderMode.Vertical
        val scope = rememberCoroutineScope()
        var viewportSize by remember { mutableStateOf(IntSize.Zero) }
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        var settleJob by remember { mutableStateOf<Job?>(null) }

        fun settleTo(
            targetOffset: Float,
            onSettled: () -> Unit,
        ) {
            settleJob?.cancel()
            settleJob =
                scope.launch {
                    animate(
                        initialValue = dragOffsetPx,
                        targetValue = targetOffset,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    ) { value, _ ->
                        dragOffsetPx = value
                    }
                    onSettled()
                    dragOffsetPx = 0f
                }
        }
        LaunchedEffect(params.uiState.pageIndex) {
            settleJob?.cancel()
            dragOffsetPx = 0f
        }
        val toggleOverlayModifier =
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { callbacks.onToggleOverlay() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(params.uiState.pageIndex) {
                        var totalDrag = 0f
                        if (isVertical) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    totalDrag = 0f
                                    settleJob?.cancel()
                                    dragOffsetPx = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                    dragOffsetPx = totalDrag
                                },
                                onDragEnd = {
                                    val dragAtRelease = totalDrag
                                    val height = viewportSize.height.toFloat().coerceAtLeast(1f)
                                    if (dragAtRelease != 0f) {
                                        val targetOffset = if (dragAtRelease < 0) -height else height
                                        settleTo(
                                            targetOffset = targetOffset,
                                        ) {
                                            if (dragAtRelease < 0) {
                                                callbacks.onSwipeNext()
                                            } else {
                                                callbacks.onSwipePrev()
                                            }
                                        }
                                    } else {
                                        dragOffsetPx = 0f
                                    }
                                    totalDrag = 0f
                                },
                                onDragCancel = {
                                    totalDrag = 0f
                                    dragOffsetPx = 0f
                                },
                            )
                        } else {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    totalDrag = 0f
                                    settleJob?.cancel()
                                    dragOffsetPx = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDrag += dragAmount
                                    dragOffsetPx = totalDrag
                                },
                                onDragEnd = {
                                    val dragAtRelease = totalDrag
                                    val width = viewportSize.width.toFloat().coerceAtLeast(1f)
                                    if (dragAtRelease != 0f) {
                                        val next =
                                            if (isRtl) {
                                                dragAtRelease > 0
                                            } else {
                                                dragAtRelease < 0
                                            }
                                        val targetOffset = if (dragAtRelease < 0) -width else width
                                        val targetImageUrl = if (next) nextImageUrl else previousImageUrl
                                        val turnPage = {
                                            if (next) {
                                                callbacks.onSwipeNext()
                                            } else {
                                                callbacks.onSwipePrev()
                                            }
                                        }
                                        if (targetImageUrl == null) {
                                            dragOffsetPx = 0f
                                            turnPage()
                                        } else {
                                            settleTo(
                                                targetOffset = targetOffset,
                                                onSettled = turnPage,
                                            )
                                        }
                                    } else {
                                        dragOffsetPx = 0f
                                    }
                                    totalDrag = 0f
                                },
                                onDragCancel = {
                                    totalDrag = 0f
                                    dragOffsetPx = 0f
                                },
                            )
                        }
                    }.then(toggleOverlayModifier),
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
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                ) {
                    if (!isVertical) {
                        val width = viewportSize.width
                        val leftImageUrl = if (isRtl) nextImageUrl else previousImageUrl
                        val rightImageUrl = if (isRtl) previousImageUrl else nextImageUrl
                        leftImageUrl?.let { url ->
                            ImagePage(
                                imageUrl = url,
                                offset =
                                    IntOffset(
                                        x = dragOffsetPx.roundToInt() - width,
                                        y = 0,
                                    ),
                            )
                        }
                        rightImageUrl?.let { url ->
                            ImagePage(
                                imageUrl = url,
                                offset =
                                    IntOffset(
                                        x = dragOffsetPx.roundToInt() + width,
                                        y = 0,
                                    ),
                            )
                        }
                    }
                    ImagePage(
                        imageUrl = imageUrl,
                        offset =
                            if (isVertical) {
                                IntOffset(0, dragOffsetPx.roundToInt())
                            } else {
                                IntOffset(dragOffsetPx.roundToInt(), 0)
                            },
                    )
                }

                if (params.uiState.isLoading) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ImagePage(
    imageUrl: String,
    offset: IntOffset,
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        modifier =
            Modifier
                .fillMaxSize()
                .offset { offset },
        contentScale = ContentScale.Fit,
    )
}
