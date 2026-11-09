package net.dom53.inkita.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dom53.inkita.data.api.KavitaApi
import okhttp3.ResponseBody
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private val pdfSignature = "%PDF-".encodeUtf8()

internal suspend fun KavitaApi.fetchPdfForReading(
    chapterId: Int,
    apiKey: String,
    target: File,
): File {
    val readerResponse = getPdf(chapterId, apiKey = apiKey.takeIf { it.isNotBlank() }, extractPdf = false)
    readerResponse.writePdfTo(target)?.let { return it }

    val readerStatus = readerResponse.code()
    if (!readerResponse.isSuccessful && readerStatus !in 500..599) {
        throw IOException("Unable to open PDF (reader HTTP $readerStatus)")
    }

    val downloadResponse = downloadChapterFile(chapterId)
    downloadResponse.writePdfTo(target)?.let { return it }
    val downloadError =
        if (downloadResponse.isSuccessful) "download did not return a PDF" else "download HTTP ${downloadResponse.code()}"
    throw IOException("Unable to open PDF (reader HTTP $readerStatus, $downloadError)")
}

private suspend fun Response<ResponseBody>.writePdfTo(target: File): File? {
    if (!isSuccessful) {
        errorBody()?.close()
        return null
    }
    return body()?.use { pdf ->
        withContext(Dispatchers.IO) {
            val source = pdf.source()
            if (!source.rangeEquals(0, pdfSignature)) return@withContext null

            val directory = target.absoluteFile.parentFile ?: throw IOException("PDF cache directory is unavailable")
            Files.createDirectories(directory.toPath())
            val pending = File.createTempFile("pdf-", ".part", directory)
            try {
                pending.sink().buffer().use { it.writeAll(source) }
                Files.move(pending.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                target
            } finally {
                pending.delete()
            }
        }
    }
}
