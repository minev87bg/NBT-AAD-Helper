package com.nbtaad.editor.log

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The text side of the app log: one line per entry, VINs masked, long texts cut. Pure, so it is tested. */
object LogText {
    private val VIN = Regex("\\b([A-HJ-NPR-Z0-9]{3})[A-HJ-NPR-Z0-9]{10}([A-HJ-NPR-Z0-9]{4})\\b")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    const val MAX_ENTRY = 3000

    /** "WBAKM42050C233076" becomes "WBA…3076": enough to tell the car's make, not enough to identify it. */
    fun mask(text: String): String = VIN.replace(text) { m -> m.groupValues[1] + "…" + m.groupValues[2] }

    fun stamp(timeMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        STAMP.format(Instant.ofEpochMilli(timeMs).atZone(zone))

    /** "2026-11-19 01:38:12.345 I/Detect: text"; a text of several lines continues on indented lines. */
    fun line(timeMs: Long, level: Char, tag: String, msg: String, zone: ZoneId = ZoneId.systemDefault()): String {
        var text = mask(msg.trimEnd())
        if (text.length > MAX_ENTRY) text = text.take(MAX_ENTRY) + "… (${text.length - MAX_ENTRY} more characters)"
        return stamp(timeMs, zone) + " $level/$tag: " + text.replace("\n", "\n    ")
    }

    /** The exception and the top of its stack, with its causes: what is needed to find where it happened. */
    fun stack(e: Throwable, maxFrames: Int = 14): String = buildString {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 4) {
            if (depth > 0) append("\nCaused by: ")
            append(t.javaClass.name)
            t.message?.let { append(": ").append(it) }
            for (f in t.stackTrace.take(maxFrames)) append("\n    at ").append(f)
            if (t.stackTrace.size > maxFrames) append("\n    … ${t.stackTrace.size - maxFrames} more")
            t = t.cause?.takeIf { it !== t }
            depth++
        }
    }

    /** The first line of a command, cut: scripts are long and only their start says what they are. */
    fun brief(cmd: String, max: Int = 110): String {
        val one = cmd.trim().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
        return if (one.length > max) one.take(max) + "…" else one
    }
}

/**
 * The log on the phone: two files in [dir], the current one and the one before it. When the current one gets over
 * [maxBytes] it becomes the old one, so the log never grows past about twice that.
 */
class LogFile(private val dir: File, private val maxBytes: Long = 400_000) {
    private val current get() = File(dir, "app.log")
    private val old get() = File(dir, "app.old.log")

    @Synchronized
    fun append(line: String) {
        try {
            dir.mkdirs()
            if (current.length() > maxBytes) {
                old.delete()
                current.renameTo(old)
            }
            current.appendText(line + "\n", Charsets.UTF_8)
        } catch (_: Exception) {
            // logging must never be the thing that fails
        }
    }

    @Synchronized
    fun readAll(): String = try {
        (if (old.exists()) old.readText(Charsets.UTF_8) else "") +
            (if (current.exists()) current.readText(Charsets.UTF_8) else "")
    } catch (_: Exception) {
        ""
    }

    @Synchronized
    fun size(): Long = (if (old.exists()) old.length() else 0L) + (if (current.exists()) current.length() else 0L)

    @Synchronized
    fun clear() {
        old.delete()
        current.delete()
    }
}

/**
 * What the code of the app calls to write the log. It writes nothing until [setup] was called (the tests and the type
 * checks run without a phone); the Android side ([start], in AppLogAndroid.kt) sets it up and adds the phone's log.
 */
object AppLog {
    @Volatile private var file: LogFile? = null
    @Volatile var echo: ((level: Char, tag: String, msg: String) -> Unit)? = null

    @Synchronized
    fun setup(dir: File, maxBytes: Long = 400_000): Boolean {
        if (file != null) return false
        file = LogFile(dir, maxBytes)
        return true
    }

    private fun write(level: Char, tag: String, msg: String) {
        try {
            echo?.invoke(level, tag, msg)
        } catch (_: Throwable) {
        }
        file?.append(LogText.line(System.currentTimeMillis(), level, tag, msg))
    }

    fun d(tag: String, msg: String) = write('D', tag, msg)
    fun i(tag: String, msg: String) = write('I', tag, msg)
    fun w(tag: String, msg: String) = write('W', tag, msg)

    /** An error, with the exception and the top of its stack when there is one. */
    fun e(tag: String, msg: String, t: Throwable? = null) =
        write('E', tag, if (t == null) msg else msg + "\n" + LogText.stack(t))

    fun size(): Long = file?.size() ?: 0L
    fun text(): String = file?.readAll() ?: ""

    fun clear() {
        file?.clear()
        i("App", "Log cleared.")
    }
}
