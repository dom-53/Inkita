package net.dom53.inkita.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.dom53.inkita.core.download.DownloadManager
import net.dom53.inkita.data.local.db.dao.DownloadDao
import net.dom53.inkita.data.local.db.entity.DownloadTaskEntity
import net.dom53.inkita.data.local.db.entity.DownloadedPageEntity
import net.dom53.inkita.domain.repository.DownloadRepository
import java.io.File

class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager,
    private val context: Context,
) : DownloadRepository {
    override fun observeTasks(): Flow<List<DownloadTaskEntity>> = downloadDao.observeAllTasks()

    override fun observeDownloadedPages(): Flow<List<DownloadedPageEntity>> = downloadDao.observeDownloadedPages()

    override fun observeValidDownloadedPages(): Flow<List<DownloadedPageEntity>> =
        downloadDao.observeDownloadedPages().map { list ->
            list.filter { page ->
                isPathPresent(page.htmlPath)
            }
        }

    override suspend fun enqueuePages(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
        pageStart: Int,
        pageEnd: Int,
    ) {
        downloadManager.enqueuePages(seriesId, volumeId, chapterId, pageStart, pageEnd)
    }

    override suspend fun cancelTask(taskId: Long) {
        downloadManager.cancelTask(taskId)
    }

    override suspend fun pauseTask(taskId: Long) {
        downloadManager.pauseTask(taskId)
    }

    override suspend fun resumeTask(taskId: Long) {
        downloadManager.resumeTask(taskId)
    }

    override suspend fun retryTask(taskId: Long) {
        downloadManager.retryTask(taskId)
    }

    override suspend fun deleteDownloadedPage(
        chapterId: Int,
        page: Int,
    ) {
        downloadDao.deleteDownloadedPage(chapterId, page)
    }

    override suspend fun cleanupMissingDownloads() {
        val all = downloadDao.getAllDownloadedPages()
        all.filter { !isPathPresent(it.htmlPath) }.forEach { missing ->
            downloadDao.deleteDownloadedPage(missing.chapterId, missing.page)
        }
    }

    override suspend fun deleteTask(id: Long) {
        downloadManager.deleteTask(id)
    }

    override suspend fun clearCompletedTasks() {
        downloadDao.deleteTasksByStates(listOf(DownloadTaskEntity.STATE_COMPLETED))
    }

    override suspend fun clearAllDownloads() {
        val pages = downloadDao.getAllDownloadedPages()
        pages.forEach { page ->
            deletePath(page.htmlPath)
            page.assetsDir?.let { deleteAssetsDir(it) }
        }
        downloadDao.clearAllDownloadedPages()
        // Try to delete legacy downloaded files under app storage
        val legacyRoot = context.getExternalFilesDir("Inkita")?.resolve("downloads")
        if (legacyRoot != null && legacyRoot.exists()) {
            legacyRoot.deleteRecursively()
        }
        // Delete PDF downloads
        val pdfRoot =
            context
                .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.resolve("Inkita")
                ?.resolve("pdfs")
        if (pdfRoot != null && pdfRoot.exists()) {
            pdfRoot.deleteRecursively()
        }
        val privateRoot = context.filesDir?.resolve("downloads")
        if (privateRoot != null && privateRoot.exists()) {
            privateRoot.deleteRecursively()
        }
        deleteLegacyExternalRoot()
        deletePublicDownloadsRoot()
        purgeMediaStoreDownloads("Download/Inkita/downloads/")
        purgeMediaStoreDownloads("Inkita/downloads/")
    }

    override suspend fun enqueuePdf(
        seriesId: Int,
        volumeId: Int?,
        bookId: Int,
        title: String?,
    ) {
        downloadManager.enqueuePdf(seriesId, volumeId, bookId, title)
    }

    private fun isPathPresent(path: String): Boolean {
        // content:// or other non-file scheme: assume present (cannot File.exists)
        if (path.startsWith("content://")) return true
        return File(path).exists()
    }

    private fun deletePath(path: String) {
        runCatching {
            when {
                path.startsWith("content://") -> {
                    context.contentResolver.delete(Uri.parse(path), null, null)
                }
                else -> {
                    File(path).delete()
                }
            }
        }
    }

    private fun purgeMediaStoreDownloads(relativePrefix: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = context.contentResolver
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("$relativePrefix%"),
            )
        }
    }

    private fun deleteAssetsDir(relativePath: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                    arrayOf("$relativePath%"),
                )
            }
        }
        val trimmed = relativePath.removePrefix("${Environment.DIRECTORY_DOWNLOADS}/")
        val legacy = Environment.getExternalStorageDirectory()?.resolve(trimmed)
        if (legacy != null && legacy.exists()) {
            legacy.deleteRecursively()
        }
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicTarget = publicDownloads?.resolve(trimmed)
        if (publicTarget != null && publicTarget.exists()) {
            publicTarget.deleteRecursively()
        }
    }

    private fun deleteLegacyExternalRoot() {
        val root = Environment.getExternalStorageDirectory()?.resolve("Inkita")
        if (root != null && root.exists()) {
            root.deleteRecursively()
        }
    }

    private fun deletePublicDownloadsRoot() {
        val publicRoot =
            Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.resolve("Inkita")
                ?.resolve("downloads")
        if (publicRoot != null && publicRoot.exists()) {
            publicRoot.deleteRecursively()
        }
    }
}
