package net.dom53.inkita.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

enum class DownloadState {
    None,
    Partial,
    Complete,
}

@Composable
internal fun DownloadStateBadge(
    state: DownloadState,
    modifier: Modifier = Modifier,
) {
    if (state == DownloadState.None) return
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                ).padding(4.dp),
    ) {
        Icon(
            imageVector =
                if (state == DownloadState.Complete) {
                    Icons.Filled.DownloadDone
                } else {
                    Icons.Filled.Downloading
                },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}
