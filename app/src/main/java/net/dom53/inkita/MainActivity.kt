package net.dom53.inkita

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.ImageLoader
import coil.compose.LocalImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.startup.StartupManager
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.AppTheme
import net.dom53.inkita.domain.repository.AuthRepository
import net.dom53.inkita.domain.repository.CollectionsRepository
import net.dom53.inkita.domain.repository.LibraryRepository
import net.dom53.inkita.domain.repository.SeriesRepository
import net.dom53.inkita.ui.browse.BrowseScreen
import net.dom53.inkita.ui.download.DownloadQueueScreen
import net.dom53.inkita.ui.download.DownloadQueueViewModelFactory
import net.dom53.inkita.ui.history.HistoryScreen
import net.dom53.inkita.ui.library.LibraryV2Screen
import net.dom53.inkita.ui.navigation.MainScreen
import net.dom53.inkita.ui.reader.model.ReaderReturn
import net.dom53.inkita.ui.reader.screen.ReaderScreen
import net.dom53.inkita.ui.seriesdetail.SeriesDetailScreenV2
import net.dom53.inkita.ui.settings.SettingsScreen
import net.dom53.inkita.ui.theme.InkitaTheme
import net.dom53.inkita.ui.updates.UpdatesScreen
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val components = StartupManager.init(applicationContext)
        val appPreferences = components.preferences
        // Apply saved locale before composing UI so strings use correct language on first draw.
        val storedLanguage = runBlocking { appPreferences.appLanguageFlow.first() }
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val localeTag = appLocales.toLanguageTags()
        val initialLanguage =
            when {
                localeTag.isNotBlank() -> localeTag
                storedLanguage.isNotBlank() -> storedLanguage
                else -> "system"
            }
        if (storedLanguage != initialLanguage) {
            runBlocking { appPreferences.setAppLanguage(initialLanguage) }
        }
        applyLocale(initialLanguage)

        setContent {
            InkitaApp(
                initialLanguage = initialLanguage,
                appPreferences = components.preferences,
                libraryRepository = components.libraryRepository,
                authRepository = components.authRepository,
                seriesRepository = components.seriesRepository,
                collectionsRepository = components.collectionsRepository,
                readingListRepository = components.readingListRepository,
                personRepository = components.personRepository,
                readerRepository = components.readerRepository,
                cacheManager = components.cacheManager,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-schedule one-off jobs (e.g., progress sync) when app returns to foreground.
        StartupManager.onResume(applicationContext)
    }
}

private const val IMPORTANT_INFO_HEADER =
    "✅ Image API key was added — please fill it in Kavita Settings."

private val IMPORTANT_INFO_ADDED =
    listOf(
        "Downloads V2: default Download API strategy for series/volume/chapter archives",
        "Downloads V2: fallback DownloadApiStrategyV2 for unsupported formats",
        "Downloads V2: normalized on-disk layout for series/volumes/chapters/specials",
        "Downloads V2: image/archive chapter downloads stored as CBZ",
        "Downloads V2: PDF downloads now show in the queue",
        "Downloads V2: centralized download state for series/volume/chapter badges",
        "Downloads V2: queue items show series/volume/chapter labels",
        "Library/Browse: download badges on series covers",
        "Settings: toggle to show/hide download badges",
        "Settings: download stats dialog",
        "Series Detail V2: tree view of downloaded files",
        "Reader: offline Image/Archive reading from downloaded CBZ",
        "Reader: basic Image/Archive reader (image pages + swipe)",
        "Reader: image reader modes (LTR/RTL/Vertical)",
        "Reader: PDF temp files cleaned on exit/startup unless downloaded",
        "Series Detail V2: chapters list swipe read/unread + download",
        "Series Detail V2: tap genre/tag to open Browse with filter",
        "Series Detail V2: collection click opens Library V2 collection",
    )

private val IMPORTANT_INFO_CHANGED =
    listOf(
        "Downloads V2: PDF items open with correct MIME type",
        "Downloads V2: queue/completed rows wrap titles cleanly",
        "Reader: EpubReaderViewModel/PdfReaderViewModel split into separate files",
        "Reader: navigation jumps across chapters at edges",
        "Reader: next chapter prompts to mark current as read when leaving early",
        "Reader: image/archive routing preserves format id",
        "UI: rounded corners aligned across Library/Series/Volume covers",
        "Series Detail: legacy screen/viewmodel removed",
        "Downloads: legacy V1 download manager/DB removed",
        "Library: legacy screen/viewmodel and cache APIs removed",
    )

private val IMPORTANT_INFO_FIXED =
    listOf(
        "Series Detail V2 cache defaults to enabled to prevent offline cache misses",
        "History list no longer crashes due to duplicate LazyColumn keys",
        "Bottom bar stays visible when opening Library/Browse via filters",
    )

private const val FORCE_SHOW_IMPORTANT_INFO = false

private fun applyLocale(languageTag: String) {
    val locales =
        if (languageTag.isBlank() || languageTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@Composable
private fun InfoSection(
    title: String,
    items: List<String>,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
    Spacer(modifier = Modifier.size(6.dp))
    items.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("•")
            Text(
                text = item,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
    }
}

@Suppress("UnusedPrivateProperty")
@Composable
fun InkitaApp(
    initialLanguage: String,
    appPreferences: AppPreferences,
    libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readingListRepository: net.dom53.inkita.domain.repository.ReadingListRepository,
    personRepository: net.dom53.inkita.domain.repository.PersonRepository,
    readerRepository: net.dom53.inkita.domain.repository.ReaderRepository,
    cacheManager: CacheManager,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifPromptShown by appPreferences.notificationsPromptShownFlow.collectAsState(initial = false)
    val notificationsEnabled =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    val showNotifDialog = remember { mutableStateOf(false) }
    val lastImportantInfoVersion by appPreferences.importantInfoVersionFlow.collectAsState(initial = -1)
    val showImportantInfoDialog = remember { mutableStateOf(false) }
    val appLanguage by appPreferences.appLanguageFlow.collectAsState(initial = initialLanguage)
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Regardless of result, don't ask again.
            scope.launch { appPreferences.setNotificationsPromptShown(true) }
            showNotifDialog.value = false
        }

    LaunchedEffect(notifPromptShown, notificationsEnabled) {
        if (!notifPromptShown && !notificationsEnabled) {
            showNotifDialog.value = true
        } else if (!notifPromptShown && notificationsEnabled) {
            appPreferences.setNotificationsPromptShown(true)
        }
    }

    LaunchedEffect(lastImportantInfoVersion) {
        if (FORCE_SHOW_IMPORTANT_INFO) {
            showImportantInfoDialog.value = true
            return@LaunchedEffect
        }
        if (lastImportantInfoVersion < 0) return@LaunchedEffect
        showImportantInfoDialog.value = BuildConfig.VERSION_CODE > lastImportantInfoVersion
    }

    LaunchedEffect(appLanguage) {
        applyLocale(appLanguage)
    }

    val appTheme by appPreferences.appThemeFlow.collectAsState(initial = AppTheme.System)
    val darkTheme =
        when (appTheme) {
            AppTheme.System -> isSystemInDarkTheme()
            AppTheme.Light -> false
            AppTheme.Dark -> true
        }
    val maxThumbnailsParallel by appPreferences.maxThumbnailsParallelFlow.collectAsState(initial = 4)
    val view = LocalView.current

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val mainRoutes = MainScreen.items.map { it.route }
    val isMainRoute = MainScreen.items.any { item -> currentRoute?.startsWith(item.route) == true }
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )

    InkitaTheme(darkTheme = darkTheme, dynamicColor = false) {
        if (showNotifDialog.value && !notifPromptShown && !notificationsEnabled) {
            AlertDialog(
                onDismissRequest = {
                    showNotifDialog.value = false
                    scope.launch { appPreferences.setNotificationsPromptShown(true) }
                },
                title = { Text(text = context.getString(R.string.notifications_prompt_title)) },
                text = { Text(context.getString(R.string.notifications_prompt_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotifDialog.value = false
                            scope.launch { appPreferences.setNotificationsPromptShown(true) }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent =
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                context.startActivity(intent)
                            }
                        },
                    ) { Text(context.getString(R.string.notifications_prompt_accept)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotifDialog.value = false
                            scope.launch { appPreferences.setNotificationsPromptShown(true) }
                        },
                    ) { Text(context.getString(R.string.notifications_prompt_dismiss)) }
                },
            )
        }
        if (showImportantInfoDialog.value) {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            AlertDialog(
                onDismissRequest = {},
                title = { Text(text = context.getString(R.string.update_info_title)) },
                text = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = IMPORTANT_INFO_HEADER,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = "📌 ${pkg.versionName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            InfoSection(
                                title = "✨ Added",
                                items = IMPORTANT_INFO_ADDED,
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            InfoSection(
                                title = "🔁 Changed",
                                items = IMPORTANT_INFO_CHANGED,
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            InfoSection(
                                title = "🛠️ Fixed",
                                items = IMPORTANT_INFO_FIXED,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showImportantInfoDialog.value = false
                            scope.launch { appPreferences.setImportantInfoVersion(BuildConfig.VERSION_CODE) }
                        },
                    ) { Text(context.getString(R.string.general_close)) }
                },
            )
        }

        val imageLoader =
            remember(maxThumbnailsParallel) {
                val dispatcher =
                    Dispatcher().apply {
                        maxRequestsPerHost = maxThumbnailsParallel
                        maxRequests = (maxThumbnailsParallel * 8).coerceIn(16, 32)
                    }
                val okHttpClient =
                    OkHttpClient
                        .Builder()
                        .dispatcher(dispatcher)
                        .build()
                ImageLoader
                    .Builder(context)
                    .okHttpClient(okHttpClient)
                    .build()
            }

        CompositionLocalProvider(LocalImageLoader provides imageLoader) {
            val surfaceColor = MaterialTheme.colorScheme.surface
            SideEffect {
                val window: Window? = (view.context as? Activity)?.window
                if (window != null) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    window.statusBarColor = surfaceColor.toArgb()
                }
            }
            Scaffold(
                bottomBar = {
                    if (isMainRoute) {
                        NavigationBar {
                            MainScreen.items.forEach { screen ->
                                val selected = currentRoute?.startsWith(screen.route) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        ),
                                    label = null,
                                    alwaysShowLabel = false,
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = MainScreen.LibraryV2.route,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable(
                        route = "${MainScreen.LibraryV2.route}?collectionId={collectionId}&collectionName={collectionName}",
                        arguments =
                            listOf(
                                navArgument("collectionId") {
                                    type = NavType.IntType
                                    defaultValue = -1
                                },
                                navArgument("collectionName") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                    ) {
                        val collectionIdArg = it.arguments?.getInt("collectionId")?.takeIf { id -> id >= 0 }
                        val collectionNameArg = it.arguments?.getString("collectionName")?.takeIf { name -> name.isNotBlank() }
                        LibraryV2Screen(
                            libraryRepository = libraryRepository,
                            seriesRepository = seriesRepository,
                            collectionsRepository = collectionsRepository,
                            readingListRepository = readingListRepository,
                            personRepository = personRepository,
                            cacheManager = cacheManager,
                            appPreferences = appPreferences,
                            onOpenSeries = { seriesId ->
                                navController.navigate("series/$seriesId")
                            },
                            initialCollectionId = collectionIdArg,
                            initialCollectionName = collectionNameArg,
                        )
                    }
                    composable(MainScreen.Updates.route) { UpdatesScreen() }
                    composable(MainScreen.History.route) {
                        HistoryScreen(
                            appPreferences = appPreferences,
                        )
                    }
                    composable(
                        route = "${MainScreen.Browse.route}?genreId={genreId}&tagId={tagId}&genreName={genreName}&tagName={tagName}",
                        arguments =
                            listOf(
                                navArgument("genreId") {
                                    type = NavType.IntType
                                    defaultValue = -1
                                },
                                navArgument("tagId") {
                                    type = NavType.IntType
                                    defaultValue = -1
                                },
                                navArgument("genreName") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("tagName") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                    ) { entry ->
                        val genreIdArg = entry.arguments?.getInt("genreId")?.takeIf { it >= 0 }
                        val tagIdArg = entry.arguments?.getInt("tagId")?.takeIf { it >= 0 }
                        val genreNameArg = entry.arguments?.getString("genreName")?.takeIf { it.isNotBlank() }
                        val tagNameArg = entry.arguments?.getString("tagName")?.takeIf { it.isNotBlank() }
                        BrowseScreen(
                            seriesRepository = seriesRepository,
                            appPreferences = appPreferences,
                            cacheManager = cacheManager,
                            onOpenSeries = { seriesId ->
                                navController.navigate("series/$seriesId")
                            },
                            initialGenreId = genreIdArg,
                            initialGenreName = genreNameArg,
                            initialTagId = tagIdArg,
                            initialTagName = tagNameArg,
                        )
                    }
                    composable(MainScreen.Downloads.route) {
                        val ctx = LocalContext.current
                        val vm = remember { DownloadQueueViewModelFactory.create(ctx, cacheManager) }
                        DownloadQueueScreen(viewModel = vm)
                    }
                    composable(MainScreen.Settings.route) {
                        SettingsScreen(
                            appPreferences = appPreferences,
                            authRepository = authRepository,
                            cacheManager = cacheManager,
                            onOpenAbout = { navController.navigate("settings/about") },
                        )
                    }
                    composable(
                        route = "series/{seriesId}",
                        arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
                    ) { entry ->
                        val seriesId = entry.arguments?.getInt("seriesId") ?: return@composable
                        val readerReturn =
                            entry.savedStateHandle
                                .getStateFlow<ReaderReturn?>(
                                    "reader_return",
                                    null,
                                ).collectAsState(initial = null)
                        val refreshSignal =
                            entry.savedStateHandle
                                .getStateFlow<Boolean>(
                                    "series_refresh",
                                    false,
                                ).collectAsState(initial = false)
                        SeriesDetailScreenV2(
                            seriesId = seriesId,
                            appPreferences = appPreferences,
                            collectionsRepository = collectionsRepository,
                            readerRepository = readerRepository,
                            cacheManager = cacheManager,
                            onOpenReader = { chapterId, page, sid, vid, fmt ->
                                navController.navigate("reader/$chapterId?page=$page&sid=$sid&vid=$vid&fmt=${fmt ?: -1}")
                            },
                            onOpenVolume = { volumeId ->
                                navController.navigate("volume/$volumeId")
                            },
                            onOpenSeries = { id ->
                                navController.navigate("series/$id")
                            },
                            onOpenBrowseGenre = { id, name ->
                                val encoded = Uri.encode(name)
                                navController.navigate("${MainScreen.Browse.route}?genreId=$id&genreName=$encoded")
                            },
                            onOpenBrowseTag = { id, name ->
                                val encoded = Uri.encode(name)
                                navController.navigate("${MainScreen.Browse.route}?tagId=$id&tagName=$encoded")
                            },
                            onOpenCollection = { id, name ->
                                val encoded = Uri.encode(name)
                                navController.navigate("${MainScreen.LibraryV2.route}?collectionId=$id&collectionName=$encoded")
                            },
                            readerReturn = readerReturn.value,
                            onConsumeReaderReturn = { entry.savedStateHandle["reader_return"] = null },
                            refreshSignal = refreshSignal.value,
                            onConsumeRefreshSignal = { entry.savedStateHandle["series_refresh"] = false },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "volume/{volumeId}",
                        arguments = listOf(navArgument("volumeId") { type = NavType.IntType }),
                    ) { entry ->
                        val volumeId = entry.arguments?.getInt("volumeId") ?: return@composable
                        val readerReturn =
                            entry.savedStateHandle
                                .getStateFlow<ReaderReturn?>(
                                    "reader_return",
                                    null,
                                ).collectAsState(initial = null)
                        net.dom53.inkita.ui.seriesdetail.VolumeDetailScreenV2(
                            volumeId = volumeId,
                            appPreferences = appPreferences,
                            readerRepository = readerRepository,
                            readerReturn = readerReturn.value,
                            onConsumeReaderReturn = {
                                entry.savedStateHandle["reader_return"] = null
                                navController.previousBackStackEntry?.savedStateHandle?.set("series_refresh", true)
                            },
                            onOpenReader = { chapterId, page, sid, vid, fmt ->
                                navController.navigate("reader/$chapterId?page=$page&sid=$sid&vid=$vid&fmt=${fmt ?: 0}")
                            },
                            onBack = {
                                navController.previousBackStackEntry?.savedStateHandle?.set("series_refresh", true)
                                navController.popBackStack()
                            },
                        )
                    }
                    composable("settings/about") {
                        net.dom53.inkita.ui.settings.screens.SettingsAboutScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "reader/{chapterId}?page={page}&sid={sid}&vid={vid}&fmt={fmt}",
                        arguments =
                            listOf(
                                navArgument("chapterId") { type = NavType.IntType },
                                navArgument("page") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                                navArgument("sid") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                                navArgument("vid") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                },
                                navArgument("fmt") {
                                    type = NavType.IntType
                                    defaultValue = -1
                                },
                            ),
                    ) { entry ->
                        val chId = entry.arguments?.getInt("chapterId") ?: return@composable
                        val page = entry.arguments?.getInt("page")
                        val sid = entry.arguments?.getInt("sid")?.takeIf { it != 0 }
                        val vid = entry.arguments?.getInt("vid")?.takeIf { it != 0 }
                        val fmt = entry.arguments?.getInt("fmt")?.takeIf { it != -1 }
                        ReaderScreen(
                            chapterId = chId,
                            initialPage = page,
                            readerRepository = readerRepository,
                            appPreferences = appPreferences,
                            seriesId = sid,
                            volumeId = vid,
                            serverUrl = config.serverUrl,
                            apiKey = config.apiKey,
                            formatId = fmt,
                            onBack = { _, pIdx, _, vIdBack ->
                                navController.previousBackStackEntry?.savedStateHandle?.set(
                                    "reader_return",
                                    ReaderReturn(
                                        volumeId = vIdBack ?: 0,
                                        page = pIdx,
                                    ),
                                )
                                navController.popBackStack()
                            },
                            onNavigateToChapter = { targetChapter, targetPage, targetSid, targetVid ->
                                val nextSid = targetSid ?: sid ?: 0
                                val nextVid = targetVid ?: vid ?: 0
                                val fmtValue = fmt ?: -1
                                navController.navigate("reader/$targetChapter?page=${targetPage ?: 0}&sid=$nextSid&vid=$nextVid&fmt=$fmtValue") {
                                    popUpTo("reader/$chId?page=${page ?: 0}&sid=${sid ?: 0}&vid=${vid ?: 0}&fmt=$fmtValue") { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
