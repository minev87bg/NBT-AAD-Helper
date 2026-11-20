package com.nbtaad.editor.experimental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    private fun para(b: MdBlock) = (b as MdBlock.Para).spans
    private fun txt(spans: List<MdSpan>) = Markdown.plain(spans)

    @Test
    fun headingsParagraphsAndRules() {
        val b = Markdown.parse("# Title\n\nSome text\nsecond line\n\n## Root cause ##\n\n---\n\n#hashtag is no heading")
        assertEquals(5, b.size)
        assertEquals(1, (b[0] as MdBlock.Heading).level)
        assertEquals("Title", txt((b[0] as MdBlock.Heading).spans))
        // a line break inside a paragraph stays a line break (as on GitHub's issue pages)
        assertEquals("Some text\nsecond line", txt(para(b[1])))
        assertEquals(2, (b[2] as MdBlock.Heading).level)
        assertEquals("Root cause", txt((b[2] as MdBlock.Heading).spans))
        assertTrue(b[3] === MdBlock.Rule)
        assertEquals("#hashtag is no heading", txt(para(b[4])))
    }

    @Test
    fun aLineUnderlinedWithDashesIsAHeadingNotARule() {
        val b = Markdown.parse("Setting\n-------\n\nbody")
        assertEquals(2, (b[0] as MdBlock.Heading).level)
        assertEquals("Setting", txt((b[0] as MdBlock.Heading).spans))
        val c = Markdown.parse("Big\n===")
        assertEquals(1, (c[0] as MdBlock.Heading).level)
    }

    @Test
    fun inlineStyles() {
        val s = Markdown.inline("a **bold** and *it* and ~~gone~~ and `code` and ***both***")
        assertEquals("a bold and it and gone and code and both", txt(s))
        assertTrue(s.first { it.text == "bold" }.bold)
        assertTrue(s.first { it.text == "it" }.italic)
        assertTrue(s.first { it.text == "gone" }.strike)
        assertTrue(s.first { it.text == "code" }.code)
        val both = s.first { it.text == "both" }
        assertTrue(both.bold && both.italic)
        assertFalse(s.first { it.text.startsWith("a ") }.bold)
    }

    @Test
    fun settingNamesWithUnderscoresStayAsTheyAre() {
        // _ inside a word is not emphasis; outside code and inside code
        val s = Markdown.inline("Set NBT_6WA_F11_TEXT=1 and `NBT_HUDTERM=log` and _slanted_ text")
        assertEquals("Set NBT_6WA_F11_TEXT=1 and NBT_HUDTERM=log and slanted text", txt(s))
        assertTrue(s.first { it.text == "slanted" }.italic)
        assertTrue(s.first { it.text == "NBT_HUDTERM=log" }.code)
        assertFalse(s.any { it.italic && it.text.contains("NBT_6WA") })
    }

    @Test
    fun starsThatAreNotEmphasisStayStars() {
        assertEquals("5 * 3 * 2", txt(Markdown.inline("5 * 3 * 2")))
        assertEquals("a ** b", txt(Markdown.inline("a ** b")))
        assertEquals("**unclosed", txt(Markdown.inline("**unclosed")))
        assertEquals("*", txt(Markdown.inline("\\*")))
    }

    @Test
    fun codeSpansKeepEverythingInside() {
        val s = Markdown.inline("run ``a ` b`` then `x*y*z` and `if (p_.street) b.add(... setText(p_.street ...))`")
        assertEquals("run a ` b then x*y*z and if (p_.street) b.add(... setText(p_.street ...))", txt(s))
        assertTrue(s.first { it.text == "x*y*z" }.code)
    }

    @Test
    fun linksPicturesAndBareAddresses() {
        val s = Markdown.inline("see [the issue](https://github.com/a/b/issues/3) and https://example.com/x.zip. Also <https://e.org/p>")
        assertEquals("see the issue and https://example.com/x.zip. Also https://e.org/p", txt(s))
        assertEquals("https://github.com/a/b/issues/3", s.first { it.text == "the issue" }.link)
        // the full stop after a bare address is not part of it
        assertEquals("https://example.com/x.zip", s.first { it.text.startsWith("https://example.com") }.link)
        assertEquals("https://e.org/p", s.last().link)

        val pic = Markdown.inline("![screen](https://user-images.example/a.png) after")
        assertEquals("[picture: screen] after", txt(pic))
        assertEquals("https://user-images.example/a.png", pic.first().link)
        // a link that points inside the repository files is shown as text only
        val rel = Markdown.inline("[doc](docs/x.md)")
        assertEquals("doc", txt(rel))
        assertEquals(null, rel.first().link)
        // a link wrapped around bold text
        val b = Markdown.inline("[**go**](https://x.y/z)")
        assertTrue(b.first().bold)
        assertEquals("https://x.y/z", b.first().link)
    }

    @Test
    fun fencedCodeIsKeptAsItIs() {
        val b = Markdown.parse("before\n```cpp\n// F11 6WA\nconst HudProfile k6wa_f11 = {\n    \"6wa-f11\",\n\n    1,   // **not bold**\n};\n```\nafter")
        assertEquals(3, b.size)
        val code = b[1] as MdBlock.Code
        assertEquals("cpp", code.lang)
        assertEquals("// F11 6WA\nconst HudProfile k6wa_f11 = {\n    \"6wa-f11\",\n\n    1,   // **not bold**\n};", code.text)
        assertEquals("after", txt(para(b[2])))
    }

    @Test
    fun anUnclosedFenceTakesTheRest() {
        val b = Markdown.parse("```\nline 1\nline 2")
        assertEquals(1, b.size)
        assertEquals("line 1\nline 2", (b[0] as MdBlock.Code).text)
    }

    @Test
    fun theTableOfIssue242() {
        val raw = "| handle | fkts seen | role |\n|---|---|---|\n| **1** | 204 SetVisibility, 205 IsVisible, 20E | guidance panel |\n" +
            "| **4** | 21D AddPolygon ×69, 219 | **main picture buffer A (arrow)** |\n| 12,13,14,16,17 | 204 + 210 TextareaSetText (all **empty** in capture) | text fields |\n\nAfter."
        val b = Markdown.parse(raw)
        assertEquals(2, b.size)
        val t = b[0] as MdBlock.Table
        assertEquals(listOf("handle", "fkts seen", "role"), t.head.map { txt(it) })
        assertEquals(3, t.rows.size)
        assertEquals("1", txt(t.rows[0][0]))
        assertTrue(t.rows[0][0].first().bold)
        assertEquals("guidance panel", txt(t.rows[0][2]))
        assertEquals("204 + 210 TextareaSetText (all empty in capture)", txt(t.rows[2][1]))
        assertEquals("After.", txt(para(b[1])))
    }

    @Test
    fun tableAlignmentCellsEscapedPipesAndShortRows() {
        val b = Markdown.parse("a | b | c\n:--|:-:|--:\nx \\| y | 2\n| only |")
        val t = b[0] as MdBlock.Table
        assertEquals(listOf(MdAlign.LEFT, MdAlign.CENTER, MdAlign.RIGHT), t.align)
        assertEquals("x | y", txt(t.rows[0][0]))
        // rows are made as wide as the header
        assertEquals(3, t.rows[0].size)
        assertEquals(3, t.rows[1].size)
        assertEquals("", txt(t.rows[1][2]))
    }

    @Test
    fun aPipeInAParagraphIsNoTable() {
        val b = Markdown.parse("a | b\nnot a table row")
        assertTrue(b.single() is MdBlock.Para)
    }

    @Test
    fun orderedListWithLazyLines() {
        val raw = "Two differences from the F20:\n1. **Main arrow** is double-buffered on handles 4 and 5, not 5 and 6.\n" +
            "2. The text handles (12/13) do not answer our `210 TextareaSetText`\n   the call times out after 1500 ms\nand the whole terminal failed.\n\nNext paragraph"
        val b = Markdown.parse(raw)
        assertEquals(3, b.size)
        assertEquals("Two differences from the F20:", txt(para(b[0])))
        val l = b[1] as MdBlock.Items
        assertTrue(l.ordered)
        assertEquals(1, l.start)
        assertEquals(2, l.items.size)
        assertEquals("Main arrow is double-buffered on handles 4 and 5, not 5 and 6.", txt(para(l.items[0][0])))
        assertEquals(
            "The text handles (12/13) do not answer our 210 TextareaSetText\nthe call times out after 1500 ms\nand the whole terminal failed.",
            txt(para(l.items[1][0])),
        )
        assertEquals("Next paragraph", txt(para(b[2])))
    }

    @Test
    fun nestedAndLooseBulletLists() {
        val raw = "- one\n  - inner a\n  - inner b\n- two\n\n- three after a blank line\n\n* other marker\n\ntext"
        val b = Markdown.parse(raw)
        val l = b[0] as MdBlock.Items
        assertFalse(l.ordered)
        assertEquals(4, l.items.size)
        assertEquals("one", txt(para(l.items[0][0])))
        val inner = l.items[0][1] as MdBlock.Items
        assertEquals(listOf("inner a", "inner b"), inner.items.map { txt(para(it[0])) })
        assertEquals("three after a blank line", txt(para(l.items[2][0])))
        assertEquals("other marker", txt(para(l.items[3][0])))
        assertEquals("text", txt(para(b[1])))
    }

    @Test
    fun taskListsAndCodeInsideAnItem() {
        val raw = "- [x] done\n- [ ] todo\n- step\n\n  ```sh\n  cat /opt/aa/nbt-aad.env\n  ```\n"
        val l = Markdown.parse(raw)[0] as MdBlock.Items
        assertEquals("☑ done", txt(para(l.items[0][0])))
        assertEquals("☐ todo", txt(para(l.items[1][0])))
        val code = l.items[2][1] as MdBlock.Code
        assertEquals("sh", code.lang)
        assertEquals("cat /opt/aa/nbt-aad.env", code.text)
    }

    @Test
    fun quotesAreReadAsBlocksToo() {
        val b = Markdown.parse("> **Note**\n> first\n>\n> - a\n> - b\n\nafter")
        val q = b[0] as MdBlock.Quote
        assertEquals(2, q.blocks.size)
        assertEquals("Note\nfirst", txt(para(q.blocks[0])))
        assertTrue(q.blocks[1] is MdBlock.Items)
        assertEquals("after", txt(para(b[1])))
    }

    @Test
    fun htmlIsReducedToWhatItMeans() {
        val raw = "<!-- template comment -->\nline<br>break\n\n<details><summary>More</summary>\n\nhidden text\n\n</details>\n\n" +
            "<img width=\"300\" alt=\"shot\" src=\"https://x.test/s.png\">\n\n<a href=\"https://x.test/p\">page</a> and <b>strong</b> and `<div>` stays"
        val b = Markdown.parse(raw)
        assertEquals("line\nbreak", txt(para(b[0])))
        assertEquals("More", txt(para(b[1])))
        assertTrue(para(b[1]).first().bold)
        assertEquals("hidden text", txt(para(b[2])))
        assertEquals("[picture: shot]", txt(para(b[3])))
        assertEquals("https://x.test/s.png", para(b[3]).first().link)
        val last = para(b[4])
        assertEquals("page and strong and <div> stays", txt(last))
        assertEquals("https://x.test/p", last.first().link)
        assertTrue(last.first { it.text == "<div>" }.code)
    }

    @Test
    fun anythingElseIsShownNotLost() {
        assertEquals(0, Markdown.parse("").size)
        assertEquals(0, Markdown.parse("\n\n  \n").size)
        val weird = Markdown.parse("[unclosed link(\n**\n```\n|\n- \n1.\n> ")
        assertTrue(weird.isNotEmpty())
        // deep nesting does not run away
        val deep = Markdown.parse((1..40).joinToString("\n") { "  ".repeat(it) + "- level $it" })
        assertTrue(deep.isNotEmpty())
    }

    @Test
    fun aLongTextIsShortenedForTheCollapsedView() {
        val long = "para one\n\n" + "x".repeat(300) + "\n\n" + "y".repeat(300) + "\n\nlast"
        val (short, cut) = Markdown.shorten(Markdown.parse(long), 420)
        assertTrue(cut)
        // the first two blocks fit under 420 characters, the third starts after that and the rest is left out
        assertEquals(3, short.size)

        val code = "```\n" + (1..30).joinToString("\n") { "line $it" } + "\n```"
        val (c, cutCode) = Markdown.shorten(Markdown.parse(code), 420, 5)
        assertTrue(cutCode)
        assertEquals("line 1\nline 2\nline 3\nline 4\nline 5\n…", (c[0] as MdBlock.Code).text)

        val items = (1..60).joinToString("\n") { "- item number $it with some words" }
        val (l, cutList) = Markdown.shorten(Markdown.parse(items), 200)
        assertTrue(cutList)
        assertTrue((l[0] as MdBlock.Items).items.size in 2..20)

        val (all, cutNone) = Markdown.shorten(Markdown.parse("short"), 420)
        assertFalse(cutNone)
        assertEquals(1, all.size)
    }

    @Test
    fun picturesAreCollectedPerPostInTheOrderTheyAppear() {
        val raw = "Look:\n\n![first](https://github.com/user-attachments/assets/aaaa-1111)\n\n" +
            "<img width=\"200\" alt=\"second\" src=\"https://user-images.githubusercontent.com/1/b.png\">\n\n" +
            "and https://example.com/shot.JPG inline, and the same ![again](https://github.com/user-attachments/assets/aaaa-1111)\n\n" +
            "| a | b |\n|---|---|\n| ![cell](https://x.test/c.webp) | text |\n\n- item ![in list](https://x.test/d.gif)\n\n" +
            "![relative](docs/pic.png) and https://example.com/page.html"
        val blocks = Markdown.parse(raw)
        val pics = Markdown.pictures(blocks)
        assertEquals(
            listOf(
                "https://github.com/user-attachments/assets/aaaa-1111", "https://user-images.githubusercontent.com/1/b.png",
                "https://example.com/shot.JPG", "https://x.test/c.webp", "https://x.test/d.gif",
            ),
            pics.map { it.url },
        )
        assertEquals("first", pics[0].alt)
        assertEquals("second", pics[1].alt)
        // a picture inside the repository is no address, a page is not a picture
        val last = (blocks.last() as MdBlock.Para).spans
        assertFalse(last.any { it.picture })
        assertEquals("[picture: relative] and https://example.com/page.html", txt(last))
        assertTrue(Markdown.pictures(Markdown.parse("no pictures here")).isEmpty())
    }

    private fun signed(exp: Long): String {
        val payload = "{\"iss\":\"github.com\",\"exp\":$exp,\"nbf\":${exp - 300}}"
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "https://private-user-images.githubusercontent.com/1/2-x.jpg?jwt=eyJhbGciOiJIUzI1NiJ9.$b64.c2ln"
    }

    @Test
    fun aSignedPictureAddressIsDeadAfterItsTime() {
        assertTrue(Markdown.expiredSigned(signed(1_000), 1_001))
        assertFalse(Markdown.expiredSigned(signed(1_000), 999))
        // no "jwt=", or something that is not one: not for this to say
        assertFalse(Markdown.expiredSigned("https://github.com/user-attachments/assets/aaaa-1111", 9_999_999_999))
        assertFalse(Markdown.expiredSigned("https://x.test/a.png?jwt=notatoken", 9_999_999_999))
        assertFalse(Markdown.expiredSigned("https://x.test/a.png?jwt=a.@@@.c", 9_999_999_999))
    }

    @Test
    fun aQuotedReplyKeepsNoDeadPictureFromTheOriginal() {
        val raw = "> thanks, works!\n> ![image](${signed(1_000)})\n\nwhat does the log say?\n\n" +
            "![mine](https://github.com/user-attachments/assets/bbbb-2222)"
        val pics = Markdown.pictures(Markdown.parse(raw), nowSec = 5_000)
        assertEquals(listOf("https://github.com/user-attachments/assets/bbbb-2222"), pics.map { it.url })
        // while its time is not over, it is shown
        assertEquals(2, Markdown.pictures(Markdown.parse(raw), nowSec = 500).size)
    }

    @Test
    fun textFilesAreToldFromOtherFiles() {
        val log = Links.file("https://github.com/user-attachments/files/123/nbt-aad.LOG", "me", "the first post")!!
        assertTrue(log.isText)
        assertTrue(Links.file("https://github.com/user-attachments/files/124/notes.md", "me", "a")!!.isText)
        assertTrue(Links.file("https://github.com/user-attachments/files/125/out.txt", "me", "a")!!.isText)
        val zip = Links.file("https://github.com/user-attachments/files/126/build.zip", "me", "a")!!
        assertFalse(zip.isText)
        // many more kinds are text: data, scripts, source
        assertTrue(Links.file("https://github.com/user-attachments/files/127/data.csv", "me", "a")!!.isText)
        assertTrue(Links.file("https://github.com/user-attachments/files/128/make_db.py", "me", "a")!!.isText)
        assertFalse(Links.file("https://github.com/user-attachments/files/129/libNbtAad.so", "me", "a")!!.isText)
        // pictures are not files
        assertEquals(null, Links.file("https://github.com/user-attachments/assets/abc", "me", "a"))
        assertTrue(Links.isImage("https://github.com/user-attachments/assets/abc"))
        assertTrue(Links.isImage("https://x.test/a.PNG?raw=1"))
        assertFalse(Links.isImage("https://x.test/video.mp4"))
        assertFalse(Links.isImage("https://x.test/vector.svg"))
    }

    private val isFile = { u: String -> Links.file(u, "", "") != null }
    private val F1 = "https://github.com/user-attachments/files/1/make_db.py"
    private val F2 = "https://github.com/user-attachments/files/2/tr.txt"

    @Test
    fun everyLineThatLinksAFileStandsApart() {
        val para = Markdown.parse("[make_db.py]($F1)\n[tr.txt]($F2)\nHello, I translated these.\nPlease review.")[0] as MdBlock.Para
        val pieces = Markdown.pieces(para.spans, isFile)
        assertEquals(3, pieces.size)
        assertEquals(listOf(F1), pieces[0].files)
        assertEquals("make_db.py", Markdown.plain(pieces[0].spans))
        assertEquals(listOf(F2), pieces[1].files)
        assertEquals(emptyList<String>(), pieces[2].files)
        assertEquals("Hello, I translated these.\nPlease review.", Markdown.plain(pieces[2].spans))
    }

    @Test
    fun textAroundFilesStaysTogetherAndOtherLinksAreNoFiles() {
        val para = Markdown.parse("intro\nsee [log]($F2) now\nmore\nand [the page](https://example.com/x)")[0] as MdBlock.Para
        val pieces = Markdown.pieces(para.spans, isFile)
        assertEquals(listOf(emptyList(), listOf(F2), emptyList()), pieces.map { it.files })
        assertEquals("intro", Markdown.plain(pieces[0].spans))
        assertEquals("see log now", Markdown.plain(pieces[1].spans))
        assertEquals("more\nand the page", Markdown.plain(pieces[2].spans))
        // no file, one piece, as it was
        val plain = Markdown.parse("just text\nand more")[0] as MdBlock.Para
        assertEquals(1, Markdown.pieces(plain.spans, isFile).size)
        assertEquals(emptyList<MdPiece>(), Markdown.pieces(emptyList(), isFile))
    }

    @Test
    fun aLineWithTwoFilesAsksForBoth() {
        val para = Markdown.parse("[a]($F1) and [b]($F2) and [a again]($F1)")[0] as MdBlock.Para
        val pieces = Markdown.pieces(para.spans, isFile)
        assertEquals(1, pieces.size)
        assertEquals(listOf(F1, F2), pieces[0].files)
    }

    @Test
    fun aBareFileAddressCountsAsALinkToo() {
        val para = Markdown.parse("log: $F2\nthanks")[0] as MdBlock.Para
        val pieces = Markdown.pieces(para.spans, isFile)
        assertEquals(listOf(listOf(F2), emptyList()), pieces.map { it.files })
    }

    @Test
    fun spansAreCutIntoLines() {
        val lines = Markdown.lines(Markdown.inline("one\ntwo **three\nfour**\n"))
        assertEquals(listOf("one", "two three", "four"), lines.map { Markdown.plain(it) })
        assertTrue(lines[2].all { it.bold })
        assertEquals(0, Markdown.lines(emptyList()).size)
    }
}
