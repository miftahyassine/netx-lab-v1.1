@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netxlab.app.core.*
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private val PALETTE = listOf(Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFE57373), Color(0xFFBA68C8),
    Color(0xFFFFF176), Color(0xFF4DB6AC), Color(0xFFF06292), Color(0xFF90A4AE), Color(0xFFA1887F))

@Composable
fun WifiLabScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var aps by remember { mutableStateOf<List<Ap>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val perms = remember { buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES) }.toTypedArray() }

    fun scan() {
        scope.launch {
            busy = true
            val o = WifiTools.scan(ctx); aps = o.aps; note = o.note; busy = false
            if (o.aps.isNotEmpty()) vm.save("Wi-Fi scan", "nearby", "${o.aps.size} access points (${if (o.fresh) "fresh" else "cached"})",
                o.aps.joinToString("\n") { "${it.ssid}|${it.bssid}|${it.rssi}|${it.freq}|${it.security}" })
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        denied = r.values.any { !it }
        if (!denied) scan()
    }
    fun ensureAndScan() {
        if (perms.all { ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) scan() else launcher.launch(perms)
    }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            listOf("Scanner", "Channels", "Signal", "Connection").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> ToolColumn {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Button(onClick = { ensureAndScan() }, enabled = !busy) { Text(if (busy) "Scanning…" else "Scan Wi-Fi") }
                    InfoIcon("Wi-Fi scanning", "Android limits foreground apps to about four scans per two minutes and may return cached results. Hidden SSIDs show as <hidden>. Distance is a rough free-space estimate.")
                }
                if (denied) ErrorText("Permission denied: Android requires location/nearby-devices access to list access points.")
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                aps.forEach { ap ->
                    NxCard {
                        Text(ap.ssid, style = MaterialTheme.typography.titleMedium)
                        Text(ap.bssid, style = com.netxlab.app.ui.theme.Mono)
                        KeyValue("Signal", "${ap.rssi} dBm · quality ${ap.quality}%")
                        KeyValue("Band / channel", "${ap.band} · ch ${ap.channel} (${ap.freq} MHz)")
                        KeyValue("Width", ap.widthMhz?.let { "$it MHz" })
                        KeyValue("Security", ap.security + if (ap.wps) " + WPS" else "")
                        KeyValue("Est. distance (approx.)", ap.distance)
                    }
                }
                if (aps.isNotEmpty()) OutputBox(aps.joinToString("\n") { "${it.ssid},${it.bssid},${it.rssi},${it.freq},${it.channel},${it.security}" }, "CSV export")
            }
            1 -> ChannelTab(aps, onScan = { ensureAndScan() })
            2 -> SignalTab()
            3 -> ConnectionTab(vm)
        }
    }
}

@Composable
private fun ChannelTab(aps: List<Ap>, onScan: () -> Unit) {
    var band by remember { mutableStateOf("2.4 GHz") }
    val tm = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val label = TextStyle(fontSize = 10.sp, color = axis)
    ToolColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("2.4 GHz", "5 GHz", "6 GHz").forEach { b -> FilterChip(band == b, { band = b }, label = { Text(b) }) }
            InfoIcon("Channel graph", "Each curve is one access point, centred on its channel. Height shows signal strength. Overlapping curves mean co-channel or adjacent-channel interference.")
        }
        val list = aps.filter { it.band == band }
        if (aps.isEmpty()) { Text("No scan data yet."); Button(onClick = onScan) { Text("Scan now") } }
        else if (list.isEmpty()) Text("No networks seen on $band (or this device cannot scan it).")
        else {
            val (fmin, fmax) = when (band) { "2.4 GHz" -> 2400 to 2490; "5 GHz" -> (list.minOf { it.freq } - 40) to (list.maxOf { it.freq } + 40); else -> (list.minOf { it.freq } - 40) to (list.maxOf { it.freq } + 40) }
            NxCard {
                Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                    val w = size.width; val h = size.height - 18.dp.toPx()
                    fun x(f: Float) = (f - fmin) / (fmax - fmin) * w
                    drawLine(axis, Offset(0f, h), Offset(w, h), 2f)
                    list.forEachIndexed { i, ap ->
                        val half = ((ap.widthMhz ?: 20) / 2f)
                        val top = h * (1f - ((ap.rssi + 100) / 70f).coerceIn(0.05f, 1f))
                        val path = Path().apply {
                            moveTo(x(ap.freq - half), h)
                            quadraticBezierTo(x(ap.freq.toFloat()), top * 2 - h, x(ap.freq + half), h)
                        }
                        val c = PALETTE[i % PALETTE.size]
                        drawPath(path, c.copy(alpha = 0.18f))
                        drawPath(path, c, style = Stroke(3f))
                    }
                    val chans = if (band == "2.4 GHz") (1..13).map { 2412 + (it - 1) * 5 to it } else list.map { it.freq to it.channel }.distinct()
                    chans.forEach { (f, ch) -> drawText(tm, ch.toString(), Offset(x(f.toFloat()) - 6f, h + 2f), label) }
                }
                list.forEachIndexed { i, ap -> Text("● ${ap.ssid} (ch ${ap.channel}, ${ap.rssi} dBm)", color = PALETTE[i % PALETTE.size], style = MaterialTheme.typography.labelMedium) }
            }
            NxCard {
                SectionTitle("Channel congestion")
                val counts = list.groupingBy { it.channel }.eachCount().toSortedMap()
                val max = counts.values.max().toFloat()
                counts.forEach { (ch, n) ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("ch $ch", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(progress = { n / max }, modifier = Modifier.weight(1f).height(8.dp))
                        Text("  $n AP", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(WifiTools.recommend(aps, band), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SignalTab() {
    val ctx = LocalContext.current
    val history = remember { mutableStateListOf<Int>() }
    var info by remember { mutableStateOf<WifiConn?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val w = NetInfo.wifi(ctx); info = w
            w.rssi?.let { history += it; if (history.size > 90) history.removeAt(0) }
            delay(1000)
        }
    }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    ToolColumn {
        val w = info
        if (w == null || !w.connected) { Text("Not connected to Wi-Fi."); return@ToolColumn }
        val avg = if (history.isEmpty()) 0.0 else history.average()
        val sd = if (history.size < 2) 0.0 else sqrt(history.sumOf { (it - avg) * (it - avg) } / history.size)
        NxCard {
            KeyValue("Current RSSI", w.rssi?.let { "$it dBm" })
            KeyValue("Quality", w.rssi?.let { "${WifiTools.qualityOf(it)}%" })
            KeyValue("Frequency", w.freq?.let { "$it MHz" })
            KeyValue("Min / avg / max", if (history.isEmpty()) null else "${history.min()} / ${"%.1f".format(avg)} / ${history.max()} dBm")
            KeyValue("Variation (std dev)", "%.1f dB".format(sd))
            KeyValue("Stability", if (history.size < 10) "collecting…" else if (sd < 2.5) "Stable" else if (sd < 5) "Moderate" else "Unstable")
        }
        NxCard {
            SectionTitle("RSSI history (1 sample / s)", "The value comes from the current Wi-Fi connection and is sampled once per second while this tab is open.")
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                for (d in listOf(-30, -50, -70, -90)) { val y = size.height * (1 - (d + 100) / 70f); drawLine(grid.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y)) }
                if (history.size > 1) {
                    val p = Path()
                    history.forEachIndexed { i, v ->
                        val x = size.width * i / 89f; val y = size.height * (1 - ((v + 100) / 70f).coerceIn(0f, 1f))
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, line, style = Stroke(4f))
                }
            }
            Text("Grid lines: -30, -50, -70, -90 dBm", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConnectionTab(vm: AppViewModel) {
    val ctx = LocalContext.current
    val w = remember { NetInfo.wifi(ctx) }; val n = remember { NetInfo.localNet(ctx) }
    val text = remember {
        buildString {
            appendLine("SSID: ${w.ssid ?: "unavailable"}"); appendLine("BSSID: ${w.bssid ?: "unavailable"}")
            appendLine("RSSI: ${w.rssi ?: "?"} dBm"); appendLine("Frequency: ${w.freq ?: "?"} MHz"); appendLine("Link speed: ${w.linkSpeed ?: "?"} Mbps")
            appendLine("Security: ${w.security ?: "unavailable"}"); appendLine("IP: ${n.ip}/${n.prefix}"); appendLine("Gateway: ${n.gateway}")
            appendLine("DNS: ${n.dns.joinToString()}"); appendLine("Interface: ${n.iface} MTU ${n.mtu}"); appendLine("Routes:"); n.routes.forEach { appendLine("  $it") }
        }
    }
    ToolColumn { OutputBox(text, "Current connection") }
}
