package net.dom53.inkita.ui.seriesdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

data class SeriesDetailUiStateV2(
    val isLoading: Boolean = true,
)

class SeriesDetailViewModelV2(
    val seriesId: Int,
) : ViewModel() {
    companion object {
        fun provideFactory(seriesId: Int): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SeriesDetailViewModelV2(seriesId) as T
                }
            }
    }
}
