@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.netxlab.app.core.*
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.data.SavedHost
import com.netxlab.app.ui.*
import kotlinx.coroutines.launch

private val TABS = listOf("Wi-Fi Audit", "Router", "Passwords", "Learn Wi-Fi", "Simulation", "Cyber Lab", "Targets")

@Composable
fun SecurityScreen(vm: AppViewModel, initialTab: Int) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, TABS.size - 1)) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) { TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
        when (tab) { 0 -> WifiAudit(vm); 1 -> RouterAudit(vm); 2 -> PasswordTab(); 3 -> LearnWifi(); 4 -> SimLab(); 5 -> CyberLab(vm); 6 -> Targets(vm) }
    }
}

@Composable
private fun WifiAudit(vm: AppViewModel) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var aps by remember { mutableStateOf<List<Ap>>(emptyList()) }; var busy by remember { mutableStateOf(false) }; var note by remember { mutableStateOf("") }
    val perms = remember { buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES) }.toTypedArray() }
    fun run() { scope.launch { busy = true; val o = WifiTools.scan(ctx); aps = o.aps; note = o.note; busy = false } }
    val l = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (it.values.all { g -> g }) run() else note = "Permission denied." }
    ToolColumn {
        WifiAccessCard()
        SectionTitle("Wireless security inspection", "This reads the security capabilities advertised by nearby access points. An encryption label alone does not prove a network is vulnerable or safe; it only shows what the AP advertises.")
        Button(enabled = !busy, onClick = { if (perms.all { ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) run() else l.launch(perms) }) { Text(if (busy) "Scanning…" else "Inspect nearby networks") }
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodyMedium)
        if (aps.isNotEmpty()) {
            aps.groupBy { it.security }.entries.sortedBy { it.key }.forEach { (sec, list) ->
                NxCard {
                    Text("$sec · ${list.size} network(s)", style = MaterialTheme.typography.titleMedium)
                    Text(WifiTools.securityOf(list.first().capabilities).second, style = MaterialTheme.typography.bodyMedium)
                    list.take(6).forEach { Text("• ${it.ssid}${if (it.wps) " (WPS advertised)" else ""}", style = MaterialTheme.typography.labelMedium) }
                }
            }
            val flagged = aps.filter { it.security == "Open" || it.security == "WEP" || it.security.startsWith("WPA (legacy") }
            OutputBox("Open/WEP/legacy-WPA networks: ${flagged.size}\nWPS advertised: ${aps.count { it.wps }}\nWPA3 capable: ${aps.count { it.security.contains("WPA3") }}", "Summary")
        }
    }
}

private val ROUTER_ITEMS = listOf(
    "Wi-Fi uses WPA2-AES or WPA3 (no WEP/WPA/TKIP)", "WPS (PIN/push-button) is disabled", "Default admin username/password was changed",
    "Admin password is unique and long (12+ chars)", "Router firmware is up to date (auto-update on if offered)", "Guest network enabled and isolated from main LAN",
    "Remote administration from the Internet is disabled", "UPnP is disabled or limited to what you need", "No unneeded port forwards / DMZ host",
    "Wi-Fi passphrase is long and random (not on a sticker/default)", "Admin page uses HTTPS", "DNS is set to a trusted resolver (or ISP default knowingly)"
)

@Composable
private fun RouterAudit(vm: AppViewModel) {
    val checks = remember { mutableStateListOf<Boolean>().also { l -> repeat(ROUTER_ITEMS.size) { l += false } } }
    var model by remember { mutableStateOf("") }; var fw by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); var okScope by remember { mutableStateOf(false) }; var gwOut by remember { mutableStateOf("") }
    ToolColumn {
        SectionTitle("Router security checklist", "Tick what you have verified on YOUR router. The report only reflects what you enter; the app cannot read your router's configuration.")
        Field(model, { model = it }, "Router model"); Field(fw, { fw = it }, "Firmware version")
        ROUTER_ITEMS.forEachIndexed { i, t -> Row(Modifier.clickable { checks[i] = !checks[i] }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checks[i], { checks[i] = it }); Text(t, style = MaterialTheme.typography.bodyMedium) } }
        Field(notes, { notes = it }, "Notes", singleLine = false)
        Button(onClick = {
            val done = checks.count { it }; val pct = done * 100 / ROUTER_ITEMS.size
            report = buildString {
                appendLine("NETX LAB router audit"); appendLine("Router: $model  Firmware: $fw"); appendLine("Score: $done/${ROUTER_ITEMS.size} ($pct%)"); appendLine()
                appendLine("Completed:"); ROUTER_ITEMS.forEachIndexed { i, t -> if (checks[i]) appendLine("  [x] $t") }
                appendLine("To do:"); ROUTER_ITEMS.forEachIndexed { i, t -> if (!checks[i]) appendLine("  [ ] $t") }
                if (notes.isNotBlank()) appendLine("\nNotes: $notes")
                appendLine("\nSelf-reported; not verified by the app.")
            }
            vm.save("Router audit", model.ifBlank { "router" }, "$done/${ROUTER_ITEMS.size} checks", report)
        }) { Text("Generate report") }
        OutputBox(report, "Audit report")
        NxCard {
            SectionTitle("Exposure check of my gateway", "Runs a TCP connect check of common ports against your default gateway from inside your network.")
            ScopeConfirm(okScope) { okScope = it }
            Button(enabled = okScope, onClick = { scope.launch {
                val gw = NetInfo.localNet(ctx).gateway; if (gw == null) { gwOut = "No gateway detected."; return@launch }
                val res = mutableListOf<PortResult>(); NetTools.portScan(gw, NetTools.COMMON_PORTS + listOf(1900, 8291, 7547), 1200, 32).collect { res += it }
                val open = res.filter { it.status == "open" }.sortedBy { it.port }
                gwOut = "Gateway $gw\nOpen: ${open.joinToString { "${it.port}/${it.service}" }.ifEmpty { "none of the probed ports" }}\n" +
                    (if (open.any { it.port == 23 }) "! Telnet open: disable it.\n" else "") + (if (open.any { it.port == 80 }) "i HTTP admin without TLS: use HTTPS if available.\n" else "")
                vm.save("Exposure check", gw, "open: ${open.joinToString { it.port.toString() }.ifEmpty { "none" }}")
            } }) { Text("Check gateway ports") }
            OutputBox(gwOut, "Gateway exposure")
        }
    }
}

@Composable
private fun PasswordTab() {
    var pw by remember { mutableStateOf("") }; var show by remember { mutableStateOf(false) }; var len by remember { mutableFloatStateOf(20f) }; var gen by remember { mutableStateOf("") }; var sym by remember { mutableStateOf(true) }
    ToolColumn {
        SectionTitle("Password strength", "Analysis is done entirely on this device. Time estimate assumes an offline attacker at 10^10 guesses/second against a fast hash; the common-word list is small and built in, so it is a rough guide only.")
        OutlinedTextField(pw, { pw = it }, label = { Text("Wi-Fi password to test") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(show, { show = it }); Text("Show") }
        if (pw.isNotEmpty()) {
            val r = PasswordLab.analyze(pw)
            NxCard {
                Text(r.rating, style = MaterialTheme.typography.headlineSmall, color = when (r.rating) { "Very weak", "Weak" -> MaterialTheme.colorScheme.error; "Fair" -> Color(0xFFFFB74D); else -> MaterialTheme.colorScheme.tertiary })
                KeyValue("Length / pool", "${r.length} chars / ${r.poolSize} symbols"); KeyValue("Raw entropy", "%.1f bits".format(r.entropyBits)); KeyValue("After pattern penalties", "%.1f bits".format(r.adjustedBits))
                KeyValue("Offline guess time (est.)", r.crackTime)
                r.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        SectionTitle("Generator")
        Text("Length: ${len.toInt()}"); Slider(len, { len = it }, valueRange = 8f..63f)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sym, { sym = it }); Text("Include symbols") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { gen = PasswordLab.generate(len.toInt(), sym) }) { Text("Random") }
            OutlinedButton(onClick = { gen = PasswordLab.generatePassphrase() }) { Text("Passphrase") }
        }
        OutputBox(gen, "Generated (not stored)")
        NxCard { SectionTitle("Tips"); Text("• 16+ random characters or a 5+ word passphrase.\n• Never reuse your router admin password as the Wi-Fi password.\n• Change the default passphrase printed on the router.\n• Prefer WPA3 / WPA2-AES; disable WPS.", style = MaterialTheme.typography.bodyMedium) }
        Text("This tool only evaluates passwords you type. It does not crack or recover Wi-Fi passwords.", style = MaterialTheme.typography.labelSmall)
    }
}

private val WIFI_TOPICS = listOf(
    "WEP weaknesses" to "WEP uses RC4 with a short 24-bit IV, so keys can be recovered from captured traffic in minutes. It must not be used.",
    "WPA2" to "WPA2-Personal derives keys from the passphrase and the SSID. Anyone who knows the passphrase can read other users' traffic; a weak passphrase can be guessed offline if a handshake is captured. WPA2-Enterprise uses 802.1X per-user credentials.",
    "WPA3" to "WPA3-Personal replaces the pre-shared key exchange with SAE (Dragonfly), which blocks offline guessing and gives forward secrecy. WPA3 also requires Protected Management Frames.",
    "WPS risks" to "WPS PIN mode has an 8-digit PIN verified in two halves, which makes brute force feasible. Disable WPS on routers.",
    "Authentication vs encryption" to "Authentication proves who may join; encryption protects data in transit. Open networks have neither; OWE adds encryption without authentication.",
    "4-way handshake (concept)" to "After joining, client and AP prove they know the same key and derive fresh session keys through four messages. The handshake never sends the passphrase itself.",
    "Rogue AP / evil twin" to "A rogue AP is an unauthorized access point on your network; an evil twin imitates a legitimate SSID to lure users. Defences: WPA3/802.1X with certificate validation, HTTPS/HSTS, VPN, and not auto-joining open networks.",
    "Deauthentication attacks" to "Deauth frames are unauthenticated in older Wi-Fi and can disconnect clients. Protected Management Frames (802.11w, required in WPA3) mitigate this.",
    "Wireless monitoring" to "Monitoring (passive capture of 802.11 frames) needs a Wi-Fi adapter and driver that support monitor mode. Ordinary Android phones do not expose this."
)

@Composable
private fun LearnWifi() {
    ToolColumn {
        WIFI_TOPICS.forEach { (t, b) -> var open by remember { mutableStateOf(false) }
            NxCard(Modifier.clickable { open = !open }) { Text(t, style = MaterialTheme.typography.titleMedium); if (open) Text(b, style = MaterialTheme.typography.bodyMedium) else Text("Tap to read", style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun SimLab() {
    val scenarios = listOf("Open network", "WEP", "WPA2 + weak passphrase", "WPA3", "Evil twin", "Deauthentication")
    var s by remember { mutableIntStateOf(0) }; var protectedPmf by remember { mutableStateOf(false) }
    ToolColumn {
        NxCard { Text("SIMULATION — all data below is fictional teaching material. No nearby network is touched, scanned or attacked.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge) }
        scenarios.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { n -> FilterChip(s == scenarios.indexOf(n), { s = scenarios.indexOf(n) }, label = { Text(n) }) } } }
        val (observer, lesson) = when (s) {
            0 -> "GET /login HTTP/1.1\nHost: shop.example\nCookie: session=ab12cd34 (visible to any nearby observer)" to "Without encryption, any unencrypted traffic can be read by someone in range. HTTPS protects the content, but not metadata such as which host you visit."
            1 -> "IV=0x00A1F3 data=7f 22 9c 10 ...\n(with enough IVs the key is recoverable)" to "WEP's short IV repeats, so statistical attacks recover the key. Replace WEP hardware."
            2 -> "Handshake captured (fictional).\nPassphrase 'summer2020' → found in common-word list within seconds (simulated)." to "WPA2 is only as strong as the passphrase. A long random passphrase makes offline guessing impractical."
            3 -> "SAE exchange: commit/confirm only.\nNo material usable for offline guessing; PMF required." to "WPA3-SAE makes every guess require live interaction with the AP and provides forward secrecy."
            4 -> "SSID 'CafeFreeWiFi' seen from two BSSIDs:\n  AA:BB:CC:00:00:01 (real)\n  AA:BB:CC:00:00:99 (twin, stronger signal)" to "Clients may join the stronger twin. 802.1X with certificate checks, VPNs and HTTPS/HSTS limit the damage. Defenders can look for duplicate SSIDs with unexpected BSSIDs/security."
            else -> if (protectedPmf) "Forged deauth frames ignored: PMF (802.11w) is enabled.\nClient stays connected." to "Protected Management Frames authenticate management traffic so forged disconnects are dropped."
                    else "Forged deauth frame → client disconnects and reconnects.\n(simulated; no frames are transmitted by this app)" to "Without PMF, management frames can be forged. This is why WPA3 mandates PMF."
        }
        if (s == 5) Row(verticalAlignment = Alignment.CenterVertically) { Switch(protectedPmf, { protectedPmf = it }); Text("  PMF (802.11w) enabled") }
        OutputBox(observer, "What an observer would see (fictional)")
        NxCard { Text(lesson, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun CyberLab(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    var open by remember { mutableStateOf<Lesson?>(null) }
    ToolColumn {
        SectionTitle("Learning center", "Works fully offline. Progress is saved on the device.")
        Text("${settings.completedLessons.size} / ${Lessons.ALL.size} completed")
        LinearProgressIndicator(progress = { settings.completedLessons.size / Lessons.ALL.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Lessons.ALL.forEach { l ->
            NxCard(Modifier.clickable { open = l }) { Text((if (l.id in settings.completedLessons) "✓ " else "") + l.title, style = MaterialTheme.typography.titleMedium) }
        }
        TextButton(onClick = { vm.resetLessons() }) { Text("Reset progress") }
    }
    open?.let { l ->
        var pick by remember(l) { mutableIntStateOf(-1) }; var hint by remember(l) { mutableStateOf(false) }; var sol by remember(l) { mutableStateOf(false) }; var layer by remember(l) { mutableIntStateOf(-1) }
        AlertDialog(onDismissRequest = { open = null }, confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } }, title = { Text(l.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.then(Modifier)) {
                Text(l.body, style = MaterialTheme.typography.bodyMedium)
                if (l.id == "osi") { Lessons.OSI.forEachIndexed { i, (n, d) -> FilledTonalButton(onClick = { layer = i }, modifier = Modifier.fillMaxWidth()) { Text(n) }; if (layer == i) Text(d, style = MaterialTheme.typography.labelMedium) } }
                Text(l.question, style = MaterialTheme.typography.titleMedium)
                l.options.forEachIndexed { i, o -> Row(Modifier.clickable { pick = i; if (i == l.answer) vm.completeLesson(l.id) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(pick == i, { pick = i; if (i == l.answer) vm.completeLesson(l.id) }); Text(o) } }
                if (pick >= 0) Text(if (pick == l.answer) "Correct!" else "Not quite, try again.", color = if (pick == l.answer) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Row { TextButton(onClick = { hint = true }) { Text("Hint") }; TextButton(onClick = { sol = true }) { Text("Solution") } }
                if (hint) Text("Hint: ${l.hint}", style = MaterialTheme.typography.labelMedium); if (sol) Text("Solution: ${l.solution}", style = MaterialTheme.typography.labelMedium)
            } })
    }
}

@Composable
private fun Targets(vm: AppViewModel) {
    val hosts by vm.hosts.collectAsState(); var addr by remember { mutableStateOf("") }; var scope by remember { mutableStateOf("") }
    ToolColumn {
        SectionTitle("Authorized targets", "Save hosts you are allowed to test and describe the permitted scope (e.g. 'my home router, TCP port scan only, until 2026-12-31').")
        Field(addr, { addr = it }, "Host / IP"); Field(scope, { scope = it }, "Permitted scope")
        Button(enabled = addr.isNotBlank(), onClick = { vm.saveHost(SavedHost(address = addr.trim(), scope = scope, authorized = true)); addr = ""; scope = "" }) { Text("Save target") }
        hosts.filter { it.authorized }.forEach { h -> NxCard { Text(h.address, style = MaterialTheme.typography.titleMedium); Text(h.scope.ifBlank { "(no scope written)" }); TextButton(onClick = { vm.deleteHost(h) }) { Text("Remove") } } }
        NxCard { SectionTitle("Rules of engagement"); Text("NETX LAB performs only connect-style checks, requires scope confirmation before port scans and contains no exploitation, credential theft, stealth or destructive features.", style = MaterialTheme.typography.bodyMedium) }
    }
}
