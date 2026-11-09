package net.dom53.inkita.core.downloadv2

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.dao.DownloadV2Dao
import net.dom53.inkita.data.local.db.entity.DownloadJobV2Entity
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.regex.Pattern

data class StoredDownload(
    val localPath: String,
    val bytes: Long,
)

class DownloadStorageManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
) {
    private val appContext = context.applicationContext

    suspend fun write(
        relativePath: String,
        input: InputStream,
    ): StoredDownload = write(relativePath, input, appPreferences.downloadLocationUriFlow.first())

    suspend fun writeText(
        relativePath: String,
        text: String,
    ): StoredDownload = write(relativePath, ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    suspend fun writeAsset(
        relativeDir: String,
        fileName: String,
        input: InputStream,
    ): StoredDownload = write("$relativeDir/$fileName", input)

    suspend fun readText(localPath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                openInputStream(localPath)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }

    suspend fun exists(localPath: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (localPath.isNullOrBlank()) {
                false
            } else if (localPath.startsWith(CONTENT_SCHEME)) {
                runCatching {
                    appContext.contentResolver.openAssetFileDescriptor(Uri.parse(localPath), "r")?.use { true } == true
                }.getOrDefault(false)
            } else {
                File(localPath.removePrefix(FILE_SCHEME)).exists()
            }
        }

    suspend fun size(localPath: String?): Long =
        withContext(Dispatchers.IO) {
            if (localPath.isNullOrBlank()) {
                0L
            } else if (localPath.startsWith(CONTENT_SCHEME)) {
                DocumentFile.fromSingleUri(appContext, Uri.parse(localPath))?.length() ?: 0L
            } else {
                File(localPath.removePrefix(FILE_SCHEME)).takeIf { it.exists() }?.length() ?: 0L
            }
        }

    suspend fun delete(localPath: String?) {
        withContext(Dispatchers.IO) {
            deletePath(localPath)
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            val customRoot = appPreferences.downloadLocationUriFlow.first()
            if (customRoot == null) {
                DownloadPaths.baseDir(appContext).deleteRecursively()
            } else {
                DocumentFile
                    .fromTreeUri(appContext, Uri.parse(customRoot))
                    ?.listFiles()
                    ?.forEach { it.delete() }
            }
        }
    }

    suspend fun totalSize(): Long =
        withContext(Dispatchers.IO) {
            val customRoot = appPreferences.downloadLocationUriFlow.first()
            if (customRoot == null) {
                dirSize(DownloadPaths.baseDir(appContext))
            } else {
                DocumentFile
                    .fromTreeUri(appContext, Uri.parse(customRoot))
                    ?.let { documentSize(it) }
                    ?: 0L
            }
        }

    suspend fun fileForReading(
        localPath: String,
        cacheDirName: String,
        fileName: String,
    ): File? =
        withContext(Dispatchers.IO) {
            if (!localPath.startsWith(CONTENT_SCHEME)) {
                return@withContext File(localPath.removePrefix(FILE_SCHEME)).takeIf { it.exists() }
            }
            val cacheDir = File(appContext.cacheDir, cacheDirName).apply { mkdirs() }
            val target = File(cacheDir, "${hashName(localPath)}-$fileName")
            if (target.exists() && target.length() > 0L) return@withContext target
            openInputStream(localPath)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.takeIf { it.exists() && it.length() > 0L }
        }

    suspend fun displayName(uri: String?): String? =
        withContext(Dispatchers.IO) {
            uri?.let {
                DocumentFile.fromTreeUri(appContext, Uri.parse(it))?.name
                    ?: Uri.parse(it).lastPathSegment
            }
        }

    suspend fun migrateDownloadLocation(
        downloadDao: DownloadV2Dao,
        targetRootUri: String?,
    ) {
        withContext(Dispatchers.IO) {
            val pending = downloadDao.countJobsByStatus(DownloadJobV2Entity.STATUS_PENDING)
            val running = downloadDao.countJobsByStatus(DownloadJobV2Entity.STATUS_RUNNING)
            check(pending == 0 && running == 0) { "Download queue must be idle before changing location." }

            val currentRootUri = appPreferences.downloadLocationUriFlow.first()
            if (currentRootUri == targetRootUri) return@withContext

            val completed =
                downloadDao
                    .getItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
                    .filter { existsBlocking(it.localPath) }
            val copied = mutableListOf<String>()
            val updates = mutableListOf<DownloadedItemV2Entity>()
            val movedSourcePaths = mutableListOf<String>()
            try {
                completed.forEach { item ->
                    val sourcePath = item.localPath ?: return@forEach
                    val relativePath = relativePathForItem(item) ?: error("Cannot map downloaded file to the new location.")
                    val stored =
                        if (item.type == DownloadedItemV2Entity.TYPE_PAGE && item.format == Format.Epub) {
                            val migrated = writeMigratedHtml(item, sourcePath, relativePath, targetRootUri)
                            movedSourcePaths += migrated.copiedAssetSources
                            migrated.stored
                        } else {
                            openInputStream(sourcePath)?.use { input ->
                                write(relativePath, input, targetRootUri)
                            } ?: error("Cannot open downloaded file")
                        }
                    copied += stored.localPath
                    movedSourcePaths += sourcePath
                    updates += item.copy(localPath = stored.localPath, bytes = stored.bytes, updatedAt = System.currentTimeMillis())
                }
                if (updates.isNotEmpty()) {
                    downloadDao.upsertItems(updates)
                }
                appPreferences.setDownloadLocationUri(targetRootUri)
                movedSourcePaths.forEach { deletePath(it) }
                if (currentRootUri == null) {
                    removeEmptyDirs(DownloadPaths.baseDir(appContext))
                }
            } catch (e: Exception) {
                copied.forEach { deletePath(it) }
                throw e
            }
        }
    }

    fun relativePathForItem(item: DownloadedItemV2Entity): String? {
        val seriesId = item.seriesId
        val volumeId = item.volumeId
        val chapterId = item.chapterId
        val fileName = item.localPath?.substringAfterLast('/')?.substringAfterLast(':')
        return when {
            item.type == DownloadedItemV2Entity.TYPE_PAGE &&
                item.format == Format.Epub &&
                seriesId != null &&
                chapterId != null &&
                item.page != null ->
                DownloadPaths.epubPageRelativePath(seriesId, volumeId, chapterId, item.page)
            item.type == DownloadedItemV2Entity.TYPE_FILE &&
                item.format == Format.Pdf &&
                seriesId != null &&
                chapterId != null ->
                DownloadPaths.pdfRelativePath(seriesId, volumeId, chapterId)
            item.type == DownloadedItemV2Entity.TYPE_FILE &&
                item.format in setOf(Format.Archive, Format.Image) &&
                seriesId != null &&
                chapterId != null ->
                DownloadPaths.cbzRelativePath(seriesId, volumeId, chapterId)
            item.type == DownloadedItemV2Entity.TYPE_FILE && fileName != null ->
                DownloadPaths.archiveDownloadRelativePath(seriesId, volumeId, chapterId, fileName)
            else -> null
        }
    }

    private fun write(
        relativePath: String,
        input: InputStream,
        targetRootUri: String?,
    ): StoredDownload {
        val normalized = relativePath.trim('/').replace('\\', '/')
        val segments = normalized.split('/').filter { it.isNotBlank() }
        require(segments.isNotEmpty()) { "Invalid download path" }
        return if (targetRootUri == null) {
            writeFile(segments, input)
        } else {
            writeDocument(targetRootUri, segments, input)
        }
    }

    private fun writeMigratedHtml(
        item: DownloadedItemV2Entity,
        sourcePath: String,
        relativePath: String,
        targetRootUri: String?,
    ): MigratedHtml {
        val html =
            openInputStream(sourcePath)?.bufferedReader()?.use { it.readText() }
                ?: error("Cannot read downloaded HTML")
        val seriesId = item.seriesId ?: error("Cannot map EPUB assets.")
        val chapterId = item.chapterId ?: error("Cannot map EPUB assets.")
        val assetsRelativeDir = DownloadPaths.epubAssetsRelativeDir(seriesId, item.volumeId, chapterId)
        val replacements = mutableMapOf<String, String>()
        val copiedAssetSources = mutableListOf<String>()
        val matcher = SRC_PATTERN.matcher(html)
        while (matcher.find()) {
            val src = matcher.group(1)
            if (src != null && !replacements.containsKey(src)) {
                copyMigratedAsset(src, assetsRelativeDir, targetRootUri)?.let { copied ->
                    copiedAssetSources += src
                    replacements[src] = copied
                }
            }
        }
        var rewritten = html
        replacements.forEach { (old, new) ->
            rewritten = rewritten.replace(old, new)
        }
        return MigratedHtml(
            stored = write(relativePath, ByteArrayInputStream(rewritten.toByteArray(Charsets.UTF_8)), targetRootUri),
            copiedAssetSources = copiedAssetSources,
        )
    }

    private fun copyMigratedAsset(
        src: String,
        assetsRelativeDir: String,
        targetRootUri: String?,
    ): String? {
        if (!src.startsWith(FILE_SCHEME) && !src.startsWith(CONTENT_SCHEME)) return null
        if (!existsBlocking(src)) return null
        val fileName = assetFileName(src)
        val stored =
            openInputStream(src)?.use { input ->
                write("$assetsRelativeDir/$fileName", input, targetRootUri)
            } ?: return null
        return toHtmlUri(stored.localPath)
    }

    private fun writeFile(
        segments: List<String>,
        input: InputStream,
    ): StoredDownload {
        val target = segments.fold(DownloadPaths.baseDir(appContext)) { file, segment -> File(file, segment) }
        target.parentFile?.mkdirs()
        val bytes = target.outputStream().use { output -> input.copyTo(output) }
        return StoredDownload(target.absolutePath, bytes)
    }

    private fun writeDocument(
        rootUri: String,
        segments: List<String>,
        input: InputStream,
    ): StoredDownload {
        val root = DocumentFile.fromTreeUri(appContext, Uri.parse(rootUri)) ?: error("Download folder is unavailable.")
        val fileName = segments.last()
        val parent =
            segments
                .dropLast(1)
                .fold(root) { dir, segment -> dir.findFile(segment) ?: dir.createDirectory(segment) ?: error("Cannot create $segment") }
        parent.findFile(fileName)?.delete()
        val target = parent.createFile(mimeType(fileName), fileName) ?: error("Cannot create $fileName")
        val bytes =
            appContext.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                input.copyTo(output)
            } ?: error("Cannot write $fileName")
        return StoredDownload(target.uri.toString(), bytes)
    }

    private fun openInputStream(localPath: String): InputStream? =
        if (localPath.startsWith(CONTENT_SCHEME)) {
            appContext.contentResolver.openInputStream(Uri.parse(localPath))
        } else {
            File(localPath.removePrefix(FILE_SCHEME)).takeIf { it.exists() }?.inputStream()
        }

    private fun existsBlocking(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return if (localPath.startsWith(CONTENT_SCHEME)) {
            runCatching {
                appContext.contentResolver.openAssetFileDescriptor(Uri.parse(localPath), "r")?.use { true } == true
            }.getOrDefault(false)
        } else {
            File(localPath.removePrefix(FILE_SCHEME)).exists()
        }
    }

    private fun deletePath(localPath: String?) {
        if (localPath.isNullOrBlank()) return
        if (localPath.startsWith(CONTENT_SCHEME)) {
            DocumentFile.fromSingleUri(appContext, Uri.parse(localPath))?.delete()
        } else {
            File(localPath.removePrefix(FILE_SCHEME)).delete()
        }
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    private fun documentSize(file: DocumentFile): Long =
        if (file.isDirectory) {
            file.listFiles().sumOf { documentSize(it) }
        } else {
            file.length()
        }

    private fun removeEmptyDirs(root: File) {
        if (!root.exists()) return
        root.walkBottomUp().filter { it.isDirectory && it.listFiles().isNullOrEmpty() }.forEach { it.delete() }
    }

    private fun mimeType(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "cbz", "zip" -> "application/zip"
            "html", "htm" -> "text/html"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "application/octet-stream"
        }

    private fun assetFileName(path: String): String {
        val parsed = Uri.parse(path)
        val candidate =
            parsed.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.takeIf { it.contains('.') }
        return candidate ?: "${hashName(path)}.bin"
    }

    private fun toHtmlUri(localPath: String): String =
        if (localPath.startsWith(CONTENT_SCHEME) || localPath.startsWith(FILE_SCHEME)) {
            localPath
        } else {
            Uri.fromFile(File(localPath)).toString()
        }

    private fun hashName(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CONTENT_SCHEME = "content://"
        private const val FILE_SCHEME = "file://"
        private val SRC_PATTERN = Pattern.compile("src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    }
}

private data class MigratedHtml(
    val stored: StoredDownload,
    val copiedAssetSources: List<String>,
)
