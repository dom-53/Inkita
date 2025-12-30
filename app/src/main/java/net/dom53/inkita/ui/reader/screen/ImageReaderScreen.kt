package net.dom53.inkita.ui.reader.screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.repository.ReaderRepository
import net.dom53.inkita.ui.reader.renderer.ImageReader
import net.dom53.inkita.ui.reader.viewmodel.ImageReaderViewModel

@Composable
fun ImageReaderScreen(
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
    bottomBarContent: (@Composable (ReaderBottomBarState, ReaderBottomBarCallbacks) -> Unit)? = null,
    settingsContent: (@Composable (ReaderSettingsState, ReaderSettingsCallbacks) -> Unit)? = null,
) {
    BaseReaderScreen(
        chapterId = chapterId,
        initialPage = initialPage,
        readerViewModel =
            viewModel(
                factory =
                    ImageReaderViewModel.provideFactory(
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
        renderer = ImageReader,
        onBack = onBack,
        onNavigateToChapter = onNavigateToChapter,
        bottomBarContent = bottomBarContent,
        settingsContent = settingsContent,
    )
}
