package com.nbtaad.editor.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstallLogicTest {
    // ---- invented files: the shape of the real ones, none of their content ----

    /** A 32-bit little-endian ELF header; [type] 2 = program, 3 = library; [machine] 40 = ARM. */
    private fun elf(type: Int = 2, machine: Int = 40, qnx: Boolean = true, extra: String = ""): ByteArray {
        val b = ByteArray(2200)
        b[0] = 0x7F; b[1] = 'E'.code.toByte(); b[2] = 'L'.code.toByte(); b[3] = 'F'.code.toByte()
        b[4] = 1; b[5] = 1; b[6] = 1
        b[16] = type.toByte(); b[17] = (type shr 8).toByte()
        b[18] = machine.toByte(); b[19] = (machine shr 8).toByte()
        if (qnx) "/usr/lib/ldqnx.so.2".toByteArray().copyInto(b, 300)
        extra.toByteArray().copyInto(b, 1000)
        return b
    }

    private fun daemon(version: String = "v1.9.91-test") = elf(extra = "NBT-AAD-VERSION=$version\u0000")
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((n, d) in entries) {
                z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val tinyZip = zipOf("a.txt" to "x".toByteArray())

    private fun pluginEntries(prefix: String = "Releases/"): Array<Pair<String, ByteArray>> = arrayOf(
        "${prefix}libNbtAad.so" to elf(type = 3, qnx = false),
        "${prefix}NbtAad.manifest" to "name=NbtAad\n".toByteArray(),
        "${prefix}NbtAad.xml" to "<?xml version=\"1.0\"?><app/>".toByteArray(),
        "${prefix}NbtAad_ALL_Texts.zip" to tinyZip,
        "${prefix}NbtAad_ALL_Images.zip" to tinyZip,
    )

    private fun release(): List<SourceFile> = listOf(
        SourceFile("nbt-aad", daemon()),
        SourceFile("Releases.zip", zipOf(*pluginEntries())),
    )

    // ---- names ----

    @Test
    fun namesAreMatchedLikeABrowserSavesThem() {
        assertEquals("nbt-aad", Names.canonical("nbt-aad"))
        assertEquals("nbt-aad", Names.canonical("nbt-aad (1)"))
        assertEquals("nbt-aad", Names.canonical("download/NBT-AAD"))
        assertEquals("NbtAad_ALL_Texts.zip", Names.canonical("NbtAad_ALL_Texts (2).zip"))
        assertEquals("libNbtAad.so", Names.canonical("Releases/libnbtaad.so"))
        assertNull(Names.canonical("nbt-aad.old"))
        assertNull(Names.canonical("readme.md"))
        assertTrue(Names.isContainer("Releases (3).zip"))
        assertFalse(Names.isContainer("NbtAad_ALL_Texts.zip"))
        assertEquals(7, Names.ALL.size)
    }

    // ---- inspection and the 80 % rule ----

    @Test
    fun aReleaseIsABuild() {
        val p = BuildPackage.of(release())
        assertEquals(Kind.BUILD, p.kind)
        assertEquals(6, p.found.size)
        assertEquals(listOf("pf_aa"), p.missing)
        assertEquals(86, p.percent)
        assertEquals("v1.9.91-test", p.version)
        assertTrue(p.fullPlugin)
        assertTrue(p.problems.toString(), p.problems.isEmpty())
        assertTrue(p.installable)
    }

    @Test
    fun theSameFilesInOneBiggerZipAreABuildToo() {
        val inner = zipOf(*pluginEntries())
        val big = zipOf("build/nbt-aad" to daemon(), "build/pf_aa" to elf(), "build/Releases.zip" to inner, "build/notes.txt" to "hi".toByteArray())
        val p = BuildPackage.of(listOf(SourceFile("test-build.zip", big)))
        assertEquals(Kind.BUILD, p.kind)
        assertEquals(100, p.percent)
        assertTrue(p.installable)
        assertTrue(p.payloads.getValue("libNbtAad.so").from.contains("Releases.zip"))
    }

    @Test
    fun theEightyPercentLine() {
        // 5 of 7 files (71 %) is below the line: the plugin without pf_aa but also without one text file
        val five = listOf(SourceFile("nbt-aad", daemon())) + pluginEntries().take(4).map { SourceFile(it.first, it.second) }
        val p = BuildPackage.of(five)
        assertEquals(5, p.found.size)
        assertEquals(Kind.SOME_FILES, p.kind)
        assertTrue(p.warnings.any { it.contains("5 of 7") })
        assertFalse(p.fullPlugin)
        assertTrue(p.installable)
        // 6 of 7 (86 %) is over the line: a build, even with one plugin file missing
        val six = five + SourceFile("pf_aa", elf())
        assertEquals(Kind.BUILD, BuildPackage.of(six).kind)
    }

    @Test
    fun aSingleDaemonIsATestBuild() {
        val p = BuildPackage.of(listOf(SourceFile("nbt-aad (1)", daemon("v2.0.0-x"))))
        assertEquals(Kind.DAEMON_ONLY, p.kind)
        assertEquals(14, p.percent)
        assertEquals("v2.0.0-x", p.version)
        assertTrue(p.installable)
    }

    @Test
    fun aPluginOnlyUpdateIsRecognised() {
        val p = BuildPackage.of(listOf(SourceFile("Releases.zip", zipOf(*pluginEntries()))))
        assertEquals(Kind.PLUGIN_ONLY, p.kind)
        assertTrue(p.fullPlugin)
        assertTrue(p.installable)
    }

    @Test
    fun otherFilesAreNotABuild() {
        val p = BuildPackage.of(listOf(SourceFile("logs.zip", zipOf("nbt-aad.log" to "x".toByteArray())), SourceFile("photo.jpg", ByteArray(40))))
        assertEquals(Kind.NOTHING, p.kind)
        assertFalse(p.installable)
        assertEquals(0, p.percent)
    }

    // ---- checks of each file ----

    @Test
    fun aProgramForAnotherMachineIsRefused() {
        val x86 = BuildPackage.of(listOf(SourceFile("nbt-aad", elf(machine = 3))))
        assertFalse(x86.installable)
        assertTrue(x86.problems.single().contains("ARM"))
        val text = BuildPackage.of(listOf(SourceFile("nbt-aad", "#!/bin/sh\necho hi\n".toByteArray())))
        assertFalse(text.installable)
        assertTrue(text.problems.single().contains("ELF"))
        val empty = BuildPackage.of(listOf(SourceFile("nbt-aad", ByteArray(0))))
        assertFalse(empty.installable)
    }

    @Test
    fun aDaemonWithoutTheQnxLoaderOnlyGetsAWarning() {
        val p = BuildPackage.of(listOf(SourceFile("nbt-aad", elf(qnx = false))))
        assertTrue(p.installable)
        assertTrue(p.warnings.any { it.contains("ldqnx") })
    }

    @Test
    fun theOtherFilesAreChecked() {
        val badLib = BuildPackage.of(listOf(SourceFile("libNbtAad.so", elf(type = 2))))
        assertFalse(badLib.installable)
        assertFalse(BuildPackage.of(listOf(SourceFile("NbtAad_ALL_Texts.zip", "nope".toByteArray()))).installable)
        assertFalse(BuildPackage.of(listOf(SourceFile("NbtAad.xml", "not xml".toByteArray()))).installable)
        assertFalse(BuildPackage.of(listOf(SourceFile("NbtAad.manifest", ByteArray(100)))).installable)
        assertTrue(BuildPackage.of(listOf(SourceFile("NbtAad.xml", "﻿  <a/>".toByteArray()))).installable)
    }

    @Test
    fun theVersionIsReadFromTheDaemon() {
        assertEquals("v1.9.91-sppreq3", BuildPackage.findVersion("xxNBT-AAD-VERSION=v1.9.91-sppreq3\u0000yy".toByteArray()))
        assertNull(BuildPackage.findVersion("NBT-AAD-VERSION=banana".toByteArray()))
        assertNull(BuildPackage.findVersion("nothing here".toByteArray()))
        assertNull(BuildPackage.findVersion(ByteArray(3)))
    }

    // ---- hostile archives ----

    @Test
    fun entriesWithUnsafePathsAreSkipped() {
        val z = zipOf("../../nbt-aad" to daemon(), "/abs/pf_aa" to elf(), "ok/NbtAad.xml" to "<a/>".toByteArray())
        val p = BuildPackage.of(listOf(SourceFile("x.zip", z)))
        assertEquals(listOf("NbtAad.xml"), p.found)
        assertTrue(p.inspection.notes.count { it.contains("unsafe") } == 2)
    }

    @Test
    fun aDuplicateKeepsTheShallowerFile() {
        val z = zipOf("deep/er/nbt-aad" to daemon("v1.0.0-deep"), "nbt-aad" to daemon("v1.0.1-top"))
        val p = BuildPackage.of(listOf(SourceFile("x.zip", z)))
        assertEquals("v1.0.1-top", p.version)
        assertTrue(p.inspection.notes.any { it.contains("twice") })
    }

    @Test
    fun tooManyEntriesOrTooMuchDataIsRefused() {
        val many = zipOf(*Array(Limits.ENTRIES + 5) { "f$it.txt" to ByteArray(1) })
        val p = BuildPackage.of(listOf(SourceFile("many.zip", many)))
        assertNotNull(p.inspection.refused)
        assertFalse(p.installable)
        // an entry above the per-file cap: a long run of zeros compresses to almost nothing
        val bomb = zipOf("nbt-aad" to ByteArray((Limits.ENTRY + 1).toInt()))
        assertTrue(bomb.size < 200_000)
        val q = BuildPackage.of(listOf(SourceFile("bomb.zip", bomb)))
        assertNotNull(q.inspection.refused)
        assertFalse(q.installable)
    }

    @Test
    fun aBrokenZipDoesNotCrash() {
        val z = zipOf("nbt-aad" to daemon())
        val cut = z.copyOf(40)   // the header of the first entry and a few bytes of its data
        val p = BuildPackage.of(listOf(SourceFile("cut.zip", cut)))
        assertFalse(p.installable)
        assertTrue(p.inspection.notes.isNotEmpty() || p.found.isEmpty())
    }

    @Test
    fun archivesInsideArchivesStopAtTwoLevels() {
        val l3 = zipOf("NbtAad.xml" to "<a/>".toByteArray())
        val l2 = zipOf("c.zip" to l3)
        val l1 = zipOf("b.zip" to l2)
        val p = BuildPackage.of(listOf(SourceFile("a.zip", l1)))
        assertEquals(emptyList<String>(), p.found)
        assertTrue(p.inspection.notes.any { it.contains("archive") })
    }

    // ---- checksum, as the head unit's cksum prints it ----

    @Test
    fun cksumMatchesThePosixExample() {
        assertEquals(930766865L, PosixCksum.crc("123456789".toByteArray()))
        assertEquals(4294967295L, PosixCksum.crc(ByteArray(0)))
        assertEquals(930766865L to 9L, PosixCksum.parse("930766865 9 /opt/aa/nbt-aad.new\n"))
        assertEquals(1L to 2L, PosixCksum.parse("\n  1   2  name"))
        assertNull(PosixCksum.parse("cksum: not found"))
        assertNull(PosixCksum.parse(""))
    }

    // ---- reading what the head unit prints ----

    @Test
    fun theFoldersAreLocatedFromTheScriptOutput() {
        val both = "P \nAA /opt/aa\nP /net/hu-omap/fs/sda0\nAA /net/hu-omap/fs/sda0/opt/aa\n" +
            "PL /net/hu-omap/fs/sda0/opt/hmi/ID5/data/ro/Plugins/NbtAad\nP /net/hu-omap\n"
        assertEquals(
            "/net/hu-omap/fs/sda0/opt/aa" to "/net/hu-omap/fs/sda0/opt/hmi/ID5/data/ro/Plugins/NbtAad",
            RemoteScripts.parseLocate(both),
        )
        assertEquals("/opt/aa" to null, RemoteScripts.parseLocate("P\nAA /opt/aa\nP /x\n"))
        assertEquals(null to null, RemoteScripts.parseLocate("P\nP /x\n"))
        assertEquals(null to null, RemoteScripts.parseLocate(""))
    }

    @Test
    fun theStatusIsReadFromTheScriptOutput() {
        val out = "F nbt-aad    8421376\nF nbt-aad.old 8000000\nOLDPLUGIN\nL libNbtAad.so   1234\nL NbtAad.xml 5\n" +
            "NBT-AAD-VERSION=v1.9.91-x\n"
        val s = RemoteScripts.parseStatus("/opt/aa", "/p", out)
        assertTrue(s.hasDaemon)
        assertTrue(s.hasPreviousDaemon)
        assertTrue(s.hasOldPlugin)
        assertTrue(s.hasPrevious)
        assertEquals(8421376L, s.files["nbt-aad"])
        assertEquals(2, s.pluginFiles.size)
        assertEquals("v1.9.91-x", s.version)
        val none = RemoteScripts.parseStatus("/opt/aa", null, "")
        assertFalse(none.hasDaemon)
        assertFalse(none.hasPrevious)
        assertNull(none.version)
    }

    @Test
    fun theDaemonProcessIsFoundInPidinOutput() {
        val out = "    19320   1 usr/aa/nbt-aad   10r RECEIVE 1\n  7 1 other 10r\n  88 1 grep nbt-aad\n"
        assertEquals(listOf(19320), RemoteScripts.parsePids(out))
        assertEquals(emptyList<Int>(), RemoteScripts.parsePids(""))
    }

    // ---- releases ----

    @Test
    fun onlyTheFilesOfABuildAreDownloaded() {
        fun a(n: String, s: Long = 10) = AssetInfo(n, s, "https://x/$n", null)
        val r = ReleaseInfo(
            "v1.9.91", "t", false, "2026-06-01T10:00:00Z", "", "u",
            listOf(a("nbt-aad", 8_000_000), a("Releases.zip", 2_000_000), a("com.github.sq00ky.android-auto-1.9.91.nbtpkg"), a("nbtpkg-record.json")),
        )
        assertEquals(listOf("nbt-aad", "Releases.zip"), ReleaseLogic.wanted(r).map { it.name })
        assertTrue(ReleaseLogic.installable(r))
        assertEquals(10_000_000L, ReleaseLogic.downloadSize(r))
        assertFalse(ReleaseLogic.installable(r.copy(assets = listOf(a("notes.txt")))))
        assertEquals("2026-06-01", ReleaseLogic.day(r.published))
        assertEquals("1.0 MB".length, ReleaseLogic.mb(1024 * 1024).length)
    }
}
