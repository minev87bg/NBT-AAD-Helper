package com.nbtaad.editor.log

import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTest {
    private val utc = ZoneId.of("UTC")

    private fun tempDir(): File = java.nio.file.Files.createTempDirectory("applog").toFile()

    @Test
    fun aVinIsMaskedWhereverItAppears() {
        assertEquals("WBA…3076", LogText.mask("WBAKM42050C233076"))
        assertEquals("Car WBA…3076, head unit 10.0.0.1", LogText.mask("Car WBAKM42050C233076, head unit 10.0.0.1"))
        // not a VIN: too short, or letters that a VIN never has
        assertEquals("WBAKM4205", LogText.mask("WBAKM4205"))
        assertEquals("IOQIOQIOQIOQIOQIO", LogText.mask("IOQIOQIOQIOQIOQIO"))
        assertEquals("nbt-aad-v1.0.17", LogText.mask("nbt-aad-v1.0.17"))
    }

    @Test
    fun aLineHasTimeLevelTagAndTheTextIndentedWhenItHasSeveralLines() {
        val l = LogText.line(0L, 'I', "Detect", "first\nsecond", utc)
        assertEquals("1970-01-01 00:00:00.000 I/Detect: first\n    second", l)
        assertTrue(LogText.line(1_700_000_000_123L, 'E', "X", "a", utc).startsWith("2023-11-14 22:13:20.123 E/X: a"))
    }

    @Test
    fun aVeryLongTextIsCut() {
        val l = LogText.line(0L, 'D', "SSH", "x".repeat(10_000), utc)
        assertTrue(l.length < 3200)
        assertTrue(l.contains("more characters"))
    }

    @Test
    fun aStackNamesTheExceptionItsCauseAndWhereItHappened() {
        val s = LogText.stack(IllegalStateException("outer", java.io.IOException("inner")))
        assertTrue(s.startsWith("java.lang.IllegalStateException: outer"))
        assertTrue(s.contains("\nCaused by: java.io.IOException: inner"))
        assertTrue(s.contains("    at "))
    }

    @Test
    fun aCommandIsShownByItsFirstLine() {
        assertEquals("ls -l /opt/aa", LogText.brief("\n  ls -l /opt/aa\nmore\nlines"))
        assertEquals("a".repeat(110) + "…", LogText.brief("a".repeat(300)))
    }

    @Test
    fun theLogKeepsWhatWasWrittenAndCanBeCleared() {
        val f = LogFile(tempDir())
        assertEquals("", f.readAll())
        f.append("one")
        f.append("two")
        assertEquals("one\ntwo\n", f.readAll())
        assertTrue(f.size() > 0)
        f.clear()
        assertEquals("", f.readAll())
        assertEquals(0L, f.size())
    }

    @Test
    fun theLogRotatesInsteadOfGrowingWithoutEnd() {
        val f = LogFile(tempDir(), maxBytes = 100)
        for (i in 1..40) f.append("line number $i of the log")
        val all = f.readAll()
        assertTrue(all.contains("line number 40 of the log"))      // the newest is there
        assertFalse(all.contains("line number 1 of the log\n"))    // the oldest is gone
        assertTrue(f.size() < 600)
    }

    @Test
    fun theAppLogWritesNothingBeforeItIsSetUpAndMasksVinsAfter() {
        AppLog.i("X", "before: WBAKM42050C233076")
        assertEquals("", AppLog.text())
        AppLog.setup(tempDir())
        AppLog.i("Detect", "Car WBAKM42050C233076 found")
        AppLog.e("Install", "failed", java.io.IOException("boom"))
        val t = AppLog.text()
        assertTrue(t.contains("I/Detect: Car WBA…3076 found"))
        assertTrue(t.contains("E/Install: failed\n    java.io.IOException: boom"))
        assertFalse(t.contains("WBAKM42050C233076"))
        AppLog.clear()
        assertFalse(AppLog.text().contains("Detect"))
        assertTrue(AppLog.text().contains("Log cleared."))
    }
}
