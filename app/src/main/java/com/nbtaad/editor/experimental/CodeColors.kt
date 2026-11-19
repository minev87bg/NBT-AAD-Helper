package com.nbtaad.editor.experimental

/** What a piece of code is, for colouring it like GitHub does. */
enum class Tok { PLAIN, KEYWORD, STRING, NUMBER, COMMENT }

data class CodeRun(val text: String, val tok: Tok)

/**
 * A light colouring of code blocks: comments, strings, numbers and the usual keywords. It does not know any language
 * well; it knows enough to make C, shell, Python, JSON and config text readable. Text blocks (logs, plain text,
 * diffs) are left uncoloured.
 */
object CodeColors {
    private val PLAIN_LANGS = setOf("text", "txt", "log", "console", "plaintext", "plain", "diff", "patch", "md", "markdown", "output")
    private val HASH_COMMENT = setOf(
        "sh", "bash", "shell", "zsh", "env", "ini", "conf", "cfg", "yaml", "yml", "toml", "python", "py", "ruby", "rb", "perl", "make", "makefile", "properties",
    )
    private val KEYWORDS = setOf(
        "const", "int", "unsigned", "signed", "char", "short", "long", "float", "double", "void", "bool", "boolean", "true", "false",
        "nullptr", "null", "NULL", "if", "else", "elif", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
        "struct", "class", "enum", "union", "typedef", "static", "inline", "extern", "auto", "namespace", "using", "template", "typename",
        "public", "private", "protected", "new", "delete", "this", "fun", "val", "var", "def", "import", "from", "as", "in", "is", "not",
        "and", "or", "None", "True", "False", "function", "let", "async", "await", "try", "catch", "throw", "finally", "package",
        "interface", "override", "sizeof", "then", "fi", "done", "esac", "export", "echo", "uint8_t", "uint16_t", "uint32_t", "uint64_t",
        "int8_t", "int16_t", "int32_t", "int64_t", "size_t", "string", "std", "final", "constexpr", "volatile",
    )

    fun runs(code: String, lang: String): List<CodeRun> {
        val l = lang.lowercase()
        if (l in PLAIN_LANGS || code.length > 20_000) return listOf(CodeRun(code, Tok.PLAIN))
        val hash = l in HASH_COMMENT
        val slash = !hash
        val singleQuotes = l.isNotEmpty()
        val out = ArrayList<CodeRun>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                out += CodeRun(plain.toString(), Tok.PLAIN)
                plain.setLength(0)
            }
        }
        fun add(text: String, tok: Tok) {
            flush()
            out += CodeRun(text, tok)
        }
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            when {
                slash && c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                    var j = i
                    while (j < n && code[j] != '\n') j++
                    add(code.substring(i, j), Tok.COMMENT)
                    i = j
                }
                slash && c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                    val end = code.indexOf("*/", i + 2)
                    val j = if (end < 0) n else end + 2
                    add(code.substring(i, j), Tok.COMMENT)
                    i = j
                }
                hash && c == '#' && (i == 0 || code[i - 1] == '\n' || code[i - 1] == ' ' || code[i - 1] == '\t') -> {
                    var j = i
                    while (j < n && code[j] != '\n') j++
                    add(code.substring(i, j), Tok.COMMENT)
                    i = j
                }
                c == '"' || (c == '\'' && singleQuotes) -> {
                    var j = i + 1
                    while (j < n && code[j] != c && code[j] != '\n') {
                        if (code[j] == '\\' && j + 1 < n && code[j + 1] != '\n') j++
                        j++
                    }
                    if (j < n && code[j] == c) j++
                    add(code.substring(i, j), Tok.STRING)
                    i = j
                }
                c.isDigit() && (i == 0 || !(code[i - 1].isLetterOrDigit() || code[i - 1] == '_')) -> {
                    var j = i + 1
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                    add(code.substring(i, j), Tok.NUMBER)
                    i = j
                }
                c.isLetter() || c == '_' -> {
                    var j = i + 1
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    if (word in KEYWORDS) add(word, Tok.KEYWORD) else plain.append(word)
                    i = j
                }
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flush()
        return out
    }
}
