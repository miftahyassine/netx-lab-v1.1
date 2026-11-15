@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netxlab.app.core.Device
import com.netxlab.app.core.IpCalc
import com.netxlab.app.core.NetInfo
import com.netxlab.app.core.NetTools
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun layoutNodes(n: Int, r: Float): List<Offset> = List(n) { i ->
    val ring = i / 12; val inRing = minOf(12, n - ring * 12)
    val ang = 2 * PI * (i % 12) / inRing + ring * 0.3
    val rad = r * (1f + 0.55f * ring)
    Offset((rad * cos(ang)).toFloat(), (rad * sin(ang)).toFloat())
}

@Composable
fun TopologyScreen(vm: AppViewModel) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val settings by vm.settings.collectAsState()
    val devices = ScanStore.devices.sortedBy { IpCalc.parseV4(it.ip) }
    var scale by remember { mutableFloatStateOf(1f) }; var offset by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<Device?>(null) }; var selGateway by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }; var err by remember { mutableStateOf<String?>(null) }
    val tm = rememberTextMeasurer()
    val c = MaterialTheme.colorScheme; val labelStyle = TextStyle(fontSize = 10.sp, color = c.onBackground)
    val local = ScanStore.local ?: remember { NetInfo.localNet(ctx) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(onClick = {
                if (job?.isActive == true) { job?.cancel(); return@Button }
                val n = NetInfo.localNet(ctx); ScanStore.local = n
                if (n.ip == null || n.prefix == null) { err = "Connect to a network first."; return@Button }
                err = null; ScanStore.devices = emptyList(); selected = null
                job = scope.launch {
                    val acc = mutableListOf<Device>()
                    try { NetTools.lanScan(n.ip, n.prefix, settings.timeoutMs).collect { acc += it; ScanStore.devices = acc.toList() } } catch (e: Exception) { if (e !is kotlinx.coroutines.CancellationException) err = e.message }
                }
            }) { Text(if (job?.isActive == true) "Stop" else "Refresh discovery") }
            TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Reset view") }
            InfoIcon("Network map", "Solid outline = a host that answered a probe from this phone (observed). Dashed lines to the gateway are inferred: all hosts in one subnet are drawn as attached to the default gateway; the real switch/AP topology cannot be discovered from a phone. Pinch to zoom, drag to pan, tap a node.")
        }
        ErrorText(err)
        Canvas(Modifier.weight(1f).fillMaxWidth()
            .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(0.4f, 4f); offset += pan } }
            .pointerInput(devices, scale, offset) {
                detectTapGestures { tap ->
                    val ctr = Offset(size.width / 2f, size.height / 2f) + offset
                    val r = minOf(size.width, size.height) * 0.32f
                    val pts = layoutNodes(devices.size, r)
                    val hitR = 28f * scale
                    val d0 = tap - ctr
                    if (sqrt(d0.x * d0.x + d0.y * d0.y) < hitR) { selGateway = true; selected = null; return@detectTapGestures }
                    val idx = pts.indexOfFirst { p -> val q = ctr + p * scale; val dx = tap.x - q.x; val dy = tap.y - q.y; sqrt(dx * dx + dy * dy) < hitR }
                    if (idx >= 0) { selected = devices[idx]; selGateway = false } else { selected = null; selGateway = false }
                }
            }
        ) {
            val ctr = Offset(size.width / 2f, size.height / 2f) + offset
            val r = minOf(size.width, size.height) * 0.32f
            val pts = layoutNodes(devices.size, r)
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            pts.forEach { p -> drawLine(c.outline, ctr, ctr + p * scale, 2f, pathEffect = dash) }
            drawCircle(c.primary.copy(alpha = 0.25f), 30f * scale, ctr); drawCircle(c.primary, 30f * scale, ctr, style = Stroke(4f))
            drawText(tm, "Gateway", ctr + Offset(-22f, 36f * scale), labelStyle)
            local.gateway?.let { drawText(tm, it, ctr + Offset(-30f, 48f * scale), labelStyle) }
            devices.forEachIndexed { i, d ->
                val q = ctr + pts[i] * scale
                val sel = selected?.ip == d.ip
                drawCircle(if (sel) c.tertiary else c.secondary.copy(alpha = 0.25f), 22f * scale, q)
                drawCircle(if (sel) c.tertiary else c.secondary, 22f * scale, q, style = Stroke(4f))
                drawText(tm, d.ip.substringAfterLast("."), q + Offset(-8f, -7f), labelStyle)
                drawText(tm, d.host ?: d.ip, q + Offset(-24f, 26f * scale), labelStyle)
            }
        }
        NxCard(Modifier.padding(12.dp)) {
            val d = selected
            if (selGateway) { KeyValue("Gateway", local.gateway); KeyValue("Your IP", local.ip?.let { "$it/${local.prefix}" }); KeyValue("DNS", local.dns.joinToString()) }
            else if (d != null) { KeyValue("IP", d.ip); KeyValue("Host", d.host); KeyValue("MAC", d.mac ?: "n/a"); KeyValue("Vendor", d.vendor); KeyValue("Response", "${d.ms} ms"); KeyValue("Open ports", d.openPorts.joinToString().ifEmpty { "none probed" }) }
            else Text("${devices.size} devices observed. Tap a node for details.", style = MaterialTheme.typography.bodyMedium)
            val svg = remember(devices, local) { toSvg(devices, local.gateway ?: "gateway") }
            val context = LocalContext.current
            TextButton(onClick = { shareText(context, svg, "NETX LAB network map (SVG)") }) { Text("Export map (SVG)") }
        }
    }
}

private fun toSvg(devs: List<Device>, gw: String): String {
    val pts = layoutNodes(devs.size, 220f)
    val sb = StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"800\" viewBox=\"-400 -400 800 800\" font-family=\"monospace\" font-size=\"11\">\n<rect x=\"-400\" y=\"-400\" width=\"800\" height=\"800\" fill=\"#0E1116\"/>\n")
    pts.forEach { sb.append("<line x1=\"0\" y1=\"0\" x2=\"${it.x}\" y2=\"${it.y}\" stroke=\"#3A4452\" stroke-dasharray=\"6 4\"/>\n") }
    sb.append("<circle r=\"26\" fill=\"#4FC3F7\"/><text y=\"42\" text-anchor=\"middle\" fill=\"#E6EDF3\">$gw</text>\n")
    devs.forEachIndexed { i, d -> sb.append("<circle cx=\"${pts[i].x}\" cy=\"${pts[i].y}\" r=\"16\" fill=\"#5B8DEF\"/><text x=\"${pts[i].x}\" y=\"${pts[i].y + 30}\" text-anchor=\"middle\" fill=\"#E6EDF3\">${d.ip}</text>\n") }
    return sb.append("</svg>").toString()
}
