package com.nbtaad.editor.env

sealed class EnvLine {
    data class Raw(val text: String) : EnvLine()

    /** An active "KEY=value" line. [raw] is the original text, kept so unchanged lines are written back untouched. */
    data class Entry(
        val key: String,
        val value: String,
        val export: Boolean,
        val trail: String = "",
        val raw: String? = null,
    ) : EnvLine()

    /** A commented-out "#KEY=value" line: an option that exists but is switched off. */
    data class Disabled(
        val key: String,
        val value: String,
        val prefix: String,
        val trail: String = "",
        val raw: String? = null,
    ) : EnvLine()
}

/**
 * A KEY=VALUE file. Comments, blank lines and ordering are kept exactly as found,
 * only the values the user changed are touched.
 *
 * Options that the file ships commented out ("#NBT_CLUSTER=6WA") are understood too: they are listed as
 * options (switched off), their values become suggestions, and the comment lines above them are their
 * description. Switching one on turns the line into an active one; switching an option off comments it out
 * again instead of deleting it.
 */
class EnvDoc(private val lines: MutableList<EnvLine>) {

    private fun keyOf(l: EnvLine): String? = when (l) {
        is EnvLine.Entry -> l.key
        is EnvLine.Disabled -> l.key
        is EnvLine.Raw -> null
    }

    /** Every option named in the file, active or commented out, in file order. */
    fun keys(): List<String> = lines.mapNotNull { keyOf(it) }.distinct()

    /** The active value, or null when the option is not switched on. */
    fun get(key: String): String? =
        lines.filterIsInstance<EnvLine.Entry>().lastOrNull { it.key == key }?.value

    /** Every value the file mentions for this option (active or commented out), without repeats. */
    fun fileValues(key: String): List<String> = lines.mapNotNull {
        when (it) {
            is EnvLine.Entry -> if (it.key == key) it.value else null
            is EnvLine.Disabled -> if (it.key == key) it.value else null
            is EnvLine.Raw -> null
        }
    }.filter { it.isNotEmpty() }.distinct()

    /** The comment block right above the option's first line, plus any comment at the end of its lines. */
    fun describe(key: String): String {
        val first = lines.indexOfFirst { keyOf(it) == key }
        if (first < 0) return ""
        val above = ArrayList<String>()
        var i = first - 1
        while (i >= 0) {
            val l = lines[i] as? EnvLine.Raw ?: break
            val t = l.text.trim()
            if (!t.startsWith("#")) break
            above += cleanComment(t)
            i--
        }
        above.reverse()
        val trails = lines.filter { keyOf(it) == key }.mapNotNull {
            when (it) {
                is EnvLine.Entry -> it.trail
                is EnvLine.Disabled -> it.trail
                is EnvLine.Raw -> null
            }
        }.map { cleanComment(it.trim()) }
        return (above + trails).filter { it.isNotEmpty() }.distinct().joinToString("\n")
    }

    /**
     * [value] == null switches the option off (the line is commented out, not deleted).
     * A non-null value switches it on: an active line is updated, otherwise a commented-out line with the
     * same value is switched on, otherwise a new line is added after the option's other lines.
     */
    fun set(key: String, value: String?) {
        val active = lines.indices.filter { (lines[it] as? EnvLine.Entry)?.key == key }
        if (value == null) {
            for (i in active) {
                val e = lines[i] as EnvLine.Entry
                lines[i] = EnvLine.Disabled(key, e.value, "#", e.trail)
            }
            return
        }
        if (active.isNotEmpty()) {
            val i = active.last()
            lines[i] = (lines[i] as EnvLine.Entry).copy(value = value, raw = null)
            return
        }
        val same = lines.indexOfFirst { it is EnvLine.Disabled && it.key == key && it.value == value }
        if (same >= 0) {
            val d = lines[same] as EnvLine.Disabled
            lines[same] = EnvLine.Entry(key, d.value, false, d.trail)
            return
        }
        val entry = EnvLine.Entry(key, value, false)
        val lastOfKey = lines.indexOfLast { it is EnvLine.Disabled && it.key == key }
        if (lastOfKey >= 0) lines.add(lastOfKey + 1, entry) else lines.add(entry)
    }

    fun copyDoc(): EnvDoc = EnvDoc(lines.toMutableList())

    fun render(): String {
        val sb = StringBuilder()
        for (l in lines) {
            when (l) {
                is EnvLine.Raw -> sb.append(l.text)
                is EnvLine.Entry -> sb.append(
                    l.raw ?: ((if (l.export) "export " else "") + l.key + "=" + l.value + l.trail)
                )
                is EnvLine.Disabled -> sb.append(l.raw ?: (l.prefix + l.key + "=" + l.value + l.trail))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        private val ACTIVE = Regex("""^\s*(export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$""")
        private val DISABLED = Regex("""^(\s*#+\s*)(NBT_[A-Za-z0-9_]+)=(.*)$""")
        private val DECORATION = Regex("""^[-=_*~#\s]*$""")

        /** "# some text" -> "some text"; ruler lines like "# -----" -> "". */
        private fun cleanComment(t: String): String {
            val s = t.trimStart('#').trim()
            return if (DECORATION.matches(s)) "" else s
        }

        /** Splits "value   # comment" into the value and the trailing comment (kept, with its spacing). */
        private fun splitTrail(v: String): Pair<String, String> {
            if (v.startsWith("\"") || v.startsWith("'")) {
                val end = v.indexOf(v[0], 1)
                if (end > 0) return v.substring(0, end + 1) to v.substring(end + 1)
                return v to ""
            }
            val m = Regex("""\s+#""").find(v) ?: return v to ""
            return v.substring(0, m.range.first) to v.substring(m.range.first)
        }

        fun parse(text: String): EnvDoc {
            val src = text.replace("\r\n", "\n").replace('\r', '\n')
            val parts = src.split('\n')
            val rows = if (parts.isNotEmpty() && parts.last().isEmpty()) parts.dropLast(1) else parts
            val out = mutableListOf<EnvLine>()
            for (raw in rows) {
                val a = ACTIVE.matchEntire(raw)
                if (a != null) {
                    val (v, trail) = splitTrail(a.groupValues[3].trim())
                    out += EnvLine.Entry(a.groupValues[2], v, a.groupValues[1].isNotEmpty(), trail, raw)
                    continue
                }
                val d = DISABLED.matchEntire(raw)
                if (d != null) {
                    val (v, trail) = splitTrail(d.groupValues[3].trim())
                    val quoted = v.startsWith("\"") || v.startsWith("'")
                    // "# NBT_X=1 is the default and means ..." is prose, not an option line
                    if (quoted || !v.any { it.isWhitespace() }) {
                        out += EnvLine.Disabled(d.groupValues[2], v, d.groupValues[1], trail, raw)
                        continue
                    }
                }
                out += EnvLine.Raw(raw)
            }
            return EnvDoc(out)
        }
    }
}
