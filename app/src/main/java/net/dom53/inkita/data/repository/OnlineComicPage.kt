package net.dom53.inkita.data.repository

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.isSameOrigin
import net.dom53.inkita.core.storage.AppConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

private const val MEBIBYTE = 1024L * 1024
private const val MAX_ARCHIVE_BYTES = 256 * MEBIBYTE
private const val MAX_PAGE_BYTES = 32 * MEBIBYTE
private const val ARCHIVE_CACHE_BYTES = 512 * MEBIBYTE

private fun AppConfig.comicImageEndpoint(): HttpUrl? =
    serverUrl.toHttpUrlOrNull()?.let { base ->
        base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + "/api/reader/image").build()
    }

internal fun AppConfig.comicPageUrl(
    chapterId: Int,
    page: Int,
): String =
    checkNotNull(comicImageEndpoint())
        .newBuilder()
        .addQueryParameter("chapterId", chapterId.toString())
        .addQueryParameter("page", page.toString())
        .addQueryParameter("apiKey", apiKey)
        .build()
        .toString()

@OptIn(ExperimentalCoilApi::class)
internal class ComicPageFallback(
    context: Context,
    private val config: AppConfig,
    private val canUseNetwork: () -> Boolean,
) : Interceptor {
    private val cacheDirectory = context.cacheDir
    private val endpoint = config.comicImageEndpoint()
    private val api by lazy { KavitaApiFactory.createAuthenticated(config, followRedirects = false) }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed(request)
        val failure = (result as? ErrorResult)?.throwable as? HttpException
        val status = failure?.response?.code ?: return result
        if (status !in 500..599 || !request.networkCachePolicy.readEnabled || !canUseNetwork()) return result
        val page = (request.data as? String)?.toHttpUrlOrNull()?.let(::matchPage) ?: return result

        return withContext(Dispatchers.IO) {
            val file = File.createTempFile("comic-page-", ".img", cacheDirectory)
            try {
                if (!extractPage(page, file)) return@withContext result
                val localRequest =
                    request
                        .newBuilder()
                        .data(file)
                        .memoryCacheKey(request.memoryCacheKey ?: MemoryCache.Key(request.data.toString()))
                        .build()
                chain.proceed(localRequest).let { if (it is ErrorResult) result else it }
            } catch (_: IOException) {
                result
            } finally {
                file.delete()
            }
        }
    }

    private fun matchPage(url: HttpUrl): ComicPage? {
        val target = endpoint ?: return null
        if (!target.isSameOrigin(url) || target.encodedPath != url.encodedPath || url.queryParameter("apiKey") != config.apiKey) return null
        val chapter = url.queryParameter("chapterId")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val page = url.queryParameter("page")?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return ComicPage(chapter, page)
    }

    private suspend fun extractPage(
        page: ComicPage,
        target: File,
    ): Boolean {
        val key = "${config.serverUrl}\n${config.apiKey}\n${page.chapterId}"
        val cache = archiveCache(cacheDirectory)
        val lock = archiveLocks[(key.hashCode() and Int.MAX_VALUE) % archiveLocks.size]
        val snapshot =
            lock.withLock {
                cache.openSnapshot(key) ?: downloadArchive(cache, key, page.chapterId)
            } ?: return false
        return snapshot.use {
            ZipFile(it.data.toFile()).use { zip -> zip.copyPage(page.index, target) }
        }
    }

    private suspend fun ZipFile.copyPage(
        index: Int,
        target: File,
    ): Boolean {
        val entry = comicImageEntries().getOrNull(index) ?: return false
        if (entry.size > MAX_PAGE_BYTES) return false
        getInputStream(entry).use { input ->
            target.outputStream().use { output -> input.copyLimited(output, MAX_PAGE_BYTES) }
        }
        return true
    }

    private suspend fun downloadArchive(
        cache: DiskCache,
        key: String,
        chapterId: Int,
    ): DiskCache.Snapshot? {
        val editor = cache.openEditor(key) ?: return null
        var completed = false
        try {
            val response = api.downloadChapterFile(chapterId)
            response.errorBody()?.close()
            val body = response.body() ?: return null
            val archive = editor.data.toFile()
            body.use {
                if (it.contentLength() > MAX_ARCHIVE_BYTES) return null
                archive.outputStream().use { output -> it.byteStream().copyLimited(output, MAX_ARCHIVE_BYTES) }
            }
            ZipFile(archive).use { if (!it.entries().hasMoreElements()) return null }
            editor.metadata.toFile().writeText("")
            val snapshot = editor.commitAndOpenSnapshot()
            completed = true
            return snapshot
        } finally {
            if (!completed) editor.abort()
        }
    }

    private data class ComicPage(
        val chapterId: Int,
        val index: Int,
    )

    private companion object {
        private val archiveLocks = List(8) { Mutex() }
        private var cache: DiskCache? = null

        @Synchronized
        private fun archiveCache(directory: File): DiskCache =
            cache ?: DiskCache
                .Builder()
                .directory(File(directory, "comic-archives"))
                .maxSizeBytes(ARCHIVE_CACHE_BYTES)
                .build()
                .also { cache = it }
    }
}

private suspend fun InputStream.copyLimited(
    output: OutputStream,
    limit: Long,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = limit
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = read(buffer)
        if (count < 0) break
        if (count > remaining) throw IOException("Comic data exceeds its cache limit")
        output.write(buffer, 0, count)
        remaining -= count
    }
}
