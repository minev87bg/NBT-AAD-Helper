package com.nbtaad.editor.net

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.nbtaad.editor.log.AppLog

/**
 * Android's own list of WiFi networks. Since Android 10 an app may not switch the phone's WiFi by itself, but it can ask
 * the system for its WiFi panel: a sheet that opens over the app (the app stays where it is, nothing is lost), where the
 * person taps a network. Older phones get the full WiFi settings page.
 */
object WifiPanel {
    /** Opens the list; false when the phone has nothing to show it with. */
    fun open(ctx: Context): Boolean {
        val action = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Settings.Panel.ACTION_WIFI
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            else -> Settings.ACTION_WIFI_SETTINGS
        }
        return try {
            ctx.startActivity(Intent(action))
            true
        } catch (e: Exception) {
            AppLog.w("Wizard", "The WiFi list did not open: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
