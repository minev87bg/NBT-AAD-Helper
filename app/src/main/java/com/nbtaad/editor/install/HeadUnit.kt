package com.nbtaad.editor.install

import com.nbtaad.editor.log.AppLog
import com.nbtaad.editor.net.SshSession
import com.nbtaad.editor.update.UpdateLogic
import java.io.IOException

enum class Level { INFO, OK, WARN, ERROR }

/**
 * How the installer talks to the person while it works: lines of text, the progress of the uploads, and a note each time
 * a part ("daemon", "plugin") has really been replaced (so the app knows what the last install changed).
 */
class Reporter(
    val say: (String, Level) -> Unit,
    val progress: (label: String, done: Long, total: Long) -> Unit,
    val swapped: (String) -> Unit = {},
)

/** What an install did. */
class Outcome(
    val daemon: Boolean,
    val plugin: Boolean,
    val skipped: List<String>,
    val daemonVersion: String?,
    val problems: List<String>,
)

/**
 * Puts the files of a build on the head unit over the SSH login the app already has, the way Sq00ky's update
 * guide (updating.md) does it by hand: stop Android Auto, never write over a file that is in use (the new file is
 * uploaded under another name and checked, the old one is renamed to .old, then the new one takes its name), make the
 * plugin folder from the new files, and keep the old ones as the way back. SSH access itself is not part of this.
 *
 * The SSH login has only a few commands (see [RemoteScripts]), so the work is done with those and with what the
 * shell does itself; the files are read and written through the network paths of the other processor.
 */
object HeadUnit {
    class Layout(val aa: String, val plugin: String?)

    private fun timeoutFor(bytes: Int): Long = 60_000L + bytes / 8

    /** "1.2 s" since the work began: each step of the log carries it, so a slow step can be seen. */
    private class Clock {
        private val start = System.nanoTime()
        private var lap = start
        fun total(): String = fmt(System.nanoTime() - start)

        /** The time since the last call, for one step. */
        fun step(): String {
            val now = System.nanoTime()
            val d = now - lap
            lap = now
            return fmt(d)
        }

        private fun fmt(ns: Long) = String.format(java.util.Locale.US, "%.1f s", ns / 1e9)
    }

    private fun diagnoseFolders(ssh: SshSession): String =
        ssh.exec(
            "echo '/opt:'; ls /opt 2>&1 | head -12; echo '/net:'; ls /net 2>&1 | head -12; " +
                "echo 'hu-omap opt:'; ls /net/hu-omap/fs/sda0/opt 2>&1 | head -12"
        ).text.trim().take(600)

    private fun notFound(ssh: SshSession): IOException = IOException(
        "The NBT-AAD folder (/opt/aa) was not found on the head unit. Is NBT-AAD installed?\n" +
            "What the SSH shell can see:\n" + diagnoseFolders(ssh)
    )

    private fun locate(ssh: SshSession): Layout {
        val (aa, pl) = RemoteScripts.parseLocate(ssh.exec(RemoteScripts.locate()).text)
        if (aa == null) throw notFound(ssh)
        return Layout(aa, pl)
    }

    /** The first look at the head unit, in one command: where the folders are, and whether Android Auto runs. */
    private class Probe(val layout: Layout, val scan: ProcScan)

    private fun probe(ssh: SshSession): Probe {
        val text = ssh.exec(RemoteScripts.probe(), timeoutMs = 60_000).text
        val (aa, pl) = RemoteScripts.parseLocate(text)
        if (aa == null) throw notFound(ssh)
        return Probe(Layout(aa, pl), RemoteScripts.parseProc(text))
    }

    private fun readStatus(ssh: SshSession, lay: Layout, withEnv: Boolean = false): UnitStatus =
        RemoteScripts.parseStatus(
            lay.aa, lay.plugin,
            ssh.exec(RemoteScripts.status(lay.aa, lay.plugin, withEnv), timeoutMs = 60_000).text,
        )

    /**
     * The installed nbt-aad read back with `cat` and looked at here (size, SHA-256, the version named inside it), or
     * null when that did not work. The head unit's own tools cannot be counted on for this: its login has no `wc`, and
     * its `grep` may not know the options the version search needs. Whatever goes wrong here must not spoil the rest of
     * the status, so it is logged and left at that.
     */
    private fun readDaemon(ssh: SshSession, lay: Layout): DaemonFile? = try {
        val r = ssh.exec("cat ${lay.aa}/${Names.DAEMON}", timeoutMs = 120_000)
        if (r.exit != 0 || r.out.isEmpty()) {
            AppLog.w("Unit", "Reading nbt-aad back failed: exit ${r.exit}, ${r.out.size} bytes${r.err.trim().takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""}")
            null
        } else {
            DaemonFile(r.out.size.toLong(), UpdateLogic.sha256Hex(r.out), BuildPackage.findVersion(r.out))
        }
    } catch (e: Exception) {
        AppLog.w("Unit", "Reading nbt-aad back failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /**
     * What is on the head unit now (read only): the folders and files, the text of nbt-aad.env, and the installed daemon
     * read back and hashed. The last step takes a few seconds, so [early] is given what is known before it, to be shown
     * while it goes on.
     */
    fun status(host: String, early: (UnitStatus) -> Unit = {}): UnitStatus =
        SshSession(host).use { ssh ->
            val lay = locate(ssh)
            val first = readStatus(ssh, lay, withEnv = true)
            early(first)
            if (first.hasDaemon) first.withDaemonFile(readDaemon(ssh, lay)) else first
        }

    /** The commands and folders the SSH login has, as text for the person to read or send on. */
    fun diagnose(host: String): String =
        SshSession(host).use { ssh ->
            val lay = try { locate(ssh) } catch (e: Exception) { null }
            val r = ssh.exec(RemoteScripts.diagnose(), timeoutMs = 40_000)
            buildString {
                appendLine("NBT-AAD folder: ${lay?.aa ?: "not found"}")
                appendLine("Plugin folder: ${lay?.plugin ?: "not found"}")
                append(r.text.trim())
                if (r.err.isNotBlank()) append("\n[errors]\n" + r.err.trim())
            }
        }

    // ---- stopping Android Auto, as the guide's step 2 ----

    private fun scan(ssh: SshSession): ProcScan =
        RemoteScripts.parseProc(try { ssh.exec(RemoteScripts.procScan(), timeoutMs = 30_000).text } catch (e: Exception) { "" })

    private fun stopDaemon(ssh: SshSession, rep: Reporter, first: ProcScan) {
        var p = first
        if (!p.scanned) {
            rep.say(
                "The app cannot see the head unit's process list, so it cannot tell whether Android Auto runs. " +
                    "If it does, press Force Stop (Media/Radio, Android Auto) and run this again.",
                Level.WARN,
            )
            return
        }
        if (!p.any) {
            rep.say("Android Auto is not running.", Level.OK)
            return
        }
        rep.say("Android Auto is still running. Stopping it…", Level.INFO)
        for (pid in p.here) try { ssh.exec(RemoteScripts.kill(pid), timeoutMs = 10_000) } catch (e: Exception) { /* checked below */ }
        for (pid in p.onHuOmap) try { ssh.exec(RemoteScripts.killOnHuOmap(pid), timeoutMs = 15_000) } catch (e: Exception) { /* checked below */ }
        Thread.sleep(2000)
        p = scan(ssh)
        if (p.any) {
            throw IOException(
                "Android Auto is still running. On the head unit open Media/Radio, Android Auto and press " +
                    "Force Stop, then try again."
            )
        }
        rep.say("Android Auto is stopped.", Level.OK)
    }


    // ---- uploading ----

    /** Sends the bytes to [path]. What arrived is checked once for all files afterwards (see [checkUpload]). */
    private fun upload(ssh: SshSession, path: String, p: Payload, done: Long, total: Long, rep: Reporter, clock: Clock) {
        rep.progress(p.name, done, total)
        var last = 0L
        val r = ssh.exec("cat > $path", stdin = p.bytes, timeoutMs = timeoutFor(p.size)) { sent ->
            if (sent - last >= 64 * 1024 || sent == p.size.toLong()) {
                last = sent
                rep.progress(p.name, done + sent, total)
            }
        }
        if (r.exit != 0) {
            throw IOException(
                "Uploading ${p.name} failed: ${r.err.trim().ifEmpty { "exit ${r.exit}" }}. " +
                    "Is there enough free space on the head unit?"
            )
        }
        rep.progress(p.name, done + p.size, total)
        rep.say("Uploaded ${p.name} (${ReleaseLogic.mb(p.size.toLong())}, ${clock.step()}).", Level.INFO)
    }

    /** The slow, sure way to prove a file arrived: read it back and compare. Only used for what `ls` could not confirm. */
    private fun readBack(ssh: SshSession, path: String, p: Payload) {
        val back = ssh.exec("cat $path", timeoutMs = timeoutFor(p.size))
        if (!back.out.contentEquals(p.bytes)) {
            throw IOException("${p.name} on the head unit differs from what was sent. Nothing was replaced.")
        }
    }

    /**
     * One command asks the head unit whether the new daemon can be run and how big each uploaded file is. The upload
     * itself is protected by SSH, so a size that matches proves nothing was cut off; a file whose size the head unit
     * cannot confirm is read back and compared.
     */
    private fun checkUpload(
        ssh: SshSession, lay: Layout, daemon: Payload?, plugin: List<Payload>, rep: Reporter, clock: Clock,
    ) {
        val r = ssh.exec(RemoteScripts.check(lay.aa, daemon != null, plugin.map { it.name }), timeoutMs = 60_000)
        if (r.exit != 0) throw IOException("Checking the uploaded files failed: ${RemoteScripts.exitMeaning(r.exit)}.")
        val c = RemoteScripts.parseCheck(r.text)
        if (daemon != null) {
            when (c.exec) {
                0 -> {}
                14 -> throw IOException(
                    "The new nbt-aad is uploaded but cannot be made executable on the head unit: its SSH login has " +
                        "no chmod the app can use. Nothing was replaced."
                )
                15 -> throw IOException("The uploaded nbt-aad is not on the head unit. Nothing was replaced.")
                else -> throw IOException("The head unit did not say whether the new nbt-aad can be run. Nothing was replaced.")
            }
        }
        var readBacks = 0
        val items = listOfNotNull(daemon?.let { Triple("nbt-aad.new", "${lay.aa}/nbt-aad.new", it) }) +
            plugin.map { Triple(it.name, "${lay.aa}/NbtAad.new/${it.name}", it) }
        for ((listedName, path, p) in items) {
            if (c.sizeIs(listedName, p.size.toLong())) continue
            readBacks++
            readBack(ssh, path, p)
        }
        rep.say(
            (if (daemon != null) "nbt-aad can be run. " else "") +
                (if (readBacks == 0) "All files arrived complete" else "All files arrived complete ($readBacks read back)") +
                " (${clock.step()}).",
            Level.OK,
        )
    }

    private fun stepFailure(what: String, code: Int, err: String): IOException {
        val why = RemoteScripts.exitMeaning(code)
        val extra = err.trim().takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
        return IOException("$what failed: $why$extra.")
    }

    // ---- install ----

    fun install(host: String, pkg: BuildPackage, rep: Reporter): Outcome {
        if (!pkg.installable) throw IOException("These files cannot be installed: ${pkg.problems.firstOrNull() ?: "nothing to install"}")
        val daemon = pkg.payloads[Names.DAEMON]
        val plugin = pkg.pluginFiles.map { pkg.payloads.getValue(it) }
        val skipped = pkg.found.filter { it == Names.FIREWALL }
        val total = (daemon?.size ?: 0).toLong() + plugin.sumOf { it.size.toLong() }
        val clock = Clock()

        rep.say("Logging in to $host…", Level.INFO)
        return SshSession(host).use { ssh ->
            rep.say("Logged in (${clock.step()}).", Level.INFO)
            val pr = probe(ssh)
            val lay = pr.layout
            rep.say("NBT-AAD folder: ${lay.aa}", Level.INFO)
            if (plugin.isNotEmpty()) {
                val pl = lay.plugin ?: throw IOException(
                    "The plugin folder (…/Plugins/NbtAad) was not found on the head unit, so the plugin files cannot " +
                        "be replaced. Nothing was changed."
                )
                rep.say("Plugin folder: $pl", Level.INFO)
            }
            stopDaemon(ssh, rep, pr.scan)
            clock.step()

            // Everything is uploaded and checked first; nothing on the head unit changes until all of it is there.
            try {
                val prep = ssh.exec(
                    RemoteScripts.prepare(lay.aa, lay.plugin, daemon != null, plugin.isNotEmpty(), keepOld = !pkg.fullPlugin),
                    timeoutMs = 90_000,
                )
                if (prep.exit != 0) throw stepFailure("Preparing the head unit", prep.exit, prep.err)
                if (RemoteScripts.remountFailed(prep.text)) {
                    rep.say("The partition could not be made writable (continuing).", Level.WARN)
                }
                rep.say("Head unit prepared (${clock.step()}).", Level.INFO)

                var done = 0L
                if (daemon != null) {
                    upload(ssh, "${lay.aa}/nbt-aad.new", daemon, done, total, rep, clock)
                    done += daemon.size
                }
                for (p in plugin) {
                    upload(ssh, "${lay.aa}/NbtAad.new/${p.name}", p, done, total, rep, clock)
                    done += p.size
                }
                checkUpload(ssh, lay, daemon, plugin, rep, clock)
            } catch (e: Exception) {
                runCatching { ssh.exec(RemoteScripts.cleanStaging(lay.aa)) }
                throw e
            }

            // the replacing and the look at the result are one command
            val ops = listOfNotNull(
                if (daemon != null) RemoteScripts.Op.SWAP_DAEMON else null,
                if (plugin.isNotEmpty()) RemoteScripts.Op.SWAP_PLUGIN else null,
            )
            val r = ssh.exec(RemoteScripts.apply(lay.aa, lay.plugin, ops, remount = false), timeoutMs = 120_000)
            val (daemonDone, pluginDone) = RemoteScripts.parseDone(r.text)
            if (daemonDone) rep.swapped("daemon")
            if (pluginDone) rep.swapped("plugin")
            if (r.exit != 0) {
                runCatching { ssh.exec(RemoteScripts.cleanStaging(lay.aa)) }
                val e = stepFailure(if (daemonDone || daemon == null) "Replacing the plugin" else "Replacing nbt-aad", r.exit, r.err)
                if (daemonDone) {
                    throw IOException(
                        "${e.message} The new nbt-aad is already in place, the old plugin is still there. " +
                            "Use \"Go back to the previous version\" (daemon only) to undo it, or install again."
                    )
                }
                throw e
            }
            rep.say(
                "Replaced " + listOfNotNull(if (daemonDone) "nbt-aad" else null, if (pluginDone) "the plugin" else null)
                    .joinToString(" and ") + ": the old files are kept as .old (${clock.step()}).",
                Level.OK,
            )

            // what the head unit holds now, as the same command printed it
            val problems = ArrayList<String>()
            val st = RemoteScripts.parseStatus(lay.aa, lay.plugin, r.text)
            if (daemon != null) {
                val f = st.files[Names.DAEMON]
                when {
                    f == null -> problems += "nbt-aad is not in the folder after the install."
                    !f.executable -> problems += "nbt-aad is not executable after the install."
                    f.size >= 0 && f.size != daemon.size.toLong() ->
                        problems += "nbt-aad on the head unit has ${f.size} bytes, expected ${daemon.size}."
                }
            }
            for (p in plugin) {
                val sz = st.pluginFiles[p.name]
                when {
                    sz == null -> problems += "${p.name} is not in the plugin folder after the install."
                    sz >= 0 && sz != p.size.toLong() ->
                        problems += "${p.name} in the plugin folder has $sz bytes, expected ${p.size}."
                }
            }
            for (m in problems) rep.say(m, Level.WARN)
            if (skipped.isNotEmpty()) {
                rep.say(
                    "pf_aa was not installed: the firewall rules are built into nbt-aad now and are set up once from the " +
                        "iDrive menu.",
                    Level.INFO,
                )
            }
            rep.say("Done in ${clock.total()}.", Level.INFO)
            Outcome(daemonDone, pluginDone, skipped, daemon?.let { BuildPackage.findVersion(it.bytes) } ?: st.version, problems)
        }
    }

    // ---- the way back ----

    /**
     * Swaps the current version with the one the last install moved aside (doing it again swaps back). The daemon and
     * the plugin are chosen separately: a failed install may have changed only one of them.
     */
    fun revert(host: String, rep: Reporter, daemon: Boolean, plugin: Boolean): Outcome {
        val clock = Clock()
        rep.say("Logging in to $host…", Level.INFO)
        return SshSession(host).use { ssh ->
            rep.say("Logged in (${clock.step()}).", Level.INFO)
            val pr = probe(ssh)
            val lay = pr.layout
            val before = readStatus(ssh, lay)
            val doDaemon = daemon && before.hasPreviousDaemon
            val doPlugin = plugin && before.hasOldPlugin && lay.plugin != null
            if (!doDaemon && !doPlugin) {
                throw IOException(
                    "There is nothing to swap back for what you chose (no nbt-aad.old or NbtAad.old in ${lay.aa})."
                )
            }
            stopDaemon(ssh, rep, pr.scan)
            clock.step()
            val ops = listOfNotNull(
                if (doDaemon) RemoteScripts.Op.REVERT_DAEMON else null,
                if (doPlugin) RemoteScripts.Op.REVERT_PLUGIN else null,
            )
            val r = ssh.exec(RemoteScripts.apply(lay.aa, lay.plugin, ops, remount = true), timeoutMs = 120_000)
            val (d, p) = RemoteScripts.parseDone(r.text)
            if (d) rep.swapped("daemon")
            if (p) rep.swapped("plugin")
            if (r.exit != 0) throw stepFailure(if (d || !doDaemon) "Going back to the previous plugin" else "Going back to the previous nbt-aad", r.exit, r.err)
            if (d) rep.say("nbt-aad is swapped with nbt-aad.old.", Level.OK)
            if (p) rep.say("The plugin folder is swapped with NbtAad.old.", Level.OK)
            val after = RemoteScripts.parseStatus(lay.aa, lay.plugin, r.text)
            if (d && after.daemonBroken) {
                rep.say("The nbt-aad that is in place now is not executable. Swap it again or install a release.", Level.WARN)
            }
            rep.say("Done in ${clock.total()}.", Level.INFO)
            Outcome(d, p, emptyList(), after.version, emptyList())
        }
    }
}
