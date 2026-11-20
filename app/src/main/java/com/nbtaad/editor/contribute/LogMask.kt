package com.nbtaad.editor.contribute

/** A log text with its secrets hidden, and how many were found (the zip and the screen say so). */
class Masked(val text: String, val passwords: Int, val vins: Int, val positions: Int = 0) {
    val any: Boolean get() = passwords + vins + positions > 0
}

/**
 * Hides what must not end up on a public issue page before a log leaves the phone: the WiFi password of the car's
 * hotspot (the daemon prints it so that it can be typed by hand: "passphrase from <file>: '<password>' (12 chars ..."),
 * the VIN (kept as "WBA…3076", like the app's own log) and where the car is: the daemon logs the car's GPS fix and the
 * location it sends to the phone, as "[GPS] ... 48.137154, 11.576124" and "Sensor LOCATION event #7 (48.137154, ...",
 * and the MOST log (NBT_MOST_LOG=1) holds the same fix as bytes. Pure, so it is tested.
 */
object LogMask {
    const val HIDDEN = "<hidden>"
    const val POSITION_HIDDEN = "<position hidden>"

    // the daemon's own lines, with a password that may hold spaces or any other character: anchored on what follows it
    private val PASS_FROM = Regex("""(passphrase from [^\n]*?: ')([^\n]*)(' \((?:decoded, )?\d+ chars)""")

    // any other "password: '...'" / "psk='...'" / "passphrase=value" (but not the "(read)" the daemon prints for a key it did not show)
    private val PASS_QUOTED = Regex("""(?i)(\b(?:passphrase|password|passwd|psk)\b\s*[=:]\s*')([^'\n]*)(')""")
    private val PASS_PLAIN = Regex("""(?i)(\b(?:passphrase|password|passwd|psk)\b\s*[=:]\s*)([^\s,;'"(][^\s,;'"]*)""")

    // a latitude and longitude with a few decimals, on the daemon's lines about the car's position and the location it sends on
    private val POSITION_LINE = Regex("""(?m)^([^\n]*(?:\[GPS]|Sensor LOCATION)[^\n]*?)(-?\d{1,3}\.\d{4,}\s*,\s*-?\d{1,3}\.\d{4,})""")

    // the MOST log's lines for the navigation's GNSS fix (52/00 fkt 440) and the other fix message (CD/01 fkt C00): the bytes are the position
    private val MOST_POSITION = Regex("""(?m)^(\[MOSTLOG][^\n]*? (?:52/00 fkt=440|CD/01 fkt=C00) [^\n]*?len=\d+:)([^\n]*)$""")

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
        var positions = 0
        t = POSITION_LINE.replace(t) { m -> positions++; m.groupValues[1] + POSITION_HIDDEN }
        t = MOST_POSITION.replace(t) { m ->
            if (m.groupValues[2].trim().startsWith(POSITION_HIDDEN)) {
                m.value
            } else {
                positions++
                m.groupValues[1] + " " + POSITION_HIDDEN
            }
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
        return Masked(t, passwords, vins, positions)
    }
}
