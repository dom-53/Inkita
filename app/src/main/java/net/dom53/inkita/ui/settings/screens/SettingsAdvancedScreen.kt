package net.dom53.inkita.ui.settings.screens

import android.content.Intent
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.material3.Slider
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.R
import net.dom53.inkita.core.cache.CacheManager
import net.dom53.inkita.core.download.DownloadManager
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.CollectionsRepositoryImpl
import net.dom53.inkita.data.repository.DownloadRepositoryImpl
import net.dom53.inkita.domain.model.Collection
import kotlin.math.max
import kotlin.math.roundToInt

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
    var libraryCacheHomeEnabled by remember { mutableStateOf(false) }
    var libraryCacheWantEnabled by remember { mutableStateOf(false) }
    var libraryCacheCollectionsEnabled by remember { mutableStateOf(false) }
    var libraryCacheReadingListsEnabled by remember { mutableStateOf(false) }
    var libraryCacheBrowsePeopleEnabled by remember { mutableStateOf(false) }
    var libraryCacheDetailsEnabled by remember { mutableStateOf(false) }
    var cacheAlwaysRefresh by remember { mutableStateOf(false) }
    var cacheStaleHours by remember { mutableStateOf(24) }
    var cacheStaleInput by remember { mutableStateOf("24") }
    var cacheStaleFocused by remember { mutableStateOf(false) }
    var debugToasts by remember { mutableStateOf(false) }
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
    var exportingShare by remember { mutableStateOf(false) }
    var exportingSave by remember { mutableStateOf(false) }
    var verboseLogging by remember { mutableStateOf(false) }
    var browsePageSize by remember { mutableStateOf(25) }
    var maxThumbnailsParallel by remember { mutableStateOf(4) }
    var disableBrowseThumbnails by remember { mutableStateOf(false) }
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
        appPreferences.cacheAlwaysRefreshFlow.collectLatest { cacheAlwaysRefresh = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.debugToastsFlow.collectLatest { debugToasts = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.cacheStaleHoursFlow.collectLatest {
            cacheStaleHours = it
            if (!cacheStaleFocused) {
                cacheStaleInput = it.toString()
            }
        }
    }
    LaunchedEffect(Unit) {
        appPreferences.libraryCacheHomeFlow.collectLatest { libraryCacheHomeEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.libraryCacheWantToReadFlow.collectLatest { libraryCacheWantEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.libraryCacheCollectionsFlow.collectLatest { libraryCacheCollectionsEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.libraryCacheReadingListsFlow.collectLatest { libraryCacheReadingListsEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.libraryCacheBrowsePeopleFlow.collectLatest { libraryCacheBrowsePeopleEnabled = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.libraryCacheDetailsFlow.collectLatest { libraryCacheDetailsEnabled = it }
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
        appPreferences.verboseLoggingFlow.collectLatest { verboseLogging = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.browsePageSizeFlow.collectLatest { browsePageSize = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.maxThumbnailsParallelFlow.collectLatest { maxThumbnailsParallel = it }
    }
    LaunchedEffect(Unit) {
        appPreferences.disableBrowseThumbnailsFlow.collectLatest { disableBrowseThumbnails = it }
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
                    text = stringResource(R.string.settings_debug_toasts_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_debug_toasts_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = debugToasts,
                onCheckedChange = { checked ->
                    debugToasts = checked
                    scope.launch { appPreferences.setDebugToasts(checked) }
                },
            )
        }

        Text(
            text = stringResource(R.string.settings_advanced_section_global_cache),
            style = MaterialTheme.typography.titleMedium,
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cache_always_refresh_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_cache_always_refresh_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = cacheAlwaysRefresh,
                onCheckedChange = { checked ->
                    cacheAlwaysRefresh = checked
                    scope.launch { appPreferences.setCacheAlwaysRefresh(checked) }
                },
                enabled = globalCacheEnabled,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = cacheStaleInput,
                onValueChange = { value ->
                    cacheStaleInput = value
                    val parsed = value.toIntOrNull()
                    if (parsed != null) {
                        cacheStaleHours = parsed.coerceIn(1, 168)
                        scope.launch { appPreferences.setCacheStaleHours(cacheStaleHours) }
                    }
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            cacheStaleFocused = state.isFocused
                            if (!state.isFocused) {
                                cacheStaleInput = cacheStaleHours.toString()
                            }
                        },
                label = { Text(stringResource(R.string.settings_cache_stale_hours_label)) },
                supportingText = { Text(stringResource(R.string.settings_cache_stale_hours_hint)) },
                singleLine = true,
                enabled = globalCacheEnabled,
            )
            Text(
                text = stringResource(R.string.settings_cache_units_hours),
                style = MaterialTheme.typography.bodyMedium,
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

        Text(
            text = stringResource(R.string.settings_advanced_section_library_cache),
            style = MaterialTheme.typography.titleMedium,
        )
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

        if (cacheEnabled) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_cache_library_sections_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CacheToggleRow(
                    title = stringResource(R.string.settings_cache_library_home_title),
                    subtitle = stringResource(R.string.settings_cache_library_home_subtitle),
                    checked = libraryCacheHomeEnabled,
                    enabled = globalCacheEnabled,
                ) { checked ->
                    libraryCacheHomeEnabled = checked
                    scope.launch { appPreferences.setLibraryCacheHomeEnabled(checked) }
                }
                CacheToggleRow(
                    title = stringResource(R.string.settings_cache_library_want_title),
                    subtitle = stringResource(R.string.settings_cache_library_want_subtitle),
                    checked = libraryCacheWantEnabled,
                    enabled = globalCacheEnabled,
                ) { checked ->
                    libraryCacheWantEnabled = checked
                    scope.launch { appPreferences.setLibraryCacheWantToReadEnabled(checked) }
                }
                CacheToggleRow(
                    title = stringResource(R.string.settings_cache_library_collections_title),
                    subtitle = stringResource(R.string.settings_cache_library_collections_subtitle),
                    checked = libraryCacheCollectionsEnabled,
                    enabled = globalCacheEnabled,
                ) { checked ->
                    libraryCacheCollectionsEnabled = checked
                    scope.launch { appPreferences.setLibraryCacheCollectionsEnabled(checked) }
                }
                CacheToggleRow(
                    title = stringResource(R.string.settings_cache_library_reading_lists_title),
                    subtitle = stringResource(R.string.settings_cache_library_reading_lists_subtitle),
                    checked = libraryCacheReadingListsEnabled,
                    enabled = globalCacheEnabled,
                ) { checked ->
                    libraryCacheReadingListsEnabled = checked
                    scope.launch { appPreferences.setLibraryCacheReadingListsEnabled(checked) }
                }
                CacheToggleRow(
                    title = stringResource(R.string.settings_cache_library_browse_people_title),
                    subtitle = stringResource(R.string.settings_cache_library_browse_people_subtitle),
                    checked = libraryCacheBrowsePeopleEnabled,
                    enabled = globalCacheEnabled,
                ) { checked ->
                    libraryCacheBrowsePeopleEnabled = checked
                    scope.launch { appPreferences.setLibraryCacheBrowsePeopleEnabled(checked) }
                }
                CacheToggleRow(
                    title = stringResource(R.string.settings_cache_library_details_title),
                    subtitle = stringResource(R.string.settings_cache_library_details_subtitle),
                    checked = libraryCacheDetailsEnabled,
                    enabled = globalCacheEnabled,
                ) { checked ->
                    libraryCacheDetailsEnabled = checked
                    scope.launch { appPreferences.setLibraryCacheDetailsEnabled(checked) }
                }
            }
        }

        Divider()

        Text(
            text = stringResource(R.string.settings_advanced_section_browse_cache),
            style = MaterialTheme.typography.titleMedium,
        )
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_browse_page_size_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_browse_page_size_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = browsePageSize.toFloat(),
                onValueChange = { value ->
                    val stepped = (value / 5f).toInt() * 5
                    browsePageSize = stepped.coerceIn(10, 50)
                },
                onValueChangeFinished = {
                    scope.launch { appPreferences.setBrowsePageSize(browsePageSize) }
                },
                valueRange = 10f..50f,
                steps = 8,
                modifier = Modifier.weight(1f),
            )
            Text(browsePageSize.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_browse_thumb_parallel_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_browse_thumb_parallel_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = maxThumbnailsParallel.toFloat(),
                onValueChange = { value ->
                    maxThumbnailsParallel = value.roundToInt().coerceIn(2, 6)
                },
                onValueChangeFinished = {
                    scope.launch { appPreferences.setMaxThumbnailsParallel(maxThumbnailsParallel) }
                },
                valueRange = 2f..6f,
                steps = 3,
                modifier = Modifier.weight(1f),
            )
            Text(
                maxThumbnailsParallel.toString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_browse_thumbnails_off_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_browse_thumbnails_off_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = disableBrowseThumbnails,
                onCheckedChange = { checked ->
                    disableBrowseThumbnails = checked
                    scope.launch { appPreferences.setDisableBrowseThumbnails(checked) }
                },
            )
        }

        Divider()

        Text(
            text = stringResource(R.string.settings_advanced_section_prefetch),
            style = MaterialTheme.typography.titleMedium,
        )
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
            text = stringResource(R.string.settings_advanced_section_cache_maintenance),
            style = MaterialTheme.typography.titleMedium,
        )
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
                            appendLine(context.getString(R.string.advanced_cache_stats_series_refs, stats.seriesListRefsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_collections, stats.collectionsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_collection_refs, stats.collectionRefsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_reading_lists, stats.readingListsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_reading_list_refs, stats.readingListRefsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_people, stats.peopleCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_person_refs, stats.personRefsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_details, stats.detailsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_related_refs, stats.relatedRefsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_volumes, stats.volumesCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_series_volume_refs, stats.seriesVolumeRefsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_chapters, stats.chaptersCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_volume_chapter_refs, stats.volumeChapterRefsCount))
                            appendLine(context.getString(R.string.advanced_cache_stats_db_size, bytesToMb(stats.dbBytes)))
                            appendLine(context.getString(R.string.advanced_cache_stats_thumbnails_count, stats.thumbnailsCount))
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

        Divider()

        Text(
            text = stringResource(R.string.advanced_logs_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.advanced_logs_verbose_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.advanced_logs_verbose_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = verboseLogging,
                onCheckedChange = { checked ->
                    verboseLogging = checked
                    scope.launch { appPreferences.setVerboseLogging(checked) }
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (exportingShare) return@OutlinedButton
                        exportingShare = true
                        scope.launch {
                            val snapshot = withContext(Dispatchers.IO) { buildConfigSnapshot(context, appPreferences) }
                            val zip =
                                withContext(Dispatchers.IO) {
                                    LoggingManager.exportLogsForShare(
                                        context,
                                        extras = mapOf("config.txt" to snapshot),
                                    )
                                }
                            exportingShare = false
                            if (zip == null) {
                                Toast.makeText(context, R.string.advanced_logs_none, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            runCatching {
                                val uri =
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        zip,
                                    )
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        context.getString(R.string.advanced_logs_share_chooser),
                                    ),
                                )
                            }.onFailure {
                                Toast
                                    .makeText(context, R.string.advanced_logs_export_failed, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(),
                    enabled = !exportingShare,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.advanced_logs_export))
                }
                OutlinedButton(
                    onClick = {
                        if (exportingSave) return@OutlinedButton
                        exportingSave = true
                        scope.launch {
                            val snapshot = withContext(Dispatchers.IO) { buildConfigSnapshot(context, appPreferences) }
                            val saved =
                                withContext(Dispatchers.IO) {
                                    LoggingManager.saveLogsToDocuments(
                                        context,
                                        extras = mapOf("config.txt" to snapshot),
                                    )
                                }
                            exportingSave = false
                            if (saved == null) {
                                Toast.makeText(context, R.string.advanced_logs_export_failed, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.advanced_logs_saved, saved.absolutePath),
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(),
                    enabled = !exportingSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.advanced_logs_save))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                OutlinedButton(
                    onClick = {
                        LoggingManager.clearLogs()
                        Toast.makeText(context, R.string.advanced_logs_cleared, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.advanced_logs_clear))
                }
            }
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

@Composable
private fun CacheToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
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

@Suppress("MaxLineLength")
private suspend fun buildConfigSnapshot(
    context: android.content.Context,
    prefs: net.dom53.inkita.core.storage.AppPreferences,
): String {
    val pkgInfo =
        runCatching {
            val pm = context.packageManager
            val pkg = pm.getPackageInfo(context.packageName, 0)
            pkg
        }.getOrNull()
    val versionName = pkgInfo?.versionName ?: net.dom53.inkita.BuildConfig.VERSION_NAME
    val versionCode =
        pkgInfo?.longVersionCode ?: net.dom53.inkita.BuildConfig.VERSION_CODE
            .toLong()
    val sdk = android.os.Build.VERSION.SDK_INT
    val cfg = prefs.configFlow.first()
    val hostMasked =
        if (cfg.serverUrl.isBlank()) {
            "unset"
        } else {
            val visible = cfg.serverUrl.take(4)
            "$visible***"
        }
    val lang = prefs.appLanguageFlow.first()
    val theme = prefs.appThemeFlow.first()
    val offline = prefs.offlineModeFlow.first()
    val cacheGlobal = prefs.cacheEnabledFlow.first()
    val cacheLib = prefs.libraryCacheEnabledFlow.first()
    val cacheBrowse = prefs.browseCacheEnabledFlow.first()
    val cacheTtl = prefs.cacheRefreshTtlMinutesFlow.first()
    val prefetchPolicy = prefs.prefetchPolicy()
    val downloadConcurrent = prefs.downloadMaxConcurrentFlow.first()
    val downloadRetry = prefs.downloadRetryEnabledFlow.first()
    val downloadRetryMax = prefs.downloadRetryMaxAttemptsFlow.first()
    val preferOfflinePages = prefs.preferOfflinePagesFlow.first()
    val deleteAfter = prefs.deleteAfterMarkReadFlow.first()
    val deleteDepth = prefs.deleteAfterReadDepthFlow.first()
    val verbose = prefs.verboseLoggingFlow.first()

    return buildString {
        appendLine("App: ${context.packageName}")
        appendLine("Version: $versionName ($versionCode) SDK=$sdk")
        appendLine("ServerHost: $hostMasked")
        appendLine("Language: $lang")
        appendLine("Theme: $theme")
        appendLine("OfflineMode: $offline")
        appendLine("CacheEnabled: $cacheGlobal lib=$cacheLib browse=$cacheBrowse ttlMin=$cacheTtl")
        appendLine(
            "Prefetch: inProgress=${prefetchPolicy.inProgressEnabled} want=${prefetchPolicy.wantEnabled} " +
                "collections=${prefetchPolicy.collectionsEnabled} details=${prefetchPolicy.detailsEnabled} " +
                "meter=${prefetchPolicy.allowMetered} lowBatt=${prefetchPolicy.allowLowBattery} " +
                "all=${prefetchPolicy.collectionsAll} ids=${prefetchPolicy.collectionIds}",
        )
        appendLine(
            "Downloads: concurrent=$downloadConcurrent autoRetry=$downloadRetry maxRetry=$downloadRetryMax preferOffline=$preferOfflinePages deleteAfter=$deleteAfter depth=$deleteDepth",
        )
        appendLine("VerboseLogging: $verbose")
    }
}
