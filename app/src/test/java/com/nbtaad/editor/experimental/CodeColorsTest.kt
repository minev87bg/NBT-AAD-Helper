package com.nbtaad.editor.experimental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeColorsTest {
    private fun toks(code: String, lang: String) = CodeColors.runs(code, lang).filter { it.tok != Tok.PLAIN }.map { it.text to it.tok }

    @Test
    fun cLikeCodeGetsCommentsStringsNumbersAndKeywords() {
        val code = "// F11 6WA\nconst HudProfile k6wa_f11 = {\n    \"6wa-f11\",\n    7, 3, 4,   // colours\n    { 0, 0, 199, 129 },\n};"
        val t = toks(code, "cpp")
        assertEquals("// F11 6WA" to Tok.COMMENT, t[0])
        assertEquals("const" to Tok.KEYWORD, t[1])
        assertTrue(("\"6wa-f11\"" to Tok.STRING) in t)
        assertTrue(("199" to Tok.NUMBER) in t)
        assertTrue(("// colours" to Tok.COMMENT) in t)
        // names that merely contain a digit or a keyword are plain
        assertTrue(t.none { it.first == "k6wa_f11" || it.first == "6wa" })
    }

    @Test
    fun theColouringNeverChangesTheText() {
        val code = "#include <x>\nif (a) { /* c */ b = 'x'; }\nunterminated \"string\nlast"
        for (lang in listOf("", "c", "sh", "json", "py")) {
            assertEquals(code, CodeColors.runs(code, lang).joinToString("") { it.text })
        }
    }

    @Test
    fun shellAndConfigUseHashComments() {
        val t = toks("# a comment\nNBT_CLUSTER=6wa  # trailing\necho \"hi\"", "sh")
        assertEquals("# a comment" to Tok.COMMENT, t[0])
        assertTrue(("# trailing" to Tok.COMMENT) in t)
        assertTrue(("echo" to Tok.KEYWORD) in t)
        // in C a # starts a directive, not a comment
        assertTrue(toks("#include <x>", "cpp").none { it.second == Tok.COMMENT })
    }

    @Test
    fun logsAndPlainTextAreLeftAlone() {
        assertEquals(listOf(CodeRun("don't 12 // x \"y\" if", Tok.PLAIN)), CodeColors.runs("don't 12 // x \"y\" if", "log"))
        // without a language an apostrophe does not start a string
        assertTrue(toks("it's fine", "").none { it.second == Tok.STRING })
    }
}
