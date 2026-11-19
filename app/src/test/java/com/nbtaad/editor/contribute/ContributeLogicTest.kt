package com.nbtaad.editor.contribute

import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributeLogicTest {
    // ---- hiding the secrets ----

    @Test
    fun theWifiPasswordOfTheDaemonsLinesIsHidden() {
        val log = "[Wireless] Wi-Fi Direct AP: ssid='BMW 1234', passphrase from /fs/sda0/x/key.txt: 'my pass word it's!' " +
            "(18 chars, decoded; type it into the phone's Wi-Fi settings to join the group by hand)\n" +
            "[Wireless] Wi-Fi Direct AP: passphrase from /a/b: 'abc12345' (decoded, 8 chars; type it on the phone's Wi-Fi settings)\n"
        val m = LogMask.mask(log)
        assertEquals(2, m.passwords)
        assertFalse(m.text.contains("my pass"))
        assertFalse(m.text.contains("abc12345"))
        assertTrue(m.text.contains("ssid='BMW 1234', passphrase from /fs/sda0/x/key.txt: '<hidden>' (18 chars, decoded;"))
        assertTrue(m.text.contains("passphrase from /a/b: '<hidden>' (decoded, 8 chars;"))
    }

    @Test
    fun otherWaysOfWritingAPasswordAreHiddenToo() {
        val m = LogMask.mask("psk='abc def'\nPassword: hunter2 next\nwpa passphrase=Secret99,x\nPASSPHRASE = 'x y z'")
        assertEquals(4, m.passwords)
        assertEquals("psk='<hidden>'\nPassword: <hidden> next\nwpa passphrase=<hidden>,x\nPASSPHRASE = '<hidden>'", m.text)
    }

    @Test
    fun linesWithoutAPasswordAreLeftAlone() {
        val same = "[Wireless] CarPlay AP config: ssid='X' passphrase=(read) bssid='aa:bb' security=4\n" +
            "[Wireless] CarPlay AP config: ssid='X' passphrase=(missing) bssid='' security=0\n" +
            "[WfdOwn] nbt-aad's passphrase is set again before the next phone link\n" +
            "[Knob] key=12 pressed\n"
        val m = LogMask.mask(same)
        assertEquals(same, m.text)
        assertEquals(0, m.passwords)
        assertFalse(m.any)
    }

    @Test
    fun hidingTwiceChangesNothingMore() {
        val once = LogMask.mask("passphrase from /a: 'secret pw' (9 chars, decoded; x) and psk=abc")
        val twice = LogMask.mask(once.text)
        assertEquals(once.text, twice.text)
        assertEquals(0, twice.passwords)
    }

    @Test
    fun aVinIsKeptAsItsFirstThreeAndLastFourCharacters() {
        val m = LogMask.mask("VIN WBAKM42050C233076 at 192.168.0.8, build NBTEVOJACINTODIAG, number 12345678901234567, WZ1DB0105LW012345.")
        assertEquals(2, m.vins)
        assertEquals("VIN WBA…3076 at 192.168.0.8, build NBTEVOJACINTODIAG, number 12345678901234567, WZ1…2345.", m.text)
    }

    // ---- finding the logs ----

    private val listing = """
        -rw-r--r--    1 root      root           46482 Nov 19 21:52 /dev/shmem/nbt-aad.log
        -rw-r--r--    1 root      root            1200 Nov 19 21:50 /dev/shmem/nbt-aad-app.log
        -rw-r--r--    1 root      root           46482 Nov 19 21:52 /net/hu-omap/dev/shmem/nbt-aad.log
        -rw-r--r--    1 root      root         5242880 Nov  2  2026 /mnt/umass01200t7/nbt-aad.old.log
        drwxr-xr-x    2 root      root               0 Nov 19 21:52 /mnt/umass01200t7/nbt-aad-dir.log
        lrwxrwxrwx    1 root      root              12 Nov 19 21:52 /mnt/umass01200t7/nbt-aad-link.log -> /somewhere.log
        ls: /net/hu-omap/mnt/umass*/nbt-aad*.log: No such file or directory
    """.trimIndent()

    @Test
    fun theListingGivesTheLogsWithSizesOnceEach() {
        val logs = LogFinder.parse(listing)
        assertEquals(listOf("nbt-aad.log", "nbt-aad-app.log", "nbt-aad.old.log"), logs.map { it.name })
        assertEquals(listOf(46482L, 1200L, 5242880L), logs.map { it.size })
        assertEquals("/dev/shmem/nbt-aad.log", logs[0].path)               // the first way in is kept
        assertEquals("in memory, gone after a reboot", logs[0].place)
        assertEquals("on the USB stick", logs[2].place)
        assertEquals("Nov 19 21:52", logs[0].stamp)
    }

    @Test
    fun aListingWithoutSizesStillGivesTheFiles() {
        val logs = LogFinder.parse("-rw-r--r-- /dev/shmem/nbt-aad.log\n")
        assertEquals(1, logs.size)
        assertEquals(-1L, logs[0].size)
        assertEquals("size unknown", LogFinder.sizeText(-1))
        assertEquals(emptyList<HeadLog>(), LogFinder.parse(""))
    }

    @Test
    fun sizesAreTold() {
        assertEquals("12 B", LogFinder.sizeText(12))
        assertEquals("46 KB", LogFinder.sizeText(46482))
        assertEquals("5.2 MB", LogFinder.sizeText(5242880))
    }

    // ---- the zip ----

    private val now = ZonedDateTime.of(2026, 11, 19, 23, 1, 0, 0, ZoneId.of("Europe/Sofia"))
    private val info = PackInfo("1.0.30 (code 30)", "Android 16, motorola", now, "v1.9.112", "Issue #187: it crashes", 2, 1)

    @Test
    fun theZipHasAboutFirstAndThenTheFiles() {
        val zip = LogPack.zip(
            listOf(PackEntry("nbt-aad.log", "hello".toByteArray()), PackEntry("nbt-aad.log", "world".toByteArray())),
            info,
        )
        val got = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                got[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(listOf("about.txt", "nbt-aad.log", "nbt-aad-2.log"), got.keys.toList())
        assertEquals("hello", got["nbt-aad.log"])
        assertEquals("world", got["nbt-aad-2.log"])
        val about = got["about.txt"]!!
        assertTrue(about.contains("App: 1.0.30 (code 30)"))
        assertTrue(about.contains("NBT-AAD on the head unit: v1.9.112"))
        assertTrue(about.contains("For: Issue #187: it crashes"))
        assertTrue(about.contains("Hidden before packing: 2 WiFi passwords, 1 VIN"))
        assertTrue(about.contains("- nbt-aad-2.log (5 bytes)"))
    }

    @Test
    fun theNamesAreMadeUniqueAndTheZipIsNamedByTime() {
        assertEquals(listOf("a.log", "a-2.log", "A-3.log", "b"), LogPack.uniqueNames(listOf("a.log", "a.log", "A.log", "b")))
        assertEquals("nbt-aad-logs-20261119-2301.zip", LogPack.zipName(now))
    }
}
