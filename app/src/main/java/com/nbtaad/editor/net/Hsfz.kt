package com.nbtaad.editor.net

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Minimal BMW HSFZ (the protocol ISTA / E-Sys / Tool32 use over ENET).
 *
 *  - UDP 6811: send 00 00 00 00 00 11 to the broadcast address, the ZGW answers with a text like
 *              "DIAGADR10BMWMAC<12 hex>BMWVIN<17 chars>"; the sender of the answer is the ZGW.
 *  - TCP 6801: frames of  [len:4 BE][control word:2][payload]. Control word 0x0001 = diagnostic message,
 *              payload = [source][target][UDS bytes]. Control word 0x0002 = acknowledge.
 *  - Head unit (HU_NBT) has diagnostic address 0x63, tester address is 0xF4, ZGW is 0x10.
 *  - "ECU reset" = UDS 11 01 to 0x63 (same as Tool32 NBTEVO.PRG job STEUERGERAETE_RESET).
 */
object Hsfz {
    const val TCP_PORT = 6801
    const val UDP_PORT = 6811

    private const val TESTER = 0xF4
    private const val ZGW = 0x10
    private const val HEAD_UNIT = 0x63

    private const val CW_DIAG = 0x0001
    private const val CW_ACK = 0x0002

    val IDENT_REQUEST = byteArrayOf(0, 0, 0, 0, 0, 0x11)

    data class Ident(val ip: String, val vin: String?, val mac: String?, val diagAddr: String?)

    fun parseIdent(ip: String, data: ByteArray, len: Int): Ident? {
        val text = String(data, 0, len, Charsets.ISO_8859_1)
        if (!text.contains("DIAGADR") && !text.contains("BMWVIN")) return null
        val vin = Regex("BMWVIN([A-Za-z0-9]{17})").find(text)?.groupValues?.get(1)
            ?.uppercase()?.takeIf { isValidVin(it) }
        val mac = Regex("BMWMAC([0-9A-Fa-f]{12})").find(text)?.groupValues?.get(1)
        val diag = Regex("DIAGADR(\\d+)").find(text)?.groupValues?.get(1)
        return Ident(ip, vin, mac, diag)
    }

    private val VIN_RE = Regex("[A-HJ-NPR-Z0-9]{17}")

    /** A real VIN: 17 characters, digits and capital letters except I, O and Q. */
    fun isValidVin(v: String): Boolean = VIN_RE.matches(v)

    private fun diagFrame(src: Int, dst: Int, uds: ByteArray): ByteArray {
        val payloadLen = 2 + uds.size
        val out = ByteArray(6 + payloadLen)
        out[0] = (payloadLen ushr 24).toByte()
        out[1] = (payloadLen ushr 16).toByte()
        out[2] = (payloadLen ushr 8).toByte()
        out[3] = payloadLen.toByte()
        out[4] = (CW_DIAG ushr 8).toByte()
        out[5] = CW_DIAG.toByte()
        out[6] = src.toByte()
        out[7] = dst.toByte()
        uds.copyInto(out, 8)
        return out
    }

    private class Frame(val type: Int, val payload: ByteArray)

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = try {
                input.read(buf, off, buf.size - off)
            } catch (e: SocketTimeoutException) {
                return false
            }
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun readFrame(input: InputStream): Frame? {
        val h = ByteArray(6)
        if (!readFully(input, h)) return null
        val len = ((h[0].toInt() and 0xFF) shl 24) or ((h[1].toInt() and 0xFF) shl 16) or
            ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
        if (len < 0 || len > 65536) return null
        val p = ByteArray(len)
        if (len > 0 && !readFully(input, p)) return null
        val type = ((h[4].toInt() and 0xFF) shl 8) or (h[5].toInt() and 0xFF)
        return Frame(type, p)
    }

    /** Reads the VIN from the ZGW (UDS 22 F190). Used only when the UDP discovery gave no VIN. */
    fun readVin(zgwIp: String): String? {
        return try {
            Socket().use { s ->
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(zgwIp, TCP_PORT), 2000)
                s.soTimeout = 2500
                val uds = byteArrayOf(0x22, 0xF1.toByte(), 0x90.toByte())
                s.getOutputStream().write(diagFrame(TESTER, ZGW, uds))
                s.getOutputStream().flush()
                repeat(4) {
                    val f = readFrame(s.getInputStream()) ?: return null
                    if (f.type == CW_DIAG) {
                        val p = f.payload
                        for (i in 0 until p.size - 3 - 17 + 1) {
                            if (p[i] == 0x62.toByte() && p[i + 1] == 0xF1.toByte() && p[i + 2] == 0x90.toByte()) {
                                val v = String(p, i + 3, 17, Charsets.ISO_8859_1).uppercase()
                                if (isValidVin(v)) return v
                            }
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Sends "ECU reset" to the head unit through the ZGW. No SSH needed. Returns (success, message). */
    fun resetHeadUnit(zgwIp: String): Pair<Boolean, String> {
        return try {
            Socket().use { s ->
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(zgwIp, TCP_PORT), 3000)
                s.soTimeout = 3000
                s.getOutputStream().write(diagFrame(TESTER, HEAD_UNIT, byteArrayOf(0x11, 0x01)))
                s.getOutputStream().flush()

                var gotAck = false
                val deadline = System.currentTimeMillis() + 8000
                while (System.currentTimeMillis() < deadline) {
                    val f = readFrame(s.getInputStream()) ?: break
                    if (f.type == CW_ACK) {
                        gotAck = true
                        continue
                    }
                    if (f.type == CW_DIAG && f.payload.size >= 3) {
                        val uds = f.payload.copyOfRange(2, f.payload.size)
                        when (uds[0].toInt() and 0xFF) {
                            0x51 -> return Pair(true, "Head unit accepted the reset command.")
                            0x7F -> {
                                val nrc = if (uds.size >= 3) uds[2].toInt() and 0xFF else -1
                                if (nrc == 0x78) continue // response pending
                                return Pair(false, "Head unit rejected the reset (error code 0x%02X).".format(nrc))
                            }
                        }
                    }
                }
                if (gotAck) Pair(true, "Reset command sent (gateway acknowledged it).")
                else Pair(false, "The gateway did not answer the reset command.")
            }
        } catch (e: Exception) {
            Pair(false, "Could not reach the gateway on port $TCP_PORT: ${e.message}")
        }
    }
}
