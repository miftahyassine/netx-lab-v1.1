@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.netxlab.app.core.*
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.data.SavedHost
import com.netxlab.app.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Last LAN scan, shared with the topology screen. */
object ScanStore {
    var devices by mutableStateOf<List<Device>>(emptyList())
    var local by mutableStateOf<LocalNet?>(null)
}

private val TABS = listOf("Ping", "Traceroute", "Ports", "LAN Scan", "DNS", "IP Toolkit", "Speed", "HTTP/TLS", "Connectivity")

@Composable
fun ToolsScreen(vm: AppViewModel, initialTab: Int) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, TABS.size - 1)) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> PingTab(vm); 1 -> TraceTab(vm); 2 -> PortsTab(vm); 3 -> LanTab(vm); 4 -> DnsTab(vm)
            5 -> IpTab(); 6 -> SpeedTab(vm); 7 -> HttpTab(); 8 -> ConnectivityTab()
        }
    }
}

@Composable
private fun PingTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("1.1.1.1") }; var count by remember { mutableStateOf("5") }
    var out by remember { mutableStateOf("") }; var job by remember { mutableStateOf<Job?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    ToolColumn {
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("Ping", "Uses the system ping binary (real ICMP). If it is blocked on your device, a Java reachability check is used instead.") }
        Field(host, { host = it }, "Hostname or IP"); Field(count, { count = it.filter(Char::isDigit).take(3) }, "Packet count")
        ErrorText(err)
        Button(onClick = {
            if (job?.isActive == true) { job?.cancel(); return@Button }
            val n = (count.toIntOrNull() ?: 4).coerceIn(1, 100); out = ""; err = null
            if (!NetTools.validHost(host)) { err = "Invalid host."; return@Button }
            job = scope.launch {
                val lines = mutableListOf<String>()
                try {
                    NetTools.ping(host, n).collect { lines += it; out = lines.joinToString("\n") }
                    val s = NetTools.pingSummary(NetTools.pingTimes(lines), n)
                    out += "\n\n$s"; vm.save("Ping", host, s)
                } catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) err = e.message }
            }
        }) { Text(if (job?.isActive == true) "Stop" else "Start ping") }
        OutputBox(out)
    }
}

@Composable
private fun TraceTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("1.1.1.1") }
    val hops = remember { mutableStateListOf<Hop>() }
    var job by remember { mutableStateOf<Job?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    ToolColumn {
        SectionTitle("Traceroute", "Sends pings with increasing TTL. Hops that do not answer (firewalls, rate limits) are shown as * and are not necessarily down.")
        Field(host, { host = it }, "Hostname or IP"); ErrorText(err)
        Button(onClick = {
            if (job?.isActive == true) { job?.cancel(); return@Button }
            hops.clear(); err = null
            if (!NetTools.validHost(host)) { err = "Invalid host."; return@Button }
            job = scope.launch {
                try { NetTools.traceroute(host).collect { hops += it }
                    vm.save("Traceroute", host, "${hops.size} hops, ${if (hops.lastOrNull()?.reached == true) "destination reached" else "destination not reached"}", hops.joinToString("\n") { "${it.ttl} ${it.ip ?: "*"}" })
                } catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) err = e.message }
            }
        }) { Text(if (job?.isActive == true) "Stop" else "Trace") }
        OutputBox(hops.joinToString("\n") { "%2d  %-40s %s".format(it.ttl, it.ip ?: "*  (no response)", it.ms?.let { m -> "%.1f ms".format(m) } ?: "") })
    }
}

@Composable
private fun PortsTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    var host by remember { mutableStateOf("") }; var ports by remember { mutableStateOf(NetTools.COMMON_PORTS.joinToString(",")) }
    var timeout by remember { mutableStateOf("1500") }; var conc by remember { mutableFloatStateOf(32f) }
    var ok by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<PortResult>() }; var job by remember { mutableStateOf<Job?>(null) }
    ToolColumn {
        SectionTitle("TCP port scanner", "TCP connect scan. 'open' = connection accepted, 'closed' = refused, 'filtered' = timeout (usually a firewall). Service names are guesses unless a banner was received.")
        Field(host, { host = it }, "Authorized host / IP")
        Field(ports, { ports = it }, "Ports (e.g. 22,80,8000-8100)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { ports = NetTools.COMMON_PORTS.joinToString(",") }, label = { Text("Common") })
            AssistChip(onClick = { ports = "1-1024" }, label = { Text("1-1024") })
            AssistChip(onClick = { ports = "80,443" }, label = { Text("Web") })
        }
        Field(timeout, { timeout = it.filter(Char::isDigit).take(5) }, "Timeout (ms)")
        Text("Concurrency: ${conc.toInt()}"); Slider(conc, { conc = it }, valueRange = 1f..128f)
        ScopeConfirm(ok) { ok = it }; ErrorText(err)
        Button(enabled = ok, onClick = {
            if (job?.isActive == true) { job?.cancel(); return@Button }
            err = null; results.clear()
            val list = NetTools.parsePorts(ports)
            if (!NetTools.validHost(host)) { err = "Enter a valid host."; return@Button }
            if (list.isEmpty()) { err = "No valid ports."; return@Button }
            job = scope.launch {
                try {
                    NetTools.portScan(host, list, timeout.toIntOrNull() ?: settings.timeoutMs, conc.toInt()).collect { results += it }
                    val open = results.filter { it.status == "open" }.map { it.port }.sorted()
                    vm.save("Port scan", host, "${list.size} ports scanned, open: ${open.joinToString().ifEmpty { "none" }}", results.sortedBy { it.port }.joinToString("\n") { "${it.port} ${it.status} ${it.service}" })
                } catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) err = e.message }
            }
        }) { Text(if (job?.isActive == true) "Stop" else "Scan") }
        val shown = results.sortedBy { it.port }
        OutputBox(shown.joinToString("\n") { "%-6d TCP  %-18s %-26s %d ms".format(it.port, it.status, it.service, it.ms) }.let { if (it.isNotEmpty()) "PORT   PROTO STATUS             SERVICE                    TIME\n$it" else it }, "Results (${shown.count { it.status == "open" }} open)")
    }
}

@Composable
private fun LanTab(vm: AppViewModel) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState(); val hosts by vm.hosts.collectAsState(); val records by vm.records.collectAsState()
    var job by remember { mutableStateOf<Job?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    val found = remember { mutableStateListOf<Device>() }
    var edit by remember { mutableStateOf<Device?>(null) }
    var cmpA by remember { mutableStateOf<Long?>(null) }; var cmpOut by remember { mutableStateOf("") }
    ToolColumn {
        SectionTitle("Network discovery", "Probes every address in your own subnet with ICMP/TCP, resolves names and reads the ARP cache if Android allows it. MAC addresses are usually hidden on Android 10+, and many phones use randomized MACs, so identification is often incomplete.")
        ErrorText(err)
        Button(onClick = {
            if (job?.isActive == true) { job?.cancel(); return@Button }
            val n = NetInfo.localNet(ctx); ScanStore.local = n
            if (n.ip == null || n.prefix == null) { err = "No IPv4 network detected. Connect to Wi-Fi first."; return@Button }
            err = null; found.clear()
            job = scope.launch {
                try {
                    NetTools.lanScan(n.ip, n.prefix, settings.timeoutMs).collect { found += it; ScanStore.devices = found.toList() }
                    ScanStore.devices = found.sortedBy { IpCalc.parseV4(it.ip) }
                    vm.save("LAN scan", "${n.ip}/${n.prefix}", "${found.size} devices responded", found.joinToString("\n") { it.ip })
                } catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) err = e.message }
            }
        }) { Text(if (job?.isActive == true) "Stop (${found.size} found)" else "Scan my network") }
        found.sortedBy { IpCalc.parseV4(it.ip) }.forEach { d ->
            val saved = hosts.firstOrNull { it.address == d.ip }
            NxCard {
                Text((saved?.alias?.ifBlank { null } ?: d.host ?: d.ip) + if (saved?.favorite == true) "  ★" else "", style = MaterialTheme.typography.titleMedium)
                KeyValue("IP", d.ip); KeyValue("Hostname", d.host); KeyValue("MAC", d.mac ?: "not available (Android restriction)"); KeyValue("Vendor", d.vendor)
                KeyValue("Response", "${d.ms} ms"); KeyValue("Open ports", d.openPorts.joinToString().ifEmpty { "none among probed" })
                saved?.note?.takeIf { it.isNotBlank() }?.let { KeyValue("Note", it) }
                TextButton(onClick = { edit = d }) { Text("Rename / note / favorite") }
            }
        }
        if (found.isNotEmpty()) OutputBox(found.sortedBy { IpCalc.parseV4(it.ip) }.joinToString("\n") { "${it.ip},${it.host ?: ""},${it.mac ?: ""},${it.ms}ms,${it.openPorts.joinToString("/")}" }, "Export (CSV)")

        val lanRecords = records.filter { it.type == "LAN scan" }
        if (lanRecords.size >= 2) {
            SectionTitle("Compare scans", "Pick an older scan, then a newer one.")
            lanRecords.take(8).forEach { r ->
                val label = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(r.timestamp)) + " · ${r.summary}"
                OutlinedButton(onClick = {
                    val a = cmpA
                    if (a == null) { cmpA = r.id; cmpOut = "Selected first scan. Now pick the second one." }
                    else { val ra = lanRecords.first { it.id == a }; val rb = r
                        val sa = ra.data.lines().filter { it.isNotBlank() }.toSet(); val sb = rb.data.lines().filter { it.isNotBlank() }.toSet()
                        cmpOut = "Only in first: ${(sa - sb).joinToString().ifEmpty { "none" }}\nOnly in second: ${(sb - sa).joinToString().ifEmpty { "none" }}\nIn both: ${(sa intersect sb).size}"; cmpA = null }
                }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
            OutputBox(cmpOut, "Comparison")
        }
    }
    edit?.let { d ->
        val s = hosts.firstOrNull { it.address == d.ip }
        var alias by remember(d) { mutableStateOf(s?.alias ?: "") }; var note by remember(d) { mutableStateOf(s?.note ?: "") }; var fav by remember(d) { mutableStateOf(s?.favorite ?: false) }
        AlertDialog(onDismissRequest = { edit = null },
            confirmButton = { TextButton(onClick = { vm.saveHost((s ?: SavedHost(address = d.ip)).copy(alias = alias, note = note, favorite = fav)); edit = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
            title = { Text(d.ip) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(alias, { alias = it }, "Local name"); Field(note, { note = it }, "Note")
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fav, { fav = it }); Text("Favorite") } } })
    }
}

@Composable
private fun DnsTab(vm: AppViewModel) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("example.com") }; var type by remember { mutableStateOf("A") }
    var server by remember { mutableStateOf(NetInfo.localNet(ctx).dns.firstOrNull() ?: "1.1.1.1") }
    var out by remember { mutableStateOf("") }; var raw by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    ToolColumn {
        SectionTitle("DNS lookup", "Sends a real DNS query over UDP port 53 (falls back to TCP if truncated) directly to the server you choose. For PTR enter an IP address; it is converted to in-addr.arpa / ip6.arpa automatically.")
        Field(name, { name = it }, "Name or IP (for PTR)")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DnsClient.TYPES.keys.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(t) }) }
        }
        Field(server, { server = it }, "DNS server")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9").forEach { s -> AssistChip(onClick = { server = s }, label = { Text(s) }) }
        }
        ErrorText(err)
        Button(enabled = !busy, onClick = {
            busy = true; err = null; out = ""
            scope.launch {
                try {
                    val r = DnsClient.query(name.trim(), type, server.trim(), 3000)
                    out = DnsClient.format(r); raw = r.rawHex
                    vm.save("DNS", "$name $type", "${r.rcode}, ${r.answers.size} answers, ${r.timeMs} ms")
                } catch (e: java.net.SocketTimeoutException) { err = "Timeout: $server did not answer (blocked, unreachable or not a DNS server)."
                } catch (e: Exception) { err = "Query failed: ${e.message}" }
                busy = false
            }
        }) { Text(if (busy) "Querying…" else "Query") }
        OutputBox(out, "Response")
        OutputBox(raw, "Raw response (hex)")
        NxCard {
            SectionTitle("Troubleshooting hints")
            Text("• NXDOMAIN: the name does not exist.\n• SERVFAIL: the server could not resolve it (DNSSEC or upstream problem).\n• REFUSED: the server does not allow your query.\n• Timeout: port 53 blocked or server down. Try another server.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun IpTab() {
    var v4 by remember { mutableStateOf("192.168.1.10/24") }; var splitTo by remember { mutableStateOf("26") }
    var v6 by remember { mutableStateOf("2001:db8::1/64") }
    var vBase by remember { mutableStateOf("10.0.0.0/24") }; var vNeeds by remember { mutableStateOf("100,50,20,2") }
    var num by remember { mutableStateOf("255") }; var radix by remember { mutableIntStateOf(10) }
    var mac by remember { mutableStateOf("aabbccddeeff") }
    ToolColumn {
        SectionTitle("IPv4 / subnet calculator", "Accepts 192.168.1.10/24 or 192.168.1.10 255.255.255.0. All calculations run locally.")
        Field(v4, { v4 = it }, "Address/CIDR or address + mask")
        val i = IpCalc.calc(v4)
        if (i == null) ErrorText("Invalid input") else {
            OutputBox("Address:    ${i.address}\nMask:       ${i.mask} (/${i.cidr})\nWildcard:   ${i.wildcard}\nNetwork:    ${i.network}\nBroadcast:  ${i.broadcast}\nFirst host: ${i.first}\nLast host:  ${i.last}\nHosts:      ${i.hosts}\nBinary:     ${i.binary}", "IPv4 result")
            Field(splitTo, { splitTo = it.filter(Char::isDigit).take(2) }, "Split into /prefix")
            val parts = splitTo.toIntOrNull()?.let { IpCalc.split(v4, it) }
            OutputBox(parts?.joinToString("\n") ?: "", "Subnets (max 256 shown)")
        }
        SectionTitle("VLSM planner", "Allocates the largest requirements first, aligned on subnet boundaries.")
        Field(vBase, { vBase = it }, "Base network"); Field(vNeeds, { vNeeds = it }, "Hosts needed (comma separated)")
        val vl = IpCalc.vlsm(vBase, vNeeds.split(",").mapNotNull { it.trim().toIntOrNull() })
        OutputBox(vl?.joinToString("\n") { "need ${it.need}: ${it.cidr}  usable ${it.usable}  ${it.range}" } ?: "Does not fit / invalid input", "VLSM plan")
        SectionTitle("IPv6")
        Field(v6, { v6 = it }, "IPv6 address[/prefix]")
        val p6 = IpCalc.parseV6(v6)
        if (p6 == null) ErrorText("Invalid IPv6 address") else {
            OutputBox("Expanded:   ${p6.expanded}\nCompressed: ${p6.compressed}\nType:       ${p6.type}\nNetwork:    ${p6.network}", "IPv6 result")
            val sub = if (p6.prefix <= 120) IpCalc.splitV6(v6, minOf(128, p6.prefix + 4), 16) else null
            OutputBox(sub?.joinToString("\n") ?: "", "First subnets at prefix+4")
        }
        SectionTitle("Converters")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(10 to "DEC", 2 to "BIN", 16 to "HEX").forEach { (r, l) -> FilterChip(radix == r, { radix = r }, label = { Text(l) }) } }
        Field(num, { num = it }, "Number")
        val bi = IpCalc.convertNumber(num, radix)
        OutputBox(if (bi == null) "Invalid number for this base" else "DEC: ${bi.toString(10)}\nBIN: ${bi.toString(2)}\nHEX: ${bi.toString(16).uppercase()}", "Number conversion")
        IpCalc.parseV4(num)?.let { OutputBox(IpCalc.toBinary(it), "IPv4 → binary") }
        Field(mac, { mac = it }, "MAC address (any format)")
        OutputBox(IpCalc.formatMac(mac)?.let { "$it\n${it.replace(":", "-")}\n${it.replace(":", "").lowercase().chunked(4).joinToString(".")}" } ?: "Invalid MAC", "MAC formats")
    }
}

@Composable
private fun SpeedTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }; var result by remember { mutableStateOf<SpeedResult?>(null) }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    ToolColumn {
        SectionTitle("Speed test", "Uses Cloudflare's public speed endpoints (speed.cloudflare.com). Results depend on the test server, a single connection, your device and the time of day. Roughly 25 MB down and 8 MB up are transferred (mobile data!).")
        Button(enabled = !busy, onClick = {
            busy = true; err = null; result = null
            scope.launch {
                try { val r = NetTools.speedTest { status = it }; result = r; status = "Done"
                    vm.save("Speed test", r.server, "↓ %.1f Mbps ↑ %.1f Mbps, latency %.0f ms".format(r.downMbps, r.upMbps, r.latencyMs))
                } catch (e: Exception) { err = "Test failed: ${e.message}" }
                busy = false
            }
        }) { Text(if (busy) status else "Start speed test") }
        ErrorText(err)
        result?.let { r -> NxCard {
            KeyValue("Server", r.server); KeyValue("Download", "%.1f Mbps".format(r.downMbps)); KeyValue("Upload", "%.1f Mbps".format(r.upMbps))
            KeyValue("Latency", "%.0f ms".format(r.latencyMs)); KeyValue("Jitter", "%.1f ms".format(r.jitterMs))
            Text("Limitation: single-stream HTTP test of limited size; may under-report fast links.", style = MaterialTheme.typography.labelSmall)
        } }
    }
}

@Composable
private fun HttpTab() {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://example.com") }; var out by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    ToolColumn {
        SectionTitle("HTTP & TLS inspector", "Fetches the URL once (no redirects followed), lists response headers, checks common security headers and shows the TLS certificate chain presented by the server.")
        Field(url, { url = it }, "URL"); ErrorText(err)
        Button(enabled = !busy, onClick = {
            busy = true; err = null; out = ""
            scope.launch { try { out = NetTools.httpInspect(url.trim()) } catch (e: Exception) { err = "${e.javaClass.simpleName}: ${e.message}" }; busy = false }
        }) { Text(if (busy) "Working…" else "Inspect") }
        OutputBox(out)
    }
}

@Composable
private fun ConnectivityTab() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var out by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    ToolColumn {
        SectionTitle("Internet connectivity", "Checks Android's validated-connectivity flag, DNS, gateway, latency stability and IPv4 vs IPv6 reachability. Gateway tests use TCP and may fail on routers that reject all probes.")
        Button(enabled = !busy, onClick = {
            busy = true; out = ""
            scope.launch {
                val n = NetInfo.localNet(ctx); val sb = StringBuilder()
                sb.appendLine("Transport: ${n.transport}; validated Internet: ${n.validated}")
                sb.appendLine("Local IP: ${n.ip}/${n.prefix}  gateway: ${n.gateway}  MTU: ${n.mtu}")
                val gw = n.gateway
                if (gw != null) {
                    val t = listOf(80, 443, 53).firstNotNullOfOrNull { NetTools.tcpTime(gw, it, 1000) }
                    sb.appendLine("Gateway reachable (TCP 80/443/53): ${if (t != null) "yes, $t ms" else "no TCP answer (may still be up)"}")
                }
                val dns = n.dns.firstOrNull()
                if (dns != null) try { val r = DnsClient.query("example.com", "A", dns, 2500); sb.appendLine("DNS $dns: ${r.rcode} in ${r.timeMs} ms") } catch (e: Exception) { sb.appendLine("DNS $dns: failed (${e.javaClass.simpleName})") }
                val samples = (1..10).mapNotNull { NetTools.tcpTime("1.1.1.1", 443, 2000) }
                if (samples.isEmpty()) sb.appendLine("Internet (1.1.1.1:443): unreachable")
                else { val avg = samples.average(); val sd = kotlin.math.sqrt(samples.sumOf { (it - avg) * (it - avg) } / samples.size)
                    sb.appendLine("Internet latency: avg %.0f ms, min ${samples.min()}, max ${samples.max()}, std dev %.1f ms, loss ${(10 - samples.size) * 10}%%".format(avg, sd)) }
                val v6 = NetTools.tcpTime("2606:4700:4700::1111", 443, 2500)
                sb.appendLine("IPv4: ${if (samples.isNotEmpty()) "ok" else "fail"}   IPv6: ${if (v6 != null) "ok ($v6 ms)" else "no IPv6 connectivity"}")
                out = sb.toString(); busy = false
            }
        }) { Text(if (busy) "Testing…" else "Run checks") }
        OutputBox(out)
    }
}
