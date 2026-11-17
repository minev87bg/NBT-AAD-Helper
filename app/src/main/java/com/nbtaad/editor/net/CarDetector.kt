package com.nbtaad.editor.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors

data class LocalNet(val name: String, val ip: String, val prefix: Int) {
    private val maskBits: Int
        get() = if (prefix <= 0) 0 else -1 shl (32 - prefix.coerceAtMost(32))

    val broadcast: String
        get() = intToIp(ipToInt(ip) or maskBits.inv())

    /** Hosts to probe: the subnet, but never more than the /24 around the phone. */
    fun sweepHosts(): List<String> = lan(ip, maxOf(prefix, 24)).filter { it != ip }
}

fun ipToInt(ip: String): Int {
    val p = ip.split('.')
    return (p[0].toInt() shl 24) or (p[1].toInt() shl 16) or (p[2].toInt() shl 8) or p[3].toInt()
}

fun intToIp(v: Int): String = "${(v ushr 24) and 255}.${(v ushr 16) and 255}.${(v ushr 8) and 255}.${v and 255}"

/** All host addresses of the network containing [ip] with the given prefix length. */
fun lan(ip: String, prefix: Int): List<String> {
    val shift = 32 - prefix
    val mask = if (prefix <= 0) 0 else -1 shl shift
    val base = ipToInt(ip) and mask
    val count = 1 shl shift
    if (count <= 2) return emptyList()
    return (1 until count - 1).map { intToIp(base + it) }
}

enum class PortState { OPEN, REFUSED }

data class DetectResult(
    val zgwIp: String? = null,
    val vin: String? = null,
    val huIp: String? = null,
    val sshOpen: Boolean = false,
    val problem: String? = null,
)

object NetworkBinder {
    /**
     * A WiFi dongle without internet makes Android route app traffic over mobile data.
     * Binding the process to the WiFi/Ethernet network avoids that. When the phone itself is the
     * hotspot there is no such network and nothing is bound (hotspot traffic is local anyway).
     */
    fun bindToLocalNetwork(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val pick = cm.allNetworks.firstOrNull { n ->
                val c = cm.getNetworkCapabilities(n)
                c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            }
            cm.bindProcessToNetwork(pick)
        } catch (_: Exception) {
        }
    }
}

object CarDetector {
    const val SSH_PORT = 22

    /** Head unit addresses seen on the ENET link-local network in the project's guides / issues. */
    private val KNOWN_HU = listOf("169.254.199.99", "169.254.199.119")

    private val SKIP_IF = listOf("lo", "rmnet", "ccmni", "dummy", "tun", "v4-", "clat", "ifb", "sit")

    fun localNetworks(): List<LocalNet> {
        val out = ArrayList<LocalNet>()
        try {
            val all = Collections.list(NetworkInterface.getNetworkInterfaces() ?: return out)
            for (ni in all) {
                val name = ni.name.lowercase()
                if (!ni.isUp || ni.isLoopback) continue
                if (SKIP_IF.any { name.startsWith(it) }) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        val ip = a.hostAddress ?: continue
                        out += LocalNet(ni.name, ip, ia.networkPrefixLength.toInt())
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Broadcast the HSFZ ident request on every phone network and wait for the ZGW to answer. */
    fun discoverZgw(nets: List<LocalNet>): Hsfz.Ident? {
        repeat(2) {
            for (net in nets) {
                val targets = linkedSetOf(net.broadcast, "255.255.255.255")
                if (net.ip.startsWith("169.254.")) targets += "169.254.255.255"
                try {
                    DatagramSocket(InetSocketAddress(InetAddress.getByName(net.ip), 0)).use { sock ->
                        sock.broadcast = true
                        sock.soTimeout = 300
                        for (t in targets) {
                            runCatching {
                                sock.send(
                                    DatagramPacket(
                                        Hsfz.IDENT_REQUEST, Hsfz.IDENT_REQUEST.size,
                                        InetAddress.getByName(t), Hsfz.UDP_PORT
                                    )
                                )
                            }
                        }
                        val end = System.currentTimeMillis() + 1500
                        val buf = ByteArray(512)
                        while (System.currentTimeMillis() < end) {
                            val p = DatagramPacket(buf, buf.size)
                            try {
                                sock.receive(p)
                            } catch (e: SocketTimeoutException) {
                                continue
                            }
                            val ip = p.address.hostAddress ?: continue
                            if (ip == net.ip) continue
                            val ident = Hsfz.parseIdent(ip, p.data, p.length)
                            if (ident != null) return ident
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun probe(host: String, port: Int, timeoutMs: Int): PortState? = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        PortState.OPEN
    } catch (e: java.net.ConnectException) {
        if (e.message?.contains("refused", ignoreCase = true) == true) PortState.REFUSED else null
    } catch (_: Exception) {
        null
    }

    /** Parallel TCP probe. Returns only hosts that answered (OPEN) or actively refused (host is up). */
    fun sweep(hosts: Collection<String>, port: Int, timeoutMs: Int): Map<String, PortState> {
        val pool = Executors.newFixedThreadPool(64)
        try {
            val futures = hosts.map { h -> pool.submit(Callable { h to probe(h, port, timeoutMs) }) }
            val out = LinkedHashMap<String, PortState>()
            for (f in futures) {
                val (h, st) = f.get()
                if (st != null) out[h] = st
            }
            return out
        } finally {
            pool.shutdownNow()
        }
    }

    fun sshOpen(ip: String): Boolean = probe(ip, SSH_PORT, 2000) == PortState.OPEN

    private fun banner(ip: String): String? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, SSH_PORT), 1500)
            s.soTimeout = 1500
            s.getInputStream().bufferedReader(Charsets.ISO_8859_1).readLine()
        }
    } catch (_: Exception) {
        null
    }

    /** Lower is better. The head unit runs OpenSSH; a WiFi router/dongle usually runs dropbear. */
    private fun rank(ip: String): Int {
        if (ip in KNOWN_HU) return 0
        val b = banner(ip) ?: return 3
        return when {
            b.contains("dropbear", ignoreCase = true) -> 4
            b.contains("OpenSSH", ignoreCase = true) -> 1
            else -> 2
        }
    }

    /** Returns (head unit address or null, SSH port open). */
    fun findHeadUnit(nets: List<LocalNet>, zgw: String?): Pair<String?, Boolean> {
        val hosts = LinkedHashSet<String>()
        hosts += KNOWN_HU
        nets.forEach { hosts += it.sweepHosts() }
        if (nets.any { it.ip.startsWith("169.254.") }) hosts += lan("169.254.199.1", 24)
        if (zgw != null) hosts += lan(zgw, 24)
        hosts.removeAll(nets.map { it.ip }.toSet())

        val states = sweep(hosts, SSH_PORT, 700)
        var open = states.filterValues { it == PortState.OPEN }.keys.toList()
        if (open.size > 1 && zgw != null) open = open.filter { it != zgw }.ifEmpty { open }
        if (open.isNotEmpty()) {
            val best = open.map { it to rank(it) }.minByOrNull { it.second }!!.first
            return best to true
        }
        val knownRefused = KNOWN_HU.firstOrNull { states[it] == PortState.REFUSED }
        return knownRefused to false
    }

    /** The whole detection used by the DETECT CAR button (and by "Check Connection"). */
    fun detect(progress: (String) -> Unit): DetectResult {
        progress("Looking at the phone's networks…")
        val nets = localNetworks()
        if (nets.isEmpty()) {
            return DetectResult(
                problem = "The phone has no WiFi, hotspot or Ethernet network. " +
                    "Switch your hotspot on / join the car's WiFi and press DETECT CAR again."
            )
        }
        progress(
            "Networks: " + nets.joinToString { "${it.name} ${it.ip}/${it.prefix}" } +
                "\nLooking for the car gateway (ZGW)…"
        )
        val ident = discoverZgw(nets)
        var zgw = ident?.ip
        var vin = ident?.vin
        if (zgw == null) {
            progress("No answer to the broadcast, scanning for port ${Hsfz.TCP_PORT}…")
            val hosts = nets.flatMap { it.sweepHosts() }.distinct()
            zgw = sweep(hosts, Hsfz.TCP_PORT, 600).filterValues { it == PortState.OPEN }.keys.firstOrNull()
            if (zgw != null) vin = Hsfz.readVin(zgw)
        }
        if (zgw == null) {
            return DetectResult(
                problem = "No car detected. Verify the WiFi connection (phone, dongle and car in the same " +
                    "network) and press DETECT CAR again."
            )
        }
        progress("Gateway found at $zgw. Looking for the head unit…")
        val (hu, open) = findHeadUnit(nets, zgw)
        return DetectResult(zgwIp = zgw, vin = vin, huIp = hu, sshOpen = open)
    }
}
