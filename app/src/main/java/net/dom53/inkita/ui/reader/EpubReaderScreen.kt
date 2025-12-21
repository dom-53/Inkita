package net.dom53.inkita.ui.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.ReaderRepository

@Composable
fun EpubReaderScreen(
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
    settingsContent: (@Composable (ReaderSettingsState, ReaderSettingsCallbacks) -> Unit)? = null,
    overlayExtras: @Composable BoxScope.() -> Unit = {},
) {
    BaseReaderScreen(
        chapterId = chapterId,
        initialPage = initialPage,
        readerViewModel =
            viewModel(
                key = "reader-$chapterId",
                factory =
                    EpubReaderViewModel.provideFactory(
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
        renderer = EpubReader,
        onBack = onBack,
        onNavigateToChapter = onNavigateToChapter,
        topBarContent = topBarContent,
        bottomBarContent = bottomBarContent,
        settingsContent = settingsContent,
        overlayExtras = overlayExtras,
    )
}
