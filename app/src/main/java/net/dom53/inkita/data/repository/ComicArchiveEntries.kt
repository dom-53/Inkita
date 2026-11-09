package net.dom53.inkita.data.repository

import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
private val naturalParts = Regex("(\\d+)|(\\D+)")

internal fun ZipFile.comicImageEntries(): List<ZipEntry> =
    entries()
        .asSequence()
        .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase(Locale.US) in imageExtensions }
        .sortedWith { a, b -> naturalCompare(a.name, b.name) }
        .toList()

private fun naturalCompare(
    a: String,
    b: String,
): Int {
    val partsA = naturalParts.findAll(a.lowercase(Locale.US)).map { it.value }.toList()
    val partsB = naturalParts.findAll(b.lowercase(Locale.US)).map { it.value }.toList()
    for (index in 0 until minOf(partsA.size, partsB.size)) {
        val partA = partsA[index]
        val partB = partsB[index]
        val numberA = partA.trimStart('0')
        val numberB = partB.trimStart('0')
        val comparison =
            if (partA.all(Char::isDigit) && partB.all(Char::isDigit)) {
                compareValuesBy(numberA, numberB, String::length, { it })
            } else {
                partA.compareTo(partB)
            }
        if (comparison != 0) return comparison
    }
    return partsA.size.compareTo(partsB.size)
}
