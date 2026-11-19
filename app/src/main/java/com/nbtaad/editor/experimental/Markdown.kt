package com.nbtaad.editor.experimental

/*
 * A small Markdown reader for what people write on GitHub: headings, paragraphs, lists (also nested and as task lists),
 * tables, fenced code, quotes, rules, links and pictures, **bold**, *italic*, ~~strike~~ and `code`. It turns the text into
 * blocks the screen can draw (see MarkdownUi.kt). Line breaks inside a paragraph stay line breaks, as on GitHub's issue
 * pages. HTML is reduced to what it means (a line break, a link, a picture, bold); the rest of the tags are dropped.
 *
 * This is on purpose not a full CommonMark reader, but a forgiving one: it never throws, and text it does not understand
 * is shown as it is.
 */

enum class MdAlign { LEFT, CENTER, RIGHT }

/** A piece of text on one line of a block, drawn in one style. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
    /** A picture: [link] is its address and [alt] its description. The screen shows it in a grid, not in the text. */
    val picture: Boolean = false,
    val alt: String = "",
)

/** A picture in a post. */
data class MdPicture(val url: String, val alt: String)

/**
 * A piece of a paragraph. Lines that link a file stand alone: [files] are the addresses of the files linked in
 * [spans], and the screen puts a button for each of them under the text. Other lines stay together in one piece.
 */
data class MdPiece(val spans: List<MdSpan>, val files: List<String>)

sealed class MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock()
    data class Para(val spans: List<MdSpan>) : MdBlock()
    data class Code(val lang: String, val text: String) : MdBlock()
    data class Quote(val blocks: List<MdBlock>) : MdBlock()
    data class Items(val ordered: Boolean, val start: Int, val items: List<List<MdBlock>>) : MdBlock()
    data class Table(
        val head: List<List<MdSpan>>,
        val align: List<MdAlign>,
        val rows: List<List<List<MdSpan>>>,
    ) : MdBlock()
    object Rule : MdBlock()
}

object Markdown {
    private const val MAX_DEPTH = 6
    private const val BREAK = '\u0001'

    // ------------------------------------------------------------------------------------------------ entry points

    fun parse(raw: String): List<MdBlock> {
        var t = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
        t = COMMENT.replace(t, "")
        return blocks(flattenHtml(t.split("\n")), 0)
    }

    /** The spans of one line of text (a table cell, a title). */
    fun inline(raw: String): List<MdSpan> {
        val out = ArrayList<MdSpan>()
        val text = decode(raw.replace(BREAK, '\n'))
        spans(text, Style(), out, 0)
        return merge(out)
    }

    /** The pictures of a post in the order they appear, each address once. */
    fun pictures(blocks: List<MdBlock>): List<MdPicture> {
        val out = LinkedHashMap<String, MdPicture>()
        fun spans(list: List<MdSpan>) {
            for (s in list) if (s.picture && s.link != null && s.link !in out) out[s.link] = MdPicture(s.link, s.alt)
        }
        fun walk(b: MdBlock) {
            when (b) {
                is MdBlock.Heading -> spans(b.spans)
                is MdBlock.Para -> spans(b.spans)
                is MdBlock.Quote -> b.blocks.forEach(::walk)
                is MdBlock.Items -> b.items.forEach { item -> item.forEach(::walk) }
                is MdBlock.Table -> {
                    b.head.forEach(::spans)
                    b.rows.forEach { r -> r.forEach(::spans) }
                }
                is MdBlock.Code, MdBlock.Rule -> {}
            }
        }
        blocks.forEach(::walk)
        return out.values.toList()
    }

    /** The text of the spans without any styling. */
    fun plain(spans: List<MdSpan>): String = spans.joinToString("") { it.text }

    /** The spans cut into lines at every line break. A break at the very end does not make an empty line. */
    fun lines(spans: List<MdSpan>): List<List<MdSpan>> {
        val out = ArrayList<List<MdSpan>>()
        var cur = ArrayList<MdSpan>()
        for (s in spans) {
            val parts = s.text.split('\n')
            parts.forEachIndexed { i, part ->
                if (i > 0) {
                    out += cur
                    cur = ArrayList()
                }
                if (part.isNotEmpty() || s.picture) cur += if (parts.size == 1) s else s.copy(text = part)
            }
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    /**
     * A paragraph cut so that every line that links a file stands alone: there the screen shows the button for the file.
     * The lines between such lines stay together. [isFile] says whether an address is a file.
     */
    fun pieces(spans: List<MdSpan>, isFile: (String) -> Boolean): List<MdPiece> {
        val out = ArrayList<MdPiece>()
        var rest = ArrayList<MdSpan>()
        fun flush() {
            if (rest.isNotEmpty()) out += MdPiece(rest, emptyList())
            rest = ArrayList()
        }
        for (line in lines(spans)) {
            val files = line.mapNotNull { s -> s.link?.takeIf { !s.picture && isFile(it) } }.distinct()
            if (files.isEmpty()) {
                if (rest.isNotEmpty()) rest += MdSpan("\n")
                rest += line
            } else {
                flush()
                out += MdPiece(line, files)
            }
        }
        flush()
        return out
    }

    /** How many characters of text a block shows. */
    fun size(b: MdBlock): Int = when (b) {
        is MdBlock.Heading -> plain(b.spans).length
        is MdBlock.Para -> plain(b.spans).length
        is MdBlock.Code -> b.text.length
        is MdBlock.Quote -> b.blocks.sumOf { size(it) }
        is MdBlock.Items -> b.items.sumOf { item -> item.sumOf { size(it) } }
        is MdBlock.Table -> b.head.sumOf { plain(it).length } + b.rows.sumOf { r -> r.sumOf { plain(it).length } }
        MdBlock.Rule -> 0
    }

    /**
     * The start of a long text for a collapsed view: whole blocks until about [maxChars] characters are shown, long code
     * and long lists cut short. The flag says whether anything was left out.
     */
    fun shorten(blocks: List<MdBlock>, maxChars: Int = 420, maxCodeLines: Int = 8): Pair<List<MdBlock>, Boolean> {
        val out = ArrayList<MdBlock>()
        var used = 0
        var cut = false
        for (b in blocks) {
            if (used >= maxChars && out.isNotEmpty()) return out to true
            when {
                b is MdBlock.Code && b.text.lines().size > maxCodeLines -> {
                    out += MdBlock.Code(b.lang, b.text.lines().take(maxCodeLines).joinToString("\n") + "\n…")
                    used += b.text.lines().take(maxCodeLines).sumOf { it.length + 1 }
                    cut = true
                }
                b is MdBlock.Items && used + size(b) > maxChars -> {
                    val kept = ArrayList<List<MdBlock>>()
                    for (item in b.items) {
                        if (used >= maxChars && kept.isNotEmpty()) break
                        kept += item
                        used += item.sumOf { size(it) }
                    }
                    if (kept.size < b.items.size) cut = true
                    out += b.copy(items = kept)
                }
                else -> {
                    out += b
                    used += size(b)
                }
            }
        }
        return out to cut
    }

    // ------------------------------------------------------------------------------------------------ HTML

    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val CODE_SPAN = Regex("`+[^`]*`+")
    private val BR = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val IMG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val IMG_SRC = Regex("""\bsrc\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val IMG_ALT = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val ANCHOR = Regex("""<a\b[^>]*?\bhref\s*=\s*["']([^"']*)["'][^>]*>(.*?)</a\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SUMMARY = Regex("""<summary[^>]*>(.*?)</summary\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val CODE_TAG = Regex("""<(?:code|kbd|tt)[^>]*>(.*?)</(?:code|kbd|tt)\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BOLD_TAG = Regex("""<(?:b|strong)\s*>(.*?)</(?:b|strong)\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ITALIC_TAG = Regex("""<(?:i|em)\s*>(.*?)</(?:i|em)\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    // a tag has a name followed by a space, a slash or the end: "<https://x>" is a link, not a tag
    private val TAG = Regex("""</?[a-zA-Z][a-zA-Z0-9]*(?:\s[^>]*)?/?>""")

    private fun flattenHtml(lines: List<String>): List<String> {
        var fence: String? = null
        val out = ArrayList<String>(lines.size)
        for (line in lines) {
            val probe = line.trimStart(' ', '>')
            val open = fence
            if (open == null) {
                val f = fenceOf(probe)
                if (f != null) {
                    fence = f
                    out += line
                    continue
                }
                out += outsideCode(line)
            } else {
                val t = probe.trim()
                if (t.length >= open.length && t.all { it == open[0] }) fence = null
                out += line
            }
        }
        return out
    }

    /** The fence characters (``` or ~~~, at least three) when [probe] opens a fenced block. */
    private fun fenceOf(probe: String): String? {
        val s = probe.trimStart(' ')
        if (s.length < 3) return null
        val c = s[0]
        if (c != '`' && c != '~') return null
        var n = 0
        while (n < s.length && s[n] == c) n++
        if (n < 3) return null
        if (c == '`' && s.substring(n).contains('`')) return null   // ```code``` on one line is inline code
        return s.substring(0, n)
    }

    private fun outsideCode(line: String): String {
        if (line.indexOf('<') < 0 && line.indexOf('&') < 0) return line
        val sb = StringBuilder()
        var last = 0
        for (m in CODE_SPAN.findAll(line)) {
            sb.append(tags(line.substring(last, m.range.first)))
            sb.append(m.value)
            last = m.range.last + 1
        }
        sb.append(tags(line.substring(last)))
        return sb.toString()
    }

    private fun tags(s: String): String {
        if (s.indexOf('<') < 0) return s
        var t = s
        t = IMG.replace(t) { m ->
            val src = IMG_SRC.find(m.value)?.groupValues?.get(1).orEmpty()
            val alt = IMG_ALT.find(m.value)?.groupValues?.get(1).orEmpty()
            if (src.isEmpty()) "" else "![$alt]($src)"
        }
        t = ANCHOR.replace(t) { m ->
            val label = m.groupValues[2].ifBlank { m.groupValues[1] }
            "[$label](${m.groupValues[1]})"
        }
        t = SUMMARY.replace(t) { m -> "**" + m.groupValues[1].trim() + "**" }
        t = CODE_TAG.replace(t) { m -> "`" + m.groupValues[1] + "`" }
        t = BOLD_TAG.replace(t) { m -> "**" + m.groupValues[1] + "**" }
        t = ITALIC_TAG.replace(t) { m -> "*" + m.groupValues[1] + "*" }
        t = BR.replace(t, BREAK.toString())
        return TAG.replace(t, "")
    }

    private fun decode(s: String): String {
        if (s.indexOf('&') < 0) return s
        return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&amp;", "&")
    }

    // ------------------------------------------------------------------------------------------------ blocks

    private val ATX = Regex("""^ {0,3}(#{1,6})(?: +(.*))?$""")
    private val HR = Regex("""^ {0,3}([-*_])(?: *\1){2,} *$""")
    private val QUOTE = Regex("""^ {0,3}>""")
    private val QUOTE_STRIP = Regex("""^ {0,3}> ?""")
    private val SETEXT = Regex("""^ {0,3}(=+|-{2,}) *$""")
    private val BULLET = Regex("""^( *)([-*+])( +.*)?$""")
    private val ORDERED = Regex("""^( *)(\d{1,9})([.)])( +.*)?$""")
    private val DELIM = Regex("""^ {0,3}\|? *:?-+:? *(?:\| *:?-+:? *)*\|? *$""")
    private val TASK = Regex("""^\[([ xX])] +(.*)$""")
    private val CLOSING_HASHES = Regex("""\s+#+\s*$""")

    private class Marker(val ordered: Boolean, val indent: Int, val number: Int, val contentIndent: Int, val content: String)

    private fun markerOf(line: String): Marker? {
        BULLET.matchEntire(line)?.let { m ->
            val ind = m.groupValues[1].length
            if (ind > 3) return null
            val rest = m.groupValues[3]
            val spaces = rest.length - rest.trimStart(' ').length
            val content = rest.trimStart(' ')
            val w = if (content.isEmpty() || spaces > 4) 1 else spaces
            return Marker(false, ind, 0, ind + 1 + w, content)
        }
        ORDERED.matchEntire(line)?.let { m ->
            val ind = m.groupValues[1].length
            if (ind > 3) return null
            val digits = m.groupValues[2]
            val rest = m.groupValues[4]
            val spaces = rest.length - rest.trimStart(' ').length
            val content = rest.trimStart(' ')
            val w = if (content.isEmpty() || spaces > 4) 1 else spaces
            val num = digits.toIntOrNull() ?: return null
            return Marker(true, ind, num, ind + digits.length + 1 + w, content)
        }
        return null
    }

    private fun indentOf(s: String): Int = s.length - s.trimStart(' ').length

    /** Whether the line starts something that is not a continuation of a paragraph. */
    private fun startsBlock(line: String): Boolean {
        if (indentOf(line) <= 3 && fenceOf(line) != null) return true
        if (ATX.matches(line) || HR.matches(line) || QUOTE.containsMatchIn(line)) return true
        val m = markerOf(line) ?: return false
        if (m.content.isEmpty()) return false
        return !m.ordered || m.number == 1
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size) return false
        val head = lines[i]
        val delim = lines[i + 1]
        if (!head.contains('|') || !delim.contains('|') || !DELIM.matches(delim)) return false
        return cells(head).size == cells(delim).size
    }

    private fun cells(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length && t[i + 1] == '|') {
                cur.append('|')
                i += 2
                continue
            }
            if (c == '|') {
                out += cur.toString().trim()
                cur.setLength(0)
            } else {
                cur.append(c)
            }
            i++
        }
        val last = cur.toString().trim()
        if (last.isNotEmpty() || out.isEmpty() || !line.trim().endsWith("|")) out += last
        return out
    }

    private fun blocks(lines: List<String>, depth: Int): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val n = lines.size
        var i = 0
        while (i < n) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            if (depth >= MAX_DEPTH) {
                // too deep to be real: show the rest as text
                out += MdBlock.Para(inline(lines.subList(i, n).joinToString("\n") { it.trim() }))
                break
            }

            // fenced code
            val fence = if (indentOf(line) <= 3) fenceOf(line) else null
            if (fence != null) {
                val indent = indentOf(line)
                val lang = line.trim().substring(fence.length).trim().takeWhile { !it.isWhitespace() && it != '{' }
                val body = ArrayList<String>()
                i++
                while (i < n) {
                    val t = lines[i].trim()
                    if (t.length >= fence.length && t.all { it == fence[0] }) {
                        i++
                        break
                    }
                    body += lines[i].drop(minOf(indent, indentOf(lines[i])))
                    i++
                }
                out += MdBlock.Code(lang, body.joinToString("\n"))
                continue
            }

            // indented code
            if (line.startsWith("    ")) {
                val body = ArrayList<String>()
                while (i < n && (lines[i].startsWith("    ") || lines[i].isBlank())) {
                    body += lines[i].drop(4)
                    i++
                }
                while (body.isNotEmpty() && body.last().isBlank()) body.removeAt(body.size - 1)
                out += MdBlock.Code("", body.joinToString("\n"))
                continue
            }

            // heading
            val atx = ATX.matchEntire(line)
            if (atx != null) {
                val text = CLOSING_HASHES.replace(atx.groupValues[2], "").trim()
                out += MdBlock.Heading(atx.groupValues[1].length, inline(text))
                i++
                continue
            }

            if (HR.matches(line)) {
                out += MdBlock.Rule
                i++
                continue
            }

            if (QUOTE.containsMatchIn(line)) {
                val inner = ArrayList<String>()
                while (i < n && QUOTE.containsMatchIn(lines[i])) {
                    inner += QUOTE_STRIP.replaceFirst(lines[i], "")
                    i++
                }
                out += MdBlock.Quote(blocks(inner, depth + 1))
                continue
            }

            if (isTableStart(lines, i)) {
                val head = cells(line)
                val align = cells(lines[i + 1]).map { d ->
                    when {
                        d.startsWith(":") && d.endsWith(":") && d.length > 1 -> MdAlign.CENTER
                        d.endsWith(":") -> MdAlign.RIGHT
                        else -> MdAlign.LEFT
                    }
                }
                i += 2
                val rows = ArrayList<List<List<MdSpan>>>()
                while (i < n && lines[i].isNotBlank() && lines[i].contains('|')) {
                    val c = cells(lines[i])
                    rows += List(head.size) { k -> inline(c.getOrElse(k) { "" }) }
                    i++
                }
                out += MdBlock.Table(head.map { inline(it) }, align, rows)
                continue
            }

            val marker = markerOf(line)
            if (marker != null) {
                i = list(lines, i, marker.ordered, depth, out)
                continue
            }

            // paragraph (or a setext heading when an underline follows)
            val para = ArrayList<String>()
            para += line.trim()
            i++
            var heading = 0
            while (i < n) {
                val l = lines[i]
                if (l.isBlank()) break
                val se = SETEXT.matchEntire(l)
                if (se != null) {
                    heading = if (se.groupValues[1].startsWith("=")) 1 else 2
                    i++
                    break
                }
                if (startsBlock(l) || isTableStart(lines, i)) break
                para += l.trim()
                i++
            }
            val spans = inline(para.joinToString("\n"))
            out += if (heading > 0) MdBlock.Heading(heading, spans) else MdBlock.Para(spans)
        }
        return out
    }

    /** Reads a list starting at [start]; adds it to [out] and returns the index after it. */
    private fun list(lines: List<String>, start: Int, ordered: Boolean, depth: Int, out: MutableList<MdBlock>): Int {
        val n = lines.size
        var i = start
        val items = ArrayList<List<MdBlock>>()
        var first: Int? = null
        while (i < n) {
            val m = markerOf(lines[i])
            if (m == null || m.ordered != ordered || HR.matches(lines[i])) break
            if (first == null) first = m.number
            var head = m.content
            TASK.matchEntire(head)?.let { t -> head = (if (t.groupValues[1] == " ") "☐ " else "☑ ") + t.groupValues[2] }
            val body = ArrayList<String>()
            body += head
            i++
            while (i < n) {
                val l = lines[i]
                if (l.isBlank()) {
                    var j = i
                    while (j < n && lines[j].isBlank()) j++
                    if (j >= n) { i = j; break }
                    if (indentOf(lines[j]) >= 2) {
                        // the item goes on after a blank line
                        while (i < j) { body += ""; i++ }
                        continue
                    }
                    break
                }
                val ind = indentOf(l)
                if (ind >= 2) {
                    body += l.drop(minOf(ind, m.contentIndent))
                    i++
                    continue
                }
                if (markerOf(l) != null || startsBlock(l) || isTableStart(lines, i)) break
                body += l.trim()   // a line of the same paragraph that was not indented
                i++
            }
            while (body.isNotEmpty() && body.last().isBlank()) body.removeAt(body.size - 1)
            items += blocks(body, depth + 1)
            // another item of this list (maybe after blank lines)?
            var k = i
            while (k < n && lines[k].isBlank()) k++
            val next = if (k < n) markerOf(lines[k]) else null
            if (next != null && next.ordered == ordered && !HR.matches(lines[k])) i = k else break
        }
        out += MdBlock.Items(ordered, first ?: 1, items)
        return i
    }

    // ------------------------------------------------------------------------------------------------ inline

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val link: String? = null,
    )

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>\"'"

    private fun spans(s: String, st: Style, out: MutableList<MdSpan>, depth: Int) {
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                out += MdSpan(buf.toString(), st.bold, st.italic, st.strike, false, st.link)
                buf.setLength(0)
            }
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && ESCAPABLE.indexOf(s[i + 1]) >= 0 -> {
                    buf.append(s[i + 1])
                    i += 2
                }

                c == '`' -> {
                    var r = 1
                    while (i + r < s.length && s[i + r] == '`') r++
                    val close = backtickClose(s, i + r, r)
                    if (close < 0) {
                        buf.append("`".repeat(r))
                        i += r
                    } else {
                        flush()
                        var code = s.substring(i + r, close).replace('\n', ' ')
                        if (code.length >= 2 && code.startsWith(' ') && code.endsWith(' ') && code.isNotBlank()) {
                            code = code.substring(1, code.length - 1)
                        }
                        out += MdSpan(code, st.bold, st.italic, st.strike, true, st.link)
                        i = close + r
                    }
                }

                c == '!' && i + 1 < s.length && s[i + 1] == '[' -> {
                    val l = bracketLink(s, i + 1)
                    if (l == null) {
                        buf.append(c)
                        i++
                    } else {
                        flush()
                        val label = plain(inline(l.label)).trim()
                        val url = httpOnly(l.dest)
                        out += MdSpan(
                            if (label.isEmpty()) "[picture]" else "[picture: $label]", st.bold, st.italic, false, false,
                            url ?: st.link, picture = url != null, alt = label,
                        )
                        i = l.end
                    }
                }

                c == '[' -> {
                    val l = bracketLink(s, i)
                    if (l == null) {
                        buf.append(c)
                        i++
                    } else {
                        flush()
                        val url = httpOnly(l.dest)
                        if (depth < 8) spans(l.label, if (url != null) st.copy(link = url) else st, out, depth + 1)
                        else out += MdSpan(l.label, st.bold, st.italic, st.strike, false, url ?: st.link)
                        i = l.end
                    }
                }

                c == '<' && (s.startsWith("<http://", i) || s.startsWith("<https://", i)) && s.indexOf('>', i) > 0 &&
                    s.substring(i + 1, s.indexOf('>', i)).none { it.isWhitespace() } -> {
                    val end = s.indexOf('>', i)
                    val url = s.substring(i + 1, end)
                    flush()
                    out += MdSpan(url, st.bold, st.italic, st.strike, false, url)
                    i = end + 1
                }

                c == 'h' && st.link == null && (s.startsWith("http://", i) || s.startsWith("https://", i)) &&
                    (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    val url = bareUrl(s, i)
                    if (url.length <= "https://".length) {
                        buf.append(c)
                        i++
                    } else {
                        flush()
                        out += if (Links.isImage(url)) {
                            MdSpan("[picture]", st.bold, st.italic, false, false, url, picture = true)
                        } else {
                            MdSpan(url, st.bold, st.italic, st.strike, false, url)
                        }
                        i += url.length
                    }
                }

                c == '*' || c == '_' || c == '~' -> {
                    var r = 1
                    while (i + r < s.length && s[i + r] == c) r++
                    val d = when {
                        c == '~' -> if (r == 2) 2 else 0
                        r > 3 -> 0
                        else -> r
                    }
                    val canOpen = d > 0 && depth < 8 && i + r < s.length && !s[i + r].isWhitespace() &&
                        (c != '_' || i == 0 || !s[i - 1].isLetterOrDigit())
                    val close = if (canOpen) emphasisClose(s, i + r, c, d) else -1
                    if (close < 0) {
                        buf.append(c.toString().repeat(r))
                        i += r
                    } else {
                        flush()
                        val inner = when {
                            c == '~' -> st.copy(strike = true)
                            d == 1 -> st.copy(italic = true)
                            d == 2 -> st.copy(bold = true)
                            else -> st.copy(bold = true, italic = true)
                        }
                        spans(s.substring(i + r, close), inner, out, depth + 1)
                        i = close + r
                    }
                }

                else -> {
                    buf.append(c)
                    i++
                }
            }
        }
        flush()
    }

    private fun backtickClose(s: String, from: Int, r: Int): Int {
        var j = from
        while (j < s.length) {
            if (s[j] == '`') {
                var l = 1
                while (j + l < s.length && s[j + l] == '`') l++
                if (l == r) return j
                j += l
            } else {
                j++
            }
        }
        return -1
    }

    private fun emphasisClose(s: String, from: Int, c: Char, d: Int): Int {
        var j = from
        while (j < s.length) {
            val ch = s[j]
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == '`') {
                var r = 1
                while (j + r < s.length && s[j + r] == '`') r++
                val close = backtickClose(s, j + r, r)
                j = if (close < 0) j + r else close + r
                continue
            }
            if (ch == c) {
                var r = 1
                while (j + r < s.length && s[j + r] == c) r++
                val prevOk = j > from && !s[j - 1].isWhitespace()
                val nextOk = c != '_' || j + r >= s.length || !s[j + r].isLetterOrDigit()
                if (r == d && prevOk && nextOk) return j
                j += r
                continue
            }
            j++
        }
        return -1
    }

    private class BracketLink(val label: String, val dest: String, val end: Int)

    /** "[label](dest)" starting at the "[" at [i]; null when it is not one. */
    private fun bracketLink(s: String, i: Int): BracketLink? {
        var depth = 0
        var j = i
        while (j < s.length) {
            val c = s[j]
            if (c == '\\') {
                j += 2
                continue
            }
            if (c == '[') depth++
            if (c == ']') {
                depth--
                if (depth == 0) break
            }
            j++
        }
        if (j >= s.length || s[j] != ']') return null
        if (j + 1 >= s.length || s[j + 1] != '(') return null
        var k = j + 2
        var parens = 1
        while (k < s.length) {
            val c = s[k]
            if (c == '\\') {
                k += 2
                continue
            }
            if (c == '(') parens++
            if (c == ')') {
                parens--
                if (parens == 0) break
            }
            if (c == '\n') return null
            k++
        }
        if (k >= s.length) return null
        var dest = s.substring(j + 2, k).trim()
        val title = dest.indexOf(" \"")
        if (title > 0) dest = dest.substring(0, title).trim()
        dest = dest.removePrefix("<").removeSuffix(">")
        return BracketLink(s.substring(i + 1, j), dest, k + 1)
    }

    private fun httpOnly(dest: String): String? =
        if (dest.startsWith("http://") || dest.startsWith("https://")) dest else null

    private fun bareUrl(s: String, from: Int): String {
        var j = from
        while (j < s.length && !s[j].isWhitespace() && s[j] != '<' && s[j] != '>' && s[j] != '"') j++
        var url = s.substring(from, j)
        while (url.isNotEmpty()) {
            val last = url.last()
            val drop = last in ".,;:!?'*_~" ||
                (last == ')' && url.count { it == ')' } > url.count { it == '(' })
            if (!drop) break
            url = url.dropLast(1)
        }
        return url
    }

    private fun merge(list: List<MdSpan>): List<MdSpan> {
        val out = ArrayList<MdSpan>()
        for (s in list) {
            if (s.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && !last.code && !s.code && !last.picture && !s.picture && last.bold == s.bold && last.italic == s.italic &&
                last.strike == s.strike && last.link == s.link
            ) {
                out[out.size - 1] = last.copy(text = last.text + s.text)
            } else {
                out += s
            }
        }
        return out
    }
}
