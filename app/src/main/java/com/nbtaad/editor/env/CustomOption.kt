package com.nbtaad.editor.env

/**
 * Options the person adds by hand: settings of newer or experimental builds that the README's list does not have.
 * Only the names are remembered (on the phone, no matter what the settings sync brings); a value is set like in any
 * other row. Kept free of Android classes so the build's unit tests can run it.
 */
object CustomOption {
    sealed interface Result
    data class Parsed(val key: String, val value: String) : Result
    data class Bad(val why: String) : Result

    private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private const val MAX_NAME = 64

    fun isName(s: String): Boolean = NAME.matches(s) && s.length <= MAX_NAME

    /**
     * Reads what was typed into the dialog. A pasted "NAME=value" in the name field is split into both parts (the
     * value field wins when it is filled in). A name that starts with nbt_ in any case is written in capitals, which
     * is how every setting of the daemon is spelled.
     */
    fun parse(rawName: String, rawValue: String): Result {
        var name = rawName.trim()
        var value = rawValue
        val eq = name.indexOf('=')
        if (eq >= 0) {
            if (value.isBlank()) value = name.substring(eq + 1)
            name = name.substring(0, eq).trim()
        }
        value = value.replace(Regex("[\r\n]+"), " ").trim()

        if (name.isEmpty()) return Bad("Type the name of the option, for example NBT_SOMETHING.")
        if (name.startsWith("nbt_", ignoreCase = true)) name = name.uppercase()
        if (name == "NBT_") return Bad("Type the name after NBT_.")
        if (name.length > MAX_NAME) return Bad("The name is too long (at most $MAX_NAME characters).")
        if (!NAME.matches(name)) {
            return Bad("A name has only letters, digits and _ , with no spaces (for example NBT_SOMETHING).")
        }
        if (name.length + 1 + value.length > EnvCheck.MAX_LINE) {
            return Bad("That line would be longer than ${EnvCheck.MAX_LINE} characters, which the head unit cuts off.")
        }
        return Parsed(name, value)
    }

    /** How the names are stored: one per line. */
    fun encode(keys: List<String>): String = keys.joinToString("\n")

    /** The stored names back; anything that is not a valid name, and repeats, are dropped. */
    fun decode(text: String?): List<String> =
        (text ?: "").lines().map { it.trim() }.filter { isName(it) }.distinct()
}
