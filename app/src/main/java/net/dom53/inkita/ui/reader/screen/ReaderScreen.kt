package net.dom53.inkita.ui.reader.screen

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.R
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.ReaderPrefs
import net.dom53.inkita.core.storage.ReaderThemeMode
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.repository.ReaderRepository
import net.dom53.inkita.ui.reader.model.ReaderFontOption
import net.dom53.inkita.ui.reader.model.readerFontOptions
import net.dom53.inkita.ui.reader.model.readerThemeOptions
import net.dom53.inkita.ui.reader.renderer.BaseReader
import net.dom53.inkita.ui.reader.renderer.ReaderRenderCallbacks
import net.dom53.inkita.ui.reader.renderer.ReaderRenderParams
import net.dom53.inkita.ui.reader.viewmodel.BaseReaderViewModel
import net.dom53.inkita.ui.reader.viewmodel.PdfReaderViewModel
import net.dom53.inkita.ui.reader.viewmodel.ReaderUiState
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ReaderScreen(
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
) {
    val format = Format.fromId(formatId)
    if (format == Format.Pdf) {
        PdfReaderScreen(
            chapterId = chapterId,
            initialPage = initialPage,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = seriesId,
            volumeId = volumeId,
            serverUrl = serverUrl,
            apiKey = apiKey,
            onBack = onBack,
            onNavigateToChapter = onNavigateToChapter,
        )
    } else if (format == Format.Image || format == Format.Archive) {
        ImageReaderScreen(
            chapterId = chapterId,
            initialPage = initialPage,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = seriesId,
            volumeId = volumeId,
            serverUrl = serverUrl,
            apiKey = apiKey,
            onBack = onBack,
            onNavigateToChapter = onNavigateToChapter,
        )
    } else {
        EpubReaderScreen(
            chapterId = chapterId,
            initialPage = initialPage,
            readerRepository = readerRepository,
            appPreferences = appPreferences,
            seriesId = seriesId,
            volumeId = volumeId,
            serverUrl = serverUrl,
            apiKey = apiKey,
            onBack = onBack,
            onNavigateToChapter = onNavigateToChapter,
        )
    }
}

@Composable
internal fun BaseReaderScreen(
    chapterId: Int,
    initialPage: Int?,
    readerViewModel: BaseReaderViewModel,
    appPreferences: AppPreferences,
    seriesId: Int?,
    volumeId: Int?,
    serverUrl: String?,
    apiKey: String? = null,
    renderer: BaseReader,
    onBack: (chapterId: Int, page: Int, seriesId: Int?, volumeId: Int?) -> Unit = { _, _, _, _ -> },
    onNavigateToChapter: (Int, Int?, Int?, Int?) -> Unit = { _, _, _, _ -> },
    topBarContent: (@Composable (String, String, () -> Unit) -> Unit)? = null,
    bottomBarContent: (@Composable (ReaderBottomBarState, ReaderBottomBarCallbacks) -> Unit)? = null,
    settingsContent: (@Composable (ReaderSettingsState, ReaderSettingsCallbacks) -> Unit)? = null,
    overlayExtras: @Composable BoxScope.() -> Unit = {},
) {
    val uiState by readerViewModel.state.collectAsState()
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            readerViewModel.clearError()
        }
    }

    BackHandler {
        val sid = uiState.bookInfo?.seriesId ?: seriesId
        val vid = uiState.bookInfo?.volumeId ?: volumeId
        onBack(chapterId, uiState.pageIndex, sid, vid)
    }
    var pendingScrollY by remember { mutableStateOf<Int?>(null) }
    var pendingScrollId by remember { mutableStateOf<String?>(null) }
    val readerPrefs by appPreferences.readerPrefsFlow.collectAsState(initial = ReaderPrefs())
    val fontOptions = remember { readerFontOptions }

    var fontSize by remember { mutableStateOf(18f) }
    var lineHeight by remember { mutableStateOf(1.4f) }
    var padding by remember { mutableStateOf(5.dp) }
    var fontFamilyId by remember { mutableStateOf(ReaderPrefs.DEFAULT_FONT_FAMILY) }
    var textAlign by remember { mutableStateOf(TextAlign.Justify) }
    var themeMode by remember { mutableStateOf(ReaderThemeMode.Light) }
    val scope = rememberCoroutineScope()
    val activity = LocalView.current.context as? Activity
    var showOverlay by remember { mutableStateOf(true) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var selectedSettingsTab by remember { mutableStateOf(ReaderSettingsTab.Font) }
    val selectedFont = fontOptions.firstOrNull { it.id == fontFamilyId } ?: readerFontOptions.first()

    LaunchedEffect(readerPrefs) {
        fontSize = readerPrefs.fontSize
        lineHeight = readerPrefs.lineHeight
        padding = readerPrefs.paddingDp.dp
        textAlign = readerPrefs.textAlign
        fontFamilyId = readerPrefs.fontFamily
        themeMode = readerPrefs.readerTheme
    }
    LaunchedEffect(uiState.bookScrollId) {
        pendingScrollId = uiState.bookScrollId
    }

    DisposableEffect(activity) {
        var controller: WindowInsetsControllerCompat? = null
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller =
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
        }
        onDispose {
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val goPrevPage: () -> Unit = goPrevPage@{
        if (uiState.pageIndex <= 0) {
            scope.launch {
                val nav = readerViewModel.getPreviousChapter()
                val chId = nav?.chapterId
                if (chId != null && chId >= 0) {
                    val targetPage = nav.pagesRead?.takeIf { it > 0 }
                    onNavigateToChapter(
                        chId,
                        targetPage,
                        nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                        nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                    )
                } else {
                    Toast
                        .makeText(
                            context,
                            context.resources.getString(R.string.reader_no_previous_chapter),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
            return@goPrevPage
        }
        val prev = (uiState.pageIndex - 1).coerceAtLeast(0)
        if (uiState.isPdf) {
            (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(prev, uiState.pageCount)
        } else {
            scope.launch {
                if (!NetworkUtils.isOnline(context) && !readerViewModel.isPageDownloaded(prev)) {
                    Toast.makeText(context, context.getString(R.string.reader_page_not_downloaded), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                readerViewModel.loadPage(prev)
            }
        }
    }
    val goNextPage: () -> Unit = goNextPage@{
        if (uiState.pageCount > 0 && uiState.pageIndex >= uiState.pageCount - 1) {
            scope.launch {
                val nav = readerViewModel.getNextChapter()
                val chId = nav?.chapterId
                if (chId != null && chId >= 0) {
                    onNavigateToChapter(
                        chId,
                        0,
                        nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                        nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                    )
                } else {
                    Toast.makeText(context, context.resources.getString(R.string.reader_no_next_chapter), Toast.LENGTH_SHORT).show()
                }
            }
            return@goNextPage
        }
        val next =
            if (uiState.pageCount > 0) {
                (uiState.pageIndex + 1).coerceAtMost((uiState.pageCount - 1).coerceAtLeast(0))
            } else {
                uiState.pageIndex + 1 // When page count is unknown (offline), allow attempting the next page.
            }
        if (uiState.isPdf) {
            (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(next, uiState.pageCount)
        } else {
            scope.launch {
                if (!NetworkUtils.isOnline(context) && !readerViewModel.isPageDownloaded(next)) {
                    Toast.makeText(context, context.getString(R.string.reader_page_not_downloaded), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                readerViewModel.loadPage(next)
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                // Keep container transparent; page colors come from reader theme CSS.
                .background(Color.Transparent),
    ) {
        renderer.Content(
            params =
                ReaderRenderParams(
                    uiState = uiState,
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    padding = padding,
                    fontOption = selectedFont,
                    textAlign = textAlign,
                    themeMode = themeMode,
                    pendingScrollY = pendingScrollY,
                    pendingScrollId = pendingScrollId,
                ),
            callbacks =
                ReaderRenderCallbacks(
                    onToggleOverlay = {
                        if (showSettingsPanel) {
                            showSettingsPanel = false
                        } else {
                            showOverlay = !showOverlay
                        }
                    },
                    onSwipeNext = { goNextPage() },
                    onSwipePrev = { goPrevPage() },
                    onConsumePendingScroll = { pendingScrollY = null },
                    onConsumeScrollId = { pendingScrollId = null },
                    onWebViewReady = { webViewRef.value = it },
                    onScrollIdle = { scrollId -> readerViewModel.markProgressAtCurrentPage(scrollId) },
                    onPdfPageChanged = { idx, count ->
                        (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(idx, count)
                    },
                ),
        )

        if (showOverlay) {
            val barTitle = uiState.bookInfo?.title ?: stringResource(R.string.reader_title)
            val barPageText = pageText(uiState.pageIndex, uiState.pageCount, uiState.bookInfo?.pageTitle, context)
            if (topBarContent != null) {
                topBarContent(barTitle, barPageText) {
                    onBack(chapterId, uiState.pageIndex, seriesId, volumeId)
                }
            } else {
                ReaderTopBar(
                    title = barTitle,
                    pageText = barPageText,
                    onBack = { onBack(chapterId, uiState.pageIndex, seriesId, volumeId) },
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                )
            }

            val bottomBarState =
                ReaderBottomBarState(
                    isLoading = uiState.isLoading,
                    enableChapterNav = uiState.bookInfo != null && !uiState.isLoading,
                    pageIndex = uiState.pageIndex,
                    pageCount = uiState.pageCount,
                    timeLeft = uiState.timeLeftText(context),
                    enableSettings = renderer.supportsTextSettings,
                )
            val bottomBarCallbacks =
                ReaderBottomBarCallbacks(
                    onScrollTop = {
                        if (renderer == net.dom53.inkita.ui.reader.renderer.ImageReader) {
                            if (uiState.pageCount > 0) {
                                readerViewModel.loadPage(0)
                            } else {
                                readerViewModel.loadPage(0)
                            }
                        } else if (uiState.isPdf) {
                            if (uiState.pageCount > 0) {
                                (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(0, uiState.pageCount)
                            }
                        } else {
                            webViewRef.value?.let { web ->
                                web.post { web.scrollTo(0, 0) }
                            }
                        }
                    },
                    onScrollBottom = {
                        if (renderer == net.dom53.inkita.ui.reader.renderer.ImageReader) {
                            if (uiState.pageCount > 0) {
                                readerViewModel.loadPage((uiState.pageCount - 1).coerceAtLeast(0))
                            }
                        } else if (uiState.isPdf) {
                            if (uiState.pageCount > 0) {
                                (readerViewModel as? PdfReaderViewModel)?.setPdfPageIndex(uiState.pageCount - 1, uiState.pageCount)
                            }
                        } else {
                            webViewRef.value?.let { web ->
                                web.post {
                                    val bottom = ((web.contentHeight * web.scale) - web.height).toInt().coerceAtLeast(0)
                                    web.scrollTo(0, bottom)
                                }
                            }
                        }
                    },
                    onPrevPage = { goPrevPage() },
                    onNextPage = { goNextPage() },
                    onOpenSettings = { if (renderer.supportsTextSettings) showSettingsPanel = !showSettingsPanel },
                    onPrevChapter = {
                        scope.launch {
                            val nav = readerViewModel.getPreviousChapter()
                            val chId = nav?.chapterId
                            if (chId != null && chId >= 0) {
                                onNavigateToChapter(
                                    chId,
                                    nav.pagesRead ?: 0,
                                    nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                                    nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                                )
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        context.resources.getString(R.string.reader_no_previous_chapter),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    },
                    onNextChapter = {
                        scope.launch {
                            val nav = readerViewModel.getNextChapter()
                            val chId = nav?.chapterId
                            if (chId != null && chId >= 0) {
                                onNavigateToChapter(
                                    chId,
                                    nav.pagesRead ?: 0,
                                    nav.seriesId ?: uiState.bookInfo?.seriesId ?: seriesId,
                                    nav.volumeId ?: uiState.bookInfo?.volumeId ?: volumeId,
                                )
                            } else {
                                Toast.makeText(context, context.resources.getString(R.string.reader_no_next_chapter), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            if (bottomBarContent != null) {
                bottomBarContent(bottomBarState, bottomBarCallbacks)
            } else {
                ReaderBottomBar(
                    isLoading = bottomBarState.isLoading,
                    enableChapterNav = bottomBarState.enableChapterNav,
                    pageIndex = bottomBarState.pageIndex,
                    pageCount = bottomBarState.pageCount,
                    timeLeft = bottomBarState.timeLeft,
                    onScrollTop = bottomBarCallbacks.onScrollTop,
                    onScrollBottom = bottomBarCallbacks.onScrollBottom,
                    onPrevPage = bottomBarCallbacks.onPrevPage,
                    onNextPage = bottomBarCallbacks.onNextPage,
                    onOpenSettings = bottomBarCallbacks.onOpenSettings,
                    onPrevChapter = bottomBarCallbacks.onPrevChapter,
                    onNextChapter = bottomBarCallbacks.onNextChapter,
                    enableSettings = bottomBarState.enableSettings,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter),
                )
            }

            AnimatedVisibility(
                visible = uiState.fromOffline,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.reader_offline_indicator), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondary)
                }
            }

            AnimatedVisibility(
                visible = renderer.supportsTextSettings && showSettingsPanel,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp), // raise above bottom bar
            ) {
                val settingsState =
                    ReaderSettingsState(
                        selectedTab = selectedSettingsTab,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        padding = padding,
                        themeMode = themeMode,
                        textAlign = textAlign,
                        fontOptions = fontOptions,
                        fontFamilyId = selectedFont.id,
                    )
                val settingsCallbacks =
                    ReaderSettingsCallbacks(
                        onSelectTab = { selectedSettingsTab = it },
                        onFontSizeChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            val new = it.coerceIn(8f, 36f)
                            fontSize = new
                            scope.launch { appPreferences.updateReaderPrefs { copy(fontSize = new) } }
                        },
                        onLineHeightChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            val new = it.coerceIn(0.8f, 2.5f)
                            lineHeight = new
                            scope.launch { appPreferences.updateReaderPrefs { copy(lineHeight = new) } }
                        },
                        onPaddingChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            val new = it.coerceIn(0.dp, 32.dp)
                            padding = new
                            scope.launch { appPreferences.updateReaderPrefs { copy(paddingDp = new.value) } }
                        },
                        onThemeChange = { mode ->
                            themeMode = mode
                            scope.launch { appPreferences.updateReaderPrefs { copy(readerTheme = mode) } }
                        },
                        onTextAlignChange = {
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            textAlign = it
                            scope.launch { appPreferences.updateReaderPrefs { copy(textAlign = it) } }
                        },
                        onFontFamilyChange = { option ->
                            pendingScrollY = webViewRef.value?.scrollY ?: 0
                            fontFamilyId = option.id
                            scope.launch {
                                appPreferences.updateReaderPrefs {
                                    copy(
                                        fontFamily = option.id,
                                        useSerif = option.isSerif,
                                    )
                                }
                            }
                        },
                    )
                if (settingsContent != null) {
                    settingsContent(settingsState, settingsCallbacks)
                } else {
                    SettingsPanel(
                        selectedTab = settingsState.selectedTab,
                        onSelectTab = settingsCallbacks.onSelectTab,
                        fontSize = settingsState.fontSize,
                        onFontSizeChange = settingsCallbacks.onFontSizeChange,
                        lineHeight = settingsState.lineHeight,
                        onLineHeightChange = settingsCallbacks.onLineHeightChange,
                        padding = settingsState.padding,
                        onPaddingChange = settingsCallbacks.onPaddingChange,
                        themeMode = settingsState.themeMode,
                        onThemeChange = settingsCallbacks.onThemeChange,
                        textAlign = settingsState.textAlign,
                        onTextAlignChange = settingsCallbacks.onTextAlignChange,
                        fontOptions = settingsState.fontOptions,
                        fontFamilyId = settingsState.fontFamilyId,
                        onFontFamilyChange = settingsCallbacks.onFontFamilyChange,
                    )
                }
            }

            overlayExtras()
        }
    }
}

private fun ReaderUiState.timeLeftText(context: Context): String? {
    val tl = timeLeft ?: return null
    val rounded = String.format(Locale.getDefault(), "%.1f", tl.avgHours)
    return "${context.resources.getString(R.string.reader_time_left)}: $rounded h"
}

@Composable
private fun ReaderTopBar(
    title: String,
    pageText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pageText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    isLoading: Boolean,
    enableChapterNav: Boolean,
    pageIndex: Int,
    pageCount: Int,
    timeLeft: String?,
    enableSettings: Boolean,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
    ) {
        timeLeft?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
        LinearProgressIndicator(
            progress = if (pageCount > 0) (pageIndex + 1f) / pageCount else 0f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left cluster: prev chapter, jump top, prev page
            IconButton(onClick = onPrevChapter, enabled = enableChapterNav) {
                Icon(Icons.Filled.FastRewind, contentDescription = stringResource(R.string.reader_prev_chapter))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onScrollTop, enabled = !isLoading) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_jump_to_start),
                        modifier = Modifier.rotate(90f),
                    )
                }
                IconButton(onClick = onPrevPage, enabled = !isLoading) {
                    Icon(Icons.Filled.NavigateBefore, contentDescription = stringResource(R.string.reader_prev_page))
                }
            }
            // Center: settings
            IconButton(onClick = onOpenSettings, enabled = enableSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.reader_reader_settings))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onNextPage, enabled = !isLoading) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = stringResource(R.string.reader_next_page))
                }
                IconButton(onClick = onScrollBottom, enabled = !isLoading) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_jump_to_end),
                        modifier = Modifier.rotate(-90f),
                    )
                }
            }
            IconButton(onClick = onNextChapter, enabled = enableChapterNav) {
                Icon(Icons.Filled.FastForward, contentDescription = stringResource(R.string.reader_next_chapter))
            }
        }
    }
}

data class ReaderBottomBarState(
    val isLoading: Boolean,
    val enableChapterNav: Boolean,
    val pageIndex: Int,
    val pageCount: Int,
    val timeLeft: String?,
    val enableSettings: Boolean,
)

data class ReaderBottomBarCallbacks(
    val onScrollTop: () -> Unit,
    val onScrollBottom: () -> Unit,
    val onPrevPage: () -> Unit,
    val onNextPage: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onPrevChapter: () -> Unit,
    val onNextChapter: () -> Unit,
)

data class ReaderSettingsState(
    val selectedTab: ReaderSettingsTab,
    val fontSize: Float,
    val lineHeight: Float,
    val padding: Dp,
    val themeMode: ReaderThemeMode,
    val textAlign: TextAlign,
    val fontOptions: List<ReaderFontOption>,
    val fontFamilyId: String,
)

data class ReaderSettingsCallbacks(
    val onSelectTab: (ReaderSettingsTab) -> Unit,
    val onFontSizeChange: (Float) -> Unit,
    val onLineHeightChange: (Float) -> Unit,
    val onPaddingChange: (Dp) -> Unit,
    val onThemeChange: (ReaderThemeMode) -> Unit,
    val onTextAlignChange: (TextAlign) -> Unit,
    val onFontFamilyChange: (ReaderFontOption) -> Unit,
)

@Composable
private fun SettingsPanel(
    selectedTab: ReaderSettingsTab,
    onSelectTab: (ReaderSettingsTab) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    padding: Dp,
    onPaddingChange: (Dp) -> Unit,
    themeMode: ReaderThemeMode,
    onThemeChange: (ReaderThemeMode) -> Unit,
    textAlign: TextAlign,
    onTextAlignChange: (TextAlign) -> Unit,
    fontOptions: List<ReaderFontOption>,
    fontFamilyId: String,
    onFontFamilyChange: (ReaderFontOption) -> Unit,
) {
    val tabs =
        listOf(
            ReaderSettingsTab.Font to Icons.Filled.FormatSize,
            ReaderSettingsTab.Theme to Icons.Filled.Palette,
            ReaderSettingsTab.Navigation to Icons.Filled.SwapHoriz,
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.reader_reader_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEach { (tab, icon) ->
                val selected = tab == selectedTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { onSelectTab(tab) },
                ) {
                    Icon(
                        icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .height(2.dp)
                                .width(32.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(1.dp),
                                ),
                    )
                }
            }
        }
        when (selectedTab) {
            ReaderSettingsTab.Font ->
                FontSettings(
                    fontSize = fontSize,
                    onFontSizeChange = onFontSizeChange,
                    lineHeight = lineHeight,
                    onLineHeightChange = onLineHeightChange,
                    padding = padding,
                    onPaddingChange = onPaddingChange,
                    textAlign = textAlign,
                    onTextAlignChange = onTextAlignChange,
                    fontOptions = fontOptions,
                    fontFamilyId = fontFamilyId,
                    onFontFamilyChange = onFontFamilyChange,
                )
            ReaderSettingsTab.Theme ->
                ThemeSettings(
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                )
            ReaderSettingsTab.Navigation ->
                Text(
                    stringResource(R.string.general_nav_option_soon),
                    style = MaterialTheme.typography.bodyMedium,
                )
        }
    }
}

public enum class ReaderSettingsTab(
    val label: String,
) {
    Font("Font"),
    Theme("Theme"),
    Navigation("Navigation"),
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun FontSettings(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    padding: Dp,
    onPaddingChange: (Dp) -> Unit,
    textAlign: TextAlign,
    onTextAlignChange: (TextAlign) -> Unit,
    fontOptions: List<ReaderFontOption>,
    fontFamilyId: String,
    onFontFamilyChange: (ReaderFontOption) -> Unit,
) {
    var fontMenuExpanded by remember { mutableStateOf(false) }
    val selectedFontLabel = fontOptions.firstOrNull { it.id == fontFamilyId }?.label ?: stringResource(R.string.reader_font_selection)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.reader_text_settings), style = MaterialTheme.typography.titleSmall)
        StepperRow(stringResource(R.string.reader_font_size), valueText = "${fontSize.toInt()}") {
            onFontSizeChange((fontSize + it))
        }
        StepperRow(stringResource(R.string.reader_line_height), valueText = String.format("%.1f", lineHeight)) {
            onLineHeightChange((lineHeight + it * 0.1f))
        }
        StepperRow(stringResource(R.string.reader_padding), valueText = "${padding.value.toInt()} dp") {
            onPaddingChange((padding + it.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.reader_alignment), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            val alignItems =
                listOf(
                    TextAlign.Start to Icons.Filled.FormatAlignLeft,
                    TextAlign.Center to Icons.Filled.FormatAlignCenter,
                    TextAlign.End to Icons.Filled.FormatAlignRight,
                    TextAlign.Justify to Icons.Filled.FormatAlignJustify,
                )
            alignItems.forEach { (align, icon) ->
                val tint = if (align == textAlign) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                IconButton(onClick = { onTextAlignChange(align) }) {
                    Icon(icon, contentDescription = "Align $align", tint = tint)
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = fontMenuExpanded,
            onExpandedChange = { fontMenuExpanded = it },
        ) {
            TextField(
                value = selectedFontLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.reader_font_family)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontMenuExpanded) },
                colors =
                    ExposedDropdownMenuDefaults.textFieldColors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                modifier =
                    Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = fontMenuExpanded,
                onDismissRequest = { fontMenuExpanded = false },
            ) {
                fontOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            fontMenuExpanded = false
                            onFontFamilyChange(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSettings(
    themeMode: ReaderThemeMode,
    onThemeChange: (ReaderThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reader_theme_header), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            readerThemeOptions.forEach { option ->
                FilterChip(
                    selected = themeMode == option.mode,
                    onClick = { onThemeChange(option.mode) },
                    label = { Text(stringResource(option.labelRes)) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }
    }
}

private fun themeColorsFor(mode: ReaderThemeMode): Pair<String, String> =
    when (mode) {
        ReaderThemeMode.Light -> "#FFFFFF" to "#111111"
        ReaderThemeMode.Dark -> "#121212" to "#E0E0E0"
        ReaderThemeMode.DarkHighContrast -> "#0A0A0A" to "#FFFFFF"
        ReaderThemeMode.Sepia -> "#F6ECD9" to "#5B4636"
        ReaderThemeMode.SepiaHighContrast -> "#F6ECD9" to "#000000"
        ReaderThemeMode.Gray -> "#222222" to "#C8C8C8"
    }

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onDelta: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = { onDelta(-1f) },
            ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.general_decrease)) }
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onDelta(1f) }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.general_increase)) }
        }
    }
}

private fun pageText(
    idx: Int,
    count: Int,
    pageTitle: String?,
    context: Context,
): String {
    val base = if (count > 0) "${context.resources.getString(R.string.general_page)} ${idx + 1} / $count" else ""
    return if (!pageTitle.isNullOrBlank()) {
        listOf(base, pageTitle).filter { it.isNotBlank() }.joinToString(" – ")
    } else {
        base
    }
}

@Composable
internal fun ReaderWebView(
    html: String,
    fontSize: Float,
    lineHeight: Float,
    paddingPx: Float,
    fontOption: ReaderFontOption,
    textAlignCss: String,
    themeMode: ReaderThemeMode,
    serverUrl: String?,
    apiKey: String?,
    onToggleOverlay: () -> Unit,
    pendingScrollY: Int?,
    onConsumePendingScroll: () -> Unit = {},
    onWebViewReady: (WebView) -> Unit = {},
    onSwipeNext: () -> Unit = {},
    onSwipePrev: () -> Unit = {},
    pendingScrollId: String? = null,
    onConsumeScrollId: () -> Unit = {},
    onScrollIdle: (String?) -> Unit = {},
) {
    if (html.isBlank()) return
    var lastLoadedHash by remember { mutableStateOf<String?>(null) }
    val contentHash = remember(html) { html.hashCode().toString() }
    val enriched =
        if (apiKey != null) {
            html.replace(Regex("(book-resources\\?[^\"'>]+)")) { match ->
                val value = match.value
                if (value.contains("apiKey=")) value else "$value&apiKey=$apiKey"
            }
        } else {
            html
        }
    val paddingTopPx = paddingPx / 2f
    val fontFaceCss =
        fontOption.assetPath?.let {
            """
        @font-face {
            font-family: '${fontOption.cssFamily}';
            src: url('$it');
            font-weight: 100 900;
            font-style: normal;
        }
        """
        } ?: ""
    val (bgColor, textColor) = themeColorsFor(themeMode)
    val themeCss =
        """
        html, body {
            background: $bgColor !important;
            color: $textColor !important;
        }
        .book-content, .book-content * {
            color: $textColor !important;
        }
        a { color: $textColor !important; }
        """.trimIndent()
    val cssBlock =
        """
        $fontFaceCss
        $themeCss
        .kavita-scale-width, .kavita-scale-width img,
        .cover_image img, .image_full img, .pc img {
            max-width: 100%;
            height: auto;
            display: block;
            margin: 0 auto;
        }
        a { text-decoration: none; color: inherit; pointer-events: none; }
        h1, h2, h3, h4, h5, h6 { text-decoration: none; }
        body, .book-content {
            font-size: ${fontSize}px !important;
            line-height: $lineHeight !important;
            text-align: $textAlignCss !important;
            font-family: '${fontOption.cssFamily}' !important;
            padding-left: ${paddingPx}px !important;
            padding-right: ${paddingPx}px !important;
            padding-top: ${paddingTopPx}px !important;
            padding-bottom: ${paddingTopPx}px !important;
        }
        """.trimIndent()
    val toLoad =
        if (enriched.contains("<head", ignoreCase = true)) {
            enriched.replaceFirst(Regex("(?i)<head>"), "<head><style>$cssBlock</style>")
        } else {
            "<html><head><style>$cssBlock</style></head><body>$enriched</body></html>"
        }
    val scrollHandler = remember { Handler(Looper.getMainLooper()) }
    var scrollRunnable: Runnable? by remember { mutableStateOf(null) }
    val scrollIdJs =
        remember {
            """
        (function() {
            var raw = Array.prototype.slice.call(document.querySelectorAll('body *'));
            var elems = [];
            for (var i = 0; i < raw.length; i++) {
                var el = raw[i];
                var tag = el.tagName.toLowerCase();
                if (tag === 'script' || tag === 'style' || tag === 'head' || tag === 'meta') continue;
                var rect = el.getBoundingClientRect();
                var hasText = (el.innerText || '').trim().length > 0;
                if (rect.height < 2 && !hasText) continue;
                elems.push(el);
            }
            if (elems.length === 0) return null;
            var y = window.pageYOffset;
            var bestIdx = 0;
            var bestDist = 1e9;
            for (var i = 0; i < elems.length; i++) {
                var el = elems[i];
                var top = el.getBoundingClientRect().top + window.pageYOffset;
                var dist = Math.abs(top - y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            var best = elems[bestIdx];
            if (!best) return null;
            if (best.id) return 'id(\"' + best.id + '\")';
            return 'idx:' + bestIdx;
        })();
        """
        }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val swipeThreshold = 120
            val swipeVelocityThreshold = 200
            val gestureDetector =
                GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            onToggleOverlay()
                            return true
                        }

                        override fun onFling(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            velocityX: Float,
                            velocityY: Float,
                        ): Boolean {
                            if (e1 == null) return false
                            val diffX = e2.x - e1.x
                            val diffY = e2.y - e1.y
                            if (abs(diffX) > abs(diffY) &&
                                abs(diffX) > swipeThreshold &&
                                abs(velocityX) > swipeVelocityThreshold
                            ) {
                                if (diffX < 0) onSwipeNext() else onSwipePrev()
                                return true
                            }
                            return false
                        }
                    },
                )
            WebView(context).apply {
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadsImagesAutomatically = true
                settings.domStorageEnabled = true
                settings.javaScriptEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            super.onPageFinished(view, url)
                            val y = pendingScrollY
                            if (view != null && y != null) {
                                view.post {
                                    view.scrollTo(0, y)
                                    view.postDelayed({ view.scrollTo(0, y) }, 50)
                                }
                                onConsumePendingScroll()
                            }
                        }
                    }
                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }
                setOnScrollChangeListener { _, _, _, _, _ ->
                    scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
                    scrollRunnable =
                        Runnable {
                            evaluateJavascript(scrollIdJs) { result ->
                                val cleaned = result?.removePrefix("\"")?.removeSuffix("\"")
                                val id = cleaned?.takeIf { it.isNotBlank() && it != "null" }
                                onScrollIdle(id)
                            }
                        }
                    scrollHandler.postDelayed(scrollRunnable!!, 300)
                }
                onWebViewReady(this)
            }
        },
        update = { webView ->
            webView.setBackgroundColor(Color.Transparent.toArgb())
            val styleJs =
                """
                (function() {
                    var css = `$cssBlock`;
                    var tag = document.getElementById('reader-style');
                    if (!tag) {
                        tag = document.createElement('style');
                        tag.id = 'reader-style';
                        document.head.appendChild(tag);
                    }
                    tag.innerHTML = css;
                })();
                """.trimIndent()
            if (lastLoadedHash != contentHash) {
                lastLoadedHash = contentHash
                webView.loadDataWithBaseURL(
                    serverUrl ?: "https://",
                    toLoad,
                    "text/html",
                    "UTF-8",
                    null,
                )
                pendingScrollId?.let { id ->
                    val isIdx = id.startsWith("idx:")
                    val targetJs =
                        if (isIdx) {
                            val idx = id.removePrefix("idx:").toIntOrNull() ?: -1
                            """
                            (function() {
                                var elems = document.querySelectorAll('body *');
                                if ($idx >= 0 && $idx < elems.length) {
                                    var el = elems[$idx];
                                    var top = el.getBoundingClientRect().top + window.pageYOffset;
                                    window.scrollTo(0, top);
                                    return true;
                                }
                                return false;
                            })();
                            """.trimIndent()
                        } else {
                            val clean =
                                id
                                    .removePrefix("id(\"")
                                    .removeSuffix("\")")
                                    .removePrefix("id('")
                                    .removeSuffix("')")
                            """
                            (function() {
                                var el = document.getElementById("$clean");
                                if (el) {
                                    var top = el.getBoundingClientRect().top + window.pageYOffset;
                                    window.scrollTo(0, top);
                                    return true;
                                }
                                return false;
                            })();
                            """.trimIndent()
                            // Todo: musí se to dělat přes js?
                        }
                    webView.postDelayed({
                        webView.evaluateJavascript(targetJs, null)
                        onConsumeScrollId()
                    }, 150)
                }
            } else {
                webView.evaluateJavascript(styleJs, null)
            }
            if (lastLoadedHash == contentHash) {
                pendingScrollY?.let { y ->
                    webView.post {
                        webView.scrollTo(0, y)
                    }
                    webView.postDelayed({ webView.scrollTo(0, y) }, 50)
                    onConsumePendingScroll()
                }
            }
        },
    )
}

@Composable
internal fun PdfPageViewer(
    pdfPath: String,
    currentPage: Int,
    onPageChanged: (Int, Int) -> Unit,
) {
    val file = remember(pdfPath) { File(pdfPath) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidthPx = remember(configuration.screenWidthDp) { with(density) { configuration.screenWidthDp.dp.roundToPx() } }
    val swipeThresholdPx = with(density) { 64.dp.toPx() }

    var pageIndex by remember { mutableStateOf(currentPage.coerceAtLeast(0)) }
    var pageCount by remember { mutableStateOf(0) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val rendererHolder = remember { mutableStateOf<Pair<PdfRenderer, ParcelFileDescriptor>?>(null) }

    LaunchedEffect(pdfPath) {
        rendererHolder.value?.let { (renderer, fd) ->
            renderer.close()
            fd.close()
        }
        if (file.exists()) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            rendererHolder.value = renderer to fd
            pageCount = renderer.pageCount
            pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(currentPage) {
        pageIndex = currentPage.coerceAtLeast(0).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    DisposableEffect(rendererHolder.value) {
        val current = rendererHolder.value
        onDispose {
            current?.let { (renderer, fd) ->
                renderer.close()
                fd.close()
            }
        }
    }

    LaunchedEffect(pageIndex, rendererHolder.value) {
        val holder = rendererHolder.value ?: return@LaunchedEffect
        val (renderer, _) = holder
        if (renderer.pageCount == 0) return@LaunchedEffect
        val idx = pageIndex.coerceIn(0, renderer.pageCount - 1)
        withContext(Dispatchers.IO) {
            renderer.openPage(idx).use { page ->
                val scale = max(1f, targetWidthPx.toFloat() / page.width.toFloat())
                val targetWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap =
                    Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply { setScale(scale, scale) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                imageBitmap = bitmap.asImageBitmap()
            }
        }
        onPageChanged(idx, renderer.pageCount)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(pageIndex, pageCount) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (abs(totalDrag) > swipeThresholdPx) {
                                    if (totalDrag < 0 && pageIndex < pageCount - 1) {
                                        pageIndex += 1
                                    } else if (totalDrag > 0 && pageIndex > 0) {
                                        pageIndex -= 1
                                    }
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            when {
                imageBitmap != null -> {
                    val ratio = imageBitmap!!.width.toFloat() / imageBitmap!!.height.toFloat()
                    Image(
                        bitmap = imageBitmap!!,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio.coerceAtLeast(0.1f))
                                .background(Color.White),
                        contentScale = ContentScale.FillWidth,
                    )
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (pageIndex > 0) {
                        pageIndex -= 1
                    }
                },
                enabled = pageIndex > 0,
            ) {
                Icon(Icons.Filled.NavigateBefore, contentDescription = stringResource(R.string.general_prev))
            }
            Text(
                text = "${pageIndex + 1} / ${pageCount.coerceAtLeast(1)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = {
                    if (pageIndex < pageCount - 1) {
                        pageIndex += 1
                    }
                },
                enabled = pageIndex < pageCount - 1,
            ) {
                Icon(Icons.Filled.NavigateNext, contentDescription = stringResource(R.string.general_next))
            }
        }
    }
}
