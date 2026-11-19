package com.nbtaad.editor.env

/** One setting that is switched on in the head unit's nbt-aad.env, with what its value means when the README says. */
class AppliedOption(val key: String, val value: String, val meaning: String?) {
    /** "2 (1280x720)" when the README names the value, else the value as it stands in the file. */
    val shown: String get() = if (meaning != null) "$value ($meaning)" else value
}

/** What "Current status" lists: the settings that nbt-aad.env switches on. Free of Android classes so the build's unit tests can run it. */
object AppliedOptions {
    fun of(envText: String, catalog: List<Opt>): List<AppliedOption> {
        val byKey = catalog.associateBy { it.key }
        return EnvDoc.parse(envText).active().map { (k, v) -> AppliedOption(k, v, byKey[k]?.labels?.get(v)) }
    }
}
