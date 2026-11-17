package com.nbtaad.editor.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reaches the internet even while the app is bound to the car's WiFi / Ethernet (which has none).
 *
 * The request goes out over a network that really has internet (mobile data, a WiFi repeater with internet,
 * Ethernet with internet), picked by looking at the network's capabilities. If nothing is marked as validated, the
 * default route is tried once, it may still work.
 */
object Internet {
    /** Runs [block] with a way to open connections; the next network is tried when it throws. */
    fun <T> run(ctx: Context, block: (open: (URL) -> HttpURLConnection) -> T): T {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nets = internetNetworks(cm)
        var last: Exception? = null
        for (n in nets) {
            try {
                return block { u -> n.openConnection(u) as HttpURLConnection }
            } catch (e: Exception) {
                last = e
            }
        }
        if (nets.isEmpty()) {
            try {
                return block { u -> u.openConnection() as HttpURLConnection }
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
}
