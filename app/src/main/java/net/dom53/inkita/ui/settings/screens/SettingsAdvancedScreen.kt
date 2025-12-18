package net.dom53.inkita.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.download.DownloadManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.CollectionsRepositoryImpl
import net.dom53.inkita.data.repository.DownloadRepositoryImpl
import net.dom53.inkita.domain.model.Collection
import kotlin.math.max

@Suppress("LongMethod")
@Composable
fun SettingsAdvancedScreen(
    appPreferences: AppPreferences,
    cacheManager: CacheManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var globalCacheEnabled by remember { mutableStateOf(true) }
    var cacheEnabled by remember { mutableStateOf(false) }
    var browseCacheEnabled by remember { mutableStateOf(false) }
    var cacheTtlMinutes by remember { mutableStateOf(0) }
    var ttlInput by remember { mutableStateOf("0") }
    var useHours by remember { mutableStateOf(false) }
    var ttlFieldFocused by remember { mutableStateOf(false) }
    var prefetchInProgress by remember { mutableStateOf(true) }
    var prefetchWant by remember { mutableStateOf(false) }
    var prefetchCollections by remember { mutableStateOf(false) }
    var prefetchDetails by remember { mutableStateOf(false) }
    var prefetchAllowMetered by remember { mutableStateOf(false) }
    var prefetchAllowLowBattery by remember { mutableStateOf(false) }
    var prefetchCollectionsAll by remember { mutableStateOf(true) }
    var selectedCollectionIds by remember { mutableStateOf(setOf<Int>()) }
    var collectionOptions by remember { mutableStateOf<List<Collection>>(emptyList()) }
    var clearTarget by remember { mutableStateOf(ClearTarget.All) }
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var cacheSizeBytes by remember { mutableStateOf(0L) }
    var statsDialog by remember { mutableStateOf<String?>(null) }
    val collectionsRepo = remember { CollectionsRepositoryImpl(context, appPreferences) }
    val downloadRepo =
        remember {
            val db = InkitaDatabase.getInstance(context)
            val manager = DownloadManager(context)
            DownloadRepositoryImpl(db.downloadDao(), manager, context.applicationContext)
        }

    LaunchedEffect(Unit) {
        cacheSizeBytes = cacheManager.getCacheSizeBytes()
    }

    LaunchedEffect(Unit) {
        appPreferences.cacheEnabledFlow.collectLatest { globalCacheEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.libraryCacheEnabledFlow.collectLatest { cacheEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.browseCacheEnabledFlow.collectLatest { browseCacheEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.cacheRefreshTtlMinutesFlow.collectLatest {
            cacheTtlMinutes = it
            if (!ttlFieldFocused) {
                ttlInput = minutesToDisplay(it, useHours)
            }
        }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchInProgressFlow.collectLatest { prefetchInProgress = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchWantFlow.collectLatest { prefetchWant = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchCollectionsFlow.collectLatest { prefetchCollections = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchDetailsFlow.collectLatest { prefetchDetails = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchAllowMeteredFlow.collectLatest { prefetchAllowMetered = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchAllowLowBatteryFlow.collectLatest { prefetchAllowLowBattery = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchCollectionsAllFlow.collectLatest { prefetchCollectionsAll = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.prefetchCollectionIdsFlow.collectLatest { ids -> selectedCollectionIds = ids.toSet() }
    }
    LaunchedEffect(Unit) {
        val cached = runCatching { appPreferences.loadCachedCollections() }.getOrDefault(emptyList())
        if (cached.isNotEmpty()) {
            collectionOptions = cached
        }
        runCatching { collectionsRepo.getCollections() }
            .onSuccess { fresh ->
                if (fresh.isNotEmpty()) {
                    collectionOptions = fresh
                    appPreferences.saveCollectionsCache(fresh)
                }
            }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
        }
        Text(
            text = stringResource(R.string.settings_item_advanced),
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_global_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_global_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = globalCacheEnabled,
                onCheckedChange = { checked ->
                    globalCacheEnabled = checked
                    scope.launch {
                        appPreferences.setCacheEnabled(checked)
                        if (!checked) {
                            cacheEnabled = false
                            browseCacheEnabled = false
                            appPreferences.setLibraryCacheEnabled(false)
                            appPreferences.setBrowseCacheEnabled(false)
                        }
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = ttlInput,
                onValueChange = { value ->
                    ttlInput = value
                    val normalized = value.replace(',', '.')
                    val parsed = normalized.toFloatOrNull()
                    if (parsed != null) {
                        val minutes = if (useHours) (parsed * 60f).toInt() else parsed.toInt()
                        cacheTtlMinutes = minutes
                        scope.launch { appPreferences.setCacheRefreshTtlMinutes(minutes) }
                    }
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            ttlFieldFocused = state.isFocused
                            if (!state.isFocused) {
                                ttlInput = minutesToDisplay(cacheTtlMinutes, useHours)
                            }
                        },
                label = {
                    Text(
                        text =
                            stringResource(
                                R.string.settings_cache_refresh_label,
                                if (useHours) stringResource(R.string.settings_units_hours) else stringResource(R.string.settings_units_minutes),
                            ),
                    )
                },
                supportingText = { Text(stringResource(R.string.settings_cache_refresh_hint)) },
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    useHours = !useHours
                    ttlInput = minutesToDisplay(cacheTtlMinutes, useHours)
                },
            ) {
                Text(if (useHours) stringResource(R.string.settings_units_hours) else stringResource(R.string.settings_units_minutes))
            }
        }

        Divider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_library_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_library_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = cacheEnabled,
                onCheckedChange = { checked ->
                    cacheEnabled = checked
                    scope.launch { appPreferences.setLibraryCacheEnabled(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_browse_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_browse_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = browseCacheEnabled,
                onCheckedChange = { checked ->
                    browseCacheEnabled = checked
                    scope.launch { appPreferences.setBrowseCacheEnabled(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Divider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_inprogress_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_inprogress_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefetchInProgress,
                onCheckedChange = { checked ->
                    prefetchInProgress = checked
                    scope.launch { appPreferences.setPrefetchInProgress(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_want_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_want_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefetchWant,
                onCheckedChange = { checked ->
                    prefetchWant = checked
                    scope.launch { appPreferences.setPrefetchWant(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_collections_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_collections_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefetchCollections,
                onCheckedChange = { checked ->
                    prefetchCollections = checked
                    scope.launch { appPreferences.setPrefetchCollections(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }
        if (prefetchCollections) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_collections_scope_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            prefetchCollectionsAll = true
                            scope.launch { appPreferences.setPrefetchCollectionsAll(true) }
                        },
                        enabled = globalCacheEnabled,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (prefetchCollectionsAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor =
                                    if (prefetchCollectionsAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Text(stringResource(R.string.settings_cache_prefetch_collections_all))
                    }
                    OutlinedButton(
                        onClick = {
                            prefetchCollectionsAll = false
                            scope.launch { appPreferences.setPrefetchCollectionsAll(false) }
                        },
                        enabled = globalCacheEnabled,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (!prefetchCollectionsAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor =
                                    if (!prefetchCollectionsAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Text(stringResource(R.string.settings_cache_prefetch_collections_selected))
                    }
                }
                if (!prefetchCollectionsAll) {
                    if (collectionOptions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_cache_prefetch_collections_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        collectionOptions.forEach { collection ->
                            val checked = selectedCollectionIds.contains(collection.id)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            val next = selectedCollectionIds.toMutableSet()
                                            if (isChecked) {
                                                next.add(collection.id)
                                            } else {
                                                next.remove(collection.id)
                                            }
                                            selectedCollectionIds = next
                                            scope.launch { appPreferences.setPrefetchCollectionIds(next.toList()) }
                                        },
                                        enabled = globalCacheEnabled,
                                    )
                                    Text(collection.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_cache_prefetch_collections_selected_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_details_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_prefetch_details_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefetchDetails,
                onCheckedChange = { checked ->
                    prefetchDetails = checked
                    scope.launch { appPreferences.setPrefetchDetails(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_prefetch_allow_metered_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_prefetch_allow_metered_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefetchAllowMetered,
                onCheckedChange = { checked ->
                    prefetchAllowMetered = checked
                    scope.launch { appPreferences.setPrefetchAllowMetered(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_prefetch_allow_low_battery_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_prefetch_allow_low_battery_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefetchAllowLowBattery,
                onCheckedChange = { checked ->
                    prefetchAllowLowBattery = checked
                    scope.launch { appPreferences.setPrefetchAllowLowBattery(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Divider()

        Text(
            text =
                stringResource(
                    R.string.settings_cache_size_label,
                    bytesToMb(cacheSizeBytes),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = {
                showClearDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear cache")
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text(stringResource(R.string.settings_cache_clear_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_cache_clear_message))
                        ClearOptionRow(
                            label = stringResource(R.string.settings_cache_clear_all),
                            selected = clearTarget == ClearTarget.All,
                        ) { clearTarget = ClearTarget.All }
                        ClearOptionRow(
                            label = stringResource(R.string.settings_cache_clear_db),
                            selected = clearTarget == ClearTarget.Database,
                        ) { clearTarget = ClearTarget.Database }
                        ClearOptionRow(
                            label = stringResource(R.string.settings_cache_clear_details),
                            selected = clearTarget == ClearTarget.Details,
                        ) { clearTarget = ClearTarget.Details }
                        ClearOptionRow(
                            label = stringResource(R.string.settings_cache_clear_thumbnails),
                            selected = clearTarget == ClearTarget.Thumbnails,
                        ) { clearTarget = ClearTarget.Thumbnails }
                        ClearOptionRow(
                            label = stringResource(R.string.advanced_cache_clear_downloaded_pages),
                            selected = clearTarget == ClearTarget.DownloadedPages,
                        ) { clearTarget = ClearTarget.DownloadedPages }
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        onClick = {
                            showClearDialog = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    when (clearTarget) {
                                        ClearTarget.All -> cacheManager.clearAllCache()
                                        ClearTarget.Database -> cacheManager.clearDatabase()
                                        ClearTarget.Details -> cacheManager.clearDetails()
                                        ClearTarget.Thumbnails -> cacheManager.clearThumbnails()
                                        ClearTarget.DownloadedPages -> downloadRepo.clearAllDownloads()
                                    }
                                    appPreferences.setLastLibraryRefresh(0)
                                    appPreferences.setLastBrowseRefresh(0)
                                }
                                cacheSizeBytes = 0L
                                android.widget.Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.settings_cache_cleared_toast),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.settings_cache_clear_confirm))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showClearDialog = false }) {
                        Text(stringResource(R.string.general_back))
                    }
                },
            )
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    val stats = cacheManager.getCacheStats()
                    val body =
                        buildString {
                            appendLine(context.getString(R.string.advanced_cache_stats_series, stats.seriesCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_tabs, stats.tabRefs))
                            appendLine(context.getString(R.string.advanced_cache_stats_browse, stats.browseRefs))
                            appendLine(context.getString(R.string.advanced_cache_stats_details, stats.detailsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_volumes, stats.volumesCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_chapters, stats.chaptersCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_db_size, bytesToMb(stats.dbBytes)))
                            appendLine(context.getString(R.string.advanced_cache_stats_thumbnails, bytesToMb(stats.thumbnailsBytes)))
                            appendLine(context.getString(R.string.advanced_cache_stats_last_library, formatTs(stats.lastLibraryRefresh)))
                            appendLine(context.getString(R.string.advanced_cache_stats_last_browse, formatTs(stats.lastBrowseRefresh)))
                        }
                    statsDialog = body
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_cache_stats_button))
        }

        statsDialog?.let { body ->
            AlertDialog(
                onDismissRequest = { statsDialog = null },
                title = { Text(stringResource(R.string.settings_cache_stats_title)) },
                text = { Text(body) },
                confirmButton = {
                    OutlinedButton(onClick = { statsDialog = null }) {
                        Text(stringResource(R.string.general_back))
                    }
                },
            )
        }
    }
}

private fun bytesToMb(bytes: Long): String {
    val mb = max(bytes, 0L) / 1_000_000.0
    return String.format("%.1f MB", mb)
}

private enum class ClearTarget {
    All,
    Database,
    Details,
    Thumbnails,
    DownloadedPages,
}

@Composable
private fun ClearOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun minutesToDisplay(
    minutes: Int,
    useHours: Boolean,
): String {
    if (useHours) {
        val hours = minutes / 60.0
        return String.format("%.1f", hours).trimEnd('0').trimEnd('.')
    }
    return minutes.toString()
}

private fun formatTs(ts: Long): String {
    if (ts <= 0) return "--"
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}
