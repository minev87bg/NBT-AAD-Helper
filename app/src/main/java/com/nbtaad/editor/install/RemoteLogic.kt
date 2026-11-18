package com.nbtaad.editor.install

/**
 * The parts of installing that need no Android and no SSH: the checksum the head unit's `cksum` prints, the shell
 * scripts that move files into place, and the reading of what they print. Kept free of Android classes so the
 * build's unit tests can run it (the scripts are also tried in a scratch folder before every release).
 */

/** The POSIX `cksum` of a byte array: a CRC-32 over the bytes and then over the length, and its complement. */
object PosixCksum {
    private val TABLE = IntArray(256).also { t ->
        for (i in 0 until 256) {
            var c = i shl 24
            repeat(8) { c = if (c and 0x80000000.toInt() != 0) (c shl 1) xor 0x04C11DB7 else c shl 1 }
            t[i] = c
        }
    }

    fun crc(data: ByteArray): Long {
        var crc = 0
        for (b in data) crc = (crc shl 8) xor TABLE[((crc ushr 24) xor (b.toInt() and 0xFF)) and 0xFF]
        var n = data.size.toLong()
        while (n != 0L) {
            crc = (crc shl 8) xor TABLE[((crc ushr 24) xor (n and 0xFF).toInt()) and 0xFF]
            n = n ushr 8
        }
        return crc.inv().toLong() and 0xFFFFFFFFL
    }

    /** "<crc> <size> <name>" as cksum prints it, or null when the text is not that. */
    fun parse(output: String): Pair<Long, Long>? {
        val m = Regex("""^\s*(\d+)\s+(\d+)\b""").find(output.lineSequence().firstOrNull { it.isNotBlank() } ?: return null)
            ?: return null
        val crc = m.groupValues[1].toLongOrNull() ?: return null
        val size = m.groupValues[2].toLongOrNull() ?: return null
        return crc to size
    }
}

/** What the folders of the head unit hold, from [RemoteScripts.status]. */
class UnitStatus(
    val aaDir: String?,
    val pluginDir: String?,
    val files: Map<String, Long>,       // files in the NBT-AAD folder: name to size
    val pluginFiles: Map<String, Long>, // files in the plugin folder: name to size
    val hasOldPlugin: Boolean,
    val version: String?,
) {
    val hasDaemon: Boolean get() = Names.DAEMON in files
    val hasPreviousDaemon: Boolean get() = "nbt-aad.old" in files
    val hasPrevious: Boolean get() = hasPreviousDaemon || hasOldPlugin
}

object RemoteScripts {
    const val PLUGIN_SUBPATH = "opt/hmi/ID5/data/ro/Plugins/NbtAad"
    /** The places the head unit's folders show up as, seen from the SSH login shell (see EnvRemote). */
    val PREFIXES = listOf("", "/net/hu-omap/fs/sda0", "/net/hu-omap")

    /** Prints `P <prefix>`, `AA <dir>` and `PL <dir>` lines for each prefix where the folder exists. */
    fun locate(): String =
        "for p in \"\" ${PREFIXES.drop(1).joinToString(" ")}; do " +
            "echo \"P \$p\"; " +
            "if [ -d \"\$p/opt/aa\" ]; then echo \"AA \$p/opt/aa\"; fi; " +
            "if [ -d \"\$p/$PLUGIN_SUBPATH\" ]; then echo \"PL \$p/$PLUGIN_SUBPATH\"; fi; " +
            "done"

    /** (NBT-AAD folder, plugin folder) from the output of [locate]; the first prefix that has both wins. */
    fun parseLocate(out: String): Pair<String?, String?> {
        class Row { var aa: String? = null; var pl: String? = null }
        val rows = ArrayList<Row>()
        for (raw in out.lines()) {
            val line = raw.trim()
            when {
                line == "P" || line.startsWith("P ") -> rows += Row()
                line.startsWith("AA ") -> rows.lastOrNull()?.aa = line.substring(3).trim()
                line.startsWith("PL ") -> rows.lastOrNull()?.pl = line.substring(3).trim()
            }
        }
        rows.firstOrNull { it.aa != null && it.pl != null }?.let { return it.aa to it.pl }
        return rows.firstNotNullOfOrNull { it.aa } to rows.firstNotNullOfOrNull { it.pl }
    }

    fun status(aa: String, pl: String?): String =
        "for f in nbt-aad nbt-aad.old; do if [ -f $aa/\$f ]; then echo \"F \$f \$(wc -c < $aa/\$f)\"; fi; done; " +
            "if [ -d $aa/NbtAad.old ]; then echo \"OLDPLUGIN\"; fi; " +
            (if (pl != null) "if [ -d $pl ]; then for f in $pl/*; do if [ -f \$f ]; then " +
                "echo \"L \$(basename \$f) \$(wc -c < \$f)\"; fi; done; fi; " else "") +
            "grep -a -o 'NBT-AAD-VERSION=v[0-9A-Za-z._+-]*' $aa/nbt-aad 2>/dev/null | head -1"

    fun parseStatus(aa: String?, pl: String?, out: String): UnitStatus {
        val files = LinkedHashMap<String, Long>()
        val plugin = LinkedHashMap<String, Long>()
        var oldPlugin = false
        var version: String? = null
        for (raw in out.lines()) {
            val line = raw.trim()
            val t = line.split(Regex("\\s+"))
            when {
                line == "OLDPLUGIN" -> oldPlugin = true
                t.size == 3 && t[0] == "F" -> t[2].toLongOrNull()?.let { files[t[1]] = it }
                t.size == 3 && t[0] == "L" -> t[2].toLongOrNull()?.let { plugin[t[1]] = it }
                line.startsWith("NBT-AAD-VERSION=") -> version = line.removePrefix("NBT-AAD-VERSION=")
            }
        }
        return UnitStatus(aa, pl, files, plugin, oldPlugin, version)
    }

    /** First word of each `pidin` line that names the daemon: its process id. */
    fun parsePids(out: String): List<Int> =
        out.lines().filter { it.contains("nbt-aad") && !it.contains("grep") }
            .mapNotNull { Regex("""^\s*(\d+)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .distinct()

    /** Size and checksum of one file as the head unit sees it: prints `<crc> <size> <name>` or nothing. */
    fun cksum(path: String): String = "cksum $path"

    fun wcBytes(path: String): String = "wc -c < $path"

    /** Makes the staging folder for the plugin; [keepOld] copies the files that are there now into it first. */
    fun stagePlugin(aa: String, pl: String?, keepOld: Boolean): String =
        "cd $aa || exit 41; rm -rf NbtAad.new; mkdir NbtAad.new || exit 42; " +
            (if (keepOld && pl != null) "if [ -d $pl ]; then cp $pl/* NbtAad.new/ || exit 43; fi; " else "")

    /**
     * Daemon: the running file is never overwritten. The new file waits as nbt-aad.new, the old one is renamed to
     * nbt-aad.old, then the new one takes its name; when that last step fails the old one is put back.
     */
    fun swapDaemon(aa: String): String = """
cd $aa || exit 11
if [ ! -f nbt-aad.new ]; then exit 15; fi
rm -f nbt-aad.old
if [ -f nbt-aad ]; then mv nbt-aad nbt-aad.old || exit 12; fi
if mv nbt-aad.new nbt-aad; then
  chmod 755 nbt-aad || exit 14
else
  if [ -f nbt-aad.old ]; then mv nbt-aad.old nbt-aad; fi
  exit 13
fi
exit 0
""".trim()

    /**
     * Plugin, as the update guide does it: the old folder moves to /opt/aa/NbtAad.old (never left inside Plugins,
     * where iDrive could load it as a second app) and a new folder is filled from the staged files. When the copy
     * fails the old folder is put back.
     */
    fun swapPlugin(aa: String, pl: String): String = """
cd $aa || exit 21
if [ ! -d NbtAad.new ]; then exit 24; fi
rm -rf NbtAad.old
if [ -d $pl ]; then mv $pl NbtAad.old || exit 22; fi
if mkdir -p $pl && cp NbtAad.new/* $pl/; then
  chmod 644 $pl/* 2>/dev/null
  rm -rf NbtAad.new
  exit 0
fi
rm -rf $pl
if [ -d NbtAad.old ]; then mv NbtAad.old $pl; fi
exit 23
""".trim()

    /** Swaps the current daemon with the previous one (so doing it twice returns to where it started). */
    fun revertDaemon(aa: String): String = """
cd $aa || exit 31
if [ ! -f nbt-aad.old ]; then exit 0; fi
if [ -f nbt-aad ]; then
  mv nbt-aad nbt-aad.swap || exit 32
  if mv nbt-aad.old nbt-aad; then
    mv nbt-aad.swap nbt-aad.old
  else
    mv nbt-aad.swap nbt-aad
    exit 33
  fi
else
  mv nbt-aad.old nbt-aad || exit 34
fi
exit 0
""".trim()

    /** The same swap for the plugin folder and /opt/aa/NbtAad.old. */
    fun revertPlugin(aa: String, pl: String): String = """
cd $aa || exit 36
if [ ! -d NbtAad.old ]; then exit 0; fi
if [ -d $pl ]; then
  mv $pl NbtAad.swap || exit 37
  if mv NbtAad.old $pl; then
    mv NbtAad.swap NbtAad.old
  else
    mv NbtAad.swap $pl
    exit 38
  fi
else
  mv NbtAad.old $pl || exit 39
fi
exit 0
""".trim()

    /** Removes the files an interrupted install left waiting to be moved into place (never a .swap: that may be the live one). */
    fun cleanStaging(aa: String): String =
        "cd $aa 2>/dev/null && rm -rf nbt-aad.new NbtAad.new; exit 0"

    fun exitMeaning(code: Int): String = when (code) {
        11, 21, 31, 36, 41 -> "the NBT-AAD folder could not be entered"
        12 -> "the old daemon could not be renamed"
        13 -> "the new daemon could not be moved into place (the old one was put back)"
        14 -> "the new daemon could not be made executable"
        15 -> "the uploaded daemon was not there"
        24 -> "the uploaded plugin files were not there"
        22 -> "the old plugin folder could not be moved away"
        23 -> "the new plugin files could not be copied (the old plugin was put back)"
        32, 34, 37, 39 -> "a file could not be renamed"
        33, 38 -> "the swap failed halfway and was undone"
        42 -> "the staging folder could not be made"
        43 -> "the current plugin files could not be copied"
        else -> "exit code $code"
    }
}
