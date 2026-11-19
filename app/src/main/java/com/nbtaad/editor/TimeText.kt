package com.nbtaad.editor

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How the app writes down when something happened. GitHub gives times in UTC ("2026-11-19T21:05:33Z"). The person wants
 * them on the phone's own clock, with the hour and the minute (several posts can come on one day), and how long ago it
 * was, so that the newest news stands out. Kept free of Android classes so the build's unit tests can run it.
 */
object TimeText {
    private val MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun parse(iso: String): Instant? = try {
        OffsetDateTime.parse(iso.trim()).toInstant()
    } catch (_: Exception) {
        null
    }

    /** "2026-11-19 23:05" on the clock of [zone] (the phone's own by default). A text that is no time gives its first 10 characters. */
    fun stamp(iso: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = parse(iso) ?: return iso.take(10)
        return MINUTE.format(t.atZone(zone))
    }

    /** "5 min ago", "3 h ago", "2 d ago", "3 w ago", "4 mo ago"; null when [iso] is no time. */
    fun ago(iso: String, now: Instant = Instant.now()): String? {
        val t = parse(iso) ?: return null
        val minutes = Duration.between(t, now).toMinutes()
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> "just now"   // also for a phone clock that runs a little behind GitHub's
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours h ago"
            days < 14 -> "$days d ago"
            days < 60 -> "${days / 7} w ago"
            else -> "${days / 30} mo ago"
        }
    }

    /** "2026-11-19 23:05  ·  3 h ago": the time and how long ago, the way every post, comment and release shows it. */
    fun full(iso: String, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
        val s = stamp(iso, zone)
        val a = ago(iso, now) ?: return s
        return "$s  ·  $a"
    }
}
