package com.nbtaad.editor.contribute

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LogSessionsTest {
    private val h = "---- nbt-aad log appended to /mnt/umass00100t11/nbt-aad.log (Save logs to USB, fsync every 2 s) ----"

    @Test
    fun theSessionsComeFromTheHeadersWithBuildAndFirstGpsTime() {
        val out = """
            OPTIONS -a -b -n
            H 1:0:$h
            B 5:310:  Build: v1.9.112 (Nov 19 2026 10:31:34): USB stick found while running
            G 40 2026-11-20 17:07:46
            H 1665:90000:$h
            B 1670:90500:  Build: v1.9.115 (Nov 20 2026 08:00:00)
            H 5360:150000:$h
            G 5522 2026-11-20 17:11:23
            G 5600 2026-11-20 17:15:00
        """.trimIndent()
        val s = LogSessions.parse(out, 200_000)
        assertEquals(3, s.size)
        assertEquals(listOf(1, 2, 3), s.map { it.number })
        assertEquals(listOf(0L, 90_000L, 150_000L), s.map { it.offset })
        assertEquals(listOf(90_000L, 60_000L, 50_000L), s.map { it.size })
        assertEquals(listOf("v1.9.112", "v1.9.115", ""), s.map { it.build })
        assertEquals(listOf("2026-11-20 17:07:46", "", "2026-11-20 17:11:23"), s.map { it.gps })
        assertEquals(listOf(false, false, true), s.map { it.last })
        assertEquals(1665L, s[1].line)
    }

    @Test
    fun withoutByteOffsetsTheSizesAreNotKnown() {
        val out = "OPTIONS -a -n\nH 1:$h\nH 700:$h\nB 705:  Build: v1.9.1 (x)\n"
        val s = LogSessions.parse(out, 5000)
        assertEquals(2, s.size)
        assertEquals(listOf(-1L, -1L), s.map { it.offset })
        assertEquals(listOf(-1L, -1L), s.map { it.size })
        assertEquals(700L, s[1].line)
        assertEquals("v1.9.1", s[1].build)
    }

    @Test
    fun nothingToListIsEmpty() {
        assertTrue(LogSessions.parse("OPTIONS -n\n", 100).isEmpty())
        assertTrue(LogSessions.parse("NOFILE\n", 100).isEmpty())
        // a build line with no header before it belongs to nothing
        assertTrue(LogSessions.parse("B 5:310:  Build: v1\n", 100).isEmpty())
    }

    @Test
    fun aSessionIsCutOutOfWhatTheHeadUnitSent() {
        val session = ("$h\nline a\nline b\n").toByteArray()
        val after = "$h\nnext run\n".toByteArray()
        val lead = 7
        val raw = ByteArray(lead) { 'x'.code.toByte() } + session + after
        assertArrayEquals(session, LogSessions.trim(raw, lead, session.size.toLong()))
        // more than the session was sent and no length is known: all of it stays
        assertEquals(session.size + after.size, LogSessions.trim(raw, lead, -1)!!.size)
        // the cut is not at a header: not the session
        assertNull(LogSessions.trim(raw, lead - 1, session.size.toLong()))
        assertNull(LogSessions.trim("".toByteArray(), 0, 10))
        assertNull(LogSessions.trim(session, 1000, 10))
    }

    @Test
    fun aSessionWithNoKnownSizeIsCutAtTheNextHeader() {
        val first = "$h\nline a\nline b\n".toByteArray()
        val both = first + "$h\nnext run\n".toByteArray()
        assertArrayEquals(first, LogSessions.cutAtNextHeader(both))
        assertArrayEquals(first, LogSessions.cutAtNextHeader(first))
    }

    @Test
    fun theGpsTimeIsShownInThePhonesTime() {
        assertEquals("20 Nov 19:07", LogSessions.localTime("2026-11-20 17:07:46", ZoneId.of("Europe/Sofia")))
        assertEquals("20 Nov 17:07", LogSessions.localTime("2026-11-20 17:07:46", ZoneId.of("UTC")))
        assertEquals("", LogSessions.localTime("", ZoneId.of("UTC")))
        assertEquals("", LogSessions.localTime("not a time", ZoneId.of("UTC")))
    }

    @Test
    fun namesAndWords() {
        assertEquals("nbt-aad-session-4.log", LogSessions.fileName(4))
        assertEquals("session 5", LogSessions.numbersText(listOf(5)))
        assertEquals("sessions 3, 5", LogSessions.numbersText(setOf(5, 3)))
        assertTrue(HeadLog("/mnt/umass00100t11/nbt-aad.log", 1).hasSessions)
        assertFalse(HeadLog("/dev/shmem/nbt-aad.log", 1).hasSessions)
        assertFalse(HeadLog("/mnt/umass00100t11/nbt-aad-app.log", 1).hasSessions)
    }
}
