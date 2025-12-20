package net.dom53.inkita.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.ui.graphics.vector.ImageVector

sealed class MainScreen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    object Library : MainScreen("library", "Library", Icons.Filled.Book)

    object LibraryV2 : MainScreen("library_v2", "Library V2", Icons.Filled.LibraryBooks)

    object Updates : MainScreen("updates", "Updates", Icons.Filled.Update)

    object History : MainScreen("history", "History", Icons.Filled.History)

    object Browse : MainScreen("browse", "Browse", Icons.Filled.Language)

    object Downloads : MainScreen("downloads", "Downloads", Icons.Filled.Download)

    object Settings : MainScreen("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val items = listOf(Library, LibraryV2, Updates, History, Browse, Downloads, Settings)
    }
}
