package net.dom53.inkita.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkMonitor
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.sync.ProgressSyncWorker
import net.dom53.inkita.data.local.db.dao.DownloadDao
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.dao.ReaderDao
import net.dom53.inkita.data.local.db.entity.CachedPageEntity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity
import net.dom53.inkita.data.local.db.entity.LocalReaderProgressEntity
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.data.mapper.toDto
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderChapterNav
import net.dom53.inkita.domain.model.ReaderPageResult
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.ReaderTimeLeft
import net.dom53.inkita.domain.repository.ReaderRepository
import java.io.File
import java.io.IOException

class ReaderRepositoryImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val readerDao: ReaderDao,
    private val downloadDao: DownloadDao? = null,
    private val downloadV2Dao: DownloadV2Dao? = null,
) : ReaderRepository {
    class PageNotDownloadedException(
        message: String,
    ) : IOException(message)

    override suspend fun getPageResult(
        chapterId: Int,
        page: Int,
    ): ReaderPageResult {
        val preferOffline = appPreferences.preferOfflinePagesFlow.first()
        val downloadedV2 = downloadV2Dao?.getDownloadedPageForChapter(chapterId, page)
        val downloadedHtmlV2 =
            downloadedV2?.localPath?.let { path ->
                if (isPathPresent(path)) {
                    readDownloadedHtml(path)
                } else {
                    null
                }
            }
        val downloaded = downloadDao?.getDownloadedPage(chapterId, page)
        val downloadedHtml =
            downloaded?.let { pageEntity ->
                if (isPathPresent(pageEntity.htmlPath)) {
                    readDownloadedHtml(pageEntity.htmlPath)
                } else {
                    runCatching { downloadDao?.deleteDownloadedPage(chapterId, page) }
                    null
                }
            }
        val cached = readerDao.getPage(chapterId, page)?.html
        val offlineHtml = downloadedHtmlV2 ?: downloadedHtml

        if (!isOnlineAllowed() && offlineHtml == null) {
            throw PageNotDownloadedException("Page not downloaded for offline reading")
        }

        if (preferOffline && offlineHtml != null) return ReaderPageResult(offlineHtml, true)

        val apiHtml =
            try {
                val api = apiOrThrow()
                val resp = api.getBookPage(chapterId, page)
                if (!resp.isSuccessful) {
                    throw Exception("Error loading page: HTTP ${resp.code()} ${resp.message()}")
                }
                val body = resp.body() ?: throw Exception("Empty page body")
                readerDao.upsertPage(
                    CachedPageEntity(
                        chapterId = chapterId,
                        page = page,
                        html = body,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                body
            } catch (io: IOException) {
                if (offlineHtml != null) return ReaderPageResult(offlineHtml, true)
                if (cached != null) return ReaderPageResult(cached, false)
                if (!isOnlineAllowed()) throw PageNotDownloadedException("Page not downloaded for offline reading")
                throw io
            } catch (e: Exception) {
                if (offlineHtml != null) return ReaderPageResult(offlineHtml, true)
                if (cached != null) return ReaderPageResult(cached, false)
                throw e
            }

        if (apiHtml != null) return ReaderPageResult(apiHtml, false)
        if (offlineHtml != null) return ReaderPageResult(offlineHtml, true)
        if (cached != null) return ReaderPageResult(cached, false)

        throw PageNotDownloadedException("Page not downloaded for offline reading")
    }

    override suspend fun getPage(
        chapterId: Int,
        page: Int,
    ): String = getPageResult(chapterId, page).html

    override suspend fun isPageDownloaded(
        chapterId: Int,
        page: Int,
    ): Boolean {
        val v2Item = downloadV2Dao?.getDownloadedPageForChapter(chapterId, page)
        if (v2Item != null) {
            val path = v2Item.localPath
            if (!path.isNullOrBlank()) {
                val present = isPathPresent(path)
                if (!present) return false
                return present
            }
        }
        val downloaded = downloadDao?.getDownloadedPage(chapterId, page) ?: return false
        val present = isPathPresent(downloaded.htmlPath)
        if (!present) {
            runCatching { downloadDao?.deleteDownloadedPage(chapterId, page) }
        }
        return present
    }

    override suspend fun getProgress(chapterId: Int): ReaderProgress? {
        val local = readerDao.getLocalProgress(chapterId)?.toDomain()
        if (!isOnlineAllowed()) return local

        val api =
            try {
                apiOrThrow()
            } catch (_: IOException) {
                return local
            }

        val remote =
            runCatching {
                val resp = api.getReaderProgress(chapterId)
                if (resp.isSuccessful) resp.body()?.toDomain() else null
            }.getOrNull()

        val latest =
            when {
                remote == null -> {
                    local?.let { runCatching { api.setReaderProgress(it.toDto()) } }
                    local
                }
                local == null -> remote
                local.lastModifiedUtcMillis > remote.lastModifiedUtcMillis -> {
                    // push local to server
                    runCatching { api.setReaderProgress(local.toDto()) }
                    local
                }
                else -> remote
            }

        latest?.let { readerDao.upsertLocalProgress(it.toEntity()) }
        return latest
    }

    override suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        val progressWithTs =
            if (progress.lastModifiedUtcMillis == 0L) {
                progress.copy(lastModifiedUtcMillis = System.currentTimeMillis())
            } else {
                progress
            }

        if (!isOnlineAllowed()) {
            readerDao.upsertLocalProgress(progressWithTs.toEntity())
            ProgressSyncWorker.enqueue(context)
            return
        }

        runCatching {
            val api = apiOrThrow()
            api.setReaderProgress(progressWithTs.toDto())
        }.onFailure {
            // fallback to local cache if network fails
            readerDao.upsertLocalProgress(progressWithTs.toEntity())
            ProgressSyncWorker.enqueue(context)
        }.onSuccess {
            readerDao.upsertLocalProgress(progressWithTs.toEntity())
        }

        maybeDeleteAfterCompletion(progressWithTs, totalPages)
        maybeDeleteAfterSlidingWindow(progressWithTs)
    }

    override suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft? =
        runCatching {
            val api = apiOrThrow()
            val resp = api.getTimeLeftForChapter(seriesId, chapterId)
            if (resp.isSuccessful) resp.body()?.toDomain() else null
        }.getOrNull()

    override suspend fun getPdfFile(chapterId: Int): File? {
        val config = appPreferences.configFlow.first()
        val target =
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "Inkita/pdfs/inkita-pdf-$chapterId.pdf",
            )
        if (target.exists()) return target

        val api =
            try {
                apiOrThrow()
            } catch (_: IOException) {
                return null
            }
        val response =
            api.getPdf(
                chapterId,
                apiKey = config.apiKey.takeIf { it.isNotBlank() },
                extractPdf = true,
            )
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        target.parentFile?.mkdirs()
        target.outputStream().use { out ->
            body.byteStream().use { input ->
                input.copyTo(out)
            }
        }
        return target
    }

    override suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? {
        return runCatching {
            val api = apiOrThrow()
            val resp = api.getNextChapter(seriesId, volumeId, currentChapterId)
            if (!resp.isSuccessful) return@runCatching null
            val id = resp.body() ?: return@runCatching null
            ReaderChapterNav(seriesId = seriesId, volumeId = volumeId, chapterId = id)
        }.getOrNull()
    }

    override suspend fun getPreviousChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? {
        return runCatching {
            val api = apiOrThrow()
            val resp = api.getPreviousChapter(seriesId, volumeId, currentChapterId)
            if (!resp.isSuccessful) return@runCatching null
            val id = resp.body() ?: return@runCatching null
            ReaderChapterNav(seriesId = seriesId, volumeId = volumeId, chapterId = id)
        }.getOrNull()
    }

    override suspend fun getContinuePoint(seriesId: Int): ReaderChapterNav? =
        runCatching {
            val api = apiOrThrow()
            val resp = api.getContinuePoint(seriesId)
            if (!resp.isSuccessful) return@runCatching null
            val chapter = resp.body() ?: return@runCatching null
            ReaderChapterNav(
                seriesId = seriesId,
                volumeId = chapter.volumeId,
                chapterId = chapter.id,
                pagesRead = chapter.pagesRead,
            )
        }.getOrNull()

    override suspend fun getBookInfo(chapterId: Int): ReaderBookInfo? =
        runCatching {
            if (!isOnlineAllowed()) return@runCatching getOfflineBookInfo(chapterId)
            val api = apiOrThrow()
            val resp = api.getBookInfo(chapterId)
            if (resp.isSuccessful) resp.body()?.toDomain() else null
        }.getOrNull() ?: getOfflineBookInfo(chapterId)

    override suspend fun markSeriesRead(seriesId: Int) {
        runCatching {
            val api = apiOrThrow()
            api.markSeriesRead(
                net.dom53.inkita.data.api.dto
                    .MarkSeriesDto(seriesId),
            )
        }
        runCatching { handleDeleteAfterRead(seriesId = seriesId, volumeIds = null) }
    }

    override suspend fun markSeriesUnread(seriesId: Int) {
        runCatching {
            val api = apiOrThrow()
            api.markSeriesUnread(
                net.dom53.inkita.data.api.dto
                    .MarkSeriesDto(seriesId),
            )
        }
        runCatching { clearDeleteAfterHistory(seriesId) }
    }

    override suspend fun markVolumeRead(
        seriesId: Int,
        volumeIds: List<Int>,
    ) {
        runCatching {
            val api = apiOrThrow()
            api.markMultipleRead(
                net.dom53.inkita.data.api.dto
                    .MarkMultipleDto(seriesId, volumeIds = volumeIds),
            )
        }
        runCatching { handleDeleteAfterRead(seriesId = seriesId, volumeIds = volumeIds) }
    }

    override suspend fun markVolumeUnread(
        seriesId: Int,
        volumeIds: List<Int>,
    ) {
        runCatching {
            val api = apiOrThrow()
            api.markMultipleUnread(
                net.dom53.inkita.data.api.dto
                    .MarkMultipleDto(seriesId, volumeIds = volumeIds),
            )
        }
        // no-op for delete-after; sliding window is handled via progress
    }

    override suspend fun syncLocalProgress() {
        if (!isOnlineAllowed()) return
        val api = runCatching { apiOrThrow() }.getOrElse { return }
        val locals = runCatching { readerDao.getAllLocalProgress() }.getOrDefault(emptyList())
        if (locals.isEmpty()) return
        locals.forEach { localEntity ->
            val local = localEntity.toDomain()
            val remote =
                runCatching {
                    val resp = api.getReaderProgress(local.chapterId)
                    if (resp.isSuccessful) resp.body()?.toDomain() else null
                }.getOrNull()
            val latest =
                when {
                    remote == null -> local
                    local.lastModifiedUtcMillis > remote.lastModifiedUtcMillis -> local
                    else -> remote
                }
            val resp =
                runCatching { api.setReaderProgress(latest.toDto()) }
                    .getOrNull()
            if (resp?.isSuccessful == true) {
                readerDao.clearLocalProgress(local.chapterId)
            } else {
                readerDao.upsertLocalProgress(latest.toEntity())
            }
        }
    }

    override suspend fun getLatestLocalProgress(seriesId: Int): ReaderProgress? {
        val locals = runCatching { readerDao.getAllLocalProgress() }.getOrDefault(emptyList())
        val latest =
            locals
                .filter { it.seriesId == seriesId }
                .maxByOrNull { it.lastModifiedUtc }
        return latest?.toDomain()
    }

    override suspend fun getLatestLocalProgressForChapters(chapterIds: Set<Int>): ReaderProgress? {
        if (chapterIds.isEmpty()) return null
        val locals = runCatching { readerDao.getAllLocalProgress() }.getOrDefault(emptyList())
        val latest =
            locals
                .filter { it.chapterId in chapterIds }
                .maxByOrNull { it.lastModifiedUtc }
        return latest?.toDomain()
    }

    private suspend fun apiOrThrow(): net.dom53.inkita.data.api.KavitaApi {
        val config = appPreferences.configFlow.first()
        check(config.isConfigured) { "Not authenticated" }
        if (!isOnlineAllowed()) throw IOException("Offline")

        return KavitaApiFactory.createAuthenticated(
            baseUrl = config.serverUrl,
            apiKey = config.apiKey,
        )
    }

    private fun readDownloadedHtml(path: String): String? =
        runCatching {
            if (path.startsWith("content://")) {
                context.contentResolver
                    .openInputStream(Uri.parse(path))
                    ?.bufferedReader()
                    ?.use { it.readText() }
            } else {
                File(path).takeIf { it.exists() }?.readText()
            }
        }.getOrNull()

    private fun isPathPresent(path: String): Boolean {
        if (path.startsWith("content://")) return true
        return File(path).exists()
    }

    private fun isOnlineAllowed(): Boolean =
        NetworkMonitor.getInstance(context, appPreferences).status.value.isOnlineAllowed

    private suspend fun getOfflineBookInfo(chapterId: Int): ReaderBookInfo? {
        val items = downloadV2Dao?.getItemsForChapter(chapterId).orEmpty()
        if (items.isEmpty()) return null
        val completed =
            items
                .filter { it.status == DownloadedItemV2Entity.STATUS_COMPLETED }
                .filter { it.page != null }
                .filter { isPathPresent(it.localPath ?: return@filter false) }
        if (completed.isEmpty()) return null
        val maxPage = completed.maxOf { it.page ?: 0 }
        val progress = readerDao.getLocalProgress(chapterId)
        val seriesId = progress?.seriesId ?: completed.firstOrNull()?.seriesId
        val volumeId = progress?.volumeId ?: completed.firstOrNull()?.volumeId
        val libraryId = progress?.libraryId
        val title =
            runCatching {
                context.getString(
                    net.dom53.inkita.R.string.series_detail_chapter_fallback,
                    chapterId,
                )
            }.getOrNull()
        return ReaderBookInfo(
            pages = maxPage + 1,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            title = null,
            pageTitle = title,
        )
    }

    private fun LocalReaderProgressEntity.toDomain(): ReaderProgress =
        ReaderProgress(
            chapterId = chapterId,
            page = page,
            bookScrollId = bookScrollId,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            lastModifiedUtcMillis = lastModifiedUtc,
        )

    private fun ReaderProgress.toEntity(): LocalReaderProgressEntity =
        LocalReaderProgressEntity(
            chapterId = chapterId,
            page = page,
            bookScrollId = bookScrollId,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            lastModifiedUtc = lastModifiedUtcMillis,
        )

    /**
     * Apply delete-after-read preferences: keep at most `depth` recently marked read volumes.
     * When depth is 1, delete the current read volumes immediately.
     */
    private suspend fun handleDeleteAfterRead(
        seriesId: Int?,
        volumeIds: List<Int>?,
    ) {
        val dao = downloadDao ?: return
        val enabled = appPreferences.deleteAfterMarkReadFlow.first()
        if (!enabled) return
        val depth = appPreferences.deleteAfterReadDepthFlow.first().coerceIn(1, 5)

        if (volumeIds.isNullOrEmpty()) {
            if (seriesId != null) {
                deleteDownloadedPages(dao.getDownloadedPagesBySeries(seriesId))
            }
            return
        }

        if (depth == 1) {
            volumeIds.forEach { id ->
                deleteDownloadedPages(dao.getDownloadedPagesByVolume(id))
            }
            return
        }

        // For volume-level completion, just delete the completed volumes when depth is 1.
        if (depth == 1) {
            volumeIds.forEach { id -> deleteDownloadedPages(dao.getDownloadedPagesByVolume(id)) }
        }
    }

    private suspend fun clearDeleteAfterHistory(seriesId: Int?) {
        if (seriesId != null) {
            downloadDao?.let { dao -> deleteDownloadedPages(dao.getDownloadedPagesBySeries(seriesId)) }
        }
    }

    private suspend fun deleteDownloadedPages(pages: List<DownloadedPageEntity>) {
        if (pages.isEmpty()) return
        pages.forEach { page ->
            runCatching {
                if (!page.htmlPath.startsWith("content://")) {
                    File(page.htmlPath).delete()
                } else {
                    context.contentResolver.delete(Uri.parse(page.htmlPath), null, null)
                }
                page.assetsDir?.let { dir ->
                    val file = File(dir)
                    if (file.exists()) file.deleteRecursively()
                }
            }
            runCatching { downloadDao?.deleteDownloadedPage(page.chapterId, page.page) }
        }
    }

    private suspend fun maybeDeleteAfterCompletion(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        val enabled = appPreferences.deleteAfterMarkReadFlow.first()
        if (!enabled) return
        val count = totalPages ?: return
        if (count <= 0) return
        if ((progress.page ?: 0) < count - 1) return
        val volumeId = progress.volumeId ?: return
        handleDeleteAfterRead(seriesId = progress.seriesId, volumeIds = listOf(volumeId))
    }

    private suspend fun maybeDeleteAfterSlidingWindow(progress: ReaderProgress) {
        val enabled = appPreferences.deleteAfterMarkReadFlow.first()
        if (!enabled) return
        val depth = appPreferences.deleteAfterReadDepthFlow.first().coerceIn(1, 5)
        val page = progress.page ?: return
        val cutoff = page - depth + 1
        if (cutoff < 0) return
        val pages =
            downloadDao
                ?.getDownloadedPagesBefore(progress.chapterId, cutoff)
                .orEmpty()
        deleteDownloadedPages(pages)
    }
}
