package com.nbtaad.editor.env

/**
 * Checks a file against the rules of the program that really reads it. The iDrive app of NBT-AAD builds the
 * daemon's environment from nbt-aad.env like this (rhmiapp/nbt_aad_app.cpp, launchDaemon):
 *  - every line is read with fgets into a 160 byte buffer, so a line is at most 159 characters,
 *  - trailing CR, LF and spaces are cut off, empty lines, lines whose FIRST character is '#' and lines
 *    without '=' are skipped,
 *  - the line is handed over as it is: no quotes are removed, no "export", no trailing comment is stripped,
 *  - only 15 settings fit (the 16th and later are ignored without any message).
 */
object EnvCheck {
    const val MAX_SETTINGS = 15
    const val MAX_LINE = 159

    class Result(val count: Int, val errors: List<String>, val warnings: List<String>)

    private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val TRAILING_COMMENT = Regex("\\s#")

    fun analyze(text: String): Result {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        var count = 0
        val lines = text.replace("\r\n", "\n").split('\n')
        for ((i, raw) in lines.withIndex()) {
            val line = raw.trimEnd('\r', ' ')
            if (line.isEmpty() || line[0] == '#' || !line.contains('=')) continue
            count++
            val no = i + 1
            if (line.length > MAX_LINE) {
                errors += "Line $no is ${line.length} characters long. The daemon reads at most $MAX_LINE per line."
            }
            val name = line.substringBefore('=')
            if (!NAME.matches(name)) {
                warnings += "Line $no: \"$name\" is not a plain variable name (a leading space or the word " +
                    "\"export\" is not understood). The daemon would not see this setting."
            } else if (TRAILING_COMMENT.containsMatchIn(line.substringAfter('='))) {
                warnings += "Line $no ($name): a comment after the value is not removed. " +
                    "The daemon reads it as part of the value."
            }
        }
        if (count > MAX_SETTINGS) {
            errors += "$count settings are switched on, but the daemon only uses the first $MAX_SETTINGS. " +
                "Set ${count - MAX_SETTINGS} of them back to (not set)."
        }
        return Result(count, errors, warnings)
    }
}
