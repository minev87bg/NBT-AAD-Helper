package com.nbtaad.editor.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nbtaad.editor.net.Internet
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.security.MessageDigest

/** What the app is now: the version it was built as, and whether it was replaced by a newer one a moment ago. */
data class Installed(val code: Long, val name: String, val justUpdated: Boolean = false)

sealed class Check {
    data class Newer(val offer: UpdateOffer) : Check()
    data class Current(val installed: Installed, val latest: String?) : Check()
}

/** The result of the system installer, which arrives after the app has handed the file over. */
object InstallEvents {
    var message by mutableStateOf<String?>(null)
}

object Updater {
    private const val ACTION = "com.nbtaad.editor.INSTALL_RESULT"
    private const val MAX_APK = 120L * 1024 * 1024
    private const val JUST_UPDATED_MS = 15L * 60 * 1000

    fun installed(ctx: Context): Installed {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        // an update keeps the first install time and moves the last update time; a fresh install has both equal
        val replaced = info.lastUpdateTime - info.firstInstallTime > 1000
        val ago = System.currentTimeMillis() - info.lastUpdateTime
        return Installed(code, info.versionName ?: "?", replaced && ago in 0..JUST_UPDATED_MS)
    }

    /** Asks GitHub for the newest release. Throws when there is no internet or GitHub does not answer. */
    fun check(ctx: Context): Check {
        val me = installed(ctx)
        val json = Internet.run(ctx) { open ->
            val c = open(URL(UpdateLogic.LATEST_URL))
            try {
                c.connectTimeout = 8000
                c.readTimeout = 15000
                c.setRequestProperty("User-Agent", "NBT-AAD-Helper")
                c.setRequestProperty("Accept", "application/vnd.github+json")
                when (val code = c.responseCode) {
                    200 -> c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                    404 -> ""   // no release has been published yet
                    403, 429 -> throw IOException("GitHub is limiting requests right now ($code), try again later.")
                    else -> throw IOException("GitHub answered $code")
                }
            } finally {
                c.disconnect()
            }
        }
        if (json.isEmpty()) return Check.Current(me, null)

        val rel = JSONObject(json)
        val tag = rel.optString("tag_name")
        val build = UpdateLogic.buildOf(tag) ?: return Check.Current(me, tag)
        val assets = rel.optJSONArray("assets")
        val names = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it).optString("name") }
        val idx = UpdateLogic.pickApk(names)
        if (idx == null || !UpdateLogic.isNewer(me.code, build)) return Check.Current(me, tag)
        val a = assets!!.getJSONObject(idx)
        return Check.Newer(
            UpdateOffer(
                tag = tag,
                build = build,
                title = rel.optString("name").ifBlank { tag },
                notes = UpdateLogic.shortNotes(rel.optString("body")),
                apkName = a.optString("name"),
                apkUrl = a.optString("browser_download_url"),
                size = a.optLong("size", -1L),
                sha256 = UpdateLogic.parseDigest(a.optString("digest", "")),
                pageUrl = rel.optString("html_url"),
            )
        )
    }

    /** Downloads the APK into the app's cache and checks it. Returns the file; throws with a readable message. */
    fun download(ctx: Context, offer: UpdateOffer, progress: (done: Long, total: Long) -> Unit): File {
        val dir = File(ctx.cacheDir, "update")
        dir.deleteRecursively()
        dir.mkdirs()
        val target = File(dir, "update.apk")
        val tmp = File(dir, "update.part")
        val digest = MessageDigest.getInstance("SHA-256")
        Internet.run(ctx) { open ->
            val c = open(URL(offer.apkUrl))
            try {
                c.connectTimeout = 10000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "NBT-AAD-Helper")
                if (c.responseCode != 200) throw IOException("GitHub answered ${c.responseCode} for the download")
                val total = if (offer.size > 0) offer.size else c.contentLengthLong
                digest.reset()
                var done = 0L
                c.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (done > MAX_APK) throw IOException("The update is unexpectedly large")
                            progress(done, total)
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
        }
        val problem = UpdateLogic.intact(tmp.length(), offer.size, UpdateLogic.hex(digest.digest()), offer.sha256)
        if (problem != null) {
            tmp.delete()
            throw IOException(problem)
        }
        if (!tmp.renameTo(target)) throw IOException("Could not store the downloaded update")
        return target
    }

    /**
     * Android only replaces an app with a copy signed by the same key. Returns false when the downloaded file is
     * for another app or signed with another key, null when this phone does not let us compare.
     */
    fun sameSigner(ctx: Context, apk: File): Boolean? {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return null
        if (archive.packageName != ctx.packageName) return false
        val mine = pm.getPackageInfo(ctx.packageName, flags)
        val a = signerHashes(archive)
        val b = signerHashes(mine)
        if (a.isEmpty() || b.isEmpty()) return null
        return a == b
    }

    private fun signerHashes(p: android.content.pm.PackageInfo): Set<String> {
        val certs: List<ByteArray> = if (Build.VERSION.SDK_INT >= 28) {
            p.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            p.signatures?.map { it.toByteArray() }.orEmpty()
        }
        return certs.map { UpdateLogic.sha256Hex(it) }.toSet()
    }

    /** False when the person still has to allow "install unknown apps" for this app (the settings page is opened). */
    fun mayInstall(ctx: Context): Boolean {
        if (ctx.packageManager.canRequestPackageInstalls()) return true
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return false
    }

    /** Hands the file to Android's installer. The system then asks the person to confirm. */
    fun install(ctx: Context, apk: File) {
        InstallEvents.message = null
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(ACTION)
            val pending = PendingIntent.getBroadcast(
                ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's answers: brings its confirmation page to the front, and reports a failure. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallEvents.message = "Updated. Open the app again."
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                InstallEvents.message = "The update was cancelled. Press UPDATE to try again."
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                InstallEvents.message = "Android refused the update because it is signed with a different key than " +
                    "the installed app. Uninstall the app once, then install the new version."
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                InstallEvents.message = "The update could not be installed" + if (why.isNotBlank()) ": $why" else "."
            }
        }
    }
}
