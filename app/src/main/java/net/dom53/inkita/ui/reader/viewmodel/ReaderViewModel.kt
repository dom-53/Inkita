package net.dom53.inkita.ui.reader.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.domain.model.Format
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderChapterNav
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.ReaderTimeLeft
import net.dom53.inkita.domain.reader.BaseReader
import net.dom53.inkita.domain.reader.ReaderLoadResult

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val content: String? = null,
    val fromOffline: Boolean = false,
    val imageUrl: String? = null,
    val imageUrls: Map<Int, String> = emptyMap(),
    val webtoonChapters: List<WebtoonChapterUiState> = emptyList(),
    val activeChapterId: Int? = null,
    val previousImageUrl: String? = null,
    val nextImageUrl: String? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val timeLeft: ReaderTimeLeft? = null,
    val bookInfo: ReaderBookInfo? = null,
    val bookScrollId: String? = null,
    val isPdf: Boolean = false,
    val pdfPath: String? = null,
)

data class WebtoonChapterUiState(
    val chapterId: Int,
    val seriesId: Int?,
    val volumeId: Int?,
    val libraryId: Int?,
    val title: String?,
    val chapterNumber: String?,
    val bookTitle: String?,
    val pageCount: Int,
    val imageUrls: Map<Int, String> = emptyMap(),
    val isLoadingNextChapter: Boolean = false,
    val hasNextChapter: Boolean? = null,
)

@Suppress("VariableNaming", "ktlint:standard:backing-property-naming")
abstract class BaseReaderViewModel(
    protected val chapterId: Int,
    protected val initialPage: Int,
    protected val reader: BaseReader,
    protected val seriesId: Int?,
    protected val volumeId: Int?,
    private val anonymous: Boolean = false,
) : ViewModel() {
    protected val _state = MutableStateFlow(ReaderUiState(pageIndex = initialPage))
    val state: StateFlow<ReaderUiState> = _state

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, timeLeft = null) }
            val savedProgress = runCatching { reader.getProgress(chapterId) }.getOrNull()
            val info = runCatching { reader.getBookInfo(chapterId) }.getOrNull()
            val totalPages = info?.pages ?: 0
            _state.update {
                it.copy(
                    isPdf = reader.format == Format.Pdf,
                    bookInfo = info,
                    pageCount = totalPages,
                    bookScrollId = savedProgress?.bookScrollId ?: it.bookScrollId,
                    pageIndex = it.pageIndex, // keep initialPage
                )
            }
            loadInitialContent()
            loadTimeLeft()
        }
    }

    open fun loadPage(index: Int) {
        viewModelScope.launch {
            val previous = _state.value
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { reader.loadPage(chapterId, index) }
                .onSuccess { result ->
                    applyLoadResult(result, index)
                    updateProgress(index)
                    if (_state.value.bookInfo?.pages == null) {
                        val info = runCatching { reader.getBookInfo(chapterId) }.getOrNull()
                        if (info != null) {
                            _state.update { st -> st.copy(bookInfo = info, pageCount = info.pages ?: st.pageCount) }
                        }
                    }
                    onPageLoaded(index)
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

    protected open fun updateProgress(
        pageIndex: Int,
        bookScrollId: String? = null,
        totalPagesOverride: Int? = null,
    ) {
        if (anonymous) return
        viewModelScope.launch {
            val info = _state.value.bookInfo
            val currentChapterId = _state.value.activeChapterId ?: chapterId
            val progress =
                ReaderProgress(
                    chapterId = currentChapterId,
                    page = pageIndex,
                    bookScrollId = bookScrollId,
                    seriesId = seriesId,
                    volumeId = info?.volumeId ?: volumeId,
                    libraryId = info?.libraryId,
                )
            val totalPages = totalPagesOverride ?: info?.pages
            runCatching { reader.setProgress(progress, totalPages = totalPages) }
            _state.update { it.copy(bookScrollId = bookScrollId ?: it.bookScrollId) }
            loadTimeLeft()
        }
    }

    private fun loadTimeLeft() {
        viewModelScope.launch {
            if (seriesId == null) return@launch
            val currentChapterId = _state.value.activeChapterId ?: chapterId
            val tl = runCatching<ReaderTimeLeft?> { reader.getTimeLeft(seriesId, currentChapterId) }.getOrNull()
            _state.update { it.copy(timeLeft = tl) }
        }
    }

    private fun loadInitialContent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { reader.loadInitial(chapterId, initialPage) }
                .onSuccess { result ->
                    applyLoadResult(result, initialPage)
                    if (reader.format != Format.Pdf) {
                        updateProgress(initialPage)
                    }
                    onPageLoaded(initialPage)
                }.onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Error loading reader") }
                }
        }
    }

    suspend fun getNextChapter(): ReaderChapterNav? {
        val info = _state.value.bookInfo
        val sid = info?.seriesId ?: seriesId ?: return null
        val vid = info?.volumeId ?: volumeId ?: return null
        val currentChapter = _state.value.activeChapterId ?: chapterId
        val nextNav = runCatching { reader.getNextChapter(sid, vid, currentChapter) }.getOrNull() ?: return null
        val nextId = nextNav.chapterId ?: return null
        // fetch fresh book info for next chapter to capture correct volume
        val nextInfo = runCatching { reader.getBookInfo(nextId) }.getOrNull()
        if (nextInfo != null) {
            val pages = nextInfo.pages ?: 0
            _state.update { it.copy(bookInfo = nextInfo, pageCount = pages, pageIndex = 0, content = null) }
        }
        return ReaderChapterNav(
            seriesId = nextInfo?.seriesId ?: nextNav.seriesId ?: sid,
            volumeId = nextInfo?.volumeId ?: nextNav.volumeId ?: vid,
            chapterId = nextId,
            pagesRead = nextNav.pagesRead ?: 0,
        )
    }

    suspend fun getPreviousChapter(): ReaderChapterNav? {
        val info = _state.value.bookInfo
        val sid = info?.seriesId ?: seriesId ?: return null
        val vid = info?.volumeId ?: volumeId ?: return null
        val currentChapter = _state.value.activeChapterId ?: chapterId
        val prevNav = runCatching { reader.getPreviousChapter(sid, vid, currentChapter) }.getOrNull() ?: return null
        val prevId = prevNav.chapterId ?: return null
        val prevInfo = runCatching { reader.getBookInfo(prevId) }.getOrNull()
        if (prevInfo != null) {
            val pages = prevInfo.pages ?: 0
            _state.update { it.copy(bookInfo = prevInfo, pageCount = pages, pageIndex = 0, content = null) }
        }
        return ReaderChapterNav(
            seriesId = prevInfo?.seriesId ?: prevNav.seriesId ?: sid,
            volumeId = prevInfo?.volumeId ?: prevNav.volumeId ?: vid,
            chapterId = prevId,
            pagesRead = prevNav.pagesRead ?: 0,
        )
    }

    fun markProgressAtCurrentPage(bookScrollId: String? = null) {
        updateProgress(_state.value.pageIndex, bookScrollId)
    }

    open suspend fun saveCurrentProgress() {
        persistCurrentProgressLocally(_state.value.pageIndex)
    }

    protected suspend fun persistCurrentProgressLocally(pageIndex: Int) {
        if (anonymous) return
        val state = _state.value
        val info = state.bookInfo
        val progress =
            ReaderProgress(
                chapterId = state.activeChapterId ?: chapterId,
                page = pageIndex,
                bookScrollId = state.bookScrollId,
                seriesId = info?.seriesId ?: seriesId,
                volumeId = info?.volumeId ?: volumeId,
                libraryId = info?.libraryId,
                lastModifiedUtcMillis = System.currentTimeMillis(),
            )
        if (net.dom53.inkita.core.logging.LoggingManager
                .isDebugEnabled()
        ) {
            net.dom53.inkita.core.logging.LoggingManager.d(
                "InkitaProgress",
                "Save on exit chapter=${progress.chapterId} page=${progress.page} series=${progress.seriesId}",
            )
        }
        reader.saveProgressLocally(progress)
    }

    fun markChapterRead() {
        val total = _state.value.pageCount
        val lastIndex = if (total > 0) total - 1 else _state.value.pageIndex
        updateProgress(lastIndex, totalPagesOverride = total.takeIf { it > 0 })
    }

    suspend fun getBookInfoFor(chapterId: Int): ReaderBookInfo? = runCatching { reader.getBookInfo(chapterId) }.getOrNull()

    open suspend fun isPageDownloaded(pageIndex: Int): Boolean {
        val currentChapterId = _state.value.activeChapterId ?: chapterId
        return runCatching { reader.isPageDownloaded(currentChapterId, pageIndex) }.getOrDefault(false)
    }

    protected open fun onPageLoaded(pageIndex: Int) = Unit

    protected fun applyLoadResult(
        result: ReaderLoadResult,
        pageIndex: Int,
    ) {
        _state.update {
            val imageUrls =
                result.imageUrl?.let { url ->
                    it.imageUrls + (pageIndex to url)
                } ?: it.imageUrls
            it.copy(
                content = result.content ?: it.content,
                fromOffline = result.fromOffline,
                pageIndex = pageIndex,
                isLoading = false,
                error = null,
                pdfPath = result.pdfPath ?: it.pdfPath,
                imageUrl = result.imageUrl ?: it.imageUrl,
                imageUrls = imageUrls,
                previousImageUrl = if (result.imageUrl != null) null else it.previousImageUrl,
                nextImageUrl = if (result.imageUrl != null) null else it.nextImageUrl,
            )
        }
    }
}
