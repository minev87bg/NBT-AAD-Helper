package com.nbtaad.editor.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the project's README so the list of settings stays current.
 *
 * The request goes out over a network that really has internet (mobile data, a WiFi repeater with internet,
 * Ethernet with internet), picked by looking at the network's capabilities, so it works even while the default
 * route of the phone points at the car's WiFi, which has none.
 */
object CatalogSync {
    const val README_URL =
        "https://raw.githubusercontent.com/Sq00ky/Supra-iDrive6-Android-Auto/main/milestone-7/readme.md"
    private const val MAX_BYTES = 3 * 1024 * 1024

    fun fetchReadme(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nets = internetNetworks(cm)
        var last: Exception? = null
        for (n in nets) {
            try {
                return download { u -> n.openConnection(u) as HttpURLConnection }
            } catch (e: Exception) {
                last = e
            }
        }
        if (nets.isEmpty()) {
            // nothing is marked as validated: try the default route once, it may still work
            try {
                return download { u -> u.openConnection() as HttpURLConnection }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("No network with internet")
    }

    private fun internetNetworks(cm: ConnectivityManager): List<Network> {
        @Suppress("DEPRECATION")
        val all = cm.allNetworks.toList()
        return all.filter { n ->
            val c = cm.getNetworkCapabilities(n)
            c != null &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    private fun download(open: (URL) -> HttpURLConnection): String {
        val conn = open(URL(README_URL))
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "NBT-AAD-Helper")
            conn.setRequestProperty("Accept", "text/plain")
            if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}")
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("The README is unexpectedly large")
                }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
