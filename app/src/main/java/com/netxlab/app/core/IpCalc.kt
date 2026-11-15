package com.netxlab.app.core

import java.math.BigInteger
import java.net.Inet6Address
import java.net.InetAddress

data class Ipv4Info(
    val address: String, val mask: String, val wildcard: String, val network: String,
    val broadcast: String, val first: String, val last: String, val hosts: Long, val cidr: Int,
    val binary: String
)

object IpCalc {
    fun parseV4(s: String): Long? {
        val p = s.trim().split(".")
        if (p.size != 4) return null
        var v = 0L
        for (x in p) {
            val n = x.trim().toIntOrNull() ?: return null
            if (n !in 0..255) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }

    fun fmtV4(v: Long): String = "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    fun maskFor(prefix: Int): Long = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL

    fun prefixFromMask(mask: Long): Int? {
        val m = mask and 0xFFFFFFFFL
        val p = java.lang.Long.bitCount(m)
        return if (maskFor(p) == m) p else null
    }

    fun toBinary(v: Long): String = (3 downTo 0).joinToString(".") {
        ((v shr (it * 8)) and 255).toString(2).padStart(8, '0')
    }

    fun calc(input: String): Ipv4Info? {
        val parts = input.trim().split("/", " ").filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.size > 2) return null
        val addr = parseV4(parts[0]) ?: return null
        val prefix: Int = if (parts.size == 1) 32 else {
            val asInt = parts[1].toIntOrNull()
            if (asInt != null) asInt else {
                val m = parseV4(parts[1]) ?: return null
                prefixFromMask(m) ?: return null
            }
        }
        if (prefix !in 0..32) return null
        val mask = maskFor(prefix)
        val net = addr and mask
        val bc = net or (mask.inv() and 0xFFFFFFFFL)
        val hosts = when (prefix) { 32 -> 1L; 31 -> 2L; else -> (1L shl (32 - prefix)) - 2 }
        val first = if (prefix >= 31) net else net + 1
        val last = if (prefix >= 31) bc else bc - 1
        return Ipv4Info(
            fmtV4(addr), fmtV4(mask), fmtV4(mask.inv() and 0xFFFFFFFFL), fmtV4(net), fmtV4(bc),
            fmtV4(first), fmtV4(last), hosts, prefix, toBinary(addr)
        )
    }

    /** Split a network into equal subnets of newPrefix (max 256 results). */
    fun split(cidr: String, newPrefix: Int): List<String>? {
        val i = calc(cidr) ?: return null
        if (newPrefix < i.cidr || newPrefix > 32) return null
        val count = 1L shl (newPrefix - i.cidr)
        if (count > 256) return null
        val base = parseV4(i.network)!!
        val step = 1L shl (32 - newPrefix)
        return (0 until count).map { "${fmtV4(base + it * step)}/$newPrefix" }
    }

    data class VlsmEntry(val need: Int, val cidr: String, val usable: Long, val range: String)

    /** Allocate subnets largest-first inside base network. Returns null if it does not fit. */
    fun vlsm(base: String, needs: List<Int>): List<VlsmEntry>? {
        val b = calc(base) ?: return null
        val start = parseV4(b.network)!!
        val end = parseV4(b.broadcast)!!
        var cursor = start
        val out = mutableListOf<VlsmEntry>()
        for (need in needs.sortedDescending()) {
            if (need < 1) return null
            var bits = 2
            while ((1L shl bits) - 2 < need) bits++
            val size = 1L shl bits
            if (cursor % size != 0L) cursor = (cursor / size + 1) * size
            if (cursor + size - 1 > end || bits > 32) return null
            val prefix = 32 - bits
            out += VlsmEntry(need, "${fmtV4(cursor)}/$prefix", size - 2, "${fmtV4(cursor + 1)} - ${fmtV4(cursor + size - 2)}")
            cursor += size
        }
        return out
    }

    // ---------------- IPv6 ----------------
    data class Ipv6Info(
        val expanded: String, val compressed: String, val type: String,
        val network: String, val prefix: Int
    )

    private fun groupsOf(bi: BigInteger): IntArray {
        val g = IntArray(8)
        for (i in 0 until 8) g[7 - i] = bi.shiftRight(16 * i).and(BigInteger.valueOf(0xFFFF)).toInt()
        return g
    }

    fun compressV6(g: IntArray): String {
        var bestStart = -1; var bestLen = 0; var i = 0
        while (i < 8) {
            if (g[i] == 0) {
                var j = i
                while (j < 8 && g[j] == 0) j++
                if (j - i > bestLen) { bestStart = i; bestLen = j - i }
                i = j
            } else i++
        }
        if (bestLen < 2) return g.joinToString(":") { it.toString(16) }
        val left = g.take(bestStart).joinToString(":") { it.toString(16) }
        val right = g.drop(bestStart + bestLen).joinToString(":") { it.toString(16) }
        return "$left::$right"
    }

    fun parseV6(input: String): Ipv6Info? {
        val t = input.trim()
        val addrPart = t.substringBefore("/")
        if (!addrPart.contains(":")) return null
        val prefix = if (t.contains("/")) (t.substringAfter("/").toIntOrNull() ?: return null) else 128
        if (prefix !in 0..128) return null
        val ia = try { InetAddress.getByName(addrPart) } catch (e: Exception) { return null }
        val bytes = ia.address
        val bi = BigInteger(1, if (ia is Inet6Address) bytes else return null)
        val g = groupsOf(bi)
        val mask = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE).shiftLeft(128 - prefix)
            .and(BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE))
        val netG = groupsOf(bi.and(mask))
        return Ipv6Info(
            g.joinToString(":") { it.toString(16).padStart(4, '0') }, compressV6(g), classifyV6(bi, g),
            compressV6(netG) + "/" + prefix, prefix
        )
    }

    fun classifyV6(bi: BigInteger, g: IntArray): String = when {
        bi == BigInteger.ZERO -> "Unspecified (::)"
        bi == BigInteger.ONE -> "Loopback (::1)"
        g[0] and 0xFFC0 == 0xFE80 -> "Link-local (fe80::/10)"
        g[0] and 0xFE00 == 0xFC00 -> "Unique local (fc00::/7)"
        g[0] and 0xFF00 == 0xFF00 -> "Multicast (ff00::/8)"
        g[0] == 0x2001 && g[1] == 0x0db8 -> "Documentation (2001:db8::/32)"
        g[0] == 0 && g[1] == 0 && g[2] == 0 && g[3] == 0 && g[4] == 0 && g[5] == 0xFFFF -> "IPv4-mapped (::ffff:0:0/96)"
        g[0] and 0xE000 == 0x2000 -> "Global unicast (2000::/3)"
        else -> "Other / reserved"
    }

    fun splitV6(cidr: String, newPrefix: Int, max: Int = 64): List<String>? {
        val info = parseV6(cidr) ?: return null
        if (newPrefix < info.prefix || newPrefix > 128 || newPrefix - info.prefix > 16) return null
        val ia = InetAddress.getByName(cidr.substringBefore("/"))
        val base = BigInteger(1, ia.address).shiftRight(128 - info.prefix).shiftLeft(128 - info.prefix)
        val count = minOf(1 shl (newPrefix - info.prefix), max)
        val step = BigInteger.ONE.shiftLeft(128 - newPrefix)
        return (0 until count).map { compressV6(groupsOf(base.add(step.multiply(BigInteger.valueOf(it.toLong()))))) + "/" + newPrefix }
    }

    // ---------------- Converters ----------------
    fun formatMac(raw: String): String? {
        val hex = raw.filter { it.isLetterOrDigit() }.lowercase()
        if (hex.length != 12 || !hex.all { it in "0123456789abcdef" }) return null
        return hex.chunked(2).joinToString(":").uppercase()
    }

    fun convertNumber(text: String, radix: Int): BigInteger? =
        try { BigInteger(text.trim().removePrefix("0x").removePrefix("0b"), radix) } catch (e: Exception) { null }
}
