package com.nbtaad.editor.contribute

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One run of the daemon in the log on the USB stick (the daemon appends to that file at every start, so it keeps a run
 * after the other, each beginning with a header line).
 */
class LogSession(
    /** 1 is the oldest. */
    val number: Int,
    /** The line of its header, counted from 1. */
    val line: Long,
    /** The byte where its header starts; -1 when the head unit's grep does not say. */
    val offset: Long,
    /** Its bytes, up to the next header or the end of the file; -1 when not known. */
    val size: Long,
    /** "v1.9.112", or "" when the run did not say. */
    val build: String,
    /** The first GPS time the car gave in the run, "2026-11-20 17:07:46" (UTC), or "". */
    val gps: String,
    /** The newest one: it may still be written to. */
    val last: Boolean,
)

/**
 * The list of sessions in a log on the stick, from what the head unit's script says ([RemoteScripts.sessions]), and the
 * cutting of one session out of what the head unit sends back. Pure, so it is tested.
 */
object LogSessions {
    /** How every session of the daemon's log on the stick begins. */
    const val HEADER = "---- nbt-aad log appended to "

    private val H = Regex("""^H (\d+):(?:(\d+):)?${Regex.escape(HEADER)}""")
    private val B = Regex("""^B \d+:(?:\d+:)?\s*Build: (\S+)""")
    private val G = Regex("""^G \d+ (\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")

    /**
     * The sessions of the script's answer: `H line:byte:header`, then the `B` (build) and `G` (first GPS time) lines of
     * that session. Without byte offsets (a grep that has no -b) the sizes are not known either. [fileSize] is the size
     * of the whole file (-1 when not known), which ends the last session.
     */
    fun parse(out: String, fileSize: Long): List<LogSession> {
        class Raw(val line: Long, val offset: Long) {
            var build = ""
            var gps = ""
        }
        val raws = ArrayList<Raw>()
        for (row in out.lines()) {
            val h = H.find(row)
            if (h != null) {
                raws += Raw(h.groupValues[1].toLong(), h.groupValues[2].toLongOrNull() ?: -1L)
                continue
            }
            val cur = raws.lastOrNull() ?: continue
            B.find(row)?.let { if (cur.build.isEmpty()) cur.build = it.groupValues[1] }
            G.find(row)?.let { if (cur.gps.isEmpty()) cur.gps = it.groupValues[1] }
        }
        val haveOffsets = raws.isNotEmpty() && raws.all { it.offset >= 0 }
        return raws.mapIndexed { i, r ->
            val end = if (i + 1 < raws.size) raws[i + 1].offset else fileSize
            val size = if (haveOffsets && end > r.offset) end - r.offset else -1L
            LogSession(i + 1, r.line, if (haveOffsets) r.offset else -1L, size, r.build, r.gps, last = i == raws.size - 1)
        }
    }

    /** True when [b] begins with a session header. */
    fun startsWithHeader(b: ByteArray): Boolean {
        if (b.size < HEADER.length) return false
        for (i in HEADER.indices) if (b[i].toInt() != HEADER[i].code) return false
        return true
    }

    /**
     * What the head unit sent: [lead] bytes that came before the header (the head unit's dd starts at a block), then the
     * session, then perhaps more than [length] bytes (a read that was stopped late). Null when the session does not start
     * with its header: the head unit cut at the wrong place, or the file is not the one that was listed.
     */
    fun trim(raw: ByteArray, lead: Int, length: Long): ByteArray? {
        if (lead < 0 || lead > raw.size) return null
        var b = if (lead == 0) raw else raw.copyOfRange(lead, raw.size)
        if (length in 1 until b.size.toLong()) b = b.copyOf(length.toInt())
        return if (startsWithHeader(b)) b else null
    }

    /** [b] up to the line before the next header (for a session whose size is not known). */
    fun cutAtNextHeader(b: ByteArray): ByteArray {
        val pat = ("\n" + HEADER).toByteArray(Charsets.ISO_8859_1)
        var i = 1
        while (i <= b.size - pat.size) {
            if (b[i] == pat[0]) {
                var k = 1
                while (k < pat.size && b[i + k] == pat[k]) k++
                if (k == pat.size) return b.copyOf(i + 1)
            }
            i++
        }
        return b
    }

    /** "20 Nov 19:07": the GPS time (UTC) of a session in the time of the phone; "" when there is none. */
    fun localTime(gps: String, zone: ZoneId): String {
        if (gps.isEmpty()) return ""
        return try {
            val utc = LocalDateTime.parse(gps, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneOffset.UTC)
            utc.withZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH))
        } catch (_: Exception) {
            ""
        }
    }

    /** The name of a session's file in the zip. */
    fun fileName(number: Int): String = "nbt-aad-session-$number.log"

    /** "session 5" or "sessions 4, 5" */
    fun numbersText(numbers: Collection<Int>): String {
        val n = numbers.sorted()
        return (if (n.size == 1) "session " else "sessions ") + n.joinToString(", ")
    }
}
