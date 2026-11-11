package net.dom53.inkita.core.update

import java.math.BigInteger

internal class ReleaseVersion private constructor(
    private val core: List<BigInteger>,
    private val prerelease: List<String>,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for (index in core.indices) {
            val result = core[index].compareTo(other.core[index])
            if (result != 0) return result
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return other.prerelease.size.compareTo(prerelease.size)
        }
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[index]
            val right = other.prerelease[index]
            val leftNumber = left.takeIf { it.all(Char::isDigit) }?.toBigInteger()
            val rightNumber = right.takeIf { it.all(Char::isDigit) }?.toBigInteger()
            val result =
                when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> left.compareTo(right)
                }
            if (result != 0) return result
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern =
            Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?""")

        fun parse(value: String): ReleaseVersion? {
            val match = pattern.matchEntire(value) ?: return null
            val prerelease =
                match.groupValues[4]
                    .takeIf { it.isNotEmpty() }
                    ?.split('.')
                    .orEmpty()
            val metadata =
                match.groupValues[5]
                    .takeIf { it.isNotEmpty() }
                    ?.split('.')
                    .orEmpty()
            if (prerelease.any { it.isEmpty() || (it.length > 1 && it.startsWith('0') && it.all(Char::isDigit)) }) return null
            if (metadata.any { it.isEmpty() }) return null
            return ReleaseVersion(match.groupValues.subList(1, 4).map { it.toBigInteger() }, prerelease)
        }
    }
}

internal fun isNewerRelease(
    currentName: String,
    currentCode: Long,
    remoteName: String,
    remoteCode: Long,
): Boolean {
    val current = ReleaseVersion.parse(currentName) ?: return false
    val remote = ReleaseVersion.parse(remoteName) ?: return false
    return remote > current || remoteCode > currentCode
}
