package net.dom53.inkita.ui.reader

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.domain.model.Format
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
    formatId: Int? = null,
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
        readerRepository = readerRepository,
        appPreferences = appPreferences,
        seriesId = seriesId,
        volumeId = volumeId,
        serverUrl = serverUrl,
        apiKey = apiKey,
        formatId = formatId ?: Format.Epub.id,
        renderer = EpubReader,
        onBack = onBack,
        onNavigateToChapter = onNavigateToChapter,
        topBarContent = topBarContent,
        bottomBarContent = bottomBarContent,
        settingsContent = settingsContent,
        overlayExtras = overlayExtras,
    )
}
