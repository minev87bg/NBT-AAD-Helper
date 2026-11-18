package com.nbtaad.editor.log

import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/*
 * The app's own log, kept on the phone (about 800 KB at most): what was detected, every SSH step, what the screens said
 * and every error with where it happened. It is for finding out what went wrong in the car: the person saves it to a
 * file and sends it along. VINs are masked; nothing else personal is written. The writing itself is [AppLog] in
 * LogFile.kt; this file is the part that needs the phone.
 */

private var appVersion = "?"

private fun device() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

/** Starts the log (once): the file, the copy into Android's own log, and the entry for a crash. Safe to call again. */
fun AppLog.start(ctx: Context) {
    if (!setup(File(ctx.applicationContext.filesDir, "logs"))) return
    appVersion = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        @Suppress("DEPRECATION") val code = pi.versionCode
        "${pi.versionName} (code $code)"
    } catch (e: Exception) {
        "?"
    }
    echo = { level, tag, msg ->
        val prio = when (level) {
            'E' -> android.util.Log.ERROR
            'W' -> android.util.Log.WARN
            'D' -> android.util.Log.DEBUG
            else -> android.util.Log.INFO
        }
        android.util.Log.println(prio, "NbtAad/$tag", msg)
    }
    i("App", "Started. Version $appVersion, ${device()}")
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, ex ->
        try {
            e("CRASH", "Uncaught exception in thread ${t.name}", ex)
        } catch (_: Throwable) {
        }
        previous?.uncaughtException(t, ex)
    }
}

/** "nbt-aad-helper-log-20261119-0140.txt" */
fun AppLog.exportName(): String =
    "nbt-aad-helper-log-" + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".txt"

/** Writes a short header and the whole log to [uri] (a file the person chose). Returns the number of bytes. */
fun AppLog.saveTo(ctx: Context, uri: Uri): Int {
    val header = buildString {
        appendLine("NBT-AAD Helper log")
        appendLine("App: $appVersion")
        appendLine("Phone: ${device()}")
        appendLine("Saved: ${ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
        appendLine("VINs are masked (WBA…3076).")
        appendLine("----")
    }
    val bytes = (header + text()).toByteArray(Charsets.UTF_8)
    ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        ?: throw java.io.IOException("The phone would not open that file for writing.")
    return bytes.size
}
