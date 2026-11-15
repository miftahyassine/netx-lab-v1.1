package com.netxlab.app.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.OutputStream
import java.net.*
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

data class PortResult(val port: Int, val status: String, val service: String, val ms: Long, val banner: String?)
data class Hop(val ttl: Int, val ip: String?, val ms: Double?, val reached: Boolean)
data class Device(val ip: String, val host: String?, val mac: String?, val vendor: String?, val ms: Long, val openPorts: List<Int>)
data class SpeedResult(val server: String, val latencyMs: Double, val jitterMs: Double, val downMbps: Double, val upMbps: Double)

object NetTools {
    private val HOST_RE = Regex("^[A-Za-z0-9._:\\-\\[\\]]+$")
    fun validHost(h: String) = h.isNotBlank() && !h.startsWith("-") && HOST_RE.matches(h) && h.length < 254

    // ---------------- Ping (system ping binary) ----------------
    fun ping(host: String, count: Int): Flow<String> = flow {
        require(validHost(host)) { "Invalid host" }
        val p = try {
            ProcessBuilder("/system/bin/ping", "-c", count.toString(), "-W", "2", host).redirectErrorStream(true).start()
        } catch (e: Exception) { null }
        if (p == null) {
            emit("System ping binary unavailable; falling back to Java reachability (TCP echo/ICMP if permitted).")
            repeat(count) { i ->
                val t0 = System.nanoTime()
                val ok = try { InetAddress.getByName(host).isReachable(2000) } catch (e: Exception) { false }
                val ms = (System.nanoTime() - t0) / 1e6
                emit(if (ok) "reply from $host: icmp_seq=${i + 1} time=${"%.1f".format(ms)} ms" else "request timeout icmp_seq=${i + 1}")
                delay(500)
            }
        } else {
            try {
                p.inputStream.bufferedReader().use { r -> while (true) { val l = r.readLine() ?: break; emit(l) } }
                p.waitFor()
            } finally { p.destroy() }
        }
    }.flowOn(Dispatchers.IO)

    private val TIME_RE = Regex("time[=<]\\s*([0-9.]+)\\s*ms")
    fun pingTimes(lines: List<String>): List<Double> = lines.mapNotNull { TIME_RE.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

    fun pingSummary(times: List<Double>, count: Int): String {
        if (times.isEmpty()) return "Summary: 0/$count replies, 100% packet loss"
        val loss = (count - times.size) * 100.0 / count
        return "Summary: ${times.size}/$count replies, %.0f%% loss, min %.1f / avg %.1f / max %.1f ms".format(loss, times.min(), times.average(), times.max())
    }

    // ---------------- Traceroute (ping with increasing TTL) ----------------
    private val IP_RE = Regex("(?:[Ff]rom|bytes from)\\s+\\(?([0-9a-fA-F.:]+)\\)?")
    fun traceroute(host: String, maxHops: Int = 20): Flow<Hop> = flow {
        require(validHost(host)) { "Invalid host" }
        for (ttl in 1..maxHops) {
            val t0 = System.nanoTime()
            val text = try {
                val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-t", ttl.toString(), "-W", "2", host).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText(); p.waitFor(); p.destroy(); out
            } catch (e: Exception) { throw IllegalStateException("System ping binary not usable on this device: ${e.message}") }
            val wall = (System.nanoTime() - t0) / 1e6
            val reached = text.contains("bytes from")
            val ip = IP_RE.find(text)?.groupValues?.get(1)?.trimEnd(':')
            val ms = pingTimes(text.lines()).firstOrNull() ?: if (ip != null) wall else null
            emit(Hop(ttl, ip, ms, reached))
            if (reached) break
        }
    }.flowOn(Dispatchers.IO)

    // ---------------- Port scanner ----------------
    val COMMON_PORTS = listOf(20, 21, 22, 23, 25, 53, 80, 110, 123, 143, 443, 445, 3306, 3389, 5432, 8080, 8443)
    private val SERVICES = mapOf(20 to "FTP-data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 80 to "HTTP",
        110 to "POP3", 123 to "NTP", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB", 3306 to "MySQL", 3389 to "RDP", 5432 to "PostgreSQL",
        8080 to "HTTP-alt", 8443 to "HTTPS-alt")

    fun parsePorts(s: String): List<Int> {
        val out = linkedSetOf<Int>()
        for (part in s.split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }) {
            if (part.contains("-")) {
                val a = part.substringBefore("-").toIntOrNull(); val b = part.substringAfter("-").toIntOrNull()
                if (a != null && b != null && a in 1..65535 && b in a..65535) for (p in a..b) { out += p; if (out.size >= 10000) return out.toList() }
            } else part.toIntOrNull()?.takeIf { it in 1..65535 }?.let { out += it }
        }
        return out.toList()
    }

    private fun guess(port: Int, banner: String?): String {
        val b = banner ?: ""
        return when {
            b.startsWith("SSH-") -> "SSH (banner)"
            b.contains("HTTP/") -> "HTTP (banner)"
            b.startsWith("220") && b.contains("FTP", true) -> "FTP (banner)"
            b.startsWith("220") -> "SMTP/FTP (banner)"
            b.startsWith("+OK") -> "POP3 (banner)"
            b.startsWith("* OK") -> "IMAP (banner)"
            else -> (SERVICES[port] ?: "unknown") + " (port-based guess)"
        }
    }

    fun portScan(host: String, ports: List<Int>, timeoutMs: Int, concurrency: Int): Flow<PortResult> = channelFlow {
        require(validHost(host)) { "Invalid host" }
        val addr = InetAddress.getByName(host)
        val sem = Semaphore(concurrency.coerceIn(1, 256))
        for (port in ports) launch(Dispatchers.IO) { sem.withPermit { send(probe(addr, port, timeoutMs)) } }
    }

    private fun probe(addr: InetAddress, port: Int, timeout: Int): PortResult {
        val t0 = System.nanoTime()
        val s = Socket()
        return try {
            s.connect(InetSocketAddress(addr, port), timeout)
            val ms = (System.nanoTime() - t0) / 1_000_000
            var banner: String? = null
            try {
                s.soTimeout = 500
                if (port == 80 || port == 8080) s.getOutputStream().write("HEAD / HTTP/1.0\r\n\r\n".toByteArray())
                val buf = ByteArray(128); val n = s.getInputStream().read(buf)
                if (n > 0) banner = String(buf, 0, n, Charsets.ISO_8859_1).replace(Regex("[^\\x20-\\x7E\\r\\n]"), ".").trim().lineSequence().firstOrNull()
            } catch (_: Exception) {}
            PortResult(port, "open", guess(port, banner), ms, banner)
        } catch (e: SocketTimeoutException) {
            PortResult(port, "filtered (timeout)", SERVICES[port] ?: "-", (System.nanoTime() - t0) / 1_000_000, null)
        } catch (e: ConnectException) {
            val closed = e.message?.contains("refused", true) == true
            PortResult(port, if (closed) "closed" else "inconclusive", SERVICES[port] ?: "-", (System.nanoTime() - t0) / 1_000_000, if (closed) null else e.message)
        } catch (e: Exception) {
            PortResult(port, "inconclusive", SERVICES[port] ?: "-", (System.nanoTime() - t0) / 1_000_000, e.message)
        } finally { try { s.close() } catch (_: Exception) {} }
    }

    // ---------------- LAN discovery ----------------
    private val PROBE_PORTS = listOf(80, 443, 22, 445, 53, 8080, 139)

    fun lanScan(ip: String, prefix: Int, timeoutMs: Int): Flow<Device> = channelFlow {
        val base = IpCalc.parseV4(ip) ?: return@channelFlow
        val p = if (prefix < 22) 22 else prefix   // cap at 1022 hosts
        val net = base and IpCalc.maskFor(p)
        val count = (1L shl (32 - p)) - 2
        val sem = Semaphore(64)
        for (i in 1..count) {
            val target = IpCalc.fmtV4(net + i)
            launch(Dispatchers.IO) { sem.withPermit { discover(target, timeoutMs)?.let { send(it) } } }
        }
    }

    private fun discover(ip: String, timeout: Int): Device? {
        val addr = InetAddress.getByName(ip)
        val t0 = System.nanoTime()
        var alive = false
        val open = mutableListOf<Int>()
        try { if (addr.isReachable(timeout)) alive = true } catch (_: Exception) {}
        for (port in PROBE_PORTS) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(addr, port), if (alive) 250 else (timeout / 2).coerceAtLeast(200))
                alive = true; open += port
            } catch (e: ConnectException) { if (e.message?.contains("refused", true) == true) alive = true }
            catch (_: Exception) {} finally { try { s.close() } catch (_: Exception) {} }
        }
        if (!alive) return null
        val ms = (System.nanoTime() - t0) / 1_000_000
        val host = try { addr.canonicalHostName.takeIf { it != ip } } catch (_: Exception) { null }
        val mac = NetInfo.arpTable()[ip]
        return Device(ip, host, mac, mac?.let { Oui.describe(it) }, ms, open)
    }

    // ---------------- Connectivity ----------------
    suspend fun tcpTime(host: String, port: Int, timeout: Int = 2000): Long? = withContext(Dispatchers.IO) {
        val s = Socket(); val t0 = System.nanoTime()
        try { s.connect(InetSocketAddress(host, port), timeout); (System.nanoTime() - t0) / 1_000_000 }
        catch (_: Exception) { null } finally { try { s.close() } catch (_: Exception) {} }
    }

    // ---------------- Speed test (Cloudflare speed endpoints) ----------------
    suspend fun speedTest(onProgress: (String) -> Unit): SpeedResult = withContext(Dispatchers.IO) {
        val base = "https://speed.cloudflare.com"
        var colo: String? = null
        onProgress("Measuring latency…")
        val lat = mutableListOf<Double>()
        repeat(8) {
            val c = URL("$base/__down?bytes=0").openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000; c.useCaches = false
            val t0 = System.nanoTime()
            c.responseCode; c.inputStream.use { it.readBytes() }
            lat += (System.nanoTime() - t0) / 1e6
            colo = colo ?: c.getHeaderField("cf-meta-colo"); c.disconnect()
        }
        val jitter = if (lat.size > 1) lat.zipWithNext { a, b -> abs(a - b) }.average() else 0.0
        onProgress("Measuring download…")
        val dc = URL("$base/__down?bytes=25000000").openConnection() as HttpURLConnection
        dc.connectTimeout = 5000; dc.readTimeout = 10000; dc.useCaches = false
        var bytes = 0L; val dt0 = System.nanoTime()
        dc.inputStream.use { ins ->
            val buf = ByteArray(65536)
            while (true) {
                val n = ins.read(buf); if (n < 0) break; bytes += n
                if ((System.nanoTime() - dt0) > 8_000_000_000L) break
            }
        }
        dc.disconnect()
        val down = bytes * 8 / ((System.nanoTime() - dt0) / 1e9) / 1e6
        onProgress("Measuring upload…")
        val size = 8_000_000
        val uc = URL("$base/__up").openConnection() as HttpURLConnection
        uc.requestMethod = "POST"; uc.doOutput = true; uc.setFixedLengthStreamingMode(size)
        uc.connectTimeout = 5000; uc.readTimeout = 15000
        val chunk = Random.nextBytes(65536); val ut0 = System.nanoTime()
        uc.outputStream.use { o: OutputStream -> var sent = 0; while (sent < size) { val n = minOf(chunk.size, size - sent); o.write(chunk, 0, n); sent += n } }
        uc.responseCode; uc.inputStream.use { it.readBytes() }
        val up = size * 8 / ((System.nanoTime() - ut0) / 1e9) / 1e6
        uc.disconnect()
        SpeedResult("speed.cloudflare.com${colo?.let { " (colo $it)" } ?: ""}", lat.average(), jitter, down, up)
    }

    // ---------------- HTTP / TLS inspection ----------------
    val SECURITY_HEADERS = listOf("strict-transport-security", "content-security-policy", "x-frame-options",
        "x-content-type-options", "referrer-policy", "permissions-policy")

    suspend fun httpInspect(url: String): String = withContext(Dispatchers.IO) {
        val u = URL(if (url.contains("://")) url else "https://$url")
        val sb = StringBuilder()
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 6000; c.instanceFollowRedirects = false; c.requestMethod = "GET"
        val t0 = System.nanoTime()
        val code = c.responseCode
        sb.appendLine("URL: $u"); sb.appendLine("Status: $code ${c.responseMessage ?: ""}  (${(System.nanoTime() - t0) / 1_000_000} ms)")
        sb.appendLine("\n-- Response headers --")
        val lower = mutableMapOf<String, String>()
        c.headerFields.forEach { (k, v) -> if (k != null) { sb.appendLine("$k: ${v.joinToString(", ")}"); lower[k.lowercase()] = v.joinToString(", ") } }
        sb.appendLine("\n-- Security header check --")
        SECURITY_HEADERS.forEach { sb.appendLine((if (lower.containsKey(it)) "[present] " else "[missing] ") + it) }
        if (c is HttpsURLConnection) {
            sb.appendLine("\n-- TLS --"); sb.appendLine("Cipher suite: ${c.cipherSuite}")
            sb.appendLine(certInfo(c.serverCertificates.filterIsInstance<X509Certificate>()))
        }
        c.disconnect(); sb.toString()
    }

    fun certInfo(chain: List<X509Certificate>): String = buildString {
        chain.forEachIndexed { i, x ->
            appendLine("Certificate #$i")
            appendLine("  Subject: ${x.subjectX500Principal.name}")
            appendLine("  Issuer:  ${x.issuerX500Principal.name}")
            appendLine("  Valid:   ${x.notBefore} → ${x.notAfter}")
            val days = (x.notAfter.time - System.currentTimeMillis()) / 86_400_000
            appendLine("  Expires in: $days days${if (days < 0) " (EXPIRED)" else ""}")
            appendLine("  Sig alg: ${x.sigAlgName}")
            val sans = try { x.subjectAlternativeNames?.joinToString(", ") { it[1].toString() } } catch (_: Exception) { null }
            if (!sans.isNullOrEmpty()) appendLine("  SANs: $sans")
            appendLine("  SHA-256: " + MessageDigest.getInstance("SHA-256").digest(x.encoded).joinToString(":") { "%02X".format(it) })
        }
    }

    // ---------------- WHOIS (TCP 43) ----------------
    suspend fun whois(query: String): String = withContext(Dispatchers.IO) {
        fun ask(server: String, q: String): String {
            Socket().use { s ->
                s.connect(InetSocketAddress(server, 43), 5000); s.soTimeout = 8000
                s.getOutputStream().write("$q\r\n".toByteArray()); s.getOutputStream().flush()
                return s.getInputStream().bufferedReader().readText()
            }
        }
        val first = ask("whois.iana.org", query)
        val refer = Regex("(?im)^refer:\\s*(\\S+)").find(first)?.groupValues?.get(1)
        if (refer != null) "Referred by IANA to $refer\n\n" + ask(refer, query) else first
    }
}
