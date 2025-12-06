package net.dom53.inkita.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.ReaderTimeLeft
import net.dom53.inkita.domain.repository.ReaderRepository
import java.io.File

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val content: String? = null,
    val fromOffline: Boolean = false,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val timeLeft: ReaderTimeLeft? = null,
    val bookInfo: ReaderBookInfo? = null,
    val bookScrollId: String? = null,
    val isPdf: Boolean = false,
    val pdfPath: String? = null,
)

class ReaderViewModel(
    private val chapterId: Int,
    private val initialPage: Int,
    private val readerRepository: ReaderRepository,
    private val seriesId: Int?,
    private val volumeId: Int?,
    private val format: Format?,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderUiState(pageIndex = initialPage))
    val state: StateFlow<ReaderUiState> = _state

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val savedProgress = runCatching { readerRepository.getProgress(chapterId) }.getOrNull()
            val info = runCatching { readerRepository.getBookInfo(chapterId) }.getOrNull()
            val totalPages = info?.pages ?: 0
            _state.update {
                it.copy(
                    isPdf = format == Format.Pdf,
                    bookInfo = info,
                    pageCount = totalPages,
                    bookScrollId = savedProgress?.bookScrollId ?: it.bookScrollId,
                    pageIndex = it.pageIndex, // keep initialPage
                )
            }
            if (format == Format.Pdf) {
                loadPdf()
                loadTimeLeft()
            } else {
                loadPage(initialPage)
                loadTimeLeft()
            }
        }
    }

    fun loadPage(index: Int) {
        viewModelScope.launch {
            val previous = _state.value
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { readerRepository.getPageResult(chapterId, index) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            content = result.html,
                            fromOffline = result.fromOffline,
                            pageIndex = index,
                            isLoading = false,
                            error = null,
                        )
                    }
                    sendProgress(index)
                    if (_state.value.bookInfo?.pages == null) {
                        val info = runCatching { readerRepository.getBookInfo(chapterId) }.getOrNull()
                        if (info != null) {
                            _state.update { st -> st.copy(bookInfo = info, pageCount = info.pages ?: st.pageCount) }
                        }
                    }
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Error loading page",
                            content = it.content ?: previous.content,
                            pageIndex = previous.pageIndex,
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun setPdfPage(
        pageIndex: Int,
        pageCount: Int,
    ) {
        _state.update { it.copy(pageIndex = pageIndex) }
        viewModelScope.launch {
            val info = _state.value.bookInfo
            val progress =
                ReaderProgress(
                    chapterId = chapterId,
                    page = pageIndex,
                    bookScrollId = null,
                    seriesId = seriesId,
                    volumeId = info?.volumeId ?: volumeId,
                    libraryId = info?.libraryId,
                )
            runCatching { readerRepository.setProgress(progress, totalPages = pageCount) }
            _state.update { it.copy(bookScrollId = null) }
        }
    }

    private fun sendProgress(
        pageIndex: Int,
        bookScrollId: String? = null,
    ) {
        viewModelScope.launch {
            val info = _state.value.bookInfo
            val progress =
                ReaderProgress(
                    chapterId = chapterId,
                    page = pageIndex,
                    bookScrollId = bookScrollId,
                    seriesId = seriesId,
                    volumeId = info?.volumeId ?: volumeId,
                    libraryId = info?.libraryId,
                )
            val totalPages = info?.pages
            runCatching { readerRepository.setProgress(progress, totalPages = totalPages) }
            _state.update { it.copy(bookScrollId = bookScrollId ?: it.bookScrollId) }
        }
    }

    private fun loadTimeLeft() {
        viewModelScope.launch {
            if (seriesId == null) return@launch
            val tl = runCatching<ReaderTimeLeft?> { readerRepository.getTimeLeft(seriesId, chapterId) }.getOrNull()
            _state.update { it.copy(timeLeft = tl) }
        }
    }

    private fun loadPdf() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val file: File? =
                runCatching { readerRepository.getPdfFile(chapterId) }
                    .getOrNull()
            if (file != null && file.exists()) {
                _state.update { it.copy(pdfPath = file.absolutePath, isLoading = false) }
            } else {
                _state.update { it.copy(isLoading = false, error = "PDF not available") }
            }
        }
    }

    suspend fun getNextChapter(): net.dom53.inkita.domain.model.ReaderChapterNav? {
        val info = _state.value.bookInfo
        val sid = info?.seriesId ?: seriesId ?: return null
        val vid = info?.volumeId ?: volumeId ?: return null
        val currentChapter = chapterId
        val nextNav = runCatching { readerRepository.getNextChapter(sid, vid, currentChapter) }.getOrNull() ?: return null
        val nextId = nextNav.chapterId ?: return null
        // fetch fresh book info for next chapter to capture correct volume
        val nextInfo = runCatching { readerRepository.getBookInfo(nextId) }.getOrNull()
        if (nextInfo != null) {
            val pages = nextInfo.pages ?: 0
            _state.update { it.copy(bookInfo = nextInfo, pageCount = pages, pageIndex = 0, content = null) }
        }
        return net.dom53.inkita.domain.model.ReaderChapterNav(
            seriesId = nextInfo?.seriesId ?: nextNav.seriesId ?: sid,
            volumeId = nextInfo?.volumeId ?: nextNav.volumeId ?: vid,
            chapterId = nextId,
            pagesRead = nextNav.pagesRead ?: 0,
        )
    }

    suspend fun getPreviousChapter(): net.dom53.inkita.domain.model.ReaderChapterNav? {
        val info = _state.value.bookInfo
        val sid = info?.seriesId ?: seriesId ?: return null
        val vid = info?.volumeId ?: volumeId ?: return null
        val currentChapter = chapterId
        val prevNav = runCatching { readerRepository.getPreviousChapter(sid, vid, currentChapter) }.getOrNull() ?: return null
        val prevId = prevNav.chapterId ?: return null
        val prevInfo = runCatching { readerRepository.getBookInfo(prevId) }.getOrNull()
        if (prevInfo != null) {
            val pages = prevInfo.pages ?: 0
            _state.update { it.copy(bookInfo = prevInfo, pageCount = pages, pageIndex = 0, content = null) }
        }
        return net.dom53.inkita.domain.model.ReaderChapterNav(
            seriesId = prevInfo?.seriesId ?: prevNav.seriesId ?: sid,
            volumeId = prevInfo?.volumeId ?: prevNav.volumeId ?: vid,
            chapterId = prevId,
            pagesRead = prevNav.pagesRead ?: 0,
        )
    }

    companion object {
        fun provideFactory(
            chapterId: Int,
            initialPage: Int,
            readerRepository: ReaderRepository,
            seriesId: Int?,
            volumeId: Int?,
            format: Format?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ReaderViewModel(chapterId, initialPage, readerRepository, seriesId, volumeId, format) as T
                }
            }
    }

    fun markProgressAtCurrentPage(bookScrollId: String? = null) {
        sendProgress(_state.value.pageIndex, bookScrollId)
    }

    suspend fun isPageDownloaded(pageIndex: Int): Boolean = runCatching { readerRepository.isPageDownloaded(chapterId, pageIndex) }.getOrDefault(false)

    fun setPdfPageIndex(
        index: Int,
        pageCount: Int,
    ) = setPdfPage(index, pageCount)
}
