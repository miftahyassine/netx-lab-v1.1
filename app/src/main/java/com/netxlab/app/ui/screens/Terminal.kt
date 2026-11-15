@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.netxlab.app.core.*
import com.netxlab.app.ui.InfoIcon
import com.netxlab.app.ui.copyText
import com.netxlab.app.ui.theme.Mono
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val COMMANDS = listOf("help", "ip", "ifconfig", "ping", "traceroute", "nslookup", "dns", "scan", "ports", "wifi", "subnet", "route", "clear", "history")

private class CmdHighlight(val cmd: Color, val num: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val a = buildAnnotatedString {
            var i = 0
            Regex("\\S+|\\s+").findAll(s).forEach { m ->
                val t = m.value
                val style = when {
                    t.isBlank() -> SpanStyle()
                    m.range.first == s.indexOfFirst { !it.isWhitespace() } -> SpanStyle(color = cmd, fontWeight = FontWeight.Bold)
                    t.startsWith("-") -> SpanStyle(color = Color(0xFFFFB74D))
                    t.any { it.isDigit() } -> SpanStyle(color = num)
                    else -> SpanStyle()
                }
                withStyle(style) { append(t) }; i += t.length
            }
        }
        return TransformedText(a, OffsetMapping.Identity)
    }
}

@Composable
fun TerminalScreen() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf("NETX LAB terminal — internal command interpreter, NOT a Linux/Kali shell.", "Type 'help' for commands.") }
    val history = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }; var hIdx by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }

    fun out(s: String) { s.lines().forEach { lines += it } }

    suspend fun exec(line: String) {
        val p = line.trim().split(Regex("\\s+")); val cmd = p[0].lowercase(); val a = p.drop(1)
        when (cmd) {
            "help" -> out("""help               this list
ip | ifconfig      interfaces and addresses
ping <host> [n]    ICMP echo (system ping)
traceroute <host>  TTL-based route trace
nslookup|dns <name> [type] [server]
scan               ping/TCP sweep of your own subnet
ports <host> [list] --authorized   TCP connect scan (needs --authorized)
wifi               current Wi-Fi connection
subnet <cidr>      subnet calculator
route              routes of the active network
history | clear""")
            "clear" -> lines.clear()
            "history" -> history.forEachIndexed { i, h -> out("${i + 1}  $h") }
            "ip", "ifconfig" -> out(NetInfo.interfaces().trimEnd())
            "route" -> NetInfo.localNet(ctx).routes.forEach { out(it) }
            "wifi" -> { val w = NetInfo.wifi(ctx)
                if (!w.connected) out("Not connected to Wi-Fi") else out("SSID ${w.ssid ?: "(hidden by Android)"}\nBSSID ${w.bssid ?: "(hidden)"}\nRSSI ${w.rssi} dBm  freq ${w.freq} MHz  ch ${w.freq?.let { WifiTools.channelOf(it) }}\nlink ${w.linkSpeed} Mbps  security ${w.security ?: "?"}") }
            "subnet" -> { val i = IpCalc.calc(a.joinToString(" ")); if (i == null) out("usage: subnet 192.168.1.10/24") else
                out("network ${i.network}/${i.cidr}\nmask ${i.mask}\nbroadcast ${i.broadcast}\nrange ${i.first} - ${i.last}\nhosts ${i.hosts}") }
            "ping" -> { val h = a.getOrNull(0); if (h == null || !NetTools.validHost(h)) out("usage: ping <host> [count]") else {
                val n = (a.getOrNull(1)?.toIntOrNull() ?: 4).coerceIn(1, 50); val got = mutableListOf<String>()
                NetTools.ping(h, n).collect { got += it; out(it) }; out(NetTools.pingSummary(NetTools.pingTimes(got), n)) } }
            "traceroute" -> { val h = a.getOrNull(0); if (h == null || !NetTools.validHost(h)) out("usage: traceroute <host>") else
                NetTools.traceroute(h).collect { out("%2d  %s  %s".format(it.ttl, it.ip ?: "*", it.ms?.let { m -> "%.1f ms".format(m) } ?: "")) } }
            "nslookup", "dns" -> { val n = a.getOrNull(0); if (n == null) out("usage: dns <name> [type] [server]") else {
                val t = (a.getOrNull(1) ?: "A").uppercase(); val s = a.getOrNull(2) ?: NetInfo.localNet(ctx).dns.firstOrNull() ?: "1.1.1.1"
                if (t !in DnsClient.TYPES) out("types: ${DnsClient.TYPES.keys.joinToString()}") else out(DnsClient.format(DnsClient.query(n, t, s)).trimEnd()) } }
            "scan" -> { val n = NetInfo.localNet(ctx); if (n.ip == null || n.prefix == null) out("No IPv4 network") else {
                out("Scanning ${n.ip}/${n.prefix} …"); var c = 0
                NetTools.lanScan(n.ip, n.prefix, 1200).collect { c++; out("${it.ip}  ${it.host ?: ""}  ${it.mac ?: ""}  ${it.ms} ms  ports ${it.openPorts.joinToString("/")}") }
                out("$c devices responded") } }
            "ports" -> { val h = a.getOrNull(0)
                if (h == null || !NetTools.validHost(h)) out("usage: ports <host> [ports] --authorized")
                else if ("--authorized" !in a) out("Refusing: add --authorized to confirm you own or may test $h")
                else { val spec = a.drop(1).firstOrNull { !it.startsWith("--") }
                    val list = if (spec == null) NetTools.COMMON_PORTS else NetTools.parsePorts(spec)
                    val res = mutableListOf<PortResult>(); NetTools.portScan(h, list, 1500, 32).collect { res += it }
                    res.sortedBy { it.port }.filter { it.status == "open" }.forEach { out("${it.port}/tcp open ${it.service} ${it.ms}ms") }
                    out("${res.count { it.status == "open" }} open of ${list.size} scanned") } }
            else -> out("$cmd: command not found. Try 'help'.")
        }
    }

    fun run() {
        val l = input.trim(); if (l.isEmpty() || busy) return
        lines += "$ $l"; history += l; hIdx = -1; input = ""; busy = true
        scope.launch {
            try { exec(l) } catch (e: CancellationException) { throw e } catch (e: Exception) { lines += "error: ${e.message ?: e.javaClass.simpleName}" }
            busy = false
        }
    }

    val primary = MaterialTheme.colorScheme.primary; val tert = MaterialTheme.colorScheme.tertiary
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Terminal", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
            InfoIcon("Terminal", "A safe internal interpreter that calls the app's own network tools. It is not a Linux shell and does not run external binaries (other than the system ping used by ping/traceroute).")
            IconButton(onClick = { copyText(ctx, lines.joinToString("\n")) }) { Icon(Icons.Default.ContentCopy, "Copy output") }
            IconButton(onClick = { lines.clear() }) { Icon(Icons.Default.Delete, "Clear") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            items(lines) { l -> Text(l, style = Mono, color = if (l.startsWith("$ ")) primary else MaterialTheme.colorScheme.onBackground) }
        }
        val sugg = remember(input) { val t = input.trim(); if (t.isNotEmpty() && !t.contains(" ")) COMMANDS.filter { it.startsWith(t.lowercase()) && it != t.lowercase() } else emptyList() }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sugg.forEach { s -> AssistChip(onClick = { input = "$s " }, label = { Text(s) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = Mono, placeholder = { Text("command") },
                visualTransformation = CmdHighlight(primary, tert), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { run() }))
            IconButton(onClick = { if (history.isNotEmpty()) { hIdx = if (hIdx < 0) history.size - 1 else maxOf(0, hIdx - 1); input = history[hIdx] } }) { Icon(Icons.Default.ArrowUpward, "Previous") }
            IconButton(onClick = { if (hIdx >= 0) { hIdx++; input = if (hIdx >= history.size) { hIdx = -1; "" } else history[hIdx] } }) { Icon(Icons.Default.ArrowDownward, "Next") }
        }
    }
}
