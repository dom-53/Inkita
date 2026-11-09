package net.dom53.inkita

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.dom53.inkita.core.storage.ImageReaderMode
import net.dom53.inkita.core.storage.ReaderThemeMode
import net.dom53.inkita.ui.reader.model.readerFontOptions
import net.dom53.inkita.ui.reader.renderer.ImageReader
import net.dom53.inkita.ui.reader.renderer.ReaderRenderCallbacks
import net.dom53.inkita.ui.reader.renderer.ReaderRenderParams
import net.dom53.inkita.ui.reader.viewmodel.ReaderUiState
import net.dom53.inkita.ui.reader.viewmodel.WebtoonChapterUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ComicZoomTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun zoomAndPanPreserveLeftToRightPageTurns() = verifyGestures(ImageReaderMode.LeftToRight)

    @Test
    fun zoomAndPanPreserveRightToLeftPageTurns() = verifyGestures(ImageReaderMode.RightToLeft)

    @Test
    fun zoomAndPanPreserveVerticalPageTurns() = verifyGestures(ImageReaderMode.Vertical)

    @Test
    fun webtoonScrollsBetweenPagesWhileZoomed() {
        val image = createPageImage()
        val visiblePage = AtomicInteger(-1)
        try {
            val chapter =
                WebtoonChapterUiState(
                    chapterId = 1,
                    seriesId = 1,
                    volumeId = null,
                    libraryId = null,
                    title = null,
                    chapterNumber = "1",
                    bookTitle = null,
                    pageCount = 6,
                    imageUrls = (0..5).associateWith { image.absolutePath },
                    hasNextChapter = false,
                )
            openReader(
                ImageReaderMode.Webtoon,
                ReaderUiState(webtoonChapters = listOf(chapter), activeChapterId = 1, isLoading = false),
                callbacks(onImagePageVisible = { _, page, _ -> visiblePage.set(page) }),
            )
            val reader = compose.onNodeWithTag("comic-reader")
            compose.waitUntil(timeoutMillis = 8000) { colorAtProbe() == Color.Blue }
            reader.performTouchInput { zoomIn() }
            assertEquals(Color.Green, colorAtProbe())
            reader.performTouchInput { swipe(center, center - Offset(width * 0.3f, 0f)) }
            assertEquals(Color.Blue, colorAtProbe())

            val initialPage = visiblePage.get()
            reader.performTouchInput {
                swipe(center + Offset(0f, height * 0.35f), center - Offset(0f, height * 0.35f), durationMillis = 500)
            }
            compose.waitUntil(timeoutMillis = 5000) { visiblePage.get() > initialPage }

            reader.performTouchInput { doubleClick() }
            val pixels = reader.captureToImage().toPixelMap()
            assertEquals(Color.Green, pixels[pixels.width / 2, pixels.height / 2])
        } finally {
            image.delete()
        }
    }

    private fun verifyGestures(mode: ImageReaderMode) {
        val image = createPageImage()
        var next = 0
        var previous = 0
        var overlays = 0
        try {
            openReader(
                mode,
                ReaderUiState(imageUrl = image.absolutePath, isLoading = false),
                callbacks(
                    onToggleOverlay = { overlays++ },
                    onSwipeNext = { next++ },
                    onSwipePrev = { previous++ },
                ),
            )
            val reader = compose.onNodeWithTag("comic-reader")
            compose.waitUntil(timeoutMillis = 8000) { colorAtProbe() == Color.Blue }

            reader.performTouchInput { zoomIn() }
            assertEquals(Color.Green, colorAtProbe())

            reader.performTouchInput { swipe(center, center - Offset(width * 0.3f, height * 0.22f)) }
            assertEquals(Color.Blue, colorAtProbe())
            compose.runOnIdle {
                assertEquals(0, next)
                assertEquals(0, previous)
                assertEquals(0, overlays)
            }

            reader.performTouchInput { doubleClick() }
            val pixels = reader.captureToImage().toPixelMap()
            assertEquals(Color.Green, pixels[pixels.width / 2, pixels.height / 2])

            fun swipePage(direction: Float) {
                reader.performTouchInput {
                    val delta =
                        if (mode == ImageReaderMode.Vertical) Offset(0f, height * 0.3f) else Offset(width * 0.3f, 0f)
                    swipe(center, center + delta * direction)
                }
            }
            swipePage(-1f)
            val movesForward = mode != ImageReaderMode.RightToLeft
            compose.runOnIdle {
                assertEquals(if (movesForward) 1 else 0, next)
                assertEquals(if (movesForward) 0 else 1, previous)
            }
            swipePage(1f)
            compose.runOnIdle {
                assertEquals(1, next)
                assertEquals(1, previous)
                assertEquals(0, overlays)
            }
        } finally {
            image.delete()
        }
    }

    private fun openReader(
        mode: ImageReaderMode,
        state: ReaderUiState,
        callbacks: ReaderRenderCallbacks,
    ) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("comic-reader")) {
                ImageReader.Content(
                    params =
                        ReaderRenderParams(
                            uiState = state,
                            serverUrl = null,
                            apiKey = null,
                            config = null,
                            fontSize = 18f,
                            lineHeight = 1.4f,
                            padding = 0.dp,
                            fontOption = readerFontOptions.first(),
                            textAlign = TextAlign.Start,
                            themeMode = ReaderThemeMode.Dark,
                            pendingScrollY = null,
                            pendingScrollId = null,
                            imageReaderMode = mode,
                            imagePrefetchPages = 0,
                        ),
                    callbacks = callbacks,
                )
            }
        }
    }

    private fun callbacks(
        onToggleOverlay: () -> Unit = {},
        onSwipeNext: () -> Unit = {},
        onSwipePrev: () -> Unit = {},
        onImagePageVisible: (Int, Int, Int) -> Unit = { _, _, _ -> },
    ) = ReaderRenderCallbacks(
        onToggleOverlay = onToggleOverlay,
        onSwipeNext = onSwipeNext,
        onSwipePrev = onSwipePrev,
        onImagePageVisible = onImagePageVisible,
        onWebtoonChapterBoundaryVisible = { _, _ -> },
        onConsumePendingScroll = {},
        onConsumeScrollId = {},
        onWebViewReady = {},
        onScrollIdle = {},
        onPdfPageChanged = { _, _ -> },
    )

    private fun TouchInjectionScope.zoomIn() {
        val span = width * 0.13f
        pinch(
            start0 = center - Offset(span, 0f),
            end0 = center - Offset(span * 2, 0f),
            start1 = center + Offset(span, 0f),
            end1 = center + Offset(span * 2, 0f),
        )
    }

    private fun colorAtProbe(): Color {
        val pixels = compose.onNodeWithTag("comic-reader").captureToImage().toPixelMap()
        return pixels[(pixels.width * 0.7f).toInt(), pixels.height / 2]
    }

    private fun createPageImage(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("zoom-page-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        for (y in 0 until bitmap.height) {
            for (x in 96 until 160) bitmap.setPixel(x, y, android.graphics.Color.GREEN)
        }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return image
    }
}
