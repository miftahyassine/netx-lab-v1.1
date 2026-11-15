package com.netxlab.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketInfo(
    val no: Int, val ts: Double, val src: String, val dst: String, val sport: Int?, val dport: Int?,
    val proto: String, val length: Int, val flags: String, val info: String, val l3: String,
    val data: ByteArray, val headerLen: Int
)

object PcapParser {
    class ParseResult(val packets: List<PacketInfo>, val format: String, val note: String)

    fun parse(b: ByteArray): ParseResult {
        if (b.size < 12) throw IllegalArgumentException("File too small")
        val magic = ByteBuffer.wrap(b, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        return when (magic) {
            0xa1b2c3d4.toInt() -> classic(b, ByteOrder.BIG_ENDIAN, false)
            0xd4c3b2a1.toInt() -> classic(b, ByteOrder.LITTLE_ENDIAN, false)
            0xa1b23c4d.toInt() -> classic(b, ByteOrder.BIG_ENDIAN, true)
            0x4d3cb2a1 -> classic(b, ByteOrder.LITTLE_ENDIAN, true)
            0x0a0d0d0a -> pcapng(b)
            else -> throw IllegalArgumentException("Not a PCAP/PCAPNG file")
        }
    }

    private fun classic(b: ByteArray, order: ByteOrder, nano: Boolean): ParseResult {
        val bb = ByteBuffer.wrap(b).order(order)
        val link = bb.getInt(20)
        var o = 24; var n = 0
        val list = ArrayList<PacketInfo>()
        while (o + 16 <= b.size) {
            val sec = bb.getInt(o).toLong() and 0xFFFFFFFFL
            val frac = bb.getInt(o + 4).toLong() and 0xFFFFFFFFL
            val incl = bb.getInt(o + 8); val orig = bb.getInt(o + 12)
            o += 16
            if (incl < 0 || o + incl > b.size) break
            val ts = sec + frac / (if (nano) 1e9 else 1e6)
            list += decode(++n, ts, b.copyOfRange(o, o + incl), orig, link)
            o += incl
        }
        return ParseResult(list, if (nano) "PCAP (nanosecond)" else "PCAP", "Link type $link")
    }

    private fun pcapng(b: ByteArray): ParseResult {
        val shbOrder = if (ByteBuffer.wrap(b, 8, 4).order(ByteOrder.BIG_ENDIAN).int == 0x1A2B3C4D) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val bb = ByteBuffer.wrap(b).order(shbOrder)
        val links = ArrayList<Int>(); val resol = ArrayList<Double>()
        var o = 0; var n = 0
        val list = ArrayList<PacketInfo>()
        while (o + 12 <= b.size) {
            val type = bb.getInt(o); val len = bb.getInt(o + 4)
            if (len < 12 || o + len > b.size) break
            when (type) {
                1 -> {
                    links += bb.getShort(o + 8).toInt() and 0xFFFF
                    var res = 1e-6
                    var p = o + 16
                    while (p + 4 <= o + len - 4) {
                        val code = bb.getShort(p).toInt() and 0xFFFF; val ol = bb.getShort(p + 2).toInt() and 0xFFFF
                        if (code == 0) break
                        if (code == 9 && ol >= 1) {
                            val v = b[p + 4].toInt() and 255
                            res = if (v and 0x80 != 0) Math.pow(2.0, -(v and 0x7F).toDouble()) else Math.pow(10.0, -v.toDouble())
                        }
                        p += 4 + ((ol + 3) / 4) * 4
                    }
                    resol += res
                }
                6 -> {
                    val ifId = bb.getInt(o + 8)
                    val hi = bb.getInt(o + 12).toLong() and 0xFFFFFFFFL; val lo = bb.getInt(o + 16).toLong() and 0xFFFFFFFFL
                    val cap = bb.getInt(o + 20); val orig = bb.getInt(o + 24)
                    if (cap >= 0 && o + 28 + cap <= b.size) {
                        val r = resol.getOrElse(ifId) { 1e-6 }
                        val ts = ((hi shl 32) or lo) * r
                        list += decode(++n, ts, b.copyOfRange(o + 28, o + 28 + cap), orig, links.getOrElse(ifId) { 1 })
                    }
                }
                3 -> {
                    val orig = bb.getInt(o + 8)
                    val cap = minOf(orig, len - 16)
                    if (cap >= 0) list += decode(++n, 0.0, b.copyOfRange(o + 12, o + 12 + cap), orig, links.getOrElse(0) { 1 })
                }
            }
            o += len
        }
        return ParseResult(list, "PCAPNG", "Interfaces: ${links.size}, link types $links")
    }

    private fun u8(d: ByteArray, o: Int) = d[o].toInt() and 255
    private fun u16(d: ByteArray, o: Int) = (u8(d, o) shl 8) or u8(d, o + 1)
    private fun ip4(d: ByteArray, o: Int) = "${u8(d, o)}.${u8(d, o + 1)}.${u8(d, o + 2)}.${u8(d, o + 3)}"
    private fun ip6(d: ByteArray, o: Int) = IpCalc.compressV6(IntArray(8) { u16(d, o + it * 2) })
    private fun mac(d: ByteArray, o: Int) = (0 until 6).joinToString(":") { "%02x".format(d[o + it]) }

    fun decode(no: Int, ts: Double, d: ByteArray, orig: Int, link: Int): PacketInfo {
        var etherType = -1; var off = 0
        var srcMac = ""; var dstMac = ""
        try {
            when (link) {
                1 -> if (d.size >= 14) {
                    dstMac = mac(d, 0); srcMac = mac(d, 6)
                    etherType = u16(d, 12); off = 14
                    while ((etherType == 0x8100 || etherType == 0x88A8) && d.size >= off + 4) { etherType = u16(d, off + 2); off += 4 }
                }
                113 -> if (d.size >= 16) { etherType = u16(d, 14); off = 16 }
                0 -> if (d.size >= 4) { off = 4; etherType = if (u8(d, 4) shr 4 == 6) 0x86DD else 0x0800 }
                101, 12 -> if (d.isNotEmpty()) { etherType = if (u8(d, 0) shr 4 == 6) 0x86DD else 0x0800 }
            }
            return when (etherType) {
                0x0800 -> decodeIp(no, ts, d, orig, off, false)
                0x86DD -> decodeIp(no, ts, d, orig, off, true)
                0x0806 -> {
                    val op = u16(d, off + 6)
                    val sip = ip4(d, off + 14); val tip = ip4(d, off + 24)
                    val info = if (op == 1) "Who has $tip? Tell $sip" else "$sip is at ${mac(d, off + 8)}"
                    PacketInfo(no, ts, srcMac.ifEmpty { sip }, dstMac.ifEmpty { tip }, null, null, "ARP", orig, "", info, "ARP", d, off)
                }
                else -> PacketInfo(no, ts, srcMac, dstMac, null, null, "Other", orig, "", "EtherType 0x%04x".format(etherType.coerceAtLeast(0)), "Other", d, off)
            }
        } catch (e: Exception) {
            return PacketInfo(no, ts, "", "", null, null, "Malformed", orig, "", "Truncated or malformed: ${e.message}", "Other", d, 0)
        }
    }

    private fun decodeIp(no: Int, ts: Double, d: ByteArray, orig: Int, off: Int, v6: Boolean): PacketInfo {
        val src: String; val dst: String; val proto: Int; val l4: Int; val l3 = if (v6) "IPv6" else "IPv4"
        if (v6) { src = ip6(d, off + 8); dst = ip6(d, off + 24); proto = u8(d, off + 6); l4 = off + 40 }
        else { val ihl = (u8(d, off) and 15) * 4; src = ip4(d, off + 12); dst = ip4(d, off + 16); proto = u8(d, off + 9); l4 = off + ihl }
        when (proto) {
            6 -> {
                val sp = u16(d, l4); val dp = u16(d, l4 + 2)
                val f = u8(d, l4 + 13); val hl = (u8(d, l4 + 12) shr 4) * 4
                val names = listOf("FIN", "SYN", "RST", "PSH", "ACK", "URG", "ECE", "CWR")
                val flags = names.indices.filter { f and (1 shl it) != 0 }.joinToString(",") { names[it] }
                val pay = l4 + hl
                var label = "TCP"; var info = "$sp → $dp [$flags]"
                if (pay < d.size) {
                    val s = String(d, pay, minOf(60, d.size - pay), Charsets.ISO_8859_1)
                    val first = s.substringBefore("\r").substringBefore("\n")
                    if (listOf("GET ", "POST ", "HEAD ", "PUT ", "DELETE ", "OPTIONS ", "HTTP/1.").any { s.startsWith(it) }) { label = "HTTP"; info = first }
                    else if (u8(d, pay) in 20..23 && u8(d, pay + 1) == 3) { label = "TLS"; info = "TLS record type ${u8(d, pay)} $sp → $dp" }
                } else if (dp == 443 || sp == 443) label = "TCP"
                return PacketInfo(no, ts, src, dst, sp, dp, label, orig, flags, info, l3, d, off)
            }
            17 -> {
                val sp = u16(d, l4); val dp = u16(d, l4 + 2)
                if (sp == 53 || dp == 53) {
                    val p = l4 + 8
                    val qr = (u8(d, p + 2) shr 7) == 1
                    val name = try { DnsClient.readName(d, p + 12).first } catch (e: Exception) { "?" }
                    return PacketInfo(no, ts, src, dst, sp, dp, "DNS", orig, "", (if (qr) "Response " else "Query ") + name, l3, d, off)
                }
                return PacketInfo(no, ts, src, dst, sp, dp, "UDP", orig, "", "$sp → $dp len=${u16(d, l4 + 4)}", l3, d, off)
            }
            1 -> return PacketInfo(no, ts, src, dst, null, null, "ICMP", orig, "", "type ${u8(d, l4)} code ${u8(d, l4 + 1)}", l3, d, off)
            58 -> return PacketInfo(no, ts, src, dst, null, null, "ICMPv6", orig, "", "type ${u8(d, l4)} code ${u8(d, l4 + 1)}", l3, d, off)
            else -> return PacketInfo(no, ts, src, dst, null, null, "IP/$proto", orig, "", "protocol $proto", l3, d, off)
        }
    }

    fun matches(p: PacketInfo, filter: String): Boolean {
        val f = filter.trim().lowercase()
        if (f.isEmpty()) return true
        return when (f) {
            "tcp" -> p.proto in listOf("TCP", "HTTP", "TLS")
            "udp" -> p.proto in listOf("UDP", "DNS")
            "icmp" -> p.proto.startsWith("ICMP")
            "dns" -> p.proto == "DNS"
            "http" -> p.proto == "HTTP"
            "https", "tls" -> p.proto == "TLS" || p.sport == 443 || p.dport == 443
            "arp" -> p.proto == "ARP"
            "ipv4" -> p.l3 == "IPv4"
            "ipv6" -> p.l3 == "IPv6"
            else -> p.src.lowercase().contains(f) || p.dst.lowercase().contains(f) || p.info.lowercase().contains(f) ||
                p.proto.lowercase() == f || p.sport?.toString() == f || p.dport?.toString() == f
        }
    }

    fun hexDump(d: ByteArray, max: Int = 2048): String = buildString {
        val n = minOf(d.size, max)
        for (i in 0 until n step 16) {
            append("%04x  ".format(i))
            for (j in 0 until 16) { if (i + j < n) append("%02x ".format(d[i + j])) else append("   "); if (j == 7) append(' ') }
            append(" ")
            for (j in 0 until 16) if (i + j < n) { val c = d[i + j].toInt() and 255; append(if (c in 32..126) c.toChar() else '.') }
            append('\n')
        }
        if (d.size > max) append("... (${d.size - max} more bytes)")
    }
}
