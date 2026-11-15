package com.netxlab.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.netxlab.app.core.LocalNet
import com.netxlab.app.core.NetInfo
import com.netxlab.app.core.WifiConn
import com.netxlab.app.core.WifiTools
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.ui.*
import java.text.DateFormat
import java.util.Date

@Composable
fun DashboardScreen(vm: AppViewModel, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings by vm.settings.collectAsState()
    val records by vm.records.collectAsState()
    var wifi by remember { mutableStateOf<WifiConn?>(null) }
    var net by remember { mutableStateOf<LocalNet?>(null) }
    val perms = remember {
        buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES) }.toTypedArray()
    }
    fun refresh() { wifi = NetInfo.wifi(ctx); net = NetInfo.localNet(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    LaunchedEffect(Unit) { refresh() }

    ToolColumn {
        NxCard {
            Row {
                SectionTitle("Connection", "SSID and BSSID are only visible to apps with location (and nearby-devices) permission. Android returns placeholders otherwise.")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { refresh() }) { Text("Refresh") }
            }
            val w = wifi; val n = net
            KeyValue("Status", when {
                n == null -> "…"
                n.transport == "none" -> "Offline"
                n.validated -> "Online (${n.transport})"
                else -> "Connected, no validated Internet (${n.transport})"
            })
            if (w?.connected == true) {
                KeyValue("SSID", w.ssid ?: "Hidden by Android (permission needed)")
                KeyValue("BSSID", w.bssid ?: "Hidden by Android")
                KeyValue("Frequency", w.freq?.let { "$it MHz · ${WifiTools.bandOf(it)} · ch ${WifiTools.channelOf(it)}" })
                KeyValue("Signal", w.rssi?.let { "$it dBm · ${WifiTools.qualityOf(it)}%" })
                KeyValue("Link speed", w.linkSpeed?.let { "$it Mbps" + (w.rxSpeed?.let { r -> " (rx $r)" } ?: "") })
                KeyValue("Security", w.security ?: "Unavailable")
            }
            KeyValue("Local IP", n?.ip?.let { "$it/${n?.prefix}" })
            KeyValue("Gateway", n?.gateway)
            KeyValue("DNS", n?.dns?.joinToString(", ")?.ifEmpty { null })
            KeyValue("Interface / MTU", n?.iface?.let { "$it · MTU ${n?.mtu ?: "?"}" })
            if (w?.connected == true && w.ssidHidden) {
                Button(onClick = { launcher.launch(perms) }) { Text("Grant Wi-Fi details access") }
            }
        }

        SectionTitle("Quick actions")
        val actions = listOf(
            "Scan Wi-Fi" to "wifi", "Scan Network" to "tools/3", "Ping" to "tools/0", "Port Scan" to "tools/2",
            "DNS Lookup" to "tools/4", "Speed Test" to "tools/6", "Network Map" to "topology", "Security Audit" to "security/0"
        )
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, route) ->
                    FilledTonalButton(
                        onClick = { if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onNavigate(route) },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
        }

        val favorites = vm.hosts.collectAsState().value.filter { it.favorite }
        if (favorites.isNotEmpty()) {
            SectionTitle("Favorite hosts")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favorites) { h -> AssistChip(onClick = { onNavigate("tools/0") }, label = { Text(h.alias.ifBlank { h.address }) }) }
            }
        }

        SectionTitle("Recent scans")
        if (records.isEmpty()) Text("Nothing yet. Results from the tools are saved here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        records.take(6).forEach { r ->
            NxCard {
                Text("${r.type} · ${r.target}", style = MaterialTheme.typography.titleMedium)
                Text(r.summary, style = MaterialTheme.typography.bodyMedium)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
