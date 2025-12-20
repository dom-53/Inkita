package net.dom53.inkita.ui.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.ReaderRepository

@Composable
fun PdfReaderScreen(
    chapterId: Int,
    initialPage: Int?,
    readerRepository: ReaderRepository,
    appPreferences: AppPreferences,
    seriesId: Int?,
    volumeId: Int?,
    serverUrl: String?,
    apiKey: String? = null,
    onBack: (chapterId: Int, page: Int, seriesId: Int?, volumeId: Int?) -> Unit = { _, _, _, _ -> },
    onNavigateToChapter: (Int, Int?, Int?, Int?) -> Unit = { _, _, _, _ -> },
    topBarContent: (@Composable (String, String, () -> Unit) -> Unit)? = null,
    bottomBarContent: (@Composable (ReaderBottomBarState, ReaderBottomBarCallbacks) -> Unit)? = null,
    overlayExtras: @Composable BoxScope.() -> Unit = {},
) {
    BaseReaderScreen(
        chapterId = chapterId,
        initialPage = initialPage,
        readerViewModel =
            viewModel(
                factory =
                    PdfReaderViewModel.provideFactory(
                        chapterId = chapterId,
                        initialPage = initialPage ?: 0,
                        readerRepository = readerRepository,
                        seriesId = seriesId,
                        volumeId = volumeId,
                    ),
            ),
        appPreferences = appPreferences,
        seriesId = seriesId,
        volumeId = volumeId,
        serverUrl = serverUrl,
        apiKey = apiKey,
        renderer = PdfReader,
        onBack = onBack,
        onNavigateToChapter = onNavigateToChapter,
        topBarContent = topBarContent,
        bottomBarContent = bottomBarContent,
        settingsContent = null,
        overlayExtras = overlayExtras,
    )
}
