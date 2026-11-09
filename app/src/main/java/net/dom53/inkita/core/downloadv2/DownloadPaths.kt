package net.dom53.inkita.core.downloadv2

import android.content.Context
import java.io.File

object DownloadPaths {
    const val ASSETS_DIR = "assets"

    fun baseDir(context: Context): File =
        context.getExternalFilesDir("download")
            ?: File(context.filesDir, "download").apply { mkdirs() }

    fun seriesDir(
        context: Context,
        seriesId: Int,
    ): File = File(baseDir(context), seriesId.toString())

    fun volumesDir(
        context: Context,
        seriesId: Int,
    ): File = File(seriesDir(context, seriesId), "volumes")

    fun volumeChaptersDir(
        context: Context,
        seriesId: Int,
        volumeId: Int,
    ): File = File(File(volumesDir(context, seriesId), volumeId.toString()), "chapters")

    fun chaptersDir(
        context: Context,
        seriesId: Int,
    ): File = File(seriesDir(context, seriesId), "chapters")

    fun specialsDir(
        context: Context,
        seriesId: Int,
    ): File = File(seriesDir(context, seriesId), "specials")

    fun chapterDir(
        context: Context,
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): File =
        if (volumeId != null) {
            File(volumeChaptersDir(context, seriesId, volumeId), chapterId.toString())
        } else {
            File(chaptersDir(context, seriesId), chapterId.toString())
        }

    fun specialChapterDir(
        context: Context,
        seriesId: Int,
        chapterId: Int,
    ): File = File(specialsDir(context, seriesId), chapterId.toString())

    fun seriesRelativeDir(seriesId: Int): String = seriesId.toString()

    fun volumeChaptersRelativeDir(
        seriesId: Int,
        volumeId: Int,
    ): String = "${seriesRelativeDir(seriesId)}/volumes/$volumeId/chapters"

    fun chaptersRelativeDir(seriesId: Int): String = "${seriesRelativeDir(seriesId)}/chapters"

    fun chapterRelativeDir(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): String =
        if (volumeId != null) {
            "${volumeChaptersRelativeDir(seriesId, volumeId)}/$chapterId"
        } else {
            "${chaptersRelativeDir(seriesId)}/$chapterId"
        }

    fun epubAssetsRelativeDir(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): String = "${chapterRelativeDir(seriesId, volumeId, chapterId)}/$ASSETS_DIR"

    fun epubPageFileName(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
        page: Int,
    ): String {
        val volume = volumeId ?: 0
        return "${seriesId}_$volume-$chapterId-$page.html"
    }

    fun epubAssetsDir(chapterDir: File): File = File(chapterDir, "assets")

    fun epubPageRelativePath(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
        page: Int,
    ): String = "${chapterRelativeDir(seriesId, volumeId, chapterId)}/${epubPageFileName(seriesId, volumeId, chapterId, page)}"

    fun pdfFile(
        context: Context,
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): File {
        val chapterDir = chapterDir(context, seriesId, volumeId, chapterId)
        val volume = volumeId ?: 0
        return File(chapterDir, "${seriesId}_$volume-$chapterId.pdf")
    }

    fun pdfRelativePath(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): String {
        val volume = volumeId ?: 0
        return "${chapterRelativeDir(seriesId, volumeId, chapterId)}/${seriesId}_$volume-$chapterId.pdf"
    }

    fun pdfTempDir(context: Context): File = File(context.cacheDir, "pdf-temp")

    fun pdfTempFile(
        context: Context,
        chapterId: Int,
    ): File = File(pdfTempDir(context), "pdf-$chapterId.pdf")

    fun cbzFile(
        context: Context,
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): File {
        val chapterDir = chapterDir(context, seriesId, volumeId, chapterId)
        val volume = volumeId ?: 0
        return File(chapterDir, "${seriesId}_$volume-$chapterId.cbz")
    }

    fun cbzRelativePath(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): String {
        val volume = volumeId ?: 0
        return "${chapterRelativeDir(seriesId, volumeId, chapterId)}/${seriesId}_$volume-$chapterId.cbz"
    }

    fun archiveDownloadRelativePath(
        seriesId: Int?,
        volumeId: Int?,
        chapterId: Int?,
        fileName: String,
    ): String =
        when {
            seriesId != null && volumeId != null && chapterId != null -> "${chapterRelativeDir(seriesId, volumeId, chapterId)}/$fileName"
            seriesId != null && chapterId != null -> "${chapterRelativeDir(seriesId, null, chapterId)}/$fileName"
            seriesId != null && volumeId != null -> "${seriesRelativeDir(seriesId)}/volumes/$volumeId/$fileName"
            seriesId != null -> "${seriesRelativeDir(seriesId)}/$fileName"
            else -> "archives/$fileName"
        }

    fun imagePageCacheDir(
        context: Context,
        chapterId: Int,
    ): File = File(context.cacheDir, "image-pages/$chapterId")

    fun imagePageCacheFile(
        context: Context,
        chapterId: Int,
        page: Int,
        extension: String,
    ): File = File(imagePageCacheDir(context, chapterId), "page-$page.$extension")
}
