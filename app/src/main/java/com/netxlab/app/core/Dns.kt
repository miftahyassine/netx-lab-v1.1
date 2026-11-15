package com.netxlab.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

object DnsClient {
    val TYPES = linkedMapOf("A" to 1, "AAAA" to 28, "MX" to 15, "CNAME" to 5, "TXT" to 16,
        "NS" to 2, "SOA" to 6, "PTR" to 12, "SRV" to 33)

    data class Record(val name: String, val type: String, val ttl: Long, val data: String)
    data class Result(
        val server: String, val rcode: String, val timeMs: Long, val transport: String,
        val answers: List<Record>, val authority: List<Record>, val additional: List<Record>, val rawHex: String
    )

    fun reverseName(ip: String): String? {
        IpCalc.parseV4(ip)?.let { return ip.split(".").reversed().joinToString(".") + ".in-addr.arpa" }
        if (ip.contains(":")) {
            val p = IpCalc.parseV6(ip) ?: return null
            return p.expanded.replace(":", "").reversed().toCharArray().joinToString(".") + ".ip6.arpa"
        }
        return null
    }

    fun buildQuery(name: String, qtype: Int, id: Int): ByteArray {
        val o = ByteArrayOutputStream()
        fun w16(v: Int) { o.write(v shr 8); o.write(v and 255) }
        w16(id); w16(0x0100); w16(1); w16(0); w16(0); w16(0)
        for (label in name.trim('.').split(".")) {
            val b = label.toByteArray(Charsets.US_ASCII)
            require(b.size in 1..63) { "Invalid label '$label'" }
            o.write(b.size); o.write(b)
        }
        o.write(0); w16(qtype); w16(1)
        return o.toByteArray()
    }

    suspend fun query(name: String, type: String, server: String, timeoutMs: Int = 3000): Result =
        withContext(Dispatchers.IO) {
            val qtype = TYPES[type] ?: throw IllegalArgumentException("Unsupported type $type")
            val qname = if (type == "PTR") (reverseName(name) ?: name) else name
            val id = Random.nextInt(65536)
            val q = buildQuery(qname, qtype, id)
            val addr = InetAddress.getByName(server)
            val t0 = System.nanoTime()
            var transport = "UDP"
            var resp: ByteArray
            DatagramSocket().use { s ->
                s.soTimeout = timeoutMs
                s.send(DatagramPacket(q, q.size, addr, 53))
                val buf = ByteArray(4096)
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                resp = buf.copyOf(p.length)
            }
            if (resp.size >= 4 && (resp[2].toInt() and 0x02) != 0) {
                transport = "TCP (UDP truncated)"
                Socket().use { s ->
                    s.soTimeout = timeoutMs
                    s.connect(InetSocketAddress(addr, 53), timeoutMs)
                    val out = s.getOutputStream()
                    out.write(byteArrayOf((q.size shr 8).toByte(), q.size.toByte())); out.write(q); out.flush()
                    val inp = DataInputStream(s.getInputStream())
                    val len = inp.readUnsignedShort()
                    resp = ByteArray(len); inp.readFully(resp)
                }
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            parse(resp, server, ms, transport)
        }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)
    private fun u32(b: ByteArray, o: Int) = (u16(b, o).toLong() shl 16) or u16(b, o + 2).toLong()

    fun readName(b: ByteArray, start: Int): Pair<String, Int> {
        val sb = StringBuilder(); var o = start; var jumped = false; var end = start; var guard = 0
        while (guard++ < 128) {
            val len = b[o].toInt() and 255
            if (len == 0) { if (!jumped) end = o + 1; break }
            if (len and 0xC0 == 0xC0) {
                val ptr = ((len and 0x3F) shl 8) or (b[o + 1].toInt() and 255)
                if (!jumped) end = o + 2
                jumped = true; o = ptr; continue
            }
            sb.append(String(b, o + 1, len, Charsets.ISO_8859_1)).append('.')
            o += len + 1
            if (!jumped) end = o
        }
        return (if (sb.isEmpty()) "." else sb.toString()) to end
    }

    fun parse(b: ByteArray, server: String, ms: Long, transport: String): Result {
        val flags = u16(b, 2)
        val rcode = when (flags and 15) {
            0 -> "NOERROR"; 1 -> "FORMERR"; 2 -> "SERVFAIL"; 3 -> "NXDOMAIN"; 4 -> "NOTIMP"; 5 -> "REFUSED"
            else -> "RCODE ${flags and 15}"
        }
        val qd = u16(b, 4); val an = u16(b, 6); val ns = u16(b, 8); val ar = u16(b, 10)
        var o = 12
        repeat(qd) { o = readName(b, o).second + 4 }
        fun records(n: Int): List<Record> {
            val l = mutableListOf<Record>()
            repeat(n) {
                val (name, no) = readName(b, o); o = no
                val t = u16(b, o); val ttl = u32(b, o + 4); val rdlen = u16(b, o + 8); o += 10
                val data = rdata(b, o, rdlen, t)
                o += rdlen
                l += Record(name, TYPES.entries.firstOrNull { it.value == t }?.key ?: "TYPE$t", ttl, data)
            }
            return l
        }
        val a = records(an); val n = records(ns); val ad = records(ar)
        return Result(server, rcode, ms, transport, a, n, ad, b.joinToString("") { "%02x".format(it) })
    }

    private fun rdata(b: ByteArray, o: Int, len: Int, t: Int): String = try {
        when (t) {
            1 -> (0 until 4).joinToString(".") { (b[o + it].toInt() and 255).toString() }
            28 -> InetAddress.getByAddress(b.copyOfRange(o, o + 16)).hostAddress ?: ""
            2, 5, 12 -> readName(b, o).first
            15 -> "${u16(b, o)} ${readName(b, o + 2).first}"
            16 -> {
                val parts = mutableListOf<String>(); var p = o
                while (p < o + len) { val l = b[p].toInt() and 255; parts += String(b, p + 1, l, Charsets.UTF_8); p += l + 1 }
                parts.joinToString(" ") { "\"$it\"" }
            }
            6 -> {
                val (m, p1) = readName(b, o); val (r, p2) = readName(b, p1)
                "$m $r serial=${u32(b, p2)} refresh=${u32(b, p2 + 4)} retry=${u32(b, p2 + 8)} expire=${u32(b, p2 + 12)} min=${u32(b, p2 + 16)}"
            }
            33 -> "prio=${u16(b, o)} weight=${u16(b, o + 2)} port=${u16(b, o + 4)} target=${readName(b, o + 6).first}"
            else -> b.copyOfRange(o, o + len).joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) { "(unparsable: ${e.message})" }

    fun format(r: Result): String = buildString {
        appendLine(";; server ${r.server} via ${r.transport}, ${r.timeMs} ms, status ${r.rcode}")
        fun sec(t: String, l: List<Record>) { if (l.isNotEmpty()) { appendLine(";; $t"); l.forEach { appendLine("${it.name}\t${it.ttl}\tIN\t${it.type}\t${it.data}") } } }
        sec("ANSWER", r.answers); sec("AUTHORITY", r.authority); sec("ADDITIONAL", r.additional)
        if (r.answers.isEmpty()) appendLine(";; no answer records")
    }
}
