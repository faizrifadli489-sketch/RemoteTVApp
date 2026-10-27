package com.faiz.tvremote.pairing

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Cari TV via mDNS (_androidtvremote2._tcp), mirror dari pair.js.
 * Kirim query PTR QU (unicast) agar tidak perlu bind port 5353.
 */
object TvDiscovery {
    private const val SERVICE = "_androidtvremote2._tcp.local"
    private const val TAG = "TvDiscovery"

    data class Device(val host: String, val name: String)

    suspend fun discover(timeoutMs: Int = 4000): List<Device> = withContext(Dispatchers.IO) {
        val found = mutableMapOf<String, String>()
        var lock: WifiManager.MulticastLock? = null
        var sock: DatagramSocket? = null
        try {
            val wm = null // diisi pemanggil bila perlu; multicast lock opsional
            sock = DatagramSocket().apply {
                soTimeout = 1000
                broadcast = true
            }
            val query = buildQuery()
            val dest = InetAddress.getByName("224.0.0.251")
            repeat(3) {
                try { sock.send(DatagramPacket(query, query.size, dest, 5353)) } catch (_: Exception) {}
                val buf = ByteArray(1500)
                val end = System.currentTimeMillis() + timeoutMs / 3
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        sock.receive(p)
                        parseResponse(p.data.copyOf(p.length), p.address.hostAddress ?: "") { host, name ->
                            found[host] = name
                        }
                    } catch (_: java.net.SocketTimeoutException) { break }
                    catch (_: Exception) { break }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "discovery gagal: ${e.message}")
        } finally {
            try { sock?.close() } catch (_: Exception) {}
            try { lock?.release() } catch (_: Exception) {}
        }
        found.map { Device(it.key, it.value) }
    }

    private fun buildQuery(): ByteArray {
        val out = mutableListOf<Byte>()
        fun label(s: String) {
            out.add(s.length.toByte())
            out.addAll(s.toByteArray().toList())
        }
        SERVICE.split(".").forEach { label(it) }
        out.add(0)
        // header: 12 byte, QDCOUNT=1
        val head = ByteArray(12).apply { this[5] = 1 }
        // QTYPE=PTR(12), QCLASS=IN+QU(0x8001)
        val tail = byteArrayOf(0, 12, 0x80.toByte(), 1)
        return head + out.toByteArray() + tail
    }

    private fun parseResponse(data: ByteArray, from: String, onFound: (String, String) -> Unit) {
        try {
            var pos = 12
            val qd = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            val an = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            val ns = ((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)
            val ar = ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
            repeat(qd) { pos = readName(data, pos).second + 4 }
            val total = an + ns + ar
            val ptrs = mutableListOf<String>()
            val srv = mutableMapOf<String, Pair<Int, String>>()
            val aRec = mutableMapOf<String, String>()
            repeat(total) {
                if (pos + 10 > data.size) return
                val (name, next) = readName(data, pos)
                val type = ((data[next].toInt() and 0xFF) shl 8) or (data[next + 1].toInt() and 0xFF)
                val len = ((data[next + 8].toInt() and 0xFF) shl 8) or (data[next + 9].toInt() and 0xFF)
                val d = next + 10
                when (type) {
                    12 -> ptrs.add(readName(data, d).first)
                    33 -> if (d + 6 < data.size) {
                        val port = ((data[d + 4].toInt() and 0xFF) shl 8) or (data[d + 5].toInt() and 0xFF)
                        srv[name] = port to readName(data, d + 6).first
                    }
                    1 -> if (len == 4 && d + 4 <= data.size) {
                        aRec[name] = "${data[d].toInt() and 0xFF}.${data[d + 1].toInt() and 0xFF}.${data[d + 2].toInt() and 0xFF}.${data[d + 3].toInt() and 0xFF}"
                    }
                }
                pos = d + len
            }
            for (inst in ptrs) {
                if (!inst.endsWith(SERVICE)) continue
                val (port, target) = srv[inst] ?: (0 to "")
                val host = aRec[target] ?: from
                val name = inst.dropLast(SERVICE.length + 1).replace("\\032", " ").ifEmpty { host }
                if (host.isNotEmpty()) onFound(host, name)
            }
        } catch (_: Exception) {}
    }

    private fun readName(data: ByteArray, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var pos = start
        var end = -1
        var hops = 0
        while (hops++ < 20 && pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if ((len and 0xC0) == 0xC0) {
                if (end < 0) end = pos + 2
                pos = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                continue
            }
            labels.add(String(data, pos + 1, len, Charsets.UTF_8))
            pos += len + 1
        }
        return labels.joinToString(".") to if (end < 0) pos else end
    }
}
