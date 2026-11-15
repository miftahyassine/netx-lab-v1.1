@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.netxlab.app.core.*
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.data.Project
import com.netxlab.app.ui.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.util.Base64

private val TABS = listOf("MAC / OUI", "Wi-Fi QR", "URL", "Public IP & WHOIS", "Text tools", "Logs", "Projects", "History")

@Composable
fun MoreScreen(vm: AppViewModel, initialTab: Int) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, TABS.size - 1)) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) { TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
        when (tab) { 0 -> MacTab(); 1 -> QrTab(); 2 -> UrlTab(); 3 -> ExternalTab(vm); 4 -> TextTab(); 5 -> LogTab(); 6 -> ProjectsTab(vm); 7 -> HistoryTab(vm) }
    }
}

@Composable
private fun MacTab() {
    var mac by remember { mutableStateOf("") }
    ToolColumn {
        SectionTitle("MAC address / OUI lookup", "Uses a small built-in subset of well-known vendor prefixes (not the full IEEE registry). Many phones use randomized MACs, which have no vendor.")
        Field(mac, { mac = it }, "MAC address")
        val f = IpCalc.formatMac(mac.filter { it.isLetterOrDigit() }.padEnd(12, '0').take(12))
        if (mac.filter { it.isLetterOrDigit() }.length >= 6) OutputBox("OUI: ${mac.filter { it.isLetterOrDigit() }.take(6).uppercase()}\nVendor: ${Oui.describe(mac)}\nLocally administered: ${Oui.isRandomized(mac)}", "Result")
    }
}

@Composable
private fun QrTab() {
    var ssid by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var sec by remember { mutableStateOf("WPA") }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }; var parse by remember { mutableStateOf("") }; var imp by remember { mutableStateOf("") }
    ToolColumn {
        SectionTitle("Wi-Fi QR code", "Generates the standard WIFI:T:...;S:...;P:...;; payload. The password is only used on this device.")
        Field(ssid, { ssid = it }, "SSID"); Field(pass, { pass = it }, "Password")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("WPA", "WEP", "nopass").forEach { s -> FilterChip(sec == s, { sec = s }, label = { Text(s) }) } }
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        Button(enabled = ssid.isNotBlank(), onClick = {
            val payload = "WIFI:T:$sec;S:${esc(ssid)};${if (sec != "nopass") "P:${esc(pass)};" else ""};"
            val m = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
            val b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            for (x in 0 until 512) for (y in 0 until 512) b.setPixel(x, y, if (m[x, y]) AColor.BLACK else AColor.WHITE)
            bmp = b
        }) { Text("Generate QR") }
        bmp?.let { Image(it.asImageBitmap(), "Wi-Fi QR code", Modifier.size(260.dp)) }
        SectionTitle("Import from WIFI: text", "Camera scanning is not included in this build. Paste the text content of a Wi-Fi QR code to parse it.")
        Field(imp, { imp = it }, "WIFI:T:WPA;S:name;P:pass;;")
        Button(onClick = {
            if (!imp.startsWith("WIFI:")) parse = "Not a WIFI: payload" else {
                val parts = Regex("(?<!\\\\);").split(imp.removePrefix("WIFI:")).filter { it.contains(":") }.associate { it.substringBefore(":") to it.substringAfter(":") }
                parse = "SSID: ${parts["S"]}\nSecurity: ${parts["T"] ?: "open"}\nPassword: ${parts["P"] ?: "(none)"}\nHidden: ${parts["H"] ?: "false"}"
            }
        }) { Text("Parse") }
        OutputBox(parse, "Parsed")
    }
}

@Composable
private fun UrlTab() {
    var u by remember { mutableStateOf("https://user@example.com:8443/a/b?x=1&y=2#frag") }
    ToolColumn {
        SectionTitle("URL analyzer", "Parses the URL locally; nothing is requested from the network.")
        Field(u, { u = it }, "URL")
        val r = try { val x = URI(u.trim()); buildString {
            appendLine("Scheme: ${x.scheme}"); appendLine("User info: ${x.userInfo}"); appendLine("Host: ${x.host}"); appendLine("Port: ${if (x.port < 0) "default" else x.port}")
            appendLine("Path: ${x.path}"); appendLine("Query: ${x.query}"); x.query?.split("&")?.forEach { appendLine("   $it") }; appendLine("Fragment: ${x.fragment}")
            if (x.scheme == "http") appendLine("! Unencrypted HTTP")
            if (x.userInfo != null) appendLine("! Credentials embedded in URL (phishing red flag)")
            if (x.host?.contains("xn--") == true) appendLine("! Punycode host (possible lookalike domain)")
            if (x.host != null && Regex("^[0-9.]+$").matches(x.host)) appendLine("! Host is a raw IP address")
        } } catch (e: Exception) { "Invalid URL: ${e.message}" }
        OutputBox(r)
    }
}

@Composable
private fun ExternalTab(vm: AppViewModel) {
    val s by vm.settings.collectAsState(); val scope = rememberCoroutineScope(); var out by remember { mutableStateOf("") }; var q by remember { mutableStateOf("example.com") }; var err by remember { mutableStateOf<String?>(null) }
    ToolColumn {
        SectionTitle("External services", "These tools contact third parties: api.ipify.org (public IP), ipwho.is (geolocation of that IP) and WHOIS servers. They see your IP address and the query. Nothing is sent unless you enable consent below.")
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(s.externalConsent, { vm.setConsent(it) }); Text("  I consent to contacting external services") }
        ErrorText(err)
        Button(enabled = s.externalConsent, onClick = { scope.launch { err = null; try {
            val ip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { URL("https://api.ipify.org").readText() }
            out = "Public IP: $ip" } catch (e: Exception) { err = e.message } } }) { Text("Public IP") }
        Button(enabled = s.externalConsent, onClick = { scope.launch { err = null; try {
            out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val j = JSONObject(URL("https://ipwho.is/").readText())
                "IP ${j.optString("ip")}\n${j.optString("city")}, ${j.optString("region")}, ${j.optString("country")}\nISP: ${j.optJSONObject("connection")?.optString("isp")}\n(approximate, from a third-party database)" } } catch (e: Exception) { err = e.message } } }) { Text("IP geolocation") }
        Field(q, { q = it }, "Domain or IP for WHOIS")
        Button(enabled = s.externalConsent && NetTools.validHost(q), onClick = { scope.launch { err = null; try { out = NetTools.whois(q.trim()).take(6000) } catch (e: Exception) { err = e.message } } }) { Text("WHOIS") }
        OutputBox(out)
    }
}

@Composable
private fun TextTab() {
    var text by remember { mutableStateOf("") }
    ToolColumn {
        SectionTitle("Text utilities", "JSON formatting, Base64 and hex conversion run locally.")
        Field(text, { text = it }, "Input", singleLine = false)
        val json = try { val t = text.trim(); if (t.startsWith("[")) JSONArray(t).toString(2) else JSONObject(t).toString(2) } catch (e: Exception) { null }
        OutputBox(json ?: "", "JSON (pretty)")
        OutputBox(if (text.isEmpty()) "" else Base64.getEncoder().encodeToString(text.toByteArray()), "Base64 encode")
        OutputBox(try { if (text.isEmpty()) "" else String(Base64.getDecoder().decode(text.trim())) } catch (e: Exception) { "" }, "Base64 decode")
        OutputBox(text.toByteArray().joinToString(" ") { "%02x".format(it) }, "Text → hex")
        OutputBox(try { val h = text.filter { !it.isWhitespace() }; if (h.length % 2 == 0 && h.isNotEmpty()) String(h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) else "" } catch (e: Exception) { "" }, "Hex → text")
    }
}

@Composable
private fun LogTab() {
    var rev by remember { mutableIntStateOf(0) }
    ToolColumn {
        Row { Button(onClick = { rev++ }) { Text("Refresh") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { AppLog.clear(); rev++ }) { Text("Clear") } }
        key(rev) { OutputBox(AppLog.all().joinToString("\n").ifEmpty { "(no log entries)" }, "Network log") }
    }
}

@Composable
private fun ProjectsTab(vm: AppViewModel) {
    val projects by vm.projects.collectAsState(); val s by vm.settings.collectAsState(); val records by vm.records.collectAsState()
    var name by remember { mutableStateOf("") }
    ToolColumn {
        SectionTitle("Investigation projects", "New scan results are attached to the active project. Export/import in Settings → Privacy.")
        Field(name, { name = it }, "New project name")
        Button(enabled = name.isNotBlank(), onClick = { vm.saveProject(Project(name = name.trim())); name = "" }) { Text("Create project") }
        projects.forEach { p ->
            var notes by remember(p.id) { mutableStateOf(p.notes) }
            NxCard {
                Text(p.name + if (s.activeProject == p.id) "  (active)" else "", style = MaterialTheme.typography.titleMedium)
                Text("${records.count { it.projectId == p.id }} records")
                Field(notes, { notes = it }, "Notes", singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { vm.setActiveProject(if (s.activeProject == p.id) -1 else p.id) }) { Text(if (s.activeProject == p.id) "Deactivate" else "Make active") }
                    TextButton(onClick = { vm.saveProject(p.copy(notes = notes)) }) { Text("Save notes") }
                    TextButton(onClick = { vm.deleteProject(p) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(vm: AppViewModel) {
    val records by vm.records.collectAsState(); val ctx = LocalContext.current
    ToolColumn {
        if (records.isEmpty()) Text("No history yet.")
        records.forEach { r -> NxCard {
            Text("${r.type} · ${r.target}", style = MaterialTheme.typography.titleMedium); Text(r.summary)
            Row { TextButton(onClick = { shareText(ctx, "${r.type} ${r.target}\n${r.summary}\n\n${r.data}") }) { Text("Export") }; TextButton(onClick = { vm.deleteRecord(r) }) { Text("Delete") } }
        } }
    }
}
