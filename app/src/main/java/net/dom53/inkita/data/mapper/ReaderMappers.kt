package net.dom53.inkita.data.mapper

import net.dom53.inkita.data.api.dto.BookInfoDto
import net.dom53.inkita.data.api.dto.ReaderChapterNavDto
import net.dom53.inkita.data.api.dto.ReaderProgressDto
import net.dom53.inkita.data.api.dto.TimeLeftDto
import net.dom53.inkita.domain.model.ReaderBookInfo
import net.dom53.inkita.domain.model.ReaderChapterNav
import net.dom53.inkita.domain.model.ReaderProgress
import net.dom53.inkita.domain.model.ReaderTimeLeft
import java.time.Instant

private fun String?.toEpochMillis(): Long = runCatching { this?.let { Instant.parse(it).toEpochMilli() } ?: 0L }.getOrDefault(0L)

fun ReaderProgressDto.toDomain(): ReaderProgress =
    ReaderProgress(
        chapterId = chapterId,
        page = pageNum,
        bookScrollId = bookScrollId,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        lastModifiedUtcMillis = lastModifiedUtc.toEpochMillis(),
    )

fun ReaderProgress.toDto(): ReaderProgressDto =
    ReaderProgressDto(
        chapterId = chapterId,
        pageNum = page,
        bookScrollId = bookScrollId,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        lastModifiedUtc = Instant.ofEpochMilli(lastModifiedUtcMillis).toString(),
    )

fun TimeLeftDto.toDomain(): ReaderTimeLeft =
    ReaderTimeLeft(
        minHours = minHours,
        maxHours = maxHours,
        avgHours = avgHours,
    )

fun BookInfoDto.toDomain(): ReaderBookInfo =
    ReaderBookInfo(
        pages = pages,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        title = bookTitle ?: seriesName,
        pageTitle = chapterTitle,
    )

fun ReaderChapterNavDto.toDomain(): ReaderChapterNav {
    val chapter = chapterId ?: id
    return ReaderChapterNav(
        seriesId = seriesId,
        volumeId = volumeId,
        chapterId = chapter,
        pagesRead = pagesRead,
    )
}
