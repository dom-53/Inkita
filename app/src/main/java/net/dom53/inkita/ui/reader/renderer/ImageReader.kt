package net.dom53.inkita.ui.reader.renderer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.ImageReaderMode
import net.dom53.inkita.ui.reader.viewmodel.WebtoonChapterUiState
import kotlin.math.max
import kotlin.math.min
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
    val chapters = uiState.webtoonChapters
    if (chapters.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val webtoonItems =
        remember(chapters) {
            buildList {
                chapters.forEach { chapter ->
                    repeat(chapter.pageCount) { pageIndex ->
                        add(WebtoonListItem.Page(chapter, pageIndex))
                    }
                    add(WebtoonListItem.Boundary(chapter.chapterId))
                }
            }
        }
    val activeChapterId = uiState.activeChapterId ?: chapters.first().chapterId
    val activePage = activeChapterId to uiState.pageIndex
    val initialItemIndex =
        webtoonItems
            .indexOfFirst { it is WebtoonListItem.Page && it.chapter.chapterId == activeChapterId && it.pageIndex == uiState.pageIndex }
            .coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItemIndex)
    var reportedPage by remember { mutableStateOf(activePage) }
    var programmaticScrollTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(listState, webtoonItems, params.imagePrefetchPages) {
        snapshotFlow { listState.mostVisiblePage(webtoonItems) }
            .distinctUntilChanged()
            .collect { visiblePage ->
                if (visiblePage != null) {
                    reportedPage = visiblePage.chapterId to visiblePage.pageIndex
                    if (programmaticScrollTarget == null) {
                        callbacks.onImagePageVisible(
                            visiblePage.chapterId,
                            visiblePage.pageIndex,
                            params.imagePrefetchPages,
                        )
                    }
                }
            }
    }
    LaunchedEffect(activeChapterId, uiState.pageIndex, webtoonItems) {
        val targetItemIndex =
            webtoonItems.indexOfFirst {
                it is WebtoonListItem.Page &&
                    it.chapter.chapterId == activeChapterId &&
                    it.pageIndex == uiState.pageIndex
            }
        if (targetItemIndex >= 0 && activePage != reportedPage) {
            programmaticScrollTarget = targetItemIndex
            try {
                listState.animateScrollToItem(targetItemIndex)
            } finally {
                programmaticScrollTarget = null
                val visiblePage = listState.mostVisiblePage(webtoonItems)
                if (visiblePage != null) {
                    reportedPage = visiblePage.chapterId to visiblePage.pageIndex
                    callbacks.onImagePageVisible(
                        visiblePage.chapterId,
                        visiblePage.pageIndex,
                        params.imagePrefetchPages,
                    )
                }
            }
        }
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
            items = webtoonItems,
            key = { it.key },
        ) { item ->
            when (item) {
                is WebtoonListItem.Page -> {
                    val imageUrl = item.chapter.imageUrls[item.pageIndex]
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
                is WebtoonListItem.Boundary -> {
                    val chapterIndex = chapters.indexOfFirst { it.chapterId == item.chapterId }
                    val chapter = chapters.getOrNull(chapterIndex)
                    val nextChapter = chapters.getOrNull(chapterIndex + 1)
                    if (chapter != null) {
                        LaunchedEffect(chapter.chapterId, chapter.hasNextChapter, chapter.isLoadingNextChapter) {
                            if (chapter.hasNextChapter == null && !chapter.isLoadingNextChapter) {
                                callbacks.onWebtoonChapterBoundaryVisible(
                                    chapter.chapterId,
                                    params.imagePrefetchPages,
                                )
                            }
                        }
                        WebtoonChapterBoundary(
                            chapter = chapter,
                            nextChapter = nextChapter,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface WebtoonListItem {
    val key: String

    data class Page(
        val chapter: WebtoonChapterUiState,
        val pageIndex: Int,
    ) : WebtoonListItem {
        override val key: String = "chapter-${chapter.chapterId}-page-$pageIndex"
    }

    data class Boundary(
        val chapterId: Int,
    ) : WebtoonListItem {
        override val key: String = "chapter-$chapterId-boundary"
    }
}

private data class WebtoonPagePosition(
    val chapterId: Int,
    val pageIndex: Int,
)

private fun LazyListState.mostVisiblePage(items: List<WebtoonListItem>): WebtoonPagePosition? {
    val layoutInfo = layoutInfo
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    return layoutInfo.visibleItemsInfo
        .mapNotNull { itemInfo ->
            val item = items.getOrNull(itemInfo.index) as? WebtoonListItem.Page ?: return@mapNotNull null
            val visibleStart = max(itemInfo.offset, viewportStart)
            val visibleEnd = min(itemInfo.offset + itemInfo.size, viewportEnd)
            val visibleSize = (visibleEnd - visibleStart).coerceAtLeast(0)
            WebtoonPagePosition(item.chapter.chapterId, item.pageIndex) to visibleSize
        }.maxByOrNull { (_, visibleSize) -> visibleSize }
        ?.first
}

@Composable
private fun WebtoonChapterBoundary(
    chapter: WebtoonChapterUiState,
    nextChapter: WebtoonChapterUiState?,
) {
    val currentTitle = chapter.displayTitle()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_webtoon_chapter_end, currentTitle),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        when {
            nextChapter != null -> {
                val nextTitle = nextChapter.displayTitle()
                Text(
                    text = stringResource(R.string.reader_webtoon_chapter_start, nextTitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
            chapter.hasNextChapter == false -> {
                Text(
                    text = stringResource(R.string.reader_webtoon_no_next_chapter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                )
            }
            else -> {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = stringResource(R.string.reader_webtoon_loading_next_chapter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                )
            }
        }
    }
}

@Composable
private fun WebtoonChapterUiState.displayTitle(): String =
    title?.takeIf { it.isNotBlank() }
        ?: chapterNumber
            ?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.reader_webtoon_chapter_number, it) }
        ?: stringResource(R.string.reader_webtoon_chapter_fallback)

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
