package net.dom53.inkita.core.downloadv2

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.core.storage.KavitaProfile
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class StoredDownload(
    val localPath: String,
    val bytes: Long,
)

class DownloadStorageManager internal constructor(
    context: Context,
    private val appPreferences: AppPreferences,
    private val saveDownloadLocation: suspend (String?) -> Unit,
) {
    constructor(context: Context, appPreferences: AppPreferences) :
        this(context, appPreferences, appPreferences::setDownloadLocationUri)

    private val appContext = context.applicationContext
    private val profile = appPreferences.profile
    private val storageChanges = storageLocks.computeIfAbsent(profile) { Mutex() }

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
                localFile(localPath).exists()
            }
        }

    suspend fun size(localPath: String?): Long =
        withContext(Dispatchers.IO) {
            if (localPath.isNullOrBlank()) {
                0L
            } else if (localPath.startsWith(CONTENT_SCHEME)) {
                DocumentFile.fromSingleUri(appContext, Uri.parse(localPath))?.length() ?: 0L
            } else {
                localFile(localPath).takeIf { it.exists() }?.length() ?: 0L
            }
        }

    suspend fun delete(localPath: String?) {
        withContext(Dispatchers.IO) {
            deletePath(localPath)
        }
    }

    suspend fun deleteDownload(
        database: InkitaDatabase,
        itemId: Long,
    ): Boolean =
        withContext(Dispatchers.IO) {
            storageChanges.withLock {
                val dao = database.downloadV2Dao()
                withContext(NonCancellable) {
                    database.withTransaction {
                        val item = dao.getItemById(itemId) ?: return@withTransaction true
                        val path = item.localPath
                        if (path != null && !dao.isPathUsedByOtherItem(itemId, pathAliases(path))) {
                            if (dao.hasUnfinishedJobFor(item.chapterId, item.volumeId, item.seriesId) || !deletePath(path)) {
                                return@withTransaction false
                            }
                        }
                        dao.deleteItemById(itemId)
                        true
                    }
                }
            }
        }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            val customRoot = appPreferences.downloadLocationUriFlow.first()
            if (customRoot == null) {
                DownloadPaths.baseDir(appContext, profile).deleteRecursively()
            } else {
                downloadDocuments(customRoot).forEach { it.delete() }
            }
        }
    }

    suspend fun totalSize(): Long =
        withContext(Dispatchers.IO) {
            val customRoot = appPreferences.downloadLocationUriFlow.first()
            if (customRoot == null) {
                dirSize(DownloadPaths.baseDir(appContext, profile))
            } else {
                downloadDocuments(customRoot).sumOf { documentSize(it) }
            }
        }

    suspend fun fileForReading(
        localPath: String,
        cacheDirName: String,
        fileName: String,
    ): File? =
        withContext(Dispatchers.IO) {
            if (!localPath.startsWith(CONTENT_SCHEME)) {
                return@withContext localFile(localPath).takeIf { it.exists() }
            }
            val cacheDir = File(appContext.cacheDir, "$cacheDirName/${profile.id}").apply { mkdirs() }
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
        database: InkitaDatabase,
        targetRootUri: String?,
    ) {
        withContext(Dispatchers.IO) {
            storageChanges.withLock {
                val dao = database.downloadV2Dao()
                check(!dao.hasUnfinishedJobs()) { "Download queue must be idle before changing location." }
                val currentRootUri = appPreferences.downloadLocationUriFlow.first()
                if (currentRootUri == targetRootUri) return@withLock

                val completed = dao.getItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
                val copies = MigrationCopies(targetRootUri)
                var committed = false
                try {
                    val updates =
                        completed.mapNotNull { item ->
                            ensureActive()
                            val sourcePath = item.localPath?.takeIf(::existsBlocking) ?: return@mapNotNull null
                            val relativePath = relativePathForItem(item) ?: error("Cannot map downloaded file to the new location.")
                            val stored =
                                if (item.type == DownloadedItemV2Entity.TYPE_PAGE && item.format == Format.Epub) {
                                    writeMigratedHtml(item, sourcePath, relativePath, copies)
                                } else {
                                    openInputStream(sourcePath)?.use { copies.write(relativePath, it) }
                                        ?: error("Cannot open downloaded file")
                                }
                            copies.sources += sourcePath
                            item.copy(localPath = stored.localPath, bytes = stored.bytes, updatedAt = System.currentTimeMillis())
                        }
                    withContext(NonCancellable) {
                        var locationSaved = false
                        try {
                            database.withTransaction {
                                check(!dao.hasUnfinishedJobs()) { "Download queue must be idle before changing location." }
                                check(dao.getItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED).toSet() == completed.toSet()) {
                                    "Downloads changed while copying files."
                                }
                                if (updates.isNotEmpty()) dao.upsertItems(updates)
                                saveDownloadLocation(targetRootUri)
                                locationSaved = true
                            }
                            committed = true
                        } catch (e: Exception) {
                            if (locationSaved) runCatching { saveDownloadLocation(currentRootUri) }.onFailure(e::addSuppressed)
                            throw e
                        }
                        runCatching {
                            database.withTransaction {
                                if (!dao.hasUnfinishedJobs()) {
                                    val referencedPaths = dao.getStoredPaths().toHashSet()
                                    copies.sources.forEach { path ->
                                        if (pathAliases(path).none { it in referencedPaths }) deletePath(path)
                                    }
                                    if (currentRootUri == null) removeEmptyDirs(DownloadPaths.baseDir(appContext, profile))
                                }
                            }
                        }
                    }
                } finally {
                    if (!committed) copies.destinations.forEach { path -> runCatching { deletePath(path) } }
                }
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
        preserveExisting: Boolean = false,
    ): StoredDownload {
        val normalized = relativePath.trim('/').replace('\\', '/')
        val segments = normalized.split('/').filter { it.isNotBlank() }
        require(segments.isNotEmpty()) { "Invalid download path" }
        return if (targetRootUri == null) {
            writeFile(segments, input, preserveExisting)
        } else {
            writeDocument(targetRootUri, segments, input, preserveExisting)
        }
    }

    private fun writeMigratedHtml(
        item: DownloadedItemV2Entity,
        sourcePath: String,
        relativePath: String,
        copies: MigrationCopies,
    ): StoredDownload {
        val html =
            openInputStream(sourcePath)?.bufferedReader()?.use { it.readText() }
                ?: error("Cannot read downloaded HTML")
        val seriesId = item.seriesId ?: error("Cannot map EPUB assets.")
        val chapterId = item.chapterId ?: error("Cannot map EPUB assets.")
        val assetsRelativeDir = DownloadPaths.epubAssetsRelativeDir(seriesId, item.volumeId, chapterId)
        val replacements = mutableMapOf<String, String>()
        val matcher = SRC_PATTERN.matcher(html)
        while (matcher.find()) {
            val src = matcher.group(1)
            if (src != null && !replacements.containsKey(src)) {
                if (isStoredAsset(sourcePath, src) && existsBlocking(src)) {
                    replacements[src] =
                        copies.assetPaths.getOrPut(src) {
                            val input = openInputStream(src) ?: error("Cannot open downloaded EPUB asset")
                            val stored = input.use { copies.write("$assetsRelativeDir/${assetFileName(src)}", it) }
                            if (isChapterAsset(sourcePath, src)) copies.sources += src
                            toHtmlUri(stored.localPath)
                        }
                }
            }
        }
        var rewritten = html
        replacements.forEach { (old, new) ->
            rewritten = rewritten.replace(old, new)
        }
        return copies.write(relativePath, ByteArrayInputStream(rewritten.toByteArray(Charsets.UTF_8)))
    }

    private inner class MigrationCopies(
        private val targetRootUri: String?,
    ) {
        val destinations = mutableSetOf<String>()
        val sources = mutableSetOf<String>()
        val assetPaths = mutableMapOf<String, String>()

        fun write(
            relativePath: String,
            input: InputStream,
        ): StoredDownload =
            this@DownloadStorageManager
                .write(relativePath, input, targetRootUri, preserveExisting = true)
                .also { destinations += it.localPath }
    }

    private fun writeFile(
        segments: List<String>,
        input: InputStream,
        preserveExisting: Boolean,
    ): StoredDownload {
        val target = segments.fold(DownloadPaths.baseDir(appContext, profile)) { file, segment -> File(file, segment) }
        target.parentFile?.mkdirs()
        val destination =
            if (preserveExisting && !target.createNewFile()) {
                File(target.parentFile, unusedName(target.name)).also { check(it.createNewFile()) }
            } else {
                target
            }
        return try {
            val bytes = destination.outputStream().use { output -> input.copyTo(output) }
            StoredDownload(destination.absolutePath, bytes)
        } catch (e: Exception) {
            if (preserveExisting) destination.delete()
            throw e
        }
    }

    private fun writeDocument(
        rootUri: String,
        segments: List<String>,
        input: InputStream,
        preserveExisting: Boolean,
    ): StoredDownload {
        val root = downloadDocumentRoot(rootUri, create = true) ?: error("Download folder is unavailable.")
        val parent =
            segments
                .dropLast(1)
                .fold(root) { dir, segment -> dir.findFile(segment) ?: dir.createDirectory(segment) ?: error("Cannot create $segment") }
        val requestedName = segments.last()
        val existing = parent.findFile(requestedName)
        val fileName = if (preserveExisting && existing != null) unusedName(requestedName) else requestedName
        if (!preserveExisting) existing?.delete()
        val target = parent.createFile(mimeType(fileName), fileName) ?: error("Cannot create $fileName")
        return try {
            val bytes =
                appContext.contentResolver.openOutputStream(target.uri, "wt")?.use { output ->
                    input.copyTo(output)
                } ?: error("Cannot write $fileName")
            StoredDownload(target.uri.toString(), bytes)
        } catch (e: Exception) {
            if (preserveExisting) target.delete()
            throw e
        }
    }

    private fun downloadDocumentRoot(
        uri: String,
        create: Boolean = false,
    ): DocumentFile? {
        val tree = DocumentFile.fromTreeUri(appContext, Uri.parse(uri)) ?: return null
        if (profile == KavitaProfile.Legacy) return tree
        return tree.findFile(profile.downloadFolder) ?: if (create) tree.createDirectory(profile.downloadFolder) else null
    }

    private fun downloadDocuments(uri: String): List<DocumentFile> =
        downloadDocumentRoot(uri)
            ?.listFiles()
            .orEmpty()
            .filterNot { profile == KavitaProfile.Legacy && it.name.orEmpty().startsWith(KavitaProfile.DOWNLOAD_FOLDER_PREFIX) }

    private fun openInputStream(localPath: String): InputStream? =
        if (localPath.startsWith(CONTENT_SCHEME)) {
            appContext.contentResolver.openInputStream(Uri.parse(localPath))
        } else {
            localFile(localPath).takeIf { it.exists() }?.inputStream()
        }

    private fun existsBlocking(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return if (localPath.startsWith(CONTENT_SCHEME)) {
            runCatching {
                appContext.contentResolver.openAssetFileDescriptor(Uri.parse(localPath), "r")?.use { true } == true
            }.getOrDefault(false)
        } else {
            localFile(localPath).exists()
        }
    }

    private fun deletePath(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return true
        return if (localPath.startsWith(CONTENT_SCHEME)) {
            DocumentFile.fromSingleUri(appContext, Uri.parse(localPath))?.delete() == true
        } else {
            val file = localFile(localPath)
            !file.exists() || (file.isFile && file.delete())
        }
    }

    private fun localFile(path: String): File = File(if (path.startsWith(FILE_SCHEME)) Uri.parse(path).path ?: path else path)

    private fun pathAliases(path: String): List<String> =
        if (path.startsWith(CONTENT_SCHEME)) {
            listOf(path)
        } else {
            val file = localFile(path)
            listOf(path, file.absolutePath, Uri.fromFile(file).toString()).distinct()
        }

    private fun isChapterAsset(
        pagePath: String,
        assetPath: String,
    ): Boolean {
        val page = Uri.parse(toHtmlUri(pagePath))
        val asset = Uri.parse(assetPath)
        if (page.scheme != asset.scheme || page.authority != asset.authority) return false
        val parent = page.path?.substringBeforeLast('/') ?: return false
        return asset.path?.substringBeforeLast('/') == "$parent/${DownloadPaths.ASSETS_DIR}"
    }

    private fun isStoredAsset(
        pagePath: String,
        assetPath: String,
    ): Boolean {
        if (isChapterAsset(pagePath, assetPath)) return true
        if (!pagePath.startsWith(CONTENT_SCHEME) || !assetPath.startsWith(CONTENT_SCHEME)) return false
        val page = Uri.parse(pagePath)
        val asset = Uri.parse(assetPath)
        return page.authority == asset.authority &&
            DocumentsContract.isTreeUri(page) &&
            DocumentsContract.isTreeUri(asset) &&
            DocumentsContract.getTreeDocumentId(page) == DocumentsContract.getTreeDocumentId(asset)
    }

    private fun unusedName(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "")
        return "$base-${UUID.randomUUID()}" + if (extension.isEmpty()) "" else ".$extension"
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
        private val storageLocks = ConcurrentHashMap<KavitaProfile, Mutex>()
        private val SRC_PATTERN = Pattern.compile("src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    }
}
