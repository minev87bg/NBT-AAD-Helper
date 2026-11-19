package com.nbtaad.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeTextTest {
    private val sofia = ZoneId.of("Europe/Sofia")
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-19T19:20:00Z")

    @Test
    fun aTimeIsWrittenOnTheClockOfTheZone() {
        // GitHub says UTC; Sofia is two hours ahead in November, so the day can change too
        assertEquals("2026-11-17 14:32", TimeText.stamp("2026-11-17T12:32:10Z", sofia))
        assertEquals("2026-11-17 12:32", TimeText.stamp("2026-11-17T12:32:10Z", utc))
        assertEquals("2026-11-18 00:30", TimeText.stamp("2026-11-17T22:30:00Z", sofia))
        // an offset instead of Z is understood as well
        assertEquals("2026-11-17 12:32", TimeText.stamp("2026-11-17T14:32:10+02:00", utc))
    }

    @Test
    fun aTextThatIsNoTimeIsNotLost() {
        assertEquals("2026-11-1x", TimeText.stamp("2026-11-1x", utc))
        assertEquals("", TimeText.stamp("", utc))
        assertNull(TimeText.parse("yesterday"))
        assertNull(TimeText.ago("yesterday", now))
        assertEquals("yesterday", TimeText.full("yesterday", utc, now))
        assertNotNull(TimeText.parse("2026-11-17T12:32:10Z"))
    }

    @Test
    fun howLongAgoIsToldInTheBiggestUnitThatFits() {
        fun ago(iso: String) = TimeText.ago(iso, now)
        assertEquals("just now", ago("2026-11-19T19:19:30Z"))
        assertEquals("just now", ago("2026-11-19T19:25:00Z"))      // a phone clock a little behind GitHub's
        assertEquals("1 min ago", ago("2026-11-19T19:19:00Z"))
        assertEquals("59 min ago", ago("2026-11-19T18:21:00Z"))
        assertEquals("1 h ago", ago("2026-11-19T18:20:00Z"))
        assertEquals("23 h ago", ago("2026-11-18T20:20:00Z"))
        assertEquals("1 d ago", ago("2026-11-18T19:20:00Z"))
        assertEquals("13 d ago", ago("2026-11-06T19:20:00Z"))
        assertEquals("2 w ago", ago("2026-11-05T19:20:00Z"))
        assertEquals("7 w ago", ago("2026-10-01T19:20:00Z"))
        assertEquals("2 mo ago", ago("2026-09-19T19:20:00Z"))
    }

    @Test
    fun theFullFormHasTheTimeAndHowLongAgo() {
        assertEquals("2026-11-19 21:05  ·  15 min ago", TimeText.full("2026-11-19T19:05:00Z", sofia, now))
        assertEquals("2026-11-17 14:32  ·  2 d ago", TimeText.full("2026-11-17T12:32:10Z", sofia, now))
    }
}
