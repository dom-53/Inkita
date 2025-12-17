package net.dom53.inkita

import android.app.Activity
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import net.dom53.inkita.ui.navigation.MainScreen
import net.dom53.inkita.ui.reader.ReaderScreen
import net.dom53.inkita.ui.seriesdetail.SeriesDetailScreen
import net.dom53.inkita.ui.settings.SettingsScreen
import net.dom53.inkita.ui.theme.InkitaTheme
import net.dom53.inkita.ui.updates.UpdatesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val components = StartupManager.init(applicationContext)
        val appPreferences = components.preferences

        setContent {
            InkitaApp(
                appPreferences = components.preferences,
                libraryRepository = components.libraryRepository,
                authRepository = components.authRepository,
                seriesRepository = components.seriesRepository,
                collectionsRepository = components.collectionsRepository,
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

@Composable
fun InkitaApp(
    appPreferences: AppPreferences,
    libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readerRepository: net.dom53.inkita.domain.repository.ReaderRepository,
    cacheManager: CacheManager,
) {
    val appTheme by appPreferences.appThemeFlow.collectAsState(initial = AppTheme.System)
    val darkTheme =
        when (appTheme) {
            AppTheme.System -> isSystemInDarkTheme()
            AppTheme.Light -> false
            AppTheme.Dark -> true
        }
    val view = LocalView.current

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val mainRoutes = MainScreen.items.map { it.route }
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", userId = 0),
    )

    InkitaTheme(darkTheme = darkTheme, dynamicColor = false) {
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
                    SeriesDetailScreen(
                        seriesId = seriesId,
                        seriesRepository = seriesRepository,
                        appPreferences = appPreferences,
                        collectionsRepository = collectionsRepository,
                        readerRepository = readerRepository,
                        readerReturn = readerReturn.value,
                        onConsumeReaderReturn = { entry.savedStateHandle["reader_return"] = null },
                        onBack = { navController.popBackStack() },
                        onOpenSeries = { targetId ->
                            navController.navigate("series/$targetId")
                        },
                        onOpenDownloads = {
                            navController.navigate(MainScreen.Downloads.route)
                        },
                        onOpenReader = { chapterId, page, sId, vId, fmt ->
                            navController.navigate("reader/$chapterId?page=${page ?: 0}&sid=$sId&vid=$vId&fmt=${fmt ?: 0}")
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
