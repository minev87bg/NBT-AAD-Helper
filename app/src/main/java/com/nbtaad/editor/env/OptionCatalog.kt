package com.nbtaad.editor.env

/**
 * One setting of nbt-aad.env, as documented in the project's README ("Configuration" section).
 *
 *  - values: ready-made choices for the dropdown. "Custom..." is always offered next to them, so the user can
 *    type any other value; an option without choices is a plain text field.
 *  - labels: what a choice means ("2" -> "1280x720"), shown next to the choice.
 *  - closed: the choices are the complete list of valid values (a value outside it is probably a typo).
 */
data class Opt(
    val key: String,
    val group: String = "",
    /** one or two short sentences, shown under the name in the list */
    val summary: String = "",
    /** the full description for the (i) balloon */
    val info: String = "",
    /** the documented default, as the README words it; null when the README names none */
    val default: String? = null,
    val values: List<String> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
    /** an example shown in an empty text field, e.g. 1440x540 */
    val hint: String? = null,
    val closed: Boolean = false,
) {
    /** "1 = on", "bmw", ... as shown in the dropdown */
    fun choiceText(v: String): String = labels[v]?.let { "$v  ($it)" } ?: v
}
