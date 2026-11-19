package com.nbtaad.editor.contribute

/** A log text with its secrets hidden, and how many were found (the zip and the screen say so). */
class Masked(val text: String, val passwords: Int, val vins: Int) {
    val any: Boolean get() = passwords + vins > 0
}

/**
 * Hides what must not end up on a public issue page before a log leaves the phone: the WiFi password of the car's
 * hotspot (the daemon prints it so that it can be typed by hand: "passphrase from <file>: '<password>' (12 chars ...")
 * and the VIN (kept as "WBA…3076", like the app's own log). Pure, so it is tested.
 */
object LogMask {
    const val HIDDEN = "<hidden>"

    // the daemon's own lines, with a password that may hold spaces or any other character: anchored on what follows it
    private val PASS_FROM = Regex("""(passphrase from [^\n]*?: ')([^\n]*)(' \((?:decoded, )?\d+ chars)""")

    // any other "password: '...'" / "psk='...'" / "passphrase=value" (but not the "(read)" the daemon prints for a key it did not show)
    private val PASS_QUOTED = Regex("""(?i)(\b(?:passphrase|password|passwd|psk)\b\s*[=:]\s*')([^'\n]*)(')""")
    private val PASS_PLAIN = Regex("""(?i)(\b(?:passphrase|password|passwd|psk)\b\s*[=:]\s*)([^\s,;'"(][^\s,;'"]*)""")

    private val VIN = Regex("""\b([A-HJ-NPR-Z0-9]{3})[A-HJ-NPR-Z0-9]{10}([A-HJ-NPR-Z0-9]{4})\b""")

    fun mask(text: String): Masked {
        var passwords = 0
        var vins = 0
        var t = PASS_FROM.replace(text) { m ->
            if (m.groupValues[2] == HIDDEN) m.value else { passwords++; m.groupValues[1] + HIDDEN + m.groupValues[3] }
        }
        t = PASS_QUOTED.replace(t) { m ->
            if (m.groupValues[2] == HIDDEN) m.value else { passwords++; m.groupValues[1] + HIDDEN + m.groupValues[3] }
        }
        t = PASS_PLAIN.replace(t) { m ->
            if (m.groupValues[2].startsWith(HIDDEN)) m.value else { passwords++; m.groupValues[1] + HIDDEN }
        }
        t = VIN.replace(t) { m ->
            val v = m.value
            // a VIN has both letters and digits; a long word or a long number is not one
            if (v.any { it.isDigit() } && v.any { it.isLetter() }) {
                vins++
                m.groupValues[1] + "…" + m.groupValues[2]
            } else {
                v
            }
        }
        return Masked(t, passwords, vins)
    }
}
