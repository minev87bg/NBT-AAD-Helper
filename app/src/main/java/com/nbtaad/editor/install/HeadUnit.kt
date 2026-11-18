package com.nbtaad.editor.install

import com.nbtaad.editor.net.SshSession
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

    private fun diagnoseFolders(ssh: SshSession): String =
        ssh.exec(
            "echo '/opt:'; ls /opt 2>&1 | head -12; echo '/net:'; ls /net 2>&1 | head -12; " +
                "echo 'hu-omap opt:'; ls /net/hu-omap/fs/sda0/opt 2>&1 | head -12"
        ).text.trim().take(600)

    private fun locate(ssh: SshSession): Layout {
        val (aa, pl) = RemoteScripts.parseLocate(ssh.exec(RemoteScripts.locate()).text)
        if (aa == null) {
            throw IOException(
                "The NBT-AAD folder (/opt/aa) was not found on the head unit. Is NBT-AAD installed?\n" +
                    "What the SSH shell can see:\n" + diagnoseFolders(ssh)
            )
        }
        return Layout(aa, pl)
    }

    private fun readStatus(ssh: SshSession, lay: Layout): UnitStatus =
        RemoteScripts.parseStatus(lay.aa, lay.plugin, ssh.exec(RemoteScripts.status(lay.aa, lay.plugin), timeoutMs = 60_000).text)

    /** What is on the head unit now (read only). */
    fun status(host: String): UnitStatus =
        SshSession(host).use { ssh -> readStatus(ssh, locate(ssh)) }

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

    private fun stopDaemon(ssh: SshSession, rep: Reporter) {
        var p = scan(ssh)
        if (!p.scanned) {
            rep.say(
                "The app cannot see the head unit's process list, so it cannot tell whether Android Auto runs. " +
                    "It relies on your Force Stop.",
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

    private fun remountRw(ssh: SshSession, rep: Reporter) {
        rep.say("Making the partition writable…", Level.INFO)
        var r = ssh.exec("mount -o remount rw /net/hu-omap/fs/sda0")
        if (r.exit != 0) {
            r = ssh.exec("mount -o remount rw /fs/sda0")
            if (r.exit != 0) {
                rep.say("The remount reported: ${r.err.trim().ifEmpty { "exit ${r.exit}" }} (continuing).", Level.WARN)
            }
        }
    }

    // ---- uploading ----

    /** Sends the bytes to [path] and proves they arrived: by the head unit's own checksum, else by reading back. */
    private fun upload(ssh: SshSession, path: String, p: Payload, done: Long, total: Long, rep: Reporter) {
        rep.say("Uploading ${p.name} (${ReleaseLogic.mb(p.size.toLong())})…", Level.INFO)
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
        rep.say("${p.name} arrived: ${verify(ssh, path, p)}.", Level.OK)
    }

    private fun verify(ssh: SshSession, path: String, p: Payload): String {
        val want = p.bytes.size.toLong()
        val sum = ssh.exec(RemoteScripts.cksum(path), timeoutMs = 60_000)
        val parsed = if (sum.exit == 0) PosixCksum.parse(sum.text) else null
        if (parsed != null) {
            if (parsed.second != want) {
                throw IOException("${p.name} arrived incomplete: ${parsed.second} of $want bytes.")
            }
            if (parsed.first == PosixCksum.crc(p.bytes)) return "checksum matches"
            // the size is right but the sum differs: either damaged, or this cksum counts differently; read it back
        } else {
            val size = ssh.exec(RemoteScripts.wcBytes(path)).text.trim().toLongOrNull()
            if (size != null && size != want) throw IOException("${p.name} arrived incomplete: $size of $want bytes.")
        }
        val back = ssh.exec("cat $path", timeoutMs = timeoutFor(p.size))
        if (!back.out.contentEquals(p.bytes)) {
            throw IOException("${p.name} on the head unit differs from what was sent. Nothing was replaced.")
        }
        return "read back and identical"
    }

    private fun script(ssh: SshSession, what: String, body: String, timeoutMs: Long = 120_000) {
        val r = ssh.exec(body, timeoutMs = timeoutMs)
        if (r.exit != 0) {
            val why = RemoteScripts.exitMeaning(r.exit)
            val err = r.err.trim().takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
            throw IOException("$what failed: $why$err.")
        }
    }

    // ---- install ----

    fun install(host: String, pkg: BuildPackage, rep: Reporter): Outcome {
        if (!pkg.installable) throw IOException("These files cannot be installed: ${pkg.problems.firstOrNull() ?: "nothing to install"}")
        val daemon = pkg.payloads[Names.DAEMON]
        val plugin = pkg.pluginFiles.map { pkg.payloads.getValue(it) }
        val skipped = pkg.found.filter { it == Names.FIREWALL }
        val total = (daemon?.size ?: 0).toLong() + plugin.sumOf { it.size.toLong() }

        rep.say("Logging in to $host…", Level.INFO)
        return SshSession(host).use { ssh ->
            val lay = locate(ssh)
            rep.say("NBT-AAD folder: ${lay.aa}", Level.INFO)
            if (plugin.isNotEmpty()) {
                val pl = lay.plugin ?: throw IOException(
                    "The plugin folder (…/Plugins/NbtAad) was not found on the head unit, so the plugin files cannot " +
                        "be replaced. Nothing was changed."
                )
                rep.say("Plugin folder: $pl", Level.INFO)
            }
            stopDaemon(ssh, rep)
            remountRw(ssh, rep)
            ssh.exec(RemoteScripts.cleanStaging(lay.aa))

            // Everything is uploaded and checked first; nothing on the head unit changes until all of it is there.
            var done = 0L
            try {
                if (daemon != null) {
                    ssh.exec(RemoteScripts.stageDaemon(lay.aa))
                    upload(ssh, "${lay.aa}/nbt-aad.new", daemon, done, total, rep)
                    done += daemon.size
                    val x = ssh.exec(RemoteScripts.makeExec(lay.aa), timeoutMs = 60_000)
                    if (x.exit != 0) {
                        throw IOException(
                            "The new nbt-aad is uploaded but cannot be made executable on the head unit: its SSH " +
                                "login has no chmod the app can use. Nothing was replaced."
                        )
                    }
                    rep.say("nbt-aad is executable.", Level.OK)
                }
                if (plugin.isNotEmpty()) {
                    script(ssh, "Preparing the plugin files", RemoteScripts.stagePlugin(lay.aa, lay.plugin, keepOld = !pkg.fullPlugin))
                    for (p in plugin) {
                        upload(ssh, "${lay.aa}/NbtAad.new/${p.name}", p, done, total, rep)
                        done += p.size
                    }
                }
            } catch (e: Exception) {
                runCatching { ssh.exec(RemoteScripts.cleanStaging(lay.aa)) }
                throw e
            }

            var daemonDone = false
            var pluginDone = false
            val problems = ArrayList<String>()
            try {
                if (daemon != null) {
                    rep.say("Replacing nbt-aad (the old one is kept as nbt-aad.old)…", Level.INFO)
                    script(ssh, "Replacing nbt-aad", RemoteScripts.swapDaemon(lay.aa))
                    daemonDone = true
                    rep.swapped("daemon")
                    rep.say("nbt-aad is replaced.", Level.OK)
                }
                if (plugin.isNotEmpty()) {
                    rep.say("Replacing the plugin folder (the old one is kept in ${lay.aa}/NbtAad.old)…", Level.INFO)
                    script(ssh, "Replacing the plugin", RemoteScripts.swapPlugin(lay.aa, lay.plugin!!))
                    pluginDone = true
                    rep.swapped("plugin")
                    rep.say("The plugin is replaced.", Level.OK)
                }
            } catch (e: Exception) {
                runCatching { ssh.exec(RemoteScripts.cleanStaging(lay.aa)) }
                if (daemonDone) {
                    throw IOException(
                        "${e.message} The new nbt-aad is already in place, the old plugin is still there. " +
                            "Use \"Go back to the previous version\" (daemon only) to undo it, or install again."
                    )
                }
                throw e
            }

            // what the head unit holds now
            val st = try { readStatus(ssh, lay) } catch (e: Exception) { null }
            if (st != null) {
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
            } else {
                problems += "The final check could not read the head unit."
            }
            for (m in problems) rep.say(m, Level.WARN)
            if (skipped.isNotEmpty()) {
                rep.say(
                    "pf_aa was not installed: the firewall rules are built into nbt-aad now and are set up once from the " +
                        "iDrive menu.",
                    Level.INFO,
                )
            }
            Outcome(daemonDone, pluginDone, skipped, daemon?.let { BuildPackage.findVersion(it.bytes) } ?: st?.version, problems)
        }
    }

    // ---- the way back ----

    /**
     * Swaps the current version with the one the last install moved aside (doing it again swaps back). The daemon and
     * the plugin are chosen separately: a failed install may have changed only one of them.
     */
    fun revert(host: String, rep: Reporter, daemon: Boolean, plugin: Boolean): Outcome {
        rep.say("Logging in to $host…", Level.INFO)
        return SshSession(host).use { ssh ->
            val lay = locate(ssh)
            val before = readStatus(ssh, lay)
            val doDaemon = daemon && before.hasPreviousDaemon
            val doPlugin = plugin && before.hasOldPlugin && lay.plugin != null
            if (!doDaemon && !doPlugin) {
                throw IOException(
                    "There is nothing to swap back for what you chose (no nbt-aad.old or NbtAad.old in ${lay.aa})."
                )
            }
            stopDaemon(ssh, rep)
            remountRw(ssh, rep)
            var d = false
            var p = false
            if (doDaemon) {
                script(ssh, "Going back to the previous nbt-aad", RemoteScripts.revertDaemon(lay.aa))
                d = true
                rep.swapped("daemon")
                rep.say("nbt-aad is swapped with nbt-aad.old.", Level.OK)
            }
            if (doPlugin) {
                script(ssh, "Going back to the previous plugin", RemoteScripts.revertPlugin(lay.aa, lay.plugin!!))
                p = true
                rep.swapped("plugin")
                rep.say("The plugin folder is swapped with NbtAad.old.", Level.OK)
            }
            val after = try { readStatus(ssh, lay) } catch (e: Exception) { null }
            if (after != null && d && after.daemonBroken) {
                rep.say("The nbt-aad that is in place now is not executable. Swap it again or install a release.", Level.WARN)
            }
            Outcome(d, p, emptyList(), after?.version, emptyList())
        }
    }
}
