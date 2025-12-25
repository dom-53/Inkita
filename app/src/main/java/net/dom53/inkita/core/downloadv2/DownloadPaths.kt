package net.dom53.inkita.core.downloadv2

import android.content.Context
import java.io.File

object DownloadPaths {
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

    fun epubPageFileName(
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
        page: Int,
    ): String {
        val volume = volumeId ?: 0
        return "${seriesId}_${volume}-${chapterId}-${page}.html"
    }

    fun epubAssetsDir(chapterDir: File): File = File(chapterDir, "assets")

    fun pdfFile(
        context: Context,
        seriesId: Int,
        volumeId: Int?,
        chapterId: Int,
    ): File {
        val chapterDir = chapterDir(context, seriesId, volumeId, chapterId)
        val volume = volumeId ?: 0
        return File(chapterDir, "${seriesId}_${volume}-${chapterId}.pdf")
    }
}
