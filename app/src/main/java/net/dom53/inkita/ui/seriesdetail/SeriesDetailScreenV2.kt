package net.dom53.inkita.ui.seriesdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import net.dom53.inkita.core.storage.AppPreferences

@Composable
fun SeriesDetailScreenV2(
    seriesId: Int,
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val viewModel: SeriesDetailViewModelV2 =
        viewModel(
            factory = SeriesDetailViewModelV2.provideFactory(seriesId, appPreferences),
        )
    val uiState = viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(uiState.value.showLoadedToast) {
        if (uiState.value.showLoadedToast) {
            Toast.makeText(context, "Detail data loaded", Toast.LENGTH_SHORT).show()
            viewModel.consumeLoadedToast()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null)
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Detail V2",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
