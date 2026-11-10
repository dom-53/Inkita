package net.dom53.inkita

import net.dom53.inkita.core.update.ReleaseVersion
import net.dom53.inkita.core.update.isNewerRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun ordersNumericPrereleasePartsNumerically() {
        assertTrue(isNewerRelease("0.3.3-beta.9", 11, "0.3.3-beta.10", 11))
        assertFalse(isNewerRelease("0.3.3-beta.10", 11, "0.3.3-beta.9", 11))
    }

    @Test
    fun followsSemanticVersionPrecedence() {
        val ordered =
            listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0")
        ordered.zipWithNext { older, newer ->
            assertTrue("$older < $newer", isNewerRelease(older, 11, newer, 11))
            assertFalse("$newer > $older", isNewerRelease(newer, 11, older, 11))
        }
    }

    @Test
    fun comparesMajorMinorAndPatchBeforePrerelease() {
        assertTrue(isNewerRelease("1.9.0", 11, "1.10.0-alpha", 11))
        assertTrue(isNewerRelease("1.0.9", 11, "1.0.10", 11))
        assertFalse(isNewerRelease("2.0.0-alpha", 11, "1.99.99", 11))
    }

    @Test
    fun ignoresBuildMetadata() {
        assertEquals(0, requireNotNull(ReleaseVersion.parse("1.0.0+001")).compareTo(requireNotNull(ReleaseVersion.parse("1.0.0+002"))))
        assertFalse(isNewerRelease("1.0.0-beta.1+first", 11, "1.0.0-beta.1+second", 11))
    }

    @Test
    fun supportsLargeNumbersAndHyphenatedIdentifiers() {
        assertTrue(isNewerRelease("1.0.0-beta.999999999999999999999", 11, "1.0.0-beta.1000000000000000000000", 11))
        assertTrue(isNewerRelease("999999999999999999999.0.0", 11, "1000000000000000000000.0.0", 11))
        assertTrue(isNewerRelease("1.0.0-0", 11, "1.0.0--1", 11))
    }

    @Test
    fun rejectsMalformedVersionsWithoutThrowing() {
        listOf("", "1.2", "01.2.3", "1.2.3-beta.01", "1.2.3-beta..1", "1.2.3+", "1.2.3+abc.", "v1.2.3").forEach {
            assertNull(it, ReleaseVersion.parse(it))
            assertFalse(isNewerRelease("1.0.0", 1, it, 2))
        }
        assertFalse(isNewerRelease("invalid", 1, "1.0.0", 2))
    }

    @Test
    fun retainsUpdatesForHigherVersionCodes() {
        assertTrue(isNewerRelease("1.0.0", 11, "1.0.0", 12))
        assertFalse(isNewerRelease("1.0.0", 11, "1.0.0", 11))
        assertFalse(isNewerRelease("1.0.0", 11, "1.0.0", 0))
    }
}
