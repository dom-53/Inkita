package net.dom53.inkita

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import net.dom53.inkita.ui.library.LibraryScreen
import net.dom53.inkita.ui.library.LibraryV2Screen
import net.dom53.inkita.ui.navigation.MainScreen
import net.dom53.inkita.ui.reader.ReaderScreen
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
fun InkitaApp(
    initialLanguage: String,
    appPreferences: AppPreferences,
    libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readingListRepository: net.dom53.inkita.domain.repository.ReadingListRepository,
    readerRepository: net.dom53.inkita.domain.repository.ReaderRepository,
    cacheManager: CacheManager,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifPromptShown by appPreferences.notificationsPromptShownFlow.collectAsState(initial = false)
    val notificationsEnabled =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    val showNotifDialog = remember { mutableStateOf(false) }
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
                    if (currentRoute in mainRoutes) {
                        NavigationBar {
                            MainScreen.items.forEach { screen ->
                                NavigationBarItem(
                                    selected = currentRoute == screen.route,
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
                    startDestination = MainScreen.Library.route,
                    modifier = Modifier.padding(innerPadding),
                ) {
                composable(MainScreen.Library.route) {
                    LibraryScreen(
                        seriesRepository = seriesRepository,
                        collectionsRepository = collectionsRepository,
                        appPreferences = appPreferences,
                        cacheManager = cacheManager,
                        onOpenSeries = { seriesId ->
                            navController.navigate("series/$seriesId")
                        },
                    )
                }
                composable(MainScreen.LibraryV2.route) {
                    LibraryV2Screen(
                        libraryRepository = libraryRepository,
                        seriesRepository = seriesRepository,
                        collectionsRepository = collectionsRepository,
                        readingListRepository = readingListRepository,
                        appPreferences = appPreferences,
                        onOpenSeries = { seriesId ->
                            navController.navigate("series/$seriesId")
                        },
                    )
                }
                composable(MainScreen.Updates.route) { UpdatesScreen() }
                    composable(MainScreen.History.route) {
                        HistoryScreen(
                            appPreferences = appPreferences,
                        )
                    }
                    composable(MainScreen.Browse.route) {
                        BrowseScreen(
                            seriesRepository = seriesRepository,
                            appPreferences = appPreferences,
                            cacheManager = cacheManager,
                            onOpenSeries = { seriesId ->
                                navController.navigate("series/$seriesId")
                            },
                        )
                    }
                    composable(MainScreen.Downloads.route) {
                        val ctx = LocalContext.current
                        val vm = remember { DownloadQueueViewModelFactory.create(ctx) }
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
                                .getStateFlow<net.dom53.inkita.ui.reader.ReaderReturn?>(
                                    "reader_return",
                                    null,
                                ).collectAsState(initial = null)
                    SeriesDetailScreenV2(
                        seriesId = seriesId,
                        onBack = { navController.popBackStack() },
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
                                    defaultValue = 0
                                },
                            ),
                    ) { entry ->
                        val chId = entry.arguments?.getInt("chapterId") ?: return@composable
                        val page = entry.arguments?.getInt("page")
                        val sid = entry.arguments?.getInt("sid")?.takeIf { it != 0 }
                        val vid = entry.arguments?.getInt("vid")?.takeIf { it != 0 }
                        val fmt = entry.arguments?.getInt("fmt")?.takeIf { it != 0 }
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
                                    net.dom53.inkita.ui.reader.ReaderReturn(
                                        volumeId = vIdBack ?: 0,
                                        page = pIdx,
                                    ),
                                )
                                navController.popBackStack()
                            },
                            onNavigateToChapter = { targetChapter, targetPage, targetSid, targetVid ->
                                val nextSid = targetSid ?: sid ?: 0
                                val nextVid = targetVid ?: vid ?: 0
                                navController.navigate("reader/$targetChapter?page=${targetPage ?: 0}&sid=$nextSid&vid=$nextVid&fmt=${fmt ?: 0}") {
                                    popUpTo("reader/$chId?page=${page ?: 0}&sid=${sid ?: 0}&vid=${vid ?: 0}&fmt=${fmt ?: 0}") { inclusive = true }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
