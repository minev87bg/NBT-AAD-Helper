package com.nbtaad.editor.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test
    fun theBuildNumberIsTheLastNumberOfTheReleaseTag() {
        assertEquals(57, UpdateLogic.buildOf("v1.0.57"))
        assertEquals(7, UpdateLogic.buildOf("1.0.7"))
        assertEquals(120, UpdateLogic.buildOf(" v1.0.120 "))
        assertNull(UpdateLogic.buildOf("pre-release"))
        assertNull(UpdateLogic.buildOf("dev"))
        assertNull(UpdateLogic.buildOf("v1.0.57-beta"))
    }

    @Test
    fun onlyAHigherBuildIsAnUpdate() {
        assertTrue(UpdateLogic.isNewer(56, 57))
        assertFalse(UpdateLogic.isNewer(57, 57))
        assertFalse(UpdateLogic.isNewer(58, 57))
        assertTrue(UpdateLogic.isNewer(1, 5))   // a local "dev" build (code 1) is offered every release
    }

    @Test
    fun theApkIsFoundAmongTheReleaseFiles() {
        assertEquals(1, UpdateLogic.pickApk(listOf("notes.txt", "NBT-AAD-Helper-v1.0.57.APK", "other.apk")))
        assertNull(UpdateLogic.pickApk(listOf("notes.txt", "source.zip")))
        assertNull(UpdateLogic.pickApk(emptyList()))
    }

    @Test
    fun githubsChecksumIsReadWhenItIsThere() {
        val hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(hex, UpdateLogic.parseDigest("sha256:$hex"))
        assertEquals(hex, UpdateLogic.parseDigest(hex.uppercase()))
        assertNull(UpdateLogic.parseDigest("sha256:abc"))
        assertNull(UpdateLogic.parseDigest("md5:$hex"))
        assertNull(UpdateLogic.parseDigest(""))
        assertNull(UpdateLogic.parseDigest(null))
        assertEquals(hex, UpdateLogic.sha256Hex("abc".toByteArray()))
    }

    @Test
    fun aDamagedDownloadIsRecognised() {
        val sha = UpdateLogic.sha256Hex("abc".toByteArray())
        assertNull(UpdateLogic.intact(3, 3, sha, sha))
        assertNull(UpdateLogic.intact(3, -1, sha, null))   // GitHub did not say size or checksum
        assertTrue(UpdateLogic.intact(2, 3, sha, sha)!!.contains("incomplete"))
        assertTrue(UpdateLogic.intact(3, 3, sha, "0".repeat(64))!!.contains("checksum"))
    }

    @Test
    fun releaseNotesAreShortAndWithoutGitTrailers() {
        val body = "Updater in the app\n\nSecond line\nCo-Authored-By: Someone <a@b.c>\nClaude-Session: https://x\n" +
            "third\nfourth\nfifth"
        assertEquals("Updater in the app\nSecond line\nthird\nfourth\n…", UpdateLogic.shortNotes(body))
        assertEquals("one", UpdateLogic.shortNotes("one"))
        assertEquals("", UpdateLogic.shortNotes(null))
    }
}
