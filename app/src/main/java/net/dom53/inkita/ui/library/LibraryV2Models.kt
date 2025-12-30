package net.dom53.inkita.ui.library

import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Library
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.model.ReadingList
import net.dom53.inkita.ui.common.DownloadState

data class HomeSeriesItem(
    val id: Int,
    val title: String,
    val localThumbPath: String? = null,
)

enum class LibraryV2Section {
    Home,
    WantToRead,
    Collections,
    ReadingList,
    BrowsePeople,
    LibrarySeries,
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
    val collections: List<Collection> = emptyList(),
    val isCollectionsLoading: Boolean = false,
    val collectionsError: String? = null,
    val selectedCollectionId: Int? = null,
    val selectedCollectionName: String? = null,
    val collectionSeries: List<net.dom53.inkita.domain.model.Series> = emptyList(),
    val isCollectionSeriesLoading: Boolean = false,
    val collectionSeriesError: String? = null,
    val readingLists: List<ReadingList> = emptyList(),
    val isReadingListsLoading: Boolean = false,
    val readingListsError: String? = null,
    val people: List<Person> = emptyList(),
    val isPeopleLoading: Boolean = false,
    val peopleError: String? = null,
    val peoplePage: Int = 1,
    val canLoadMorePeople: Boolean = true,
    val isPeopleLoadingMore: Boolean = false,
    val selectedLibraryId: Int? = null,
    val selectedLibraryName: String? = null,
    val librarySeries: List<net.dom53.inkita.domain.model.Series> = emptyList(),
    val isLibrarySeriesLoading: Boolean = false,
    val isLibrarySeriesLoadingMore: Boolean = false,
    val librarySeriesError: String? = null,
    val librarySeriesPage: Int = 1,
    val canLoadMoreLibrarySeries: Boolean = true,
    val libraryAccessDenied: Boolean = false,
    val selectedSection: LibraryV2Section = LibraryV2Section.Home,
    val downloadStates: Map<Int, DownloadState> = emptyMap(),
)
