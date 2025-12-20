package net.dom53.inkita.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.domain.model.Library
import net.dom53.inkita.domain.repository.LibraryRepository
import net.dom53.inkita.domain.repository.SeriesRepository

data class HomeSeriesItem(
    val id: Int,
    val title: String,
)

enum class LibraryV2Section {
    Home,
    WantToRead,
}

data class LibraryV2UiState(
    val libraries: List<Library> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val onDeck: List<HomeSeriesItem> = emptyList(),
    val recentlyUpdated: List<HomeSeriesItem> = emptyList(),
    val recentlyAdded: List<HomeSeriesItem> = emptyList(),
    val isHomeLoading: Boolean = true,
    val homeError: String? = null,
    val wantToRead: List<net.dom53.inkita.domain.model.Series> = emptyList(),
    val isWantToReadLoading: Boolean = false,
    val wantToReadError: String? = null,
    val selectedSection: LibraryV2Section = LibraryV2Section.Home,
)

class LibraryV2ViewModel(
    private val libraryRepository: LibraryRepository,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryV2UiState())
    val state: StateFlow<LibraryV2UiState> = _state

    init {
        loadLibraries()
        loadHome()
    }

    fun selectSection(section: LibraryV2Section) {
        _state.update { it.copy(selectedSection = section) }
        if (section == LibraryV2Section.WantToRead) {
            ensureWantToRead()
        }
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = runCatching { libraryRepository.getLibraries() }
            _state.update {
                it.copy(
                    libraries = result.getOrDefault(emptyList()),
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun loadHome() {
        viewModelScope.launch {
            _state.update { it.copy(isHomeLoading = true, homeError = null) }
            val onDeckResult = runCatching { seriesRepository.getOnDeckSeries(1, 20, 0) }
            val updatedResult = runCatching { seriesRepository.getRecentlyUpdatedSeries(1, 20) }
            val addedResult = runCatching { seriesRepository.getRecentlyAddedSeries(1, 20) }

            val onDeck =
                onDeckResult.getOrDefault(emptyList()).map { series ->
                    val title = series.name.ifBlank { "Series ${series.id}" }
                    HomeSeriesItem(id = series.id, title = title)
                }
            val recentlyUpdated =
                updatedResult.getOrDefault(emptyList()).mapNotNull { item ->
                    val id = item.seriesId ?: return@mapNotNull null
                    val title =
                        item.seriesName?.ifBlank { null }
                            ?: item.title?.ifBlank { null }
                            ?: "Series $id"
                    HomeSeriesItem(id = id, title = title)
                }
            val recentlyAdded =
                addedResult.getOrDefault(emptyList()).map { series ->
                    val title = series.name.ifBlank { "Series ${series.id}" }
                    HomeSeriesItem(id = series.id, title = title)
                }

            val error =
                onDeckResult.exceptionOrNull()
                    ?: updatedResult.exceptionOrNull()
                    ?: addedResult.exceptionOrNull()
            _state.update {
                it.copy(
                    onDeck = onDeck,
                    recentlyUpdated = recentlyUpdated,
                    recentlyAdded = recentlyAdded,
                    isHomeLoading = false,
                    homeError = error?.message,
                )
            }
        }
    }

    private fun ensureWantToRead() {
        val current = _state.value
        if (current.isWantToReadLoading || current.wantToRead.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isWantToReadLoading = true, wantToReadError = null) }
            val result = runCatching { seriesRepository.getWantToReadSeries(1, 50) }
            _state.update {
                it.copy(
                    wantToRead = result.getOrDefault(emptyList()),
                    isWantToReadLoading = false,
                    wantToReadError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            libraryRepository: LibraryRepository,
            seriesRepository: SeriesRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryV2ViewModel(libraryRepository, seriesRepository) as T
                }
            }
    }
}
