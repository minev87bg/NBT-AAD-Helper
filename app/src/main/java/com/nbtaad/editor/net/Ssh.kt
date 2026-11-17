package com.nbtaad.editor.net

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Properties

/**
 * Credentials / method as documented in the NBT-AAD project (Sq00ky/Supra-iDrive6-Android-Auto, install.md):
 *   ssh root@<head unit> -o HostKeyAlgorithms=+ssh-dss -m hmac-sha1     (older guides: +ssh-rsa)
 *   password: ts&SK412
 */
object SshConfig {
    const val USER = "root"
    const val PASSWORD = "ts&SK412"
    const val PORT = 22
}

class SshSession(host: String) : AutoCloseable {
    private val session: Session

    init {
        val jsch = JSch()
        session = jsch.getSession(SshConfig.USER, host, SshConfig.PORT)
        session.setPassword(SshConfig.PASSWORD)
        val cfg = Properties()
        cfg["StrictHostKeyChecking"] = "no"
        cfg["PreferredAuthentications"] = "password,keyboard-interactive"
        // The head unit runs a very old OpenSSH (QNX): allow the legacy algorithms JSch disables by default.
        cfg["server_host_key"] = "ssh-dss,ssh-rsa,rsa-sha2-256,rsa-sha2-512,ecdsa-sha2-nistp256,ssh-ed25519"
        cfg["kex"] = "diffie-hellman-group-exchange-sha256,diffie-hellman-group-exchange-sha1," +
            "diffie-hellman-group14-sha256,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1," +
            "ecdh-sha2-nistp256"
        val ciphers = "aes128-ctr,aes192-ctr,aes256-ctr,aes128-cbc,aes192-cbc,aes256-cbc,3des-cbc,blowfish-cbc"
        cfg["cipher.s2c"] = ciphers
        cfg["cipher.c2s"] = ciphers
        val macs = "hmac-sha1,hmac-sha2-256,hmac-sha2-512,hmac-md5"
        cfg["mac.s2c"] = macs
        cfg["mac.c2s"] = macs
        session.setConfig(cfg)
        session.timeout = 15000
        session.connect(8000)
    }

    class Result(val out: ByteArray, val err: String, val exit: Int) {
        val text: String get() = String(out, Charsets.UTF_8)
    }

    /** Runs one command (the head unit has no sftp/scp, so everything goes through exec channels). */
    fun exec(cmd: String, stdin: ByteArray? = null, timeoutMs: Long = 20000): Result {
        val ch = session.openChannel("exec") as ChannelExec
        ch.setCommand(cmd)
        val errBuf = ByteArrayOutputStream()
        ch.setErrStream(errBuf)
        ch.setInputStream(if (stdin != null) ByteArrayInputStream(stdin) else null)
        val input = ch.inputStream
        ch.connect(5000)

        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        val end = System.currentTimeMillis() + timeoutMs
        try {
            while (true) {
                while (input.available() > 0) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                if (ch.isClosed) {
                    while (input.available() > 0) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    break
                }
                if (System.currentTimeMillis() > end) throw IOException("Timed out running a command on the head unit")
                Thread.sleep(40)
            }
            return Result(out.toByteArray(), errBuf.toString("UTF-8"), ch.exitStatus)
        } finally {
            ch.disconnect()
        }
    }

    override fun close() {
        session.disconnect()
    }
}

fun friendlyError(e: Throwable): String {
    val m = e.message ?: e.javaClass.simpleName
    return when {
        m.contains("Auth fail", true) -> "The head unit rejected the login (Auth fail)."
        m.contains("Algorithm negotiation fail", true) -> "SSH algorithm negotiation failed: $m"
        m.contains("refused", true) -> "Connection refused: SSH (port 22) is closed on the head unit."
        m.contains("timed out", true) || m.contains("timeout", true) -> "Timed out talking to the head unit ($m)."
        else -> m
    }
}

object EnvRemote {
    const val DIR = "/opt/aa"
    const val PATH = "/opt/aa/nbt-aad.env"

    sealed class ReadResult {
        data class Found(val text: String) : ReadResult()
        object NoFile : ReadResult()
        object NoFolder : ReadResult()
    }

    fun read(host: String, progress: (String) -> Unit): ReadResult {
        progress("Logging in to $host…")
        return SshSession(host).use { ssh ->
            progress("Reading $PATH…")
            val r = ssh.exec(
                "if [ ! -d $DIR ]; then echo __NODIR__; " +
                    "elif [ -f $PATH ]; then cat $PATH; else echo __NOFILE__; fi"
            )
            val t = r.text
            when {
                t.trim() == "__NODIR__" -> ReadResult.NoFolder
                t.trim() == "__NOFILE__" -> ReadResult.NoFile
                else -> ReadResult.Found(t)
            }
        }
    }

    /** Uploads [bytes] as nbt-aad.env (keeping nbt-aad.env.bak on the head unit) and verifies it by reading it back. */
    fun write(host: String, bytes: ByteArray, progress: (String) -> Unit) {
        progress("Logging in to $host…")
        SshSession(host).use { ssh ->
            progress("Making the partition writable…")
            var r = ssh.exec("mount -o remount rw /net/hu-omap/fs/sda0")
            if (r.exit != 0) {
                r = ssh.exec("mount -o remount rw /fs/sda0")
                if (r.exit != 0) progress("Remount reported: ${r.err.trim().ifEmpty { "exit ${r.exit}" }} (continuing)")
            }

            progress("Uploading ${bytes.size} bytes…")
            r = ssh.exec("cat > $PATH.new", stdin = bytes)
            if (r.exit != 0) throw IOException("Upload failed: ${r.err.trim().ifEmpty { "exit ${r.exit}" }}")

            progress("Replacing $PATH…")
            r = ssh.exec("[ -f $PATH ] && cp $PATH $PATH.bak; mv $PATH.new $PATH && sync")
            if (r.exit != 0) throw IOException("Could not replace the file: ${r.err.trim().ifEmpty { "exit ${r.exit}" }}")

            progress("Verifying…")
            val back = ssh.exec("cat $PATH")
            if (!back.out.contentEquals(bytes)) {
                throw IOException("Verification failed: the file on the head unit differs from what was sent.")
            }
        }
    }
}
