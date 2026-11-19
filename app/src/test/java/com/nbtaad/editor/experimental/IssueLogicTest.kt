package com.nbtaad.editor.experimental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueLogicTest {
    private val issues = """
        [
          {"number": 10, "title": "Bmw G30 2019 nbt id6 only connectin ", "state": "open", "comments": 3,
           "user": {"login": "kedzior12"}, "created_at": "2026-10-28T10:00:00Z", "updated_at": "2026-10-29T08:00:00Z",
           "labels": [{"name": "bug"}, {"name": "help wanted"}], "html_url": "https://github.com/o/r/issues/10",
           "body": "My car only connects once.\r\n\r\n<!-- template -->\r\n![shot](https://user-images.githubusercontent.com/1/a.png)\r\nSee the log: https://github.com/user-attachments/files/123/log.zip."},
          {"number": 45, "title": "WIP: Audio Improvements", "state": "open", "comments": 1, "draft": true,
           "user": {"login": "tom-pryor"}, "created_at": "2026-11-01T10:00:00Z", "updated_at": "2026-11-02T08:00:00Z",
           "labels": [], "html_url": "https://github.com/o/r/pull/45", "body": null,
           "pull_request": {"url": "https://api.github.com/repos/o/r/pulls/45", "merged_at": null}},
          {"number": 12, "title": "Done thing ü", "state": "closed", "comments": 0,
           "user": {"login": "a"}, "created_at": "2026-10-01T10:00:00Z", "updated_at": "2026-10-02T08:00:00Z",
           "html_url": "https://github.com/o/r/pull/12", "body": "ok",
           "pull_request": {"merged_at": "2026-10-02T08:00:00Z"}}
        ]
    """.trimIndent()

    @Test
    fun theListOfIssuesAndPullRequestsIsRead() {
        val t = IssueParser.topics(issues)
        assertEquals(3, t.size)
        assertEquals(10, t[0].number)
        assertFalse(t[0].isPr)
        assertEquals("Bmw G30 2019 nbt id6 only connectin", t[0].title)
        assertEquals("kedzior12", t[0].author)
        assertEquals(listOf("bug", "help wanted"), t[0].labels)
        assertEquals(3, t[0].comments)
        assertTrue(t[0].open)
        assertTrue(t[1].isPr)
        assertTrue(t[1].draft)
        assertEquals("", t[1].body)             // a null body is an empty text
        assertFalse(t[1].merged)
        assertTrue(t[2].isPr)
        assertTrue(t[2].merged)
        assertFalse(t[2].open)
        assertEquals("Done thing ü", t[2].title)
    }

    @Test
    fun theCommentsAreReadAndKnowTheirPage() {
        val json = """[{"issue_url":"https://api.github.com/repos/o/r/issues/45","html_url":"https://github.com/o/r/pull/45#issuecomment-9",
            "user":{"login":"x"},"created_at":"2026-11-02T08:00:00Z","body":"hello"}]"""
        val c = IssueParser.comments(json)
        assertEquals(1, c.size)
        assertEquals(45, c[0].topic)
        assertEquals("x", c[0].author)
        assertEquals("hello", c[0].body)
        assertEquals(10, IssueParser.numberFromUrl("https://github.com/o/r/issues/10#issuecomment-1"))
        assertNull(IssueParser.numberFromUrl("https://github.com/o/r"))
    }

    @Test
    fun jsonHandlesWhatGithubSends() {
        val v = Json.parse("""{"a":[1,2.5,-3,true,false,null],"s":"x\n\"y\" é 😀","o":{}}""").asObj()!!
        assertEquals(listOf<Any?>(1L, 2.5, -3L, true, false, null), v["a"])
        assertEquals("x\n\"y\" é 😀", v["s"])
        assertEquals(emptyMap<String, Any?>(), v["o"])
        assertEquals(emptyList<Any?>(), Json.parse("[]"))
        try {
            Json.parse("{\"a\":")
            org.junit.Assert.fail("should fail")
        } catch (e: Json.JsonException) {
            // expected
        }
    }

    @Test
    fun filesAreToldFromPicturesAndPages() {
        val text = """
            Test build: https://github.com/user-attachments/files/18/nbt-aad-test.zip.
            Picture: https://github.com/user-attachments/assets/aaaa-bbbb and https://user-images.githubusercontent.com/1/a.png
            Release: https://github.com/Sq00ky/Supra-iDrive6-Android-Auto/releases/download/v1.0/nbt-aad
            Log: https://github.com/user-attachments/files/19/debug%20log.txt
            Artifact: https://github.com/o/r/actions/runs/123/artifacts/456
            Page: https://github.com/o/r/issues/3
            See (https://example.com/build.apk) too
        """.trimIndent()
        val f = Links.files(text, "bob", "a comment")
        assertEquals(
            listOf("nbt-aad-test.zip", "nbt-aad", "debug log.txt", "build artifact of an Actions run", "build.apk"),
            f.map { it.name },
        )
        assertEquals(FileKind.BUILD, f[0].kind)
        assertTrue(f[0].installable)
        assertEquals(FileKind.BUILD, f[1].kind)     // a build's own file name
        assertEquals(FileKind.OTHER, f[2].kind)
        assertEquals(FileKind.NEEDS_LOGIN, f[3].kind)
        assertFalse(f[3].installable)
        assertEquals(FileKind.OTHER, f[4].kind)
        assertEquals("bob", f[0].by)
        assertEquals(listOf("https://github.com/o/r/issues/3"), Links.pages(text).filter { it.contains("/issues/") })
        assertTrue(Links.isPicture("https://github.com/user-attachments/assets/aaaa"))
    }

    @Test
    fun aLinkAtTheEndOfASentenceLosesItsFullStop() {
        assertEquals(
            listOf("https://github.com/user-attachments/files/123/log.zip"),
            Links.find("See https://github.com/user-attachments/files/123/log.zip."),
        )
        assertNull(Links.file("http://example.com/a.zip", "", ""))   // only https
    }

    @Test
    fun theTextIsMadeReadable() {
        val t = PageText.clean("Hi\r\n<!-- hidden -->\r\n![x](https://a/b.png)\r\nSee [the log](https://github.com/x) <b>now</b>\r\n\r\n\r\n\r\nBye")
        assertEquals("Hi\n\n[picture]\nSee the log now\n\nBye", t)
        assertTrue(PageText.gist(IssueParser.topics(issues)[0].body).startsWith("My car only connects once."))
        assertEquals("", PageText.gist("```\ncode only\n```"))
    }

    @Test
    fun settingsNamedInThePagesAreCollected() {
        val s = Mentions.settings(listOf("Set NBT_FOO=1 and NBT_BAR_BAZ", "again NBT_FOO, and NBT_* is not one, nor NBT_", "nbt_lower"))
        assertEquals(listOf("NBT_FOO", "NBT_BAR_BAZ"), s)
    }

    @Test
    fun theDigestSaysWhatThePageIsAndLinksWhatItShares() {
        val t = IssueParser.topics(issues)[0]
        val comments = listOf(
            Comment(
                10, "helper", "2026-10-30T09:00:00Z",
                "Try NBT_FORCE_X=1. Details at https://example.com/doc and a build https://github.com/user-attachments/files/5/fix.zip", "",
            ),
        )
        val d = Digest.of(t, comments)
        assertTrue(d.lines[0].startsWith("Issue #10, open, opened by kedzior12 on 2026-10-28. 1 comment from 2 people"))
        assertTrue(d.lines.any { it.startsWith("Labels: bug, help wanted") })
        assertTrue(d.lines.any { it.startsWith("It says: My car only connects once.") })
        assertTrue(d.lines.any { it.startsWith("Latest from helper (2026-10-30)") })
        assertTrue(d.lines.any { it == "Settings mentioned: NBT_FORCE_X." })
        assertTrue(d.lines.any { it.startsWith("Files shared: log.zip, fix.zip") })
        // files first, then the pages
        assertEquals("https://github.com/user-attachments/files/123/log.zip", d.links[0])
        assertTrue(d.links.contains("https://example.com/doc"))
        val pr = Digest.of(IssueParser.topics(issues)[2], emptyList())
        assertTrue(pr.lines[0].startsWith("Pull request #12, merged"))
    }

    @Test
    fun theRateLimitMessageNamesTheTime() {
        val zone = java.time.ZoneId.of("UTC")
        val m = RateLimit.usedUp(1_700_000_000L + 600, 1_700_000_000L, zone)
        assertTrue(m.contains("Try again at 22:23"))
        assertTrue(m.contains("in about 10 minutes"))
        assertTrue(RateLimit.usedUp(null).endsWith("Try again in a while."))
        assertNotNull(Json.parse("1"))
    }

    @Test
    fun aSignInPageIsNotTakenForTheFile() {
        assertTrue(Links.looksLikePage("<!DOCTYPE html><html>".toByteArray()))
        assertTrue(Links.looksLikePage("  \n<html lang=en>".toByteArray()))
        assertFalse(Links.looksLikePage(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0)))   // a zip starts with PK
        assertFalse(Links.looksLikePage(ByteArray(0)))
    }
}
