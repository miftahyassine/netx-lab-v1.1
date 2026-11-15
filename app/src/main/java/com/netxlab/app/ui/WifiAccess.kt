package com.netxlab.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object WifiAccess {
    fun problem(ctx: Context): String? {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) return "Wi-Fi is turned off."
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return "Precise Location permission is missing. Android hides SSIDs and scan results without it. Tap 'Allow' and choose PRECISE (not approximate)."
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
            return "'Nearby devices' permission is missing. Tap 'Allow'."
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm))
            return "The phone's Location switch is OFF. Android returns no Wi-Fi scan results and hides the SSID while it is off. Turn Location on (quick settings), then come back."
        return null
    }
}

/** Shows what is blocking Wi-Fi data (if anything) with one-tap fixes. Draws nothing when all is fine. */
@Composable
fun WifiAccessCard(onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { rev++; onChanged() } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val perms = remember { buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES) }.toTypedArray() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { rev++; onChanged() }
    val problem = remember(rev) { WifiAccess.problem(ctx) } ?: return
    NxCard {
        Text("Wi-Fi data is blocked", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(problem, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { launcher.launch(perms) }) { Text("Allow") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Location") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("App settings") }
        }
    }
}
