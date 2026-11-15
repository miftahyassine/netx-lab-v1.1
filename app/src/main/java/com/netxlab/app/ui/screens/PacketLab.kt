@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.netxlab.app.core.PacketInfo
import com.netxlab.app.core.PcapParser
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.ui.*
import com.netxlab.app.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PacketLabScreen(vm: AppViewModel) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var packets by remember { mutableStateOf<List<PacketInfo>>(emptyList()) }
    var fileInfo by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }; var sel by remember { mutableStateOf<PacketInfo?>(null) }; var tab by remember { mutableIntStateOf(0) }
    var pendingExport by remember { mutableStateOf("") }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; err = null
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    require(bytes.size <= 64 * 1024 * 1024) { "File larger than 64 MB is not supported." }
                    PcapParser.parse(bytes)
                }
                packets = res.packets
                fileInfo = "${res.format} · ${res.packets.size} packets · ${res.note}"
                vm.save("PCAP import", uri.lastPathSegment ?: "file", "${res.packets.size} packets (${res.format})")
            } catch (e: Exception) { err = "Import failed: ${e.message}"; packets = emptyList() }
            busy = false
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray()) }
    }

    val shown = remember(packets, filter) { packets.filter { PcapParser.matches(it, filter) } }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            listOf("Packets", "Statistics", "Live capture").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Button(onClick = { open.launch(arrayOf("*/*")) }, enabled = !busy) { Text(if (busy) "Parsing…" else "Open PCAP / PCAPNG") }
                    InfoIcon("Packet Lab", "Imports classic PCAP and PCAPNG files (Ethernet, raw IP, Linux cooked and loopback link types). Filters: tcp, udp, icmp, dns, http, https, arp, ipv4, ipv6, or any text/IP/port.")
                }
                ErrorText(err)
                if (fileInfo.isNotEmpty()) Text(fileInfo, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("tcp", "udp", "icmp", "dns", "http", "https", "arp", "ipv4", "ipv6").forEach { f -> FilterChip(filter == f, { filter = if (filter == f) "" else f }, label = { Text(f.uppercase()) }) }
                }
                Field(filter, { filter = it }, "Filter (protocol, IP, port or text)")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${shown.size} of ${packets.size} packets", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(enabled = shown.isNotEmpty(), onClick = {
                        val q = "\""
                        pendingExport = "no,time,src,dst,sport,dport,proto,len,flags,info\n" + shown.take(100000).joinToString("\n") { p ->
                            val t = String.format("%.6f", p.ts)
                            p.no.toString() + "," + t + "," + p.src + "," + p.dst + "," + (p.sport ?: "") + "," + (p.dport ?: "") + "," + p.proto + "," + p.length + "," + p.flags + "," + q + p.info.replace(q, "'") + q
                        }
                        export.launch("netxlab-filtered.csv")
                    }) { Text("Export filtered") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown.take(3000)) { p ->
                        Card(Modifier.fillMaxWidth().clickable { sel = p }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(8.dp)) {
                                Text("#${p.no}  ${p.proto}  ${p.length} B  t=${"%.3f".format(p.ts)}", style = MaterialTheme.typography.labelMedium)
                                Text("${p.src}${p.sport?.let { ":$it" } ?: ""} → ${p.dst}${p.dport?.let { ":$it" } ?: ""}", style = Mono)
                                Text(p.info, style = Mono, maxLines = 2)
                            }
                        }
                    }
                }
            }
            1 -> ToolColumn {
                if (packets.isEmpty()) Text("Open a capture file first.") else {
                    val total = packets.size.toFloat()
                    NxCard {
                        SectionTitle("Protocol distribution")
                        packets.groupingBy { it.proto }.eachCount().entries.sortedByDescending { it.value }.forEach { (k, v) ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(k, Modifier.width(70.dp), style = MaterialTheme.typography.labelMedium)
                                LinearProgressIndicator(progress = { v / total }, modifier = Modifier.weight(1f).height(8.dp))
                                Text("  $v", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    val bytes = packets.sumOf { it.length.toLong() }
                    val dur = (packets.maxOf { it.ts } - packets.minOf { it.ts })
                    NxCard { KeyValue("Packets", packets.size.toString()); KeyValue("Bytes", bytes.toString()); KeyValue("Duration", "%.3f s".format(dur)) }
                    val conv = packets.filter { it.src.isNotEmpty() && it.dst.isNotEmpty() }.groupBy { listOf(it.src, it.dst).sorted().joinToString(" ↔ ") }
                    val text = conv.entries.sortedByDescending { it.value.size }.take(40).joinToString("\n") { (k, v) -> "$k  ${v.size} pkts  ${v.sumOf { it.length }} B" }
                    OutputBox(text, "Conversations (top 40)")
                }
            }
            2 -> ToolColumn {
                NxCard {
                    SectionTitle("Live capture: not included in this build")
                    Text("A VpnService-based capture can only see traffic generated by this phone itself, not other devices' Wi-Fi traffic, and is not monitor mode or promiscuous capture. Raw 802.11 frames and monitor mode are not available on ordinary Android hardware without root and special chipsets.\n\nThis version therefore supports offline analysis of capture files made elsewhere (e.g. Wireshark, tcpdump, or PCAPdroid).", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    sel?.let { p ->
        AlertDialog(onDismissRequest = { sel = null }, confirmButton = { TextButton(onClick = { sel = null }) { Text("Close") } },
            title = { Text("Packet #${p.no} · ${p.proto}") },
            text = { Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyValue("Layer 3", p.l3); KeyValue("Source", p.src + (p.sport?.let { ":$it" } ?: "")); KeyValue("Destination", p.dst + (p.dport?.let { ":$it" } ?: ""))
                KeyValue("Length", "${p.length} B (captured ${p.data.size})"); if (p.flags.isNotEmpty()) KeyValue("TCP flags", p.flags); KeyValue("Info", p.info)
                Text("Hex / ASCII", style = MaterialTheme.typography.labelLarge)
                Text(PcapParser.hexDump(p.data), style = Mono, modifier = Modifier.horizontalScroll(rememberScrollState()))
            } })
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))
