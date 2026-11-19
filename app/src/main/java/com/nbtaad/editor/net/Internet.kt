package com.nbtaad.editor.net

import com.nbtaad.editor.log.AppLog
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
 * phone's default network is tried once, it may still work.
 */
/** The server answered, and what it said is the answer (a refused key, a used-up limit): no other network is tried. */
open class Answered(message: String) : IOException(message)

object Internet {
    /** Runs [block] with a way to open connections; the next network is tried when it throws. */
    fun <T> run(ctx: Context, block: (open: (URL) -> HttpURLConnection) -> T): T {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nets = internetNetworks(cm)
        var last: Exception? = null
        for (n in nets) {
            try {
                return block { u -> n.openConnection(u) as HttpURLConnection }
            } catch (e: Answered) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Net", "Request failed on network $n: ${e.javaClass.simpleName}: ${e.message}")
                last = e
            }
        }
        if (nets.isEmpty()) {
            // nothing is marked as having internet (it may just have connected): try the phone's own default network,
            // asked for by name, so a network the app was bound to for the car is never what answers
            val def = try { cm.activeNetwork } catch (_: Exception) { null }
            if (def != null) {
                try {
                    return block { u -> def.openConnection(u) as HttpURLConnection }
                } catch (e: Answered) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w("Net", "Request failed on the default network: ${e.javaClass.simpleName}: ${e.message}")
                    last = e
                }
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
