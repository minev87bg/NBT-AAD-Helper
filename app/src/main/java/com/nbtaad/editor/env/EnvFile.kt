package com.nbtaad.editor.env

sealed class EnvLine {
    data class Raw(val text: String) : EnvLine()

    /** An active "KEY=value" line. [raw] is the original text, kept so unchanged lines are written back untouched. */
    data class Entry(
        val key: String,
        val value: String,
        val raw: String? = null,
    ) : EnvLine()

    /** A commented-out "#KEY=value" line: a setting that was switched off, kept so its value is not lost. */
    data class Disabled(
        val key: String,
        val value: String,
        val prefix: String,
        val raw: String? = null,
    ) : EnvLine()
}

/**
 * A KEY=VALUE file. Comments, blank lines and ordering are kept exactly as found, only the values the user
 * changed are touched.
 *
 * What counts as a setting follows the program that reads the file on the head unit (see [EnvCheck]): a line is
 * KEY=value when it starts with the variable name, with no "export" and no leading space, and the value is the
 * rest of the line, trailing comment included. Switching a setting off comments its line out instead of deleting
 * it ("#NBT_X=1"), so the value can come back.
 */
class EnvDoc(private val lines: MutableList<EnvLine>) {

    private fun keyOf(l: EnvLine): String? = when (l) {
        is EnvLine.Entry -> l.key
        is EnvLine.Disabled -> l.key
        is EnvLine.Raw -> null
    }

    /** Every setting named in the file, active or commented out, in file order. */
    fun keys(): List<String> = lines.mapNotNull { keyOf(it) }.distinct()

    /** The active value, or null when the setting is not switched on. */
    fun get(key: String): String? =
        lines.filterIsInstance<EnvLine.Entry>().lastOrNull { it.key == key }?.value

    /**
     * The settings that are switched on, in file order, each with the value the daemon would get. A setting with an
     * empty value counts as off (the settings page treats it the same way), and one that appears twice counts once
     * with its last value, as [get] has it.
     */
    fun active(): List<Pair<String, String>> =
        keys().mapNotNull { k -> get(k)?.takeIf { it.isNotBlank() }?.let { k to it } }

    /**
     * [value] == null switches the setting off (the line is commented out, not deleted).
     * A non-null value switches it on: an active line is updated, otherwise a commented-out line with the
     * same value is switched on, otherwise a new line is added after the setting's other lines.
     */
    fun set(key: String, value: String?) {
        val active = lines.indices.filter { (lines[it] as? EnvLine.Entry)?.key == key }
        if (value == null) {
            for (i in active) {
                val e = lines[i] as EnvLine.Entry
                lines[i] = EnvLine.Disabled(key, e.value, "#")
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
            lines[same] = EnvLine.Entry(key, d.value)
            return
        }
        val entry = EnvLine.Entry(key, value)
        val lastOfKey = lines.indexOfLast { it is EnvLine.Disabled && it.key == key }
        if (lastOfKey >= 0) lines.add(lastOfKey + 1, entry) else lines.add(entry)
    }

    fun copyDoc(): EnvDoc = EnvDoc(lines.toMutableList())

    fun render(): String {
        val sb = StringBuilder()
        for (l in lines) {
            when (l) {
                is EnvLine.Raw -> sb.append(l.text)
                is EnvLine.Entry -> sb.append(l.raw ?: (l.key + "=" + l.value))
                is EnvLine.Disabled -> sb.append(l.raw ?: (l.prefix + l.key + "=" + l.value))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        private val ACTIVE = Regex("""^([A-Za-z_][A-Za-z0-9_]*)=(.*)$""")
        private val DISABLED = Regex("""^(\s*#+\s*)(NBT_[A-Za-z0-9_]+)=(\S*)\s*$""")

        fun parse(text: String): EnvDoc {
            val src = text.replace("\r\n", "\n").replace('\r', '\n')
            val parts = src.split('\n')
            val rows = if (parts.isNotEmpty() && parts.last().isEmpty()) parts.dropLast(1) else parts
            val out = mutableListOf<EnvLine>()
            for (raw in rows) {
                val a = ACTIVE.matchEntire(raw)
                if (a != null) {
                    out += EnvLine.Entry(a.groupValues[1], a.groupValues[2].trimEnd(), raw)
                    continue
                }
                val d = DISABLED.matchEntire(raw)
                if (d != null) {
                    out += EnvLine.Disabled(d.groupValues[2], d.groupValues[3], d.groupValues[1], raw)
                    continue
                }
                out += EnvLine.Raw(raw)
            }
            return EnvDoc(out)
        }
    }
}
