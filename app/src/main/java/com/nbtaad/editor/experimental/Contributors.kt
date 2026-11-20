package com.nbtaad.editor.experimental

/**
 * The people who build the project the app is made for. Their names are lit up on every page and comment of the
 * Experimental page, as a small thank you, and so that their posts are easy to find.
 */
object Contributors {
    /** GitHub logins (lower case): Sq00ky, mhmm-ok and tom-pryor. */
    private val MAIN = setOf("sq00ky", "mhmm-ok", "tom-pryor")

    /** GitHub does not care about the case of a login, nor does this. */
    fun isMain(login: String?): Boolean = login != null && login.trim().lowercase() in MAIN
}
