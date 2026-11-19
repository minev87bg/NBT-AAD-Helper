package com.nbtaad.editor.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class InstalledCheckTest {
    private val zone = ZoneId.of("Europe/Sofia")
    private val now = Instant.parse("2026-11-19T19:20:00Z")

    private fun sum(c: Char) = c.toString().repeat(64)

    /** A release as GitHub lists it: a loose nbt-aad (with a checksum unless [sha] is null) and Releases.zip. */
    private fun rel(
        tag: String, sha: String?, published: String, title: String = tag, pre: Boolean = false, daemonSize: Long = 900_000,
    ) = ReleaseInfo(
        tag, title, pre, published, "", "https://github.com/x/$tag",
        listOf(AssetInfo("nbt-aad", daemonSize, "https://x/$tag/nbt-aad", sha), AssetInfo("Releases.zip", 300_000, "https://x/$tag/r.zip", sum('0'))),
    )

    // newest first, as GitHub lists them
    private val r112 = rel("v1.9.112", sum('a'), "2026-11-19T10:00:00Z")
    private val r111 = rel("v1.9.111-cluster", sum('b'), "2026-11-18T10:00:00Z", title = "v1.9.111 - Gauge cluster type in the menu")
    private val r94 = rel("v1.9.94-wfdssid", sum('c'), "2026-11-17T10:00:00Z")
    private val old = rel("v1.9.50", null, "2026-10-01T10:00:00Z")
    private val releases = listOf(r112, r111, r94, old)

    @Test
    fun theSameBytesAreAnOfficialRelease() {
        val v = InstalledCheck.judge("v1.9.112", sum('a'), 900_000, releases)
        assertEquals(Origin.OFFICIAL, v.origin)
        assertSame(r112, v.release)
        assertNull("it is the newest", v.newer)
    }

    @Test
    fun theBytesDecideEvenWhenTheVersionNameIsMissingOrWrong() {
        assertSame(r94, InstalledCheck.judge(null, sum('c'), null, releases).release)
        val v = InstalledCheck.judge("v9.9.9-whatever", sum('c'), null, releases)
        assertEquals(Origin.OFFICIAL, v.origin)
        assertSame(r94, v.release)
    }

    @Test
    fun anOlderOfficialReleaseSaysWhichOneIsNewer() {
        val v = InstalledCheck.judge("v1.9.111-cluster", sum('b'), null, releases)
        assertEquals(Origin.OFFICIAL, v.origin)
        assertSame(r112, v.newer)
        val words = InstalledCheck.describe(v, "v1.9.111-cluster", zone, now).detail
        assertTrue(words, words.contains("A newer release is out: v1.9.112, 2026-11-19 12:00  ·  9 h ago."))
    }

    @Test
    fun aPreReleaseIsOfficialButSaysItIsATestBuild() {
        val pre = rel("v1.9.113-beta", sum('d'), "2026-11-19T18:00:00Z", pre = true)
        val list = listOf(pre) + releases
        val v = InstalledCheck.judge("v1.9.113-beta", sum('d'), null, list)
        assertEquals(Origin.OFFICIAL, v.origin)
        assertNull("a pre-release does not make the stable one outdated", InstalledCheck.judge("v1.9.112", sum('a'), null, list).newer)
        val d = InstalledCheck.describe(v, "v1.9.113-beta", zone, now)
        assertEquals("Official pre-release", d.headline)
        assertEquals(Tone.WARN, d.tone)
    }

    @Test
    fun theVersionNameIsMatchedByTheTagOrByTheStartOfTheTitle() {
        assertTrue(InstalledCheck.sameName(r112, "v1.9.112"))
        assertTrue(InstalledCheck.sameName(r112, " V1.9.112 "))
        assertTrue(InstalledCheck.sameName(r111, "v1.9.111-cluster"))
        assertTrue("the title starts with the version", InstalledCheck.sameName(r111, "v1.9.111"))
        assertFalse(InstalledCheck.sameName(r111, "v1.9.11"))
        assertFalse(InstalledCheck.sameName(r112, "v1.9.112-test"))
        assertFalse(InstalledCheck.sameName(r112, ""))
    }

    @Test
    fun aNameWithoutAChecksumIsOnlyAMatch() {
        // v1.9.50 lists no checksum: the name is all there is
        val v = InstalledCheck.judge("v1.9.50", sum('f'), null, releases)
        assertEquals(Origin.MATCHES_NAME, v.origin)
        assertFalse(v.unread)
        val d = InstalledCheck.describe(v, "v1.9.50", zone, now)
        assertEquals("Matches release v1.9.50", d.headline)
        assertTrue(d.detail, d.detail.contains("GitHub lists no checksum"))
    }

    @Test
    fun aFileThatCouldNotBeReadIsMatchedByNameAndSaysSo() {
        val v = InstalledCheck.judge("v1.9.112", null, null, releases)
        assertEquals(Origin.MATCHES_NAME, v.origin)
        assertTrue(v.unread)
        assertTrue(InstalledCheck.describe(v, "v1.9.112", zone, now).detail.contains("could not be read back"))
    }

    @Test
    fun theSameNameWithOtherBytesIsNotTheOfficialFile() {
        val v = InstalledCheck.judge("v1.9.112", sum('9'), 900_000, releases)
        assertEquals(Origin.CHANGED, v.origin)
        assertSame(r112, v.release)
        val d = InstalledCheck.describe(v, "v1.9.112", zone, now)
        assertEquals("Not the official file", d.headline)
        assertEquals(Tone.WARN, d.tone)
    }

    @Test
    fun aDifferentSizeProvesADifferenceWhenThereIsNoChecksum() {
        val noSum = listOf(rel("v1.9.60", null, "2026-10-10T10:00:00Z", daemonSize = 800_000))
        assertEquals(Origin.CHANGED, InstalledCheck.judge("v1.9.60", sum('9'), 800_001, noSum).origin)
        assertEquals(Origin.MATCHES_NAME, InstalledCheck.judge("v1.9.60", sum('9'), 800_000, noSum).origin)
    }

    @Test
    fun aBuildThatIsNotOnThePageIsNotOfficial() {
        val v = InstalledCheck.judge("v1.9.113-test", sum('9'), 900_000, releases)
        assertEquals(Origin.UNLISTED, v.origin)
        assertNull(v.release)
        val d = InstalledCheck.describe(v, "v1.9.113-test", zone, now)
        assertEquals("Not an official release", d.headline)
        assertEquals(Tone.WARN, d.tone)
        // no version inside, only bytes nobody published
        assertEquals(Origin.UNLISTED, InstalledCheck.judge(null, sum('9'), 900_000, releases).origin)
    }

    @Test
    fun withoutTheReleasesOrTheFileNothingIsClaimed() {
        val offline = InstalledCheck.judge("v1.9.112", sum('a'), 900_000, null)
        assertEquals(Origin.UNKNOWN, offline.origin)
        assertEquals(Tone.PLAIN, InstalledCheck.describe(offline, "v1.9.112", zone, now).tone)
        val unreadable = InstalledCheck.judge(null, null, null, releases)
        assertEquals(Origin.UNKNOWN, unreadable.origin)
        assertTrue(InstalledCheck.describe(unreadable, null, zone, now).detail.contains("could not be read"))
    }

    @Test
    fun theOfficialFileIsDescribedWithItsReleaseAndTheLatestMark() {
        val d = InstalledCheck.describe(InstalledCheck.judge("v1.9.112", sum('a'), null, releases), "v1.9.112", zone, now)
        assertEquals("Official release", d.headline)
        assertEquals(Tone.GOOD, d.tone)
        assertEquals(
            "The same file as v1.9.112 on Sq00ky's Releases page, compared byte for byte. " +
                "Published 2026-11-19 12:00  ·  9 h ago. It is the latest release.",
            d.detail,
        )
    }

    @Test
    fun aReleaseWithoutALooseDaemonCanOnlyBeMatchedByName() {
        val zipOnly = ReleaseInfo("v1.9.70", "v1.9.70", false, "2026-10-20T10:00:00Z", "", "u", listOf(AssetInfo("Releases.zip", 1, "u", sum('1'))))
        assertEquals(Origin.MATCHES_NAME, InstalledCheck.judge("v1.9.70", sum('9'), 1, listOf(zipOnly)).origin)
        assertEquals(Origin.UNLISTED, InstalledCheck.judge("v1.9.71", sum('9'), 1, listOf(zipOnly)).origin)
    }
}
