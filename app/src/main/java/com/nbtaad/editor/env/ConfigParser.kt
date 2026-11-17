package com.nbtaad.editor.env

/**
 * Turns the "Configuration (nbt-aad.env)" section of the NBT-AAD README into a list of [Opt].
 *
 * The README is written for people, not machines, so the parser is built around the shapes the tables really use:
 *  - one setting per row (the common case),
 *  - several settings in one row, sharing one description (`A`, `B`, `C`), also as `NBT_TOUCH_SX/SY/OX/OY`,
 *  - `KEY=a|b|c` in the name cell (the allowed values) and `KEY=1` (an on/off switch),
 *  - templates such as `NBT_KEY_<SLOT>`,
 *  - 2 or 3 columns, and the "Turn off with" table where `0` switches a feature off.
 * Choices for the dropdowns come from the text itself (`media` = ..., `30` or `60`, defaults), and
 * [ConfigOverrides] refines the settings where the text alone is not enough. A setting that is new in the README
 * still shows up with its name, default and description, so the list keeps growing without an app update.
 *
 * Pure Kotlin on purpose: it runs in plain JVM unit tests.
 */
object ConfigParser {
    const val GROUP_MENTIONED = "Also mentioned in the descriptions"
    const val GROUP_FILE_ONLY = "In your file, not in the settings list"

    private const val SEP = '\u0001'
    private const val MAX_INFO = 2400

    private enum class Kind { COMMON, FEATURE, ADVANCED }
    private class Row(val group: String, val kind: Kind, val cells: List<String>)
    private class KeySpec(
        val key: String,
        val values: List<String> = emptyList(),
        val hint: String? = null,
        val boolForm: Boolean = false,
    )

    private val SEG = Regex("`([^`]*)`")
    private val KEY_OK = Regex("^NBT_[A-Z0-9_]*[A-Z0-9]$")
    private val SIMPLE = Regex("^[A-Za-z0-9_.:-]{1,16}$")
    private val NOT_A_DEFAULT = setOf("", "(none)", "(unset)", "none", "unset", "see above")

    /** The Configuration section of a whole README, or null when it is not there any more. */
    fun extractSection(readme: String): String? {
        val lines = readme.replace("\r\n", "\n").split('\n')
        val start = lines.indexOfFirst { it.startsWith("## Configuration") }
        if (start < 0) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].startsWith("## ")) {
                end = i
                break
            }
        }
        return lines.subList(start, end).joinToString("\n")
    }

    fun parse(section: String): List<Opt> {
        val text = section.replace("\r\n", "\n")
        val out = LinkedHashMap<String, Opt>()
        for (row in readRows(text)) {
            for (o in optsOf(row)) {
                val old = out[o.key]
                if (old == null) {
                    out[o.key] = o
                } else if (o.info.isNotEmpty() && !old.info.contains(o.info.take(60))) {
                    // the same setting is also named in another row: keep both descriptions
                    out[o.key] = old.copy(info = limit(old.info + "\n\nAlso in the README:\n" + o.info))
                }
            }
        }
        val known = out.keys.toMutableSet()
        for (e in ConfigOverrides.EXTRA) {
            if (e.key !in known) out[e.key] = e
            known += e.key
        }
        for (m in mentioned(text, known)) {
            out[m.key] = m
            known += m.key
        }
        return out.values.filter { it.key !in ConfigOverrides.HIDDEN }.map { ConfigOverrides.apply(it) }
    }

    // ---------------------------------------------------------------------------------------------
    // Tables
    // ---------------------------------------------------------------------------------------------

    private fun readRows(section: String): List<Row> {
        val out = ArrayList<Row>()
        var group = ""
        var header: List<String>? = null
        for (raw in section.split('\n')) {
            val line = raw.trim()
            if (line.startsWith("###")) {
                group = clean(line.trimStart('#'))
                header = null
                continue
            }
            if (!line.startsWith("|")) {
                header = null
                continue
            }
            val cells = splitRow(line)
            val h = header
            if (h == null) {
                header = cells.map { it.lowercase() }
                continue
            }
            if (cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' || it == ' ' } }) continue
            if (!h[0].contains("setting")) continue
            val kind = when {
                h.size >= 2 && h[1].startsWith("default") -> Kind.COMMON
                h.size >= 2 && h[1].startsWith("turn off") -> Kind.FEATURE
                else -> Kind.ADVANCED
            }
            if (cells.size >= 2) out += Row(group, kind, cells)
        }
        return out
    }

    /** Splits a table row at '|', except the "\|" that the README uses for a literal bar. */
    private fun splitRow(line: String): List<String> {
        val t = line.trim().removePrefix("|").removeSuffix("|").replace("\\|", SEP.toString())
        return t.split('|').map { it.trim().replace(SEP, '|') }
    }

    private fun optsOf(row: Row): List<Opt> {
        val keyCell = row.cells[0]
        val effectRaw: String
        var defaultCell: String? = null
        var offWith: String? = null
        when (row.kind) {
            Kind.COMMON -> {
                defaultCell = row.cells.getOrNull(1)
                effectRaw = row.cells.drop(2).joinToString(" | ")
            }
            Kind.FEATURE -> {
                offWith = row.cells.getOrNull(1)
                effectRaw = row.cells.drop(2).joinToString(" | ")
            }
            Kind.ADVANCED -> {
                if (row.cells.size >= 3) {
                    defaultCell = row.cells[1]
                    effectRaw = row.cells.drop(2).joinToString(" | ")
                } else {
                    effectRaw = row.cells[1]
                }
            }
        }

        val specs = keySpecs(keyCell)
        if (specs.isEmpty()) return emptyList()

        val template = SEG.findAll(keyCell).any { it.groupValues[1].contains("<SLOT>") }
        if (template) return templateOpts(row, keyCell, effectRaw)

        val shared = specs.size > 1
        val clauses = if (shared) splitClauses(effectRaw, specs.size) else null
        val names = specs.joinToString(", ") { it.key }

        return specs.mapIndexed { i, spec ->
            val own = clauses?.get(i) ?: effectRaw
            val ownClean = clean(if (clauses != null && clauses.prefix.isNotEmpty()) clauses.prefix + " " + own else own)
            val infoBody = buildString {
                if (shared && clauses == null) append("Described together with: ").append(names).append("\n\n")
                append(clean(effectRaw.takeIf { clauses == null } ?: (clauses!!.prefix + " " + own)))
            }

            var default: String? = null
            var values = spec.values
            var labels = emptyMap<String, String>()
            var bool = spec.boolForm

            when (row.kind) {
                Kind.FEATURE -> {
                    val off = clean(offWith ?: "")
                    if (off == "0") {
                        bool = true
                        default = "on"
                    } else if (off.startsWith("set to")) {
                        values = SEG.findAll(offWith ?: "").map { it.groupValues[1] }.filter { SIMPLE.matches(it) }.toList()
                    } else if (off.isNotEmpty()) {
                        default = off
                    }
                }
                else -> {
                    val d = clean(defaultCell ?: "")
                    if (d.lowercase() !in NOT_A_DEFAULT) default = d
                }
            }
            if (default == null && !(shared && clauses == null)) default = findDefault(own)
            if (clauses != null && default == null) default = findDefault(own)

            val dl = default?.lowercase()
            if (dl in setOf("on", "off", "yes", "no", "true", "false")) bool = true

            if (bool) {
                values = listOf("1", "0")
                labels = mapOf("1" to "on", "0" to "off")
            } else if (values.isEmpty() && !(shared && clauses == null)) {
                val found = textValues(own)
                values = found.keys.toList()
                labels = found
                val dTok = default?.takeIf { SIMPLE.matches(it) }
                if (dTok != null && values.isNotEmpty() && dTok !in values) values = listOf(dTok) + values
            }
            if (values.size < 2 && labels.isNotEmpty() && values.isEmpty()) labels = emptyMap()
            if (values.size < 2) {
                // a lone value is not worth a dropdown: it becomes the example in the text field
                values = emptyList()
            }
            labels = labels.filterKeys { it in values }

            labels = labels.filterValues { it.isNotBlank() }
            val hint = if (bool) null else spec.hint ?: rangeHint(own) ?: exampleHint(own) ?: default?.takeIf { SIMPLE.matches(it) }
            Opt(
                key = spec.key,
                group = row.group,
                summary = summaryOf(ownClean),
                info = limit(
                    infoBody + (default?.let { "\n\nDefault: $it" } ?: ""),
                ),
                default = default,
                values = values,
                labels = labels,
                hint = hint,
            )
        }
    }

    /** `NBT_KEY_<SLOT>`: one setting per hard key, with the default key code of each slot. */
    private fun templateOpts(row: Row, keyCell: String, effectRaw: String): List<Opt> {
        val tpl = SEG.findAll(keyCell).map { it.groupValues[1] }.first { it.contains("<SLOT>") }.trim()
        val slots = Regex("SLOT\\s*=\\s*`([^`]+)`").find(effectRaw)?.groupValues?.get(1)
            ?.split(Regex("[\\s,]+"))?.filter { it.isNotEmpty() } ?: return emptyList()
        val defs = Regex("Defaults\\s+(.+)$").find(clean(effectRaw))?.groupValues?.get(1)
            ?.split(',')?.map { it.trim() } ?: emptyList()
        val labels = LinkedHashMap<String, String>()
        labels["0"] = "ignore"
        for (d in defs) {
            val p = d.split(Regex("\\s+"), limit = 2)
            if (p.size == 2 && p[0].all { it.isDigit() }) labels.putIfAbsent(p[0], p[1])
        }
        val values = labels.keys.toList()
        val body = clean(effectRaw)
        return slots.mapIndexed { i, slot ->
            val d = defs.getOrNull(i)?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.all { c -> c.isDigit() } }
            Opt(
                key = tpl.replace("<SLOT>", slot),
                group = row.group,
                summary = "Android keycode for the $slot hard key; 0 = ignore the key.",
                info = limit(
                    "Android keycode for the $slot hard key on the iDrive controller. 0 = ignore the key. " +
                        "Choose a ready-made code or use Custom for any other Android keycode.\n\n" +
                        "Full README text for all keys:\n" + body +
                        (d?.let { "\n\nDefault for $slot: $it" + (labels[it]?.let { l -> " ($l)" } ?: "") } ?: ""),
                ),
                default = d?.let { it + (labels[it]?.let { l -> " ($l)" } ?: "") },
                values = values,
                labels = labels,
                hint = d,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The name cell
    // ---------------------------------------------------------------------------------------------

    private fun keySpecs(cell: String): List<KeySpec> {
        val out = ArrayList<KeySpec>()
        for (m in SEG.findAll(cell)) {
            val s = m.groupValues[1].trim()
            val eq = s.indexOf('=')
            val k = (if (eq >= 0) s.substring(0, eq) else s).trim()
            val v = if (eq >= 0) s.substring(eq + 1).trim() else null
            if (k.contains("<SLOT>")) {
                out += KeySpec(k)
                continue
            }
            val keys = if (k.contains('/')) {
                val p = k.split('/')
                val prefix = p[0].substringBeforeLast('_') + "_"
                listOf(p[0]) + p.drop(1).map { prefix + it }
            } else {
                listOf(k)
            }
            var vals = emptyList<String>()
            var hint: String? = null
            var boolForm = false
            if (!v.isNullOrEmpty()) {
                val alts = v.split('|').flatMap { a ->
                    if (Regex("^[A-Za-z0-9]/[A-Za-z0-9]$").matches(a)) a.split('/') else listOf(a.trim())
                }.filter { it.isNotEmpty() }
                vals = alts.filter { SIMPLE.matches(it) }
                hint = alts.firstOrNull { !SIMPLE.matches(it) }
                if (vals.size == 1 && vals[0] in setOf("0", "1") && hint == null) {
                    boolForm = true
                    vals = emptyList()
                }
            }
            for (key in keys) {
                if (KEY_OK.matches(key)) out += KeySpec(key, vals, hint, boolForm)
            }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------------------------------------------

    /** Clauses of a comma list, one per setting, when the README describes the settings in the same order. */
    private class Clauses(val prefix: String, private val items: List<String>) {
        operator fun get(i: Int): String = items[i]
    }

    private fun splitClauses(effect: String, n: Int): Clauses? {
        var text = effect.trim()
        var prefix = ""
        val colon = Regex("^([A-Za-z][A-Za-z ]{2,28}):\\s+").find(text)
        if (colon != null) {
            prefix = colon.groupValues[1] + ":"
            text = text.substring(colon.range.last + 1)
        }
        val parts = ArrayList<String>()
        var depth = 0
        var inTick = false
        val cur = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    inTick = !inTick
                    cur.append(c)
                }
                inTick -> cur.append(c)
                c == '(' -> {
                    depth++
                    cur.append(c)
                }
                c == ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    cur.append(c)
                }
                c == ',' && depth == 0 && text.getOrNull(i + 1) == ' ' -> {
                    parts += cur.toString().trim()
                    cur.setLength(0)
                }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotBlank()) parts += cur.toString().trim()
        return if (parts.size == n && n > 1) Clauses(prefix, parts) else null
    }

    private val DEFAULT_TICK = Regex("(?i)\\bdefault(?: in [A-Za-z ]+?)?[: ]+`([^`]+)`")
    private val DEFAULT_WORD = Regex("(?i)\\bdefault\\s+(on|off)\\b")
    private val DEFAULT_NUM = Regex("(?i)\\bdefault\\s+(\\d[\\w.]*)")

    private fun findDefault(text: String): String? =
        DEFAULT_TICK.find(text)?.groupValues?.get(1)
            ?: DEFAULT_WORD.find(text)?.groupValues?.get(1)?.lowercase()
            ?: DEFAULT_NUM.find(text)?.groupValues?.get(1)

    /** `1`..`60` -> "1 to 60". */
    private fun rangeHint(text: String): String? {
        val m = Regex("`(-?[\\d.]+)`(?:\\.\\.|-)`(-?[\\d.]+)`").find(text) ?: return null
        return m.groupValues[1] + " to " + m.groupValues[2]
    }

    private fun exampleHint(text: String): String? =
        Regex("e\\.g\\.\\s*`([^`]{1,40})`").find(text)?.groupValues?.get(1)

    /**
     * Choices named in the text: "`media` = Media/Radio list", "`toyota`, `bmw`, `mini` or `rollsroyce`",
     * "`30` or `60`". Ranges (`1`-`60`) are not choices. Returns value -> meaning ("" when unknown).
     */
    private fun textValues(text: String): LinkedHashMap<String, String> {
        val found = LinkedHashMap<String, String>()
        val t = Regex("`[-\\d.]+`(?:\\.\\.|-)`[-\\d.]+`").replace(text, " ")
        // "`tok` = meaning"
        for (m in Regex("`([^`]{1,16})`\\s*=\\s*((?:[^,;.(`)]|\\.(?=\\d)){1,44})(?=[,;(`)]|\\.\\s|\\.$|$)").findAll(t)) {
            val tok = m.groupValues[1]
            if (SIMPLE.matches(tok) && !tok.startsWith("NBT_")) {
                val label = clean(m.groupValues[2])
                found.putIfAbsent(tok, if (label.length < 2) "" else label)
            }
        }
        for (m in Regex("`([^`]{1,16})`\\s*=").findAll(t)) {
            val tok = m.groupValues[1]
            if (SIMPLE.matches(tok) && !tok.startsWith("NBT_")) found.putIfAbsent(tok, "")
        }
        // "`a`, `b` or `c`", "`30` or `60`", "`1` / `0`"
        for (run in Regex("`[^`]{1,16}`(?:\\s*(?:,|/|\\bor\\b)\\s*`[^`]{1,16}`)+").findAll(t)) {
            for (m in SEG.findAll(run.value)) {
                val tok = m.groupValues[1]
                if (SIMPLE.matches(tok) && !tok.startsWith("NBT_")) found.putIfAbsent(tok, "")
            }
        }
        if (found.size > 8) return LinkedHashMap()
        return found
    }

    private fun summaryOf(clean: String): String {
        if (clean.isEmpty()) return ""
        val sentences = clean.split(Regex("(?<!e\\.g)(?<!i\\.e)(?<=[a-z0-9)])\\.\\s+(?=[A-Z])"))
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s.trim().let { if (it.endsWith(".")) it else "$it." })
            if (sb.length >= 50) break
        }
        var r = sb.toString().removeSuffix(".")
        r = r.replaceFirstChar { it.uppercaseChar() }
        if (r.length > 150) r = r.take(147).trimEnd() + "..."
        return r
    }

    private fun limit(s: String): String =
        if (s.length <= MAX_INFO) s else s.take(MAX_INFO - 3).trimEnd() + "..."

    /** Plain text from README markdown: no backticks, bold or links. */
    fun clean(s: String): String {
        var t = s.replace(SEP, '|')
        t = Regex("\\[([^\\]]+)]\\([^)]*\\)").replace(t, "$1")
        t = t.replace("**", "").replace("`", "")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    // ---------------------------------------------------------------------------------------------
    // Settings that are only named inside descriptions
    // ---------------------------------------------------------------------------------------------

    private fun mentioned(section: String, known: Set<String>): List<Opt> {
        val out = LinkedHashMap<String, Opt>()
        for (raw in section.split('\n')) {
            val line = raw.trim()
            if (!line.startsWith("|")) continue
            val cells = splitRow(line)
            val owner = keySpecs(cells[0]).firstOrNull()?.key ?: continue
            val text = cells.drop(1).joinToString(" | ")
            for (m in Regex("`(NBT_[A-Z0-9_]*[A-Z0-9])(=([^`]*))?`").findAll(text)) {
                val key = m.groupValues[1]
                if (key in known || key in out) continue
                val v = m.groupValues[3].trim()
                val before = text.substring(0, m.range.first)
                val start = before.lastIndexOfAny(charArrayOf('.', ';')).let { if (it < 0) 0 else it + 1 }
                val after = text.indexOfAny(charArrayOf('.', ';'), m.range.last).let { if (it < 0) text.length else it + 1 }
                val sentence = clean(text.substring(start, after))
                out[key] = Opt(
                    key = key,
                    group = GROUP_MENTIONED,
                    summary = "Named in the description of $owner.",
                    info = limit("Named in the description of $owner:\n" + sentence),
                    values = emptyList(),
                    hint = v.takeIf { it.isNotEmpty() && SIMPLE.matches(it) },
                )
            }
        }
        return out.values.toList()
    }
}
