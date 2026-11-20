package com.nbtaad.editor.contribute

import com.nbtaad.editor.install.RemoteScripts
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the scripts for the sessions with this machine's own sh, grep and dd on a log made like the daemon's, so that the
 * logic of the scripts (which only a car can show in full) is at least checked on a shell.
 */
class SessionScriptsTest {
    private val header = "---- nbt-aad log appended to /mnt/umass00100t11/nbt-aad.log (Save logs to USB, fsync every 2 s) ----"

    private fun session(build: String, gps: List<String>, filler: Int): String = buildString {
        append(header).append('\n')
        append("====================================================\n")
        append("  NBT-AAD: Native Wireless Android Auto Daemon      \n")
        append("  Build: $build (Nov 19 2026 10:31:34)\n")
        append("====================================================\n")
        for (i in 1..filler) {
            append("[AASession] >>> Streaming Media Frame $i on Channel 6 (1205 bytes)\n")
            if (i == 3) for (g in gps) append("[GPS] car GPS fix: 42.697700, 23.321900 speed 0 (UTC $g)\n")
        }
    }

    private val s1 = session("v1.9.110", listOf("2026-11-19 10:00:01"), 40)
    private val s2 = session("v1.9.112", emptyList(), 900)
    private val s3 = session("v1.9.115", listOf("2026-11-20 17:11:23", "2026-11-20 17:15:00"), 2500)

    private fun run(script: String): ByteArray {
        val p = ProcessBuilder("/bin/sh", "-c", script).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val out = p.inputStream.readBytes()
        p.waitFor()
        return out
    }

    private fun tools(): Boolean = File("/bin/sh").canExecute() && listOf("grep", "dd", "cat").all { t ->
        System.getenv("PATH").orEmpty().split(':').any { File(it, t).canExecute() }
    }

    private fun withFile(body: (File, List<LogSession>) -> Unit) {
        assumeTrue(tools())
        val f = File.createTempFile("nbt-aad-sessions", ".log")
        try {
            f.writeBytes((s1 + s2 + s3).toByteArray())
            val list = LogSessions.parse(String(run(RemoteScripts.sessions(f.absolutePath))), f.length())
            body(f, list)
        } finally {
            f.delete()
        }
    }

    @Test
    fun theListingFindsEverySessionAndNoPosition() = withFile { f, list ->
        val answer = String(run(RemoteScripts.sessions(f.absolutePath)))
        assertEquals(3, list.size)
        assertEquals(s1.length.toLong(), list[0].size)
        assertEquals(s2.length.toLong(), list[1].size)
        assertEquals(s3.length.toLong(), list[2].size)
        assertEquals(listOf("v1.9.110", "v1.9.112", "v1.9.115"), list.map { it.build })
        assertEquals(listOf("2026-11-19 10:00:01", "", "2026-11-20 17:11:23"), list.map { it.gps })
        // the car's position stays on the head unit, and only the first GPS time of a run is told
        assertFalse("42.69" in answer)
        assertFalse("23.32" in answer)
        assertFalse("17:15:00" in answer)
        assertTrue(answer.lines().size < 20)
    }

    @Test
    fun aMiddleSessionIsCutOutWithDd() = withFile { f, list ->
        val s = list[1]
        val lead = (s.offset % RemoteScripts.DD_BLOCK).toInt()
        val out = run(RemoteScripts.sessionDd(f.absolutePath, s.offset, s.size))
        assertArrayEquals(s2.toByteArray(), LogSessions.trim(out, lead, s.size))
    }

    @Test
    fun theNewestSessionIsReadToTheEndWithDd() = withFile { f, list ->
        val s = list[2]
        val lead = (s.offset % RemoteScripts.DD_BLOCK).toInt()
        val out = run(RemoteScripts.sessionDd(f.absolutePath, s.offset, -1))
        assertArrayEquals(s3.toByteArray(), LogSessions.trim(out, lead, -1))
    }

    @Test
    fun theSameSessionsAreCutOutWithReadAndCat() = withFile { f, list ->
        for ((s, expected) in list.zip(listOf(s1, s2, s3))) {
            val out = run(RemoteScripts.sessionRead(f.absolutePath, s.line))
            // read and cat go on to the end of the file: the app stops at the length, or at the next header
            assertArrayEquals(expected.toByteArray(), LogSessions.trim(out, 0, if (s.last) -1 else s.size))
            assertArrayEquals(expected.toByteArray(), if (s.last) out else LogSessions.cutAtNextHeader(out))
        }
    }

    @Test
    fun aFileThatIsNotThereIsToldSo() {
        assumeTrue(tools())
        assertTrue("NOFILE" in String(run(RemoteScripts.sessions("/nonexistent/nbt-aad.log"))))
    }
}
