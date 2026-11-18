package com.nbtaad.editor.net

import java.io.IOException

/**
 * The SSH connection could not even be made, so nothing was sent: the address may be old (the hotspot hands out new
 * ones when the dongle joins again), the head unit may be restarting, or the car asleep.
 */
class SshUnreachable(message: String, cause: Throwable? = null) : IOException(message, cause)

object Reach {
    /** Shown where an action needs the head unit and the first screen found none (or was skipped). */
    const val NO_UNIT = "No head unit is connected. Go back to the first screen and press DETECT CAR."

    /** Whether a failed connect means "no connection" (as opposed to a refused login or no common algorithm). */
    fun looksUnreachable(e: Throwable): Boolean {
        val msg = e.message ?: ""
        if (msg.contains("Auth fail", true) || msg.contains("Algorithm negotiation", true)) return false
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 6) {
            if (t is java.net.SocketException || t is java.net.SocketTimeoutException || t is java.net.UnknownHostException) {
                return true
            }
            t = t.cause
            depth++
        }
        return listOf("timeout", "timed out", "refused", "No route", "unreachable", "End of IO Stream", "reset")
            .any { msg.contains(it, ignoreCase = true) }
    }
}

/**
 * Runs [block] with the head unit's address. When no SSH connection can be made, the car is looked for again
 * ([rescan] does what DETECT CAR does and returns the address it found, or null) and [block] runs once more with that
 * address. Only a failed connect starts a second run, never a failure halfway through the work.
 */
fun <T> runOnHeadUnit(
    host: () -> String?,
    rescan: () -> String?,
    onRescan: () -> Unit = {},
    block: (String) -> T,
): T {
    val first = host() ?: throw IOException(Reach.NO_UNIT)
    try {
        return block(first)
    } catch (e: SshUnreachable) {
        onRescan()
        val fresh = rescan() ?: throw e
        return block(fresh)
    }
}
