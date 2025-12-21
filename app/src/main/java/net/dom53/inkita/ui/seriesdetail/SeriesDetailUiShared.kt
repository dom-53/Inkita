package net.dom53.inkita.ui.seriesdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.AppConfig
import net.dom53.inkita.ui.common.chapterCoverUrl
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class SeriesDetailTab {
    Books,
    Chapters,
    Specials,
    Related,
    Recommendations,
    Reviews,
    ;

    val label: String
        get() =
            when (this) {
                Books -> "Books"
                Chapters -> "Chapters"
                Specials -> "Specials"
                Related -> "Related"
                Recommendations -> "Recommendations"
                Reviews -> "Reviews"
            }
}

internal data class TabItem(
    val id: SeriesDetailTab,
    val count: Int,
)

@Composable
internal fun SectionChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val labelText = if (count != null) "$label $count" else label
    AssistChip(
        onClick = onClick,
        label = { Text(labelText) },
        colors =
            androidx.compose.material3.AssistChipDefaults.assistChipColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                labelColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ),
    )
}

@Composable
internal fun ChapterListV2(
    chapters: List<net.dom53.inkita.data.api.dto.ChapterDto>,
    config: AppConfig,
    downloadStates: Map<Int, Boolean> = emptyMap(),
    onChapterClick: (net.dom53.inkita.data.api.dto.ChapterDto, Int) -> Unit = { _, _ -> },
) {
    if (chapters.isEmpty()) return
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        chapters.forEachIndexed { index, chapter ->
            val coverUrl = chapterCoverUrl(config, chapter.id)
            val title =
                chapter.titleName?.takeIf { it.isNotBlank() }
                    ?: chapter.title?.takeIf { it.isNotBlank() }
                    ?: chapter.range?.takeIf { it.isNotBlank() }
                    ?: "Chapter ${index + 1}"
            val pagesRead = chapter.pagesRead ?: 0
            val pagesTotal = chapter.pages ?: 0
            Column(
                modifier =
                    Modifier
                        .width(140.dp)
                        .clickable { onChapterClick(chapter, index) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    CoverImage(
                        coverUrl = coverUrl,
                        context = androidx.compose.ui.platform.LocalContext.current,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                    )
                    if (downloadStates[chapter.id] == true) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 6.dp, bottom = 10.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .padding(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (pagesRead == 0) {
                        Canvas(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f),
                        ) {
                            val sizePx = 26.dp.toPx()
                            val path =
                                Path().apply {
                                    moveTo(size.width - sizePx, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width, sizePx)
                                    close()
                                }
                            drawPath(
                                path = path,
                                color = Color(0xFFE91E63),
                            )
                        }
                    }
                    if (pagesTotal > 0 && pagesRead in 1 until pagesTotal) {
                        val progress =
                            (pagesRead.toFloat() / pagesTotal.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .align(Alignment.BottomStart)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .align(Alignment.BottomStart)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Ch. ${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun CoverImage(
    coverUrl: String?,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    if (coverUrl != null) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier =
                modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

internal fun formatHours(value: Float?): String? = value?.let { String.format(Locale.US, "%.1f h", it) }

internal fun formatHoursDouble(value: Double?): String? = value?.let { String.format(Locale.US, "%.1f h", it) }

internal fun formatHoursRange(
    min: Double?,
    max: Double?,
): String? {
    if (min == null && max == null) return null
    val minText = min?.let { String.format(Locale.US, "%.1f h", it) }
    val maxText = max?.let { String.format(Locale.US, "%.1f h", it) }
    return when {
        minText != null && maxText != null -> "$minText - $maxText"
        minText != null -> minText
        else -> maxText
    }
}

internal fun formatHoursRangeInt(
    min: Int?,
    max: Int?,
): String? = formatHoursRange(min?.toDouble(), max?.toDouble())

internal fun formatCount(value: Long): String =
    when {
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000L -> String.format(Locale.US, "%.0fk", value / 1_000f)
        else -> value.toString()
    }

internal fun formatDate(value: String): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    val parsed =
        runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).toLocalDate() }.getOrNull()
            ?: runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
    return parsed?.format(formatter) ?: value
}

internal fun formatYear(value: String): String? {
    val parsed =
        runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).toLocalDate() }.getOrNull()
            ?: runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
    return parsed?.year?.toString()
}

internal fun volumeNumberText(volume: net.dom53.inkita.data.api.dto.VolumeDto): String? {
    val volNumber = volume.minNumber ?: volume.maxNumber
    if (volNumber == null) return null
    return if (volNumber % 1f == 0f) volNumber.toInt().toString() else volNumber.toString()
}

@Composable
internal fun CollectionDialogV2(
    collections: List<net.dom53.inkita.domain.model.Collection>,
    isLoading: Boolean,
    error: String?,
    membership: Set<Int>,
    onDismiss: () -> Unit,
    onLoadCollections: () -> Unit,
    onToggle: (net.dom53.inkita.domain.model.Collection, Boolean) -> Unit,
    onCreateCollection: (String) -> Unit,
) {
    var newTitle by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.general_collections)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else {
                    if (collections.isEmpty()) {
                        Text(stringResource(R.string.series_detail_no_colls_reload))
                        TextButton(onClick = onLoadCollections) {
                            Text(stringResource(R.string.series_detail_load_colls))
                        }
                    } else {
                        collections.forEach { collection ->
                            val isInCollection = membership.contains(collection.id)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggle(collection, !isInCollection) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(collection.name)
                                androidx.compose.material3.Switch(
                                    checked = isInCollection,
                                    onCheckedChange = { onToggle(collection, it) },
                                )
                            }
                        }
                    }
                }

                androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text(stringResource(R.string.general_create_new_coll))
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.general_coll_name)) },
                )
                Button(
                    onClick = {
                        onCreateCollection(newTitle.trim())
                        newTitle = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newTitle.isNotBlank(),
                ) {
                    Text(stringResource(R.string.general_create_and_add))
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_close))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummarySectionV2(
    summary: String?,
    genres: List<String>,
    tags: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val summaryText = summary?.ifBlank { null }
        if (summaryText != null) {
            Text(
                text = summaryText,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onToggle) {
                    Text(
                        text = if (expanded) stringResource(R.string.general_less) else stringResource(R.string.general_more),
                    )
                }
            }
        }

        if (expanded) {
            ChipGroup(
                title = stringResource(R.string.general_genres),
                items = genres,
            )
            ChipGroup(
                title = stringResource(R.string.general_tags),
                items = tags,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipGroup(
    title: String,
    items: List<String>,
) {
    val trimmed = items.mapNotNull { it.trim().takeIf { titleText -> titleText.isNotEmpty() } }
    if (trimmed.isEmpty()) {
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            trimmed.forEach { item ->
                AssistChip(onClick = {}, label = { Text(item) })
            }
        }
    }
}
