package com.nbtaad.editor.contribute

/** The steps of the log wizard, in the order the person goes through them. */
enum class WizardStep(val label: String) {
    VERSION("Version"),
    SETTINGS("Settings"),
    TEST("Test"),
    RETURN("WiFi"),
    LOGS("Logs");

    val number: Int get() = ordinal + 1

    /**
     * The step that BACK goes to, or null on the first one (BACK then leaves the wizard). The logs step goes back to the
     * test, not to the WiFi step: that one moves on by itself as soon as the head unit answers.
     */
    fun before(): WizardStep? = when (this) {
        VERSION -> null
        SETTINGS -> VERSION
        TEST -> SETTINGS
        RETURN -> TEST
        LOGS -> TEST
    }
}

/** Addresses of the project's pages that the app opens in the browser. */
object PageLinks {
    /** An issue or pull request, scrolled down to the box where a new comment is written. */
    fun commentBox(url: String): String = url.substringBefore('#') + "#issue-comment-box"
}
