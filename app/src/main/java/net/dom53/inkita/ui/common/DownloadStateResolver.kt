package net.dom53.inkita.ui.common

import net.dom53.inkita.data.api.dto.ChapterDto
import net.dom53.inkita.data.api.dto.SeriesDetailDto
import net.dom53.inkita.data.api.dto.VolumeDto
import net.dom53.inkita.data.local.db.entity.DownloadedItemV2Entity
import net.dom53.inkita.domain.model.Format
import java.io.File

object DownloadStateResolver {
    fun resolveSeriesState(
        format: Format?,
        pagesHint: Int?,
        detail: SeriesDetailDto?,
        items: List<DownloadedItemV2Entity>,
    ): DownloadState {
        val completedPages = countCompleted(items, DownloadedItemV2Entity.TYPE_PAGE)
        val completedFiles = countCompleted(items, DownloadedItemV2Entity.TYPE_FILE)
        val expected =
            if (isSingleFileFormat(format)) {
                countChapters(detail)
            } else {
                val pages = pagesHint?.takeIf { it > 0 } ?: sumPages(detail)
                pages.takeIf { it > 0 } ?: 0
            }
        val completed =
            when {
                isSingleFileFormat(format) -> completedFiles
                completedPages > 0 -> completedPages
                else -> completedFiles
            }
        return resolveCompletion(expected, completed)
    }

    fun resolveVolumeState(
        format: Format?,
        volume: VolumeDto,
        items: List<DownloadedItemV2Entity>,
    ): DownloadState {
        val completedPages = countCompleted(items, DownloadedItemV2Entity.TYPE_PAGE)
        val completedFiles = countCompleted(items, DownloadedItemV2Entity.TYPE_FILE)
        val expected =
            if (isSingleFileFormat(format)) {
                volume.chapters?.size ?: 0
            } else {
                volume.pages
                    ?.takeIf { it > 0 }
                    ?: volume.chapters
                        ?.sumOf { it.pages ?: 0 }
                        ?.takeIf { it > 0 }
                    ?: 0
            }
        val completed =
            when {
                isSingleFileFormat(format) -> completedFiles
                completedPages > 0 -> completedPages
                else -> completedFiles
            }
        return resolveCompletion(expected, completed)
    }

    fun resolveChapterState(
        format: Format?,
        chapter: ChapterDto,
        items: List<DownloadedItemV2Entity>,
    ): DownloadState {
        val completedPages = countCompleted(items, DownloadedItemV2Entity.TYPE_PAGE)
        val completedFiles = countCompleted(items, DownloadedItemV2Entity.TYPE_FILE)
        val expected = if (isSingleFileFormat(format)) 1 else chapter.pages?.takeIf { it > 0 } ?: 0
        val completed =
            when {
                isSingleFileFormat(format) -> completedFiles
                completedPages > 0 -> completedPages
                else -> completedFiles
            }
        return resolveCompletion(expected, completed)
    }

    fun resolveCompletion(
        expected: Int,
        completed: Int,
    ): DownloadState =
        when {
            expected > 0 && completed >= expected -> DownloadState.Complete
            completed > 0 -> DownloadState.Partial
            else -> DownloadState.None
        }

    fun isSingleFileFormat(format: Format?): Boolean =
        when (format) {
            Format.Pdf,
            Format.Image,
            Format.Archive,
            -> true
            else -> false
        }

    private fun countChapters(detail: SeriesDetailDto?): Int = collectChapters(detail).size

    private fun sumPages(detail: SeriesDetailDto?): Int =
        collectChapters(detail)
            .sumOf { it.pages ?: 0 }

    private fun collectChapters(detail: SeriesDetailDto?): List<ChapterDto> {
        if (detail == null) return emptyList()
        return buildList {
            detail.volumes?.forEach { volume ->
                volume.chapters?.let { addAll(it) }
            }
            detail.chapters?.let { addAll(it) }
            detail.specials?.let { addAll(it) }
            detail.storylineChapters?.let { addAll(it) }
        }.distinctBy { it.id }
    }

    private fun countCompleted(
        items: List<DownloadedItemV2Entity>,
        type: String,
    ): Int =
        items
            .filter { it.type == type }
            .count { isItemPathPresent(it.localPath) }

    private fun isItemPathPresent(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return if (path.startsWith("content://")) {
            true
        } else {
            File(path).exists()
        }
    }
}
