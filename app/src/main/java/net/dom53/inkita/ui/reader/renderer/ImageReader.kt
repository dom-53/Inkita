package net.dom53.inkita.ui.reader.renderer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
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
        val isWebtoon = params.imageReaderMode == ImageReaderMode.Webtoon

        if (isWebtoon) {
            WebtoonContent(params = params, callbacks = callbacks)
            return
        }

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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(params.uiState.pageIndex, params.imageReaderMode) {
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
                                        val next = dragAtRelease < 0
                                        val targetOffset = if (dragAtRelease < 0) -height else height
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
                    }.pointerInput(params.imageReaderMode, viewportSize) {
                        detectTapGestures { offset ->
                            if (isVertical) {
                                callbacks.onToggleOverlay()
                            } else {
                                val width = viewportSize.width.toFloat().coerceAtLeast(1f)
                                when {
                                    offset.x < width * 0.4f -> {
                                        if (isRtl) {
                                            callbacks.onSwipeNext()
                                        } else {
                                            callbacks.onSwipePrev()
                                        }
                                    }
                                    offset.x > width * 0.6f -> {
                                        if (isRtl) {
                                            callbacks.onSwipePrev()
                                        } else {
                                            callbacks.onSwipeNext()
                                        }
                                    }
                                    else -> callbacks.onToggleOverlay()
                                }
                            }
                        }
                    },
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
                    if (isVertical) {
                        val height = viewportSize.height
                        previousImageUrl?.let { url ->
                            ImagePage(
                                imageUrl = url,
                                offset =
                                    IntOffset(
                                        x = 0,
                                        y = dragOffsetPx.roundToInt() - height,
                                    ),
                            )
                        }
                        nextImageUrl?.let { url ->
                            ImagePage(
                                imageUrl = url,
                                offset =
                                    IntOffset(
                                        x = 0,
                                        y = dragOffsetPx.roundToInt() + height,
                                    ),
                            )
                        }
                    } else {
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
private fun WebtoonContent(
    params: ReaderRenderParams,
    callbacks: ReaderRenderCallbacks,
) {
    val uiState = params.uiState
    val imageUrls =
        remember(
            uiState.imageUrls,
            uiState.pageIndex,
            uiState.imageUrl,
        ) {
            buildMap {
                putAll(uiState.imageUrls)
                uiState.imageUrl?.let { put(uiState.pageIndex, it) }
            }
        }
    val lastKnownPage = imageUrls.keys.maxOrNull() ?: uiState.pageIndex
    val pageCount = uiState.pageCount.coerceAtLeast(lastKnownPage + 1).coerceAtLeast(1)
    val initialPage = uiState.pageIndex.coerceIn(0, pageCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    var reportedPageIndex by remember { mutableIntStateOf(initialPage) }
    var programmaticScrollTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(listState, pageCount, params.imagePrefetchPages) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { pageIndex ->
                reportedPageIndex = pageIndex
                if (programmaticScrollTarget == null) {
                    callbacks.onImagePageVisible(pageIndex, params.imagePrefetchPages)
                }
            }
    }
    LaunchedEffect(uiState.pageIndex, pageCount) {
        val targetPage = uiState.pageIndex.coerceIn(0, pageCount - 1)
        if (targetPage != reportedPageIndex) {
            programmaticScrollTarget = targetPage
            try {
                listState.animateScrollToItem(targetPage)
            } finally {
                programmaticScrollTarget = null
                val visiblePage = listState.firstVisibleItemIndex
                reportedPageIndex = visiblePage
                callbacks.onImagePageVisible(visiblePage, params.imagePrefetchPages)
            }
        }
    }

    if (imageUrls.isEmpty() && uiState.error != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = uiState.error,
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { callbacks.onToggleOverlay() }
                },
    ) {
        items(
            count = pageCount,
            key = { it },
        ) { pageIndex ->
            val imageUrl = imageUrls[pageIndex]
            if (imageUrl == null) {
                WebtoonPlaceholder()
            } else {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                        is AsyncImagePainter.State.Error -> WebtoonPlaceholder(showProgress = false)
                        else -> WebtoonPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
private fun WebtoonPlaceholder(showProgress: Boolean = true) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(WEBTOON_PLACEHOLDER_ASPECT_RATIO),
        contentAlignment = Alignment.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator()
        } else {
            Text(
                text = stringResource(R.string.general_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private const val WEBTOON_PLACEHOLDER_ASPECT_RATIO = 0.7f

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
