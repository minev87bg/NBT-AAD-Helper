package com.nbtaad.editor.env

/**
 * One editable option.
 *  - values: preset choices for the dropdown (empty = plain text field)
 *  - custom: also allow typing a value that is not in the presets
 */
data class Opt(
    val key: String,
    val title: String,
    val info: String,
    val values: List<String> = emptyList(),
    val custom: Boolean = false,
    /** false = known from the project's issues but not present in the file on the head unit */
    val inFile: Boolean = true,
    /** an example value taken from the file, shown as a hint in an empty text field */
    val hint: String? = null,
)

/**
 * IMPORTANT: this list was compiled from the public issue threads of Sq00ky/Supra-iDrive6-Android-Auto
 * (the README does not document the variables). Descriptions are best-effort summaries of what people
 * reported there, not the daemon's own documentation. To add or fix an option just edit this list.
 * Keys found in the file on the head unit that are NOT listed here are shown automatically as text fields.
 */
val CATALOG: List<Opt> = listOf(
    Opt(
        "NBT_CLUSTER", "Instrument cluster",
        "Instrument cluster type code. Choose one of the codes your nbt-aad.env lists (6WA or 6WB).",
        listOf("6WA", "6WB"), custom = true,
    ),
    Opt(
        "NBT_SCREEN", "Screen resolution",
        "Panel size as WIDTHxHEIGHT. Newer builds read it from the head unit; set it when the picture is " +
            "squashed or too narrow. Values seen in issues: 1280x480 (MINI 8.8\"), 1180x480, 1440x540 (G31 10.25\").",
        listOf("1280x480", "1180x480", "1440x540"), custom = true,
    ),
    Opt(
        "NBT_VID_DPI", "Video DPI",
        "Density of the Android Auto picture (default 140). Higher = bigger icons and text. " +
            "152 to 180 were reported to look better on 1280x480 panels.",
        listOf("120", "140", "152", "160", "170", "180", "200"), custom = true,
    ),
    Opt(
        "NBT_VID_INSETS", "Video insets",
        "Margins of the Android Auto picture inside the canvas, comma separated. A working MINI setup used " +
            "0,240,40,40; the default on a 1280x480 panel was reported as 0,240,0,0.",
        listOf("0,240,0,0", "0,240,40,40"), custom = true,
    ),
    Opt(
        "NBT_VEHICLE", "Vehicle identity",
        "Name and logo the phone sees. Without a carplay.ini the car is shown as Toyota GR Supra; " +
            "set bmw for BMW cars.",
        listOf("bmw"), custom = true,
    ),
    Opt(
        "NBT_ALT_WIFI", "Wireless AA WiFi source",
        "DIRECT = use the car's WiFi Direct (screen mirroring) group. Unset or BOTH = WiFi Direct first, " +
            "then the CarPlay hotspot after two tries without a connection.",
        listOf("DIRECT", "BOTH"),
    ),
    Opt(
        "NBT_WFD_SSID", "WiFi Direct SSID",
        "SSID of the car's WiFi Direct group (looks like DIRECT-xx-BMW...). Used on cars without CarPlay.",
    ),
    Opt(
        "NBT_USB", "USB mode",
        "Use the car's USB port for Android Auto (experimental, reported on a car without CarPlay).",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_USB_DIRECT", "USB direct mode",
        "Experimental. Reported to fix TLS drops when NBT_USB is on.",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_BT_CONN", "Bluetooth audio connection ID",
        "Car Bluetooth audio connection the daemon requests when a session starts. 0xc7 made media audio " +
            "start by itself and was used as a crash workaround.",
        listOf("0xc7"), custom = true,
    ),
    Opt(
        "NBT_AUDIOCONN_RETRIES", "Audio connect retries",
        "How often the daemon retries the Bluetooth audio connection. 0 was used as a stability workaround, " +
            "10 in a working MINI setup.",
        listOf("0", "3", "5", "10"), custom = true,
    ),
    Opt(
        "NBT_SPP_RECONNECT", "Bluetooth SPP reconnect",
        "Reconnect the Bluetooth serial channel automatically. 1 was used in a working MINI setup.",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_AA_PORT", "Android Auto TCP port",
        "TCP port of the daemon's Android Auto server. 49600 was used in a workaround for ConnectedDrive problems.",
        listOf("49600"), custom = true,
    ),
    Opt(
        "NBT_KEEP_PF", "Keep PF component",
        "Set to 1 in a reported workaround for ConnectedDrive stopping to work after installation " +
            "(together with NBT_AA_PORT=49600). Exact meaning: see the project issues.",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_AA_HOLD", "Screen exclusivity (knob block)",
        "On by default. While Android Auto is on screen the iDrive knob is blocked. 0 lets the MENU key " +
            "exit Android Auto again but disables the knob block.",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_KEY_MENU", "Forward MENU key to phone",
        "0 = the MENU key is not forwarded to the phone (used together with 'MENU opens iDrive').",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_BACK_HOLD_MS", "BACK hold time (ms)",
        "How long BACK must be held to exit Android Auto. 1000 was used in a report.",
        listOf("500", "1000", "1500", "2000"), custom = true,
    ),
    Opt(
        "NBT_CP_HANDOFF", "CarPlay video hand-off",
        "0 = create the video decoder context immediately instead of taking it over from the factory " +
            "CarPlay when the phone connects.",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_MMR_SPAWN", "Start own mm-renderer",
        "Reported as 1 on a car where the factory renderer was not running (CarPlay activation lost).",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_CHILD_NO_PRELOAD", "Child processes without LD_PRELOAD",
        "Reported as 1 together with NBT_MMR_SPAWN=1 in the same case.",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_VERBOSE", "Verbose log",
        "More detail in the daemon log.",
        listOf("0", "1"),
    ),
    Opt(
        "NBT_LOG_FILE", "Log file path",
        "Write the daemon log to this file, e.g. /dev/shmem/nbt-aad.log (RAM) or a path on a USB stick " +
            "like /mnt/umassXXXX/nbt-aad.log.",
        listOf("/dev/shmem/nbt-aad.log"), custom = true,
    ),
)
