package com.nbtaad.editor.install

/**
 * The parts of installing that need no Android and no SSH: the shell scripts that move files into place, and the
 * reading of what they print. Kept free of Android classes so the
 * build's unit tests can run it (the scripts are also tried in a scratch folder before every release).
 */

/** One file in the NBT-AAD folder: its size (-1 when the head unit has no tool to tell) and whether it can be run. */
class FileState(val size: Long, val executable: Boolean)

/**
 * The nbt-aad that is on the head unit, read back over SSH with `cat` (the one tool the login surely has) and looked at
 * here: how big it is, its SHA-256, and the version it names inside. The SHA-256 is what is compared with the checksums
 * GitHub lists for the files of the project's releases.
 */
class DaemonFile(val size: Long, val sha256: String, val version: String?)

/** What the folders of the head unit hold, from [RemoteScripts.status]. */
class UnitStatus(
    val aaDir: String?,
    val pluginDir: String?,
    val files: Map<String, FileState>,  // files in the NBT-AAD folder
    val pluginFiles: Map<String, Long>, // files in the plugin folder: name to size (-1 = unknown)
    val hasOldPlugin: Boolean,
    val version: String?,
    /** The text of nbt-aad.env, when it was asked for and the file is there (else null; see [envMissing]). */
    val envText: String? = null,
    /** nbt-aad.env was asked for and there is no such file: it is optional, every setting then runs at its default. */
    val envMissing: Boolean = false,
    /** The installed nbt-aad as read back and hashed here; null while that has not been done or when it did not work. */
    val daemonFile: DaemonFile? = null,
    /** Reading the installed nbt-aad back was tried and did not work. */
    val daemonUnreadable: Boolean = false,
    /** The build the daemon named in its RAM log when it last started (null: no such log, or no such line in it). */
    val runningVersion: String? = null,
) {
    val hasDaemon: Boolean get() = Names.DAEMON in files
    val hasPreviousDaemon: Boolean get() = "nbt-aad.old" in files
    val hasPrevious: Boolean get() = hasPreviousDaemon || hasOldPlugin

    /** An nbt-aad that the head unit cannot run (for example after an install that stopped halfway). */
    val daemonBroken: Boolean get() = files[Names.DAEMON]?.let { !it.executable } ?: false

    /** The version the installed file names inside itself: what the head unit's own tools found, else what was read from it. */
    val fileVersion: String? get() = version ?: daemonFile?.version

    /**
     * The version to show: the one inside the file when that is known, else the build the daemon logged when it last
     * started (instant to ask for, and what Android Auto is running, which is the installed file unless it was replaced since).
     */
    val installedVersion: String? get() = fileVersion ?: runningVersion

    /** [installedVersion] is the one from the daemon's log, not from the file. */
    val versionFromLog: Boolean get() = fileVersion == null && runningVersion != null

    /** The same status with the result of reading the installed daemon back ([file] null: it did not work). */
    fun withDaemonFile(file: DaemonFile?): UnitStatus =
        UnitStatus(
            aaDir, pluginDir, files, pluginFiles, hasOldPlugin, version, envText, envMissing,
            daemonFile = file, daemonUnreadable = file == null, runningVersion = runningVersion,
        )

    /** One line for the log: where the folders are and what is in them. */
    fun describe(): String =
        "folder $aaDir, plugin $pluginDir, version $installedVersion${if (versionFromLog) " (from the daemon's log)" else ""}, files " +
            files.entries.joinToString { "${it.key}(${it.value.size}${if (it.value.executable) ",exec" else ""})" } +
            ", ${pluginFiles.size} plugin files, old plugin $hasOldPlugin" +
            (daemonFile?.let { ", daemon read back ${it.size} bytes sha256 ${it.sha256.take(16)}" }
                ?: if (daemonUnreadable) ", daemon could not be read back" else "") +
            (if (envText != null) ", nbt-aad.env ${envText.length} bytes" else if (envMissing) ", no nbt-aad.env" else "")
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

    // ---- the pieces of shell that the scripts are built from; each SSH command costs the slow head unit a good part of
    // a second, so a whole step (look, prepare, check, replace) is one script made of these pieces ----

    private val LOCATE_PART = """
        for p in "" /net/hu-omap/fs/sda0 /net/hu-omap; do
          echo "P ¤p"
          if [ -d "¤p/opt/aa" ]; then echo "AA ¤p/opt/aa"; fi
          if [ -d "¤p/$PLUGIN_SUBPATH" ]; then echo "PL ¤p/$PLUGIN_SUBPATH"; fi
        done
    """.trimIndent()

    private val PROC_PART = """
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
    """.trimIndent()

    private val REMOUNT_PART = """
        if mount -o remount rw /net/hu-omap/fs/sda0 >/dev/null 2>&1; then echo "REMOUNT ok"
        elif mount -o remount rw /fs/sda0 >/dev/null 2>&1; then echo "REMOUNT ok"
        else echo "REMOUNT failed"
        fi
    """.trimIndent()

    /**
     * The build lines of the daemon's RAM log (`grep` is there, unlike wc/head/od; the project's update guide asks for the
     * same). The log is in RAM: it is there once Android Auto has been started since the head unit booted. Android Auto
     * runs on the hu-omap processor, which this login may reach only as /net/hu-omap (as it does the /opt/aa folder), so both
     * names are tried.
     */
    private val BUILD_PART = """
        for d in /dev/shmem /net/hu-omap/dev/shmem; do
          if [ -f ¤d/nbt-aad.log ]; then
            echo "BUILD-BEGIN"
            grep "Build: " ¤d/nbt-aad.log 2>/dev/null
            echo ""
            echo "BUILD-END"
            break
          fi
        done
    """.trimIndent()

    private fun statusPart(hasPlugin: Boolean): String = """
        for f in nbt-aad nbt-aad.old nbt-aad.new; do
          if [ -f @AA@/¤f ]; then
            if [ -x @AA@/¤f ]; then m=x; else m=-; fi
            echo "F ¤f ¤m ¤(wc -c < @AA@/¤f 2>/dev/null)"
          fi
        done
        if [ -d @AA@/NbtAad.old ]; then echo "OLDPLUGIN"; fi
    """.trimIndent() + (if (hasPlugin) "\n" + """
        if [ -d @PL@ ]; then
          for f in @PL@/*; do
            if [ -f ¤f ]; then echo "L ¤{f##*/} ¤(wc -c < ¤f 2>/dev/null)"; fi
          done
        fi
    """.trimIndent() else "") + "\n" + """
        grep -a -o 'NBT-AAD-VERSION=v[0-9A-Za-z._+-]*' @AA@/nbt-aad 2>/dev/null
    """.trimIndent()

    /**
     * The text of nbt-aad.env between two marker lines (`cat` is one of the few tools the login has). The extra empty
     * line makes sure the end marker starts on a line of its own, whether or not the file ends with a line break.
     */
    private val ENV_PART = """
        if [ -f @AA@/nbt-aad.env ]; then
          echo "ENV-BEGIN"
          cat @AA@/nbt-aad.env
          echo ""
          echo "ENV-END"
        else
          echo "ENV-NONE"
        fi
    """.trimIndent()

    /** Makes sure nbt-aad.new can be run, with chmod in as many places as it may be. Returns 14: it cannot. */
    private val MAKE_EXEC_FN = """
        make_exec() {
          f=nbt-aad.new
          if [ ! -f ¤f ]; then return 15; fi
          if [ -x ¤f ]; then return 0; fi
          chmod 755 ¤f 2>/dev/null
          if [ -x ¤f ]; then return 0; fi
          for c in /proc/boot/chmod /bin/chmod /usr/bin/chmod /sbin/chmod /usr/sbin/chmod /usr/local/bin/chmod; do
            if [ -x ¤c ]; then
              ¤c 755 ¤f 2>/dev/null
              if [ -x ¤f ]; then return 0; fi
            fi
          done
          on -f /net/hu-omap/ /bin/sh -c "chmod 755 @HUAA@/nbt-aad.new" < /dev/null 2>/dev/null
          if [ -x ¤f ]; then return 0; fi
          on -f /net/hu-omap/ chmod 755 @HUAA@/nbt-aad.new < /dev/null 2>/dev/null
          if [ -x ¤f ]; then return 0; fi
          return 14
        }
    """.trimIndent()

    /**
     * Daemon: the running file is never overwritten. The new file waits as nbt-aad.new (and must be executable), the
     * old one is renamed to nbt-aad.old, then the new one takes its name; when that last step fails the old one is put
     * back. A current nbt-aad that cannot be run (a broken install) never pushes a good nbt-aad.old out: it is set
     * aside as nbt-aad.broken instead.
     */
    private val SWAP_DAEMON_FN = """
        swap_daemon() {
          cd @AA@ || return 11
          if [ ! -f nbt-aad.new ]; then return 15; fi
          if [ ! -x nbt-aad.new ]; then return 14; fi
          if [ -f nbt-aad ]; then
            if [ -x nbt-aad ] || [ ! -f nbt-aad.old ]; then
              rm -f nbt-aad.old
              mv nbt-aad nbt-aad.old || return 12
            else
              rm -f nbt-aad.broken
              mv nbt-aad nbt-aad.broken || return 12
            fi
          fi
          if mv nbt-aad.new nbt-aad; then return 0; fi
          if [ ! -f nbt-aad ]; then
            if [ -f nbt-aad.old ]; then mv nbt-aad.old nbt-aad; fi
          fi
          return 13
        }
    """.trimIndent()

    /**
     * Plugin, as the update guide does it: the old folder moves to /opt/aa/NbtAad.old (never left inside Plugins,
     * where iDrive could load it as a second app) and a new folder is filled from the staged files. When the copy
     * fails the old folder is put back.
     */
    private val SWAP_PLUGIN_FN = """
        swap_plugin() {
          cd @AA@ || return 21
          if [ ! -d NbtAad.new ]; then return 24; fi
          rm -rf NbtAad.old
          if [ -d @PL@ ]; then mv @PL@ NbtAad.old || return 22; fi
          if mkdir -p @PL@ && cp NbtAad.new/* @PL@/; then
            chmod 644 @PL@/* 2>/dev/null
            rm -rf NbtAad.new
            return 0
          fi
          rm -rf @PL@
          if [ -d NbtAad.old ]; then mv NbtAad.old @PL@; fi
          return 23
        }
    """.trimIndent()

    /** Swaps the current daemon with the previous one (so doing it twice returns to where it started). */
    private val REVERT_DAEMON_FN = """
        revert_daemon() {
          cd @AA@ || return 31
          if [ ! -f nbt-aad.old ]; then return 0; fi
          if [ -f nbt-aad ]; then
            mv nbt-aad nbt-aad.swap || return 32
            if mv nbt-aad.old nbt-aad; then
              mv nbt-aad.swap nbt-aad.old
            else
              mv nbt-aad.swap nbt-aad
              return 33
            fi
          else
            mv nbt-aad.old nbt-aad || return 34
          fi
          return 0
        }
    """.trimIndent()

    /** The same swap for the plugin folder and /opt/aa/NbtAad.old. */
    private val REVERT_PLUGIN_FN = """
        revert_plugin() {
          cd @AA@ || return 36
          if [ ! -d NbtAad.old ]; then return 0; fi
          if [ -d @PL@ ]; then
            mv @PL@ NbtAad.swap || return 37
            if mv NbtAad.old @PL@; then
              mv NbtAad.swap NbtAad.old
            else
              mv NbtAad.swap @PL@
              return 38
            fi
          else
            mv NbtAad.old @PL@ || return 39
          fi
          return 0
        }
    """.trimIndent()

    /** The copy of a program that can be run which the upload writes the new daemon into (see [prepare]). */
    private val STAGE_DAEMON_PART = """
        for t in /bin/cat /usr/bin/cat /proc/boot/cat /usr/local/bin/cat @AA@/nbt-aad; do
          if [ -x ¤t ]; then
            if cp ¤t nbt-aad.new 2>/dev/null; then break; fi
          fi
        done
    """.trimIndent()

    // ---- the scripts ----

    /** Prints `P <prefix>`, `AA <dir>` and `PL <dir>` lines for each prefix where the folder exists. */
    fun locate(): String = sh(LOCATE_PART + "\nexit 0")

    /**
     * Looks through the process lists (/proc here and /net/hu-omap/proc there) for a program called nbt-aad, using only
     * the shell: each process's exefile holds the path of its program.
     */
    fun procScan(): String = sh(PROC_PART + "\nexit 0")

    /** The first look at the head unit in one command: where the folders are ([locate]) and whether Android Auto runs. */
    fun probe(): String = sh(LOCATE_PART + "\n" + PROC_PART + "\nexit 0")

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

    /**
     * What is in the folders. Sizes are asked of `wc` when the head unit has it and left empty otherwise. With
     * [withEnv] the text of nbt-aad.env follows (see [parseStatus]).
     */
    fun status(aa: String, pl: String?, withEnv: Boolean = false): String =
        sh(statusPart(pl != null) + "\n" + BUILD_PART + (if (withEnv) "\n" + ENV_PART else "") + "\nexit 0", aa, pl ?: "")

    fun parseStatus(aa: String?, pl: String?, out: String): UnitStatus {
        val files = LinkedHashMap<String, FileState>()
        val plugin = LinkedHashMap<String, Long>()
        var oldPlugin = false
        var version: String? = null
        var envBody: StringBuilder? = null   // the lines of nbt-aad.env while they are being read
        var envText: String? = null          // set once the end marker has been seen: a cut-off file is not trusted
        var envMissing = false
        var inBuild = false                  // between the markers of the daemon's log lines
        val builds = ArrayList<String>()
        var runningVersion: String? = null   // the last build named in the log, once its end marker has been seen
        for (raw in out.lines()) {
            val line = raw.trim()
            if (inBuild) {
                if (line == "BUILD-END") {
                    runningVersion = builds.lastOrNull()
                    inBuild = false
                } else {
                    BUILD_LINE.matchEntire(line)?.let { builds += it.groupValues[1] }
                }
                continue
            }
            if (envBody != null) {
                // everything between the markers belongs to the file, whatever it looks like
                if (line == "ENV-END") {
                    envText = envBody.toString().trimEnd('\n').let { if (it.isEmpty()) it else it + "\n" }
                    envBody = null
                } else {
                    envBody.append(raw).append('\n')
                }
                continue
            }
            val t = line.split(Regex("\\s+"))
            when {
                line == "BUILD-BEGIN" -> { inBuild = true; builds.clear() }
                line == "ENV-BEGIN" -> envBody = StringBuilder()
                line == "ENV-NONE" -> envMissing = true
                line == "OLDPLUGIN" -> oldPlugin = true
                t.size >= 3 && t[0] == "F" -> files[t[1]] = FileState(t.getOrNull(3)?.toLongOrNull() ?: -1L, t[2] == "x")
                t.size >= 2 && t[0] == "L" -> plugin[t[1]] = t.getOrNull(2)?.toLongOrNull() ?: -1L
                line.startsWith("NBT-AAD-VERSION=") -> version = line.removePrefix("NBT-AAD-VERSION=")
            }
        }
        return UnitStatus(aa, pl, files, plugin, oldPlugin, version, envText, envMissing, runningVersion = runningVersion)
    }

    /** A line of the daemon's log that names its build: `Build: v1.9.112 (Nov 18 2026 12:34:56)`. */
    private val BUILD_LINE = Regex("""Build:\s+(\S+)\s+\(.*""")

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

    /**
     * Everything that has to happen before the files are uploaded, in one command: the partition is made writable,
     * what an interrupted install left is removed, and the staging places are made. For the daemon that is a copy of a
     * program that can be run, which the upload then writes into: a copy keeps the mode of its source and writing into
     * an existing file keeps it, so the new daemon is executable without `chmod`, which the head unit's SSH login does
     * not have. For the plugin it is a folder, filled with the current files first when only some of them are replaced.
     */
    fun prepare(aa: String, pl: String?, daemon: Boolean, plugin: Boolean, keepOld: Boolean): String = sh(
        REMOUNT_PART + "\n" +
            "cd @AA@ || exit 51\n" +
            "rm -rf nbt-aad.new NbtAad.new\n" +
            (if (daemon) STAGE_DAEMON_PART + "\n" else "") +
            (if (plugin) "mkdir NbtAad.new || exit 42\n" else "") +
            (if (plugin && keepOld && pl != null) "if [ -d @PL@ ]; then cp @PL@/* NbtAad.new/ || exit 43; fi\n" else "") +
            "exit 0",
        aa, pl ?: "",
    )

    /** Does the output of [prepare] or [apply] say the partition could not be made writable? */
    fun remountFailed(out: String): Boolean = out.lines().any { it.trim() == "REMOUNT failed" }

    /**
     * The check before anything is replaced, in one command: the new daemon can be run (`EXEC 0`; 14 = it cannot), and
     * the sizes of the uploaded files as `ls -l` prints them. `ls` only speeds things up: whatever it cannot confirm
     * is read back instead, so nothing is decided by it.
     */
    fun check(aa: String, daemon: Boolean, plugin: List<String>): String {
        val files = (if (daemon) listOf("nbt-aad.new") else emptyList()) +
            plugin.filter { it in Names.ALL }.map { "NbtAad.new/$it" }
        return sh(
            "cd @AA@ || exit 51\n" +
                (if (daemon) MAKE_EXEC_FN + "\nmake_exec\necho \"EXEC ¤?\"\n" else "") +
                (if (files.isNotEmpty()) "ls -l ${files.joinToString(" ")} 2>/dev/null\n" else "") +
                "exit 0",
            aa,
        )
    }

    /** What [check] found: the code of the "can it be run" test (null: not asked) and, per file name, the words of its `ls -l` line. */
    class Checked(val exec: Int?, val listed: Map<String, List<String>>) {
        /** True when the `ls -l` line of [name] contains [size] as a word, which is how the head unit says how big it is. */
        fun sizeIs(name: String, size: Long): Boolean = listed[name]?.contains(size.toString()) == true
    }

    fun parseCheck(out: String): Checked {
        var exec: Int? = null
        val listed = LinkedHashMap<String, List<String>>()
        for (raw in out.lines()) {
            val t = raw.trim().split(Regex("\\s+"))
            when {
                t.size == 2 && t[0] == "EXEC" -> exec = t[1].toIntOrNull()
                t.size >= 6 && t[0].length >= 10 && t[0][0] == '-' -> listed[t.last().substringAfterLast('/')] = t
            }
        }
        return Checked(exec, listed)
    }

    /** The steps [apply] can do. */
    enum class Op(val fn: String, val part: String) { // part: the marker printed once the step is done
        SWAP_DAEMON("swap_daemon", "daemon"),
        SWAP_PLUGIN("swap_plugin", "plugin"),
        REVERT_DAEMON("revert_daemon", "daemon"),
        REVERT_PLUGIN("revert_plugin", "plugin"),
    }

    private fun fnOf(op: Op): String = when (op) {
        Op.SWAP_DAEMON -> SWAP_DAEMON_FN
        Op.SWAP_PLUGIN -> SWAP_PLUGIN_FN
        Op.REVERT_DAEMON -> REVERT_DAEMON_FN
        Op.REVERT_PLUGIN -> REVERT_PLUGIN_FN
    }

    /**
     * Does the steps in order and stops at the first one that fails (its code is the exit code; `DONE daemon` / `DONE
     * plugin` are printed for each step that was finished), then prints what the folders hold now like [status].
     */
    fun apply(aa: String, pl: String?, ops: List<Op>, remount: Boolean, withStatus: Boolean = true): String {
        val b = StringBuilder()
        if (remount) b.append(REMOUNT_PART).append('\n')
        for (op in ops.distinct()) b.append(fnOf(op)).append('\n')
        for (op in ops) b.append("${op.fn} || exit ¤?\necho \"DONE ${op.part}\"\n")
        if (withStatus) b.append(statusPart(pl != null)).append('\n')
        b.append("exit 0")
        return sh(b.toString(), aa, pl ?: "")
    }

    /** (daemon done, plugin done) from the output of [apply], also when it stopped halfway. */
    fun parseDone(out: String): Pair<Boolean, Boolean> {
        val lines = out.lines().map { it.trim() }
        return ("DONE daemon" in lines) to ("DONE plugin" in lines)
    }

    /** One step alone, as a script of its own (the tests try these in a scratch folder). */
    fun swapDaemon(aa: String): String = apply(aa, null, listOf(Op.SWAP_DAEMON), remount = false, withStatus = false)
    fun swapPlugin(aa: String, pl: String): String = apply(aa, pl, listOf(Op.SWAP_PLUGIN), remount = false, withStatus = false)
    fun revertDaemon(aa: String): String = apply(aa, null, listOf(Op.REVERT_DAEMON), remount = false, withStatus = false)
    fun revertPlugin(aa: String, pl: String): String = apply(aa, pl, listOf(Op.REVERT_PLUGIN), remount = false, withStatus = false)

    /** Removes the files an interrupted install left waiting to be moved into place (never a .swap: that may be the live one). */
    fun cleanStaging(aa: String): String = sh("cd @AA@ 2>/dev/null && rm -rf nbt-aad.new NbtAad.new\nexit 0", aa)

    /**
     * The daemon's log files (nbt-aad.log, nbt-aad-app.log, and the ones on a USB stick) with their sizes, in the places the
     * project's guides name: /dev/shmem (RAM, gone at the next reboot) and the stick, each as this login or the hu-omap
     * processor sees it. `ls -l` is what the head unit has for sizes. Read the result with LogFinder.parse.
     */
    fun findLogs(): String = sh(
        """
        for d in /dev/shmem /net/hu-omap/dev/shmem /mnt/umass* /net/hu-omap/mnt/umass*; do
          if [ -d ¤d ]; then
            ls -l ¤d/nbt-aad*.log 2>/dev/null
          fi
        done
        exit 0
        """,
    )

    /**
     * Deletes the given files (the daemon's logs; the paths were checked to be plain) and says for each what happened:
     * `OK path`, `GONE path` (it was not there any more) or `FAIL path`. Other lines of the answer are ignored.
     */
    fun removeFiles(paths: List<String>): String = sh(
        """
        for f in ${paths.joinToString(" ")}; do
          if [ -e ¤f ]; then
            if rm -f ¤f 2>&1; then echo "OK ¤f"; else echo "FAIL ¤f"; fi
          else
            echo "GONE ¤f"
          fi
        done
        exit 0
        """,
    )

    /**
     * The sessions in the daemon's log on the stick (one local pass of grep over the file, so it takes as long as the
     * stick needs to be read, but only a few lines travel): `H line:byte:header` for every start, `B ...` for its build
     * line and `G line date time` for the first GPS fix of that run. The GPS line is cut down to its time here, on the head
     * unit, so that no position leaves it. The head unit's grep may lack -a or -b: what it knows is tried on a one-line
     * input first, and without -b the answer has no byte offsets.
     */
    fun sessions(path: String): String = sh(
        """
        f="${path}"
        if [ ! -f "¤f" ]; then echo "NOFILE"; exit 0; fi
        if echo x | grep -a -b -n x >/dev/null 2>&1; then o="-a -b -n"
        elif echo x | grep -b -n x >/dev/null 2>&1; then o="-b -n"
        elif echo x | grep -a -n x >/dev/null 2>&1; then o="-a -n"
        else o="-n"
        fi
        echo "OPTIONS ¤o"
        gps=0
        grep ¤o -e '^---- nbt-aad log appended to ' -e '^  Build: ' -e 'car GPS fix' "¤f" 2>/dev/null | while read -r l; do
          case "¤l" in
            *'---- nbt-aad log appended to '*)
              echo "H ¤l"
              gps=0;;
            *'  Build: '*)
              echo "B ¤l";;
            *'car GPS fix'*)
              if [ "¤gps" = 0 ]; then
                t="¤{l##*UTC }"
                set -- ¤t
                d="¤1"
                h="¤{2%?}"
                case "¤d" in
                  [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9])
                    echo "G ¤{l%%:*} ¤d ¤h"
                    gps=1;;
                esac
              fi;;
          esac
        done
        exit 0
        """,
    )

    /** The block size of [sessionDd]: it starts at a whole block, the app drops the bytes before the header. */
    const val DD_BLOCK = 4096

    /**
     * One session from its byte [offset] on, with dd (it jumps there at once). [length] > 0 makes dd stop after the
     * blocks that hold the session; otherwise it goes on to the end of the file. Nothing but the data goes to stdout; a
     * head unit without dd answers with exit 4.
     */
    fun sessionDd(path: String, offset: Long, length: Long): String {
        val skip = offset / DD_BLOCK
        val lead = offset % DD_BLOCK
        val count = if (length > 0) " count=${(lead + length + DD_BLOCK - 1) / DD_BLOCK}" else ""
        return sh(
            """
            if type dd >/dev/null 2>&1; then
              dd if="${path}" bs=$DD_BLOCK skip=$skip$count 2>/dev/null
            else
              echo "no dd" >&2
              exit 4
            fi
            """,
        )
    }

    /**
     * One session from its header's line on, with what every shell has: `read` the lines before it away and `cat` the
     * rest (the shell leaves a file it reads from just after the last line it took, so cat goes on from there).
     * Slower than [sessionDd], which is why that is tried first.
     */
    fun sessionRead(path: String, line: Long): String = sh(
        """
        {
          i=0
          while [ ¤i -lt ${line - 1} ]; do
            read -r x || break
            i=¤((i+1))
          done
          cat
        } < "${path}"
        """,
    )

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
