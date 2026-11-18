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

/** One file in the NBT-AAD folder: its size (-1 when the head unit has no tool to tell) and whether it can be run. */
class FileState(val size: Long, val executable: Boolean)

/** What the folders of the head unit hold, from [RemoteScripts.status]. */
class UnitStatus(
    val aaDir: String?,
    val pluginDir: String?,
    val files: Map<String, FileState>,  // files in the NBT-AAD folder
    val pluginFiles: Map<String, Long>, // files in the plugin folder: name to size (-1 = unknown)
    val hasOldPlugin: Boolean,
    val version: String?,
) {
    val hasDaemon: Boolean get() = Names.DAEMON in files
    val hasPreviousDaemon: Boolean get() = "nbt-aad.old" in files
    val hasPrevious: Boolean get() = hasPreviousDaemon || hasOldPlugin

    /** An nbt-aad that the head unit cannot run (for example after an install that stopped halfway). */
    val daemonBroken: Boolean get() = files[Names.DAEMON]?.let { !it.executable } ?: false
}

/** The daemon processes found by [RemoteScripts.procScan]: on this processor and on the one that runs Android Auto. */
class ProcScan(val scanned: Boolean, val here: List<Int>, val onHuOmap: List<Int>) {
    val any: Boolean get() = here.isNotEmpty() || onHuOmap.isNotEmpty()
}

/**
 * The SSH login of the head unit has a very short list of commands: cat, cp, mv, rm and mkdir are there, chmod, wc and
 * cksum were not found by a car test. Everything here uses only what the shell itself does (tests with [ ], read, case,
 * loops, globs) plus those five commands, and tries the other tools only as extras. Placeholders in the scripts: ¤ is
 * a shell dollar sign, @AA@ the NBT-AAD folder, @PL@ the plugin folder, @HUAA@ the NBT-AAD folder as the processor that
 * runs Android Auto names it.
 */
object RemoteScripts {
    const val PLUGIN_SUBPATH = "opt/hmi/ID5/data/ro/Plugins/NbtAad"
    /** The places the head unit's folders show up as, seen from the SSH login shell (see EnvRemote). */
    val PREFIXES = listOf("", "/net/hu-omap/fs/sda0", "/net/hu-omap")

    /** Added to every script: more places to look for commands (the interactive shell has them, an SSH command may not). */
    const val PATH_FIX = "PATH=¤PATH:/proc/boot:/bin:/usr/bin:/sbin:/usr/sbin:/usr/local/bin; export PATH\n"

    /** Tests set this to "" to see how the scripts do with only the short PATH of the car. */
    @JvmField var pathFix: String = PATH_FIX

    private fun sh(body: String, aa: String = "", pl: String = ""): String =
        (pathFix + body.trimIndent())
            .replace("@AA@", aa).replace("@PL@", pl).replace("@HUAA@", onHuOmapPath(aa))
            .replace('¤', '$')

    /** A path as the processor that runs Android Auto names it: /net/hu-omap/fs/sda0/opt/aa is /fs/sda0/opt/aa there. */
    fun onHuOmapPath(p: String): String = if (p.startsWith("/net/hu-omap/")) p.removePrefix("/net/hu-omap") else p

    /** Prints `P <prefix>`, `AA <dir>` and `PL <dir>` lines for each prefix where the folder exists. */
    fun locate(): String = sh(
        """
        for p in "" /net/hu-omap/fs/sda0 /net/hu-omap; do
          echo "P ¤p"
          if [ -d "¤p/opt/aa" ]; then echo "AA ¤p/opt/aa"; fi
          if [ -d "¤p/$PLUGIN_SUBPATH" ]; then echo "PL ¤p/$PLUGIN_SUBPATH"; fi
        done
        exit 0
        """
    )

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

    /** What is in the folders. Sizes are asked of `wc` when the head unit has it and left empty otherwise. */
    fun status(aa: String, pl: String?): String = sh(
        """
        for f in nbt-aad nbt-aad.old nbt-aad.new; do
          if [ -f @AA@/¤f ]; then
            if [ -x @AA@/¤f ]; then m=x; else m=-; fi
            echo "F ¤f ¤m ¤(wc -c < @AA@/¤f 2>/dev/null)"
          fi
        done
        if [ -d @AA@/NbtAad.old ]; then echo "OLDPLUGIN"; fi
        """ + (if (pl != null) """

        if [ -d @PL@ ]; then
          for f in @PL@/*; do
            if [ -f ¤f ]; then echo "L ¤{f##*/} ¤(wc -c < ¤f 2>/dev/null)"; fi
          done
        fi""" else "") + """

        grep -a -o 'NBT-AAD-VERSION=v[0-9A-Za-z._+-]*' @AA@/nbt-aad 2>/dev/null | head -1
        exit 0
        """,
        aa, pl ?: "",
    )

    fun parseStatus(aa: String?, pl: String?, out: String): UnitStatus {
        val files = LinkedHashMap<String, FileState>()
        val plugin = LinkedHashMap<String, Long>()
        var oldPlugin = false
        var version: String? = null
        for (raw in out.lines()) {
            val line = raw.trim()
            val t = line.split(Regex("\\s+"))
            when {
                line == "OLDPLUGIN" -> oldPlugin = true
                t.size >= 3 && t[0] == "F" -> files[t[1]] = FileState(t.getOrNull(3)?.toLongOrNull() ?: -1L, t[2] == "x")
                t.size >= 2 && t[0] == "L" -> plugin[t[1]] = t.getOrNull(2)?.toLongOrNull() ?: -1L
                line.startsWith("NBT-AAD-VERSION=") -> version = line.removePrefix("NBT-AAD-VERSION=")
            }
        }
        return UnitStatus(aa, pl, files, plugin, oldPlugin, version)
    }

    /**
     * Looks through the process lists (/proc here and /net/hu-omap/proc there) for a program called nbt-aad, using only
     * the shell: each process's exefile holds the path of its program.
     */
    fun procScan(): String = sh(
        """
        for d in /proc /net/hu-omap/proc; do
          if [ -d ¤d ]; then
            echo "SCAN ¤d"
            for p in ¤d/*; do
              if [ -e ¤p/exefile ]; then
                x=""
                read x < ¤p/exefile 2>/dev/null
                case "¤x" in *nbt-aad*) echo "PID ¤d ¤{p##*/}";; esac
              fi
            done
          fi
        done
        exit 0
        """
    )

    fun parseProc(out: String): ProcScan {
        var scanned = false
        val here = ArrayList<Int>()
        val far = ArrayList<Int>()
        for (raw in out.lines()) {
            val t = raw.trim().split(Regex("\\s+"))
            when {
                t.size == 2 && t[0] == "SCAN" -> scanned = true
                t.size == 3 && t[0] == "PID" -> t[2].toIntOrNull()?.let { if (t[1] == "/proc") here += it else far += it }
            }
        }
        return ProcScan(scanned, here.distinct(), far.distinct())
    }

    fun kill(pid: Int): String = sh("kill $pid\nexit 0")

    /** `kill` on the processor that runs Android Auto, as the update guide's shell on it would. */
    fun killOnHuOmap(pid: Int): String = sh("on -f /net/hu-omap/ /bin/sh -c 'kill $pid' < /dev/null 2>/dev/null\nexit 0")

    /** Size and checksum of one file as the head unit sees it: prints `<crc> <size> <name>` or nothing. */
    fun cksum(path: String): String = sh("cksum $path")

    fun wcBytes(path: String): String = sh("wc -c < $path")

    /**
     * Gets the file for the new daemon ready: a copy of a program that can be run, which the upload then writes into.
     * A copy keeps the mode of its source and writing into an existing file keeps it, so the new daemon is executable
     * without `chmod`, which the head unit's SSH login does not have.
     */
    fun stageDaemon(aa: String): String = sh(
        """
        cd @AA@ || exit 51
        rm -f nbt-aad.new
        for t in /bin/cat /usr/bin/cat /proc/boot/cat /usr/local/bin/cat @AA@/nbt-aad; do
          if [ -x ¤t ]; then
            if cp ¤t nbt-aad.new 2>/dev/null; then break; fi
          fi
        done
        exit 0
        """,
        aa,
    )

    /** Makes sure nbt-aad.new can be run, with chmod in as many places as it may be. Exit 14: it cannot. */
    fun makeExec(aa: String): String = sh(
        """
        cd @AA@ || exit 51
        f=nbt-aad.new
        if [ ! -f ¤f ]; then exit 15; fi
        if [ -x ¤f ]; then exit 0; fi
        chmod 755 ¤f 2>/dev/null
        if [ -x ¤f ]; then exit 0; fi
        for c in /proc/boot/chmod /bin/chmod /usr/bin/chmod /sbin/chmod /usr/sbin/chmod /usr/local/bin/chmod; do
          if [ -x ¤c ]; then
            ¤c 755 ¤f 2>/dev/null
            if [ -x ¤f ]; then exit 0; fi
          fi
        done
        on -f /net/hu-omap/ /bin/sh -c "chmod 755 @HUAA@/nbt-aad.new" < /dev/null 2>/dev/null
        if [ -x ¤f ]; then exit 0; fi
        on -f /net/hu-omap/ chmod 755 @HUAA@/nbt-aad.new < /dev/null 2>/dev/null
        if [ -x ¤f ]; then exit 0; fi
        exit 14
        """,
        aa,
    )

    /** Makes the staging folder for the plugin; [keepOld] copies the files that are there now into it first. */
    fun stagePlugin(aa: String, pl: String?, keepOld: Boolean): String = sh(
        """
        cd @AA@ || exit 41
        rm -rf NbtAad.new
        mkdir NbtAad.new || exit 42
        """ + (if (keepOld && pl != null) """

        if [ -d @PL@ ]; then cp @PL@/* NbtAad.new/ || exit 43; fi""" else "") + """

        exit 0
        """,
        aa, pl ?: "",
    )

    /**
     * Daemon: the running file is never overwritten. The new file waits as nbt-aad.new (and must be executable), the
     * old one is renamed to nbt-aad.old, then the new one takes its name; when that last step fails the old one is put
     * back. A current nbt-aad that cannot be run (a broken install) never pushes a good nbt-aad.old out: it is set
     * aside as nbt-aad.broken instead.
     */
    fun swapDaemon(aa: String): String = sh(
        """
        cd @AA@ || exit 11
        if [ ! -f nbt-aad.new ]; then exit 15; fi
        if [ ! -x nbt-aad.new ]; then exit 14; fi
        if [ -f nbt-aad ]; then
          if [ -x nbt-aad ] || [ ! -f nbt-aad.old ]; then
            rm -f nbt-aad.old
            mv nbt-aad nbt-aad.old || exit 12
          else
            rm -f nbt-aad.broken
            mv nbt-aad nbt-aad.broken || exit 12
          fi
        fi
        if mv nbt-aad.new nbt-aad; then exit 0; fi
        if [ ! -f nbt-aad ]; then
          if [ -f nbt-aad.old ]; then mv nbt-aad.old nbt-aad; fi
        fi
        exit 13
        """,
        aa,
    )

    /**
     * Plugin, as the update guide does it: the old folder moves to /opt/aa/NbtAad.old (never left inside Plugins,
     * where iDrive could load it as a second app) and a new folder is filled from the staged files. When the copy
     * fails the old folder is put back.
     */
    fun swapPlugin(aa: String, pl: String): String = sh(
        """
        cd @AA@ || exit 21
        if [ ! -d NbtAad.new ]; then exit 24; fi
        rm -rf NbtAad.old
        if [ -d @PL@ ]; then mv @PL@ NbtAad.old || exit 22; fi
        if mkdir -p @PL@ && cp NbtAad.new/* @PL@/; then
          chmod 644 @PL@/* 2>/dev/null
          rm -rf NbtAad.new
          exit 0
        fi
        rm -rf @PL@
        if [ -d NbtAad.old ]; then mv NbtAad.old @PL@; fi
        exit 23
        """,
        aa, pl,
    )

    /** Swaps the current daemon with the previous one (so doing it twice returns to where it started). */
    fun revertDaemon(aa: String): String = sh(
        """
        cd @AA@ || exit 31
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
        """,
        aa,
    )

    /** The same swap for the plugin folder and /opt/aa/NbtAad.old. */
    fun revertPlugin(aa: String, pl: String): String = sh(
        """
        cd @AA@ || exit 36
        if [ ! -d NbtAad.old ]; then exit 0; fi
        if [ -d @PL@ ]; then
          mv @PL@ NbtAad.swap || exit 37
          if mv NbtAad.old @PL@; then
            mv NbtAad.swap NbtAad.old
          else
            mv NbtAad.swap @PL@
            exit 38
          fi
        else
          mv NbtAad.old @PL@ || exit 39
        fi
        exit 0
        """,
        aa, pl,
    )

    /** Removes the files an interrupted install left waiting to be moved into place (never a .swap: that may be the live one). */
    fun cleanStaging(aa: String): String = sh("cd @AA@ 2>/dev/null && rm -rf nbt-aad.new NbtAad.new\nexit 0", aa)

    /** Which commands and folders the SSH login of the head unit has: shown to the person, who can send it on. */
    fun diagnose(): String = sh(
        """
        echo "PATH=¤PATH"
        echo "UMASK ¤(umask 2>&1)"
        echo "ID ¤(id 2>&1)"
        for c in chmod wc cksum ls grep head basename pidin on kill slay sleep mount df dd cmp od sync cat cp mv rm mkdir; do
          echo "T ¤c: ¤(type ¤c 2>&1)"
        done
        for d in /proc/boot /bin /usr/bin /sbin /usr/sbin; do
          l=""
          for f in ¤d/*; do l="¤l ¤{f##*/}"; done
          echo "DIR ¤d:¤l"
        done
        exit 0
        """
    )

    fun exitMeaning(code: Int): String = when (code) {
        11, 21, 31, 36, 41, 51 -> "the NBT-AAD folder could not be entered"
        12 -> "the old daemon could not be renamed"
        13 -> "the new daemon could not be moved into place (the old one was put back)"
        14 -> "the new daemon is not executable and the head unit's SSH login has no way to make it so"
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
