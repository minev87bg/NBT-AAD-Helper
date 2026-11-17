package com.nbtaad.editor.env

sealed class EnvLine {
    data class Raw(val text: String) : EnvLine()
    data class Entry(val key: String, val value: String, val export: Boolean) : EnvLine()
}

/**
 * A KEY=VALUE file. Comments, blank lines and ordering are kept exactly as found,
 * only the values the user changed are touched.
 */
class EnvDoc(private val lines: MutableList<EnvLine>) {

    fun keys(): List<String> = lines.filterIsInstance<EnvLine.Entry>().map { it.key }.distinct()

    fun get(key: String): String? =
        lines.filterIsInstance<EnvLine.Entry>().lastOrNull { it.key == key }?.value

    /** [value] == null removes the key from the file. */
    fun set(key: String, value: String?) {
        if (value == null) {
            lines.removeAll { it is EnvLine.Entry && it.key == key }
            return
        }
        val idx = lines.indexOfLast { it is EnvLine.Entry && it.key == key }
        if (idx >= 0) {
            lines[idx] = (lines[idx] as EnvLine.Entry).copy(value = value)
        } else {
            lines.add(EnvLine.Entry(key, value, false))
        }
    }

    fun copyDoc(): EnvDoc = EnvDoc(lines.toMutableList())

    fun render(): String {
        val sb = StringBuilder()
        for (l in lines) {
            when (l) {
                is EnvLine.Raw -> sb.append(l.text)
                is EnvLine.Entry -> {
                    if (l.export) sb.append("export ")
                    sb.append(l.key).append('=').append(l.value)
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        private val LINE = Regex("""^\s*(export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$""")

        fun parse(text: String): EnvDoc {
            val src = text.replace("\r\n", "\n").replace('\r', '\n')
            val parts = src.split('\n')
            val rows = if (parts.isNotEmpty() && parts.last().isEmpty()) parts.dropLast(1) else parts
            val out = mutableListOf<EnvLine>()
            for (raw in rows) {
                val m = LINE.matchEntire(raw)
                if (m != null) {
                    out += EnvLine.Entry(m.groupValues[2], m.groupValues[3].trim(), m.groupValues[1].isNotEmpty())
                } else {
                    out += EnvLine.Raw(raw)
                }
            }
            return EnvDoc(out)
        }
    }
}
