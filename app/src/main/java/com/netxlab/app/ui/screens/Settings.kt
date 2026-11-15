@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.netxlab.app.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.netxlab.app.core.AppLog
import com.netxlab.app.data.AppTheme
import com.netxlab.app.data.AppViewModel
import com.netxlab.app.ui.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsState(); val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }; var confirmClear by remember { mutableStateOf(false) }; var exportText by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u: Uri? ->
        if (u != null) ctx.contentResolver.openOutputStream(u)?.use { it.write(exportText.toByteArray()) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) { val t = ctx.contentResolver.openInputStream(u)?.use { it.readBytes().decodeToString() } ?: ""; vm.importJson(t) { msg = it } }
    }
    ToolColumn {
        NxCard {
            SectionTitle("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AppTheme.values().forEach { t -> FilterChip(s.theme == t, { vm.setTheme(t) }, label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            SwitchRow("Show Information Icons", s.infoIcons) { vm.setInfoIcons(it) }
        }
        NxCard {
            SectionTitle("Behavior")
            SwitchRow("Haptic feedback", s.haptics) { vm.setHaptics(it) }
            SwitchRow("Sound feedback (stored preference)", s.sound) { vm.setSound(it) }
            Text("Default scan timeout: ${s.timeoutMs} ms"); Slider(s.timeoutMs.toFloat(), { vm.setTimeout(it.toInt()) }, valueRange = 300f..5000f)
        }
        NxCard {
            SectionTitle("Privacy")
            SwitchRow("Allow external services (public IP, geolocation, WHOIS)", s.externalConsent) { vm.setConsent(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { scope.launch { exportText = vm.exportJson(); export.launch("netxlab-export.json") } }) { Text("Export data") }
                OutlinedButton(onClick = { import.launch(arrayOf("application/json", "*/*")) }) { Text("Import") }
            }
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear history, hosts & projects") }
            if (msg.isNotEmpty()) Text(msg)
            Text("All data stays on this device unless you export or enable external services.", style = MaterialTheme.typography.labelSmall)
        }
        NxCard {
            SectionTitle("Advanced")
            SwitchRow("Developer mode", s.developer) { vm.setDeveloper(it) }
            if (s.developer) {
                KeyValue("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"); KeyValue("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
                OutputBox(AppLog.all().joinToString("\n").ifEmpty { "(no debug log entries)" }, "Debug log")
            }
        }
        NxCard {
            SectionTitle("About NETX LAB")
            Text("Version 1.0.0 · min Android 8.0 (API 26) · Kotlin, Jetpack Compose, Room, Hilt.", style = MaterialTheme.typography.bodyMedium)
            Text("Open-source components: AndroidX / Jetpack Compose (Apache-2.0), Kotlin & kotlinx.coroutines (Apache-2.0), Dagger Hilt (Apache-2.0), Room (Apache-2.0), ZXing core (Apache-2.0).", style = MaterialTheme.typography.labelSmall)
            Text("Use only on networks you own or are authorized to test.", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false; msg = "Cleared" }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        title = { Text("Delete local data?") }, text = { Text("This removes scan history, saved hosts and projects.") })
}

@Composable
private fun SwitchRow(label: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(v, onChange) }
}
