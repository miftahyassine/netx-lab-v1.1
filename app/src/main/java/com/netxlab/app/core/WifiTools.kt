package com.netxlab.app.core

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.log10
import kotlin.math.pow

data class Ap(
    val ssid: String, val bssid: String, val rssi: Int, val freq: Int, val channel: Int, val widthMhz: Int?,
    val security: String, val capabilities: String, val wps: Boolean, val band: String, val quality: Int, val distance: String
)

data class ScanOutcome(val aps: List<Ap>, val fresh: Boolean, val startRefused: Boolean, val note: String)

object WifiTools {
    fun channelOf(f: Int): Int = when {
        f == 2484 -> 14
        f in 2412..2472 -> (f - 2412) / 5 + 1
        f in 5160..5885 -> (f - 5000) / 5
        f in 5955..7115 -> (f - 5950) / 5
        else -> -1
    }

    fun bandOf(f: Int) = when { f < 3000 -> "2.4 GHz"; f < 5900 -> "5 GHz"; else -> "6 GHz" }

    fun qualityOf(rssi: Int) = (2 * (rssi + 100)).coerceIn(0, 100)

    /** Free-space path-loss estimate. Real walls and interference make this very approximate. */
    fun distanceCategory(rssi: Int, freq: Int): String {
        val d = 10.0.pow((27.55 - 20 * log10(freq.toDouble()) + kotlin.math.abs(rssi)) / 20.0)
        return when { d < 5 -> "Very near (~<5 m)"; d < 15 -> "Near (~5–15 m)"; d < 40 -> "Medium (~15–40 m)"; else -> "Far (~>40 m)" }
    }

    /** Returns (label, note). */
    fun securityOf(cap: String): Pair<String, String> {
        val c = cap.uppercase()
        return when {
            c.contains("WEP") -> "WEP" to "Broken: keys can be recovered quickly. Replace with WPA2/WPA3."
            c.contains("EAP") && (c.contains("SUITE-B") || c.contains("WPA3")) -> "WPA3-Enterprise" to "Per-user authentication; strongest option."
            c.contains("EAP") -> "WPA2/WPA-Enterprise" to "Per-user authentication (802.1X); depends on server configuration."
            c.contains("SAE") && c.contains("PSK") -> "WPA2/WPA3 transition" to "Supports WPA3 but also allows older WPA2 clients."
            c.contains("SAE") -> "WPA3-Personal" to "Modern; resists offline guessing of captured handshakes."
            c.contains("OWE") -> "Enhanced Open (OWE)" to "Encrypts open networks, but gives no authentication."
            c.contains("RSN") || c.contains("WPA2") -> "WPA2-Personal" to "Good with a long random passphrase; weak passphrases can be guessed offline."
            c.contains("WPA") -> "WPA (legacy)" to "Outdated (TKIP-era). Upgrade to WPA2/WPA3."
            else -> "Open" to "No encryption: anyone nearby can read unencrypted traffic."
        }
    }

    @Suppress("DEPRECATION")
    private fun toAp(r: ScanResult): Ap {
        val w = when (r.channelWidth) { 0 -> 20; 1 -> 40; 2 -> 80; 3 -> 160; 4 -> 160; 5 -> 320; else -> null }
        val sec = securityOf(r.capabilities ?: "")
        return Ap(
            r.SSID.takeIf { !it.isNullOrBlank() } ?: "<hidden>", r.BSSID ?: "", r.level, r.frequency, channelOf(r.frequency), w,
            sec.first, r.capabilities ?: "", (r.capabilities ?: "").contains("WPS"), bandOf(r.frequency),
            qualityOf(r.level), distanceCategory(r.level, r.frequency)
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun scan(ctx: Context): ScanOutcome = withContext(Dispatchers.Main) {
        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) return@withContext ScanOutcome(emptyList(), false, false, "Wi-Fi is turned off.")
        @Suppress("DEPRECATION")
        val started = try { wm.startScan() } catch (e: SecurityException) { false }
        var fresh = false
        if (started) {
            fresh = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Boolean> { c ->
                    val r = object : BroadcastReceiver() {
                        override fun onReceive(x: Context?, i: Intent?) {
                            try { app.unregisterReceiver(this) } catch (_: Exception) {}
                            if (c.isActive) c.resume(i?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true)
                        }
                    }
                    ContextCompat.registerReceiver(app, r, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
                    c.invokeOnCancellation { try { app.unregisterReceiver(r) } catch (_: Exception) {} }
                }
            } ?: false
        }
        val results = try { wm.scanResults.map { toAp(it) } } catch (e: SecurityException) {
            return@withContext ScanOutcome(emptyList(), false, !started, "Permission denied. Grant location / nearby devices access.")
        }
        val blocked = if (results.isEmpty()) com.netxlab.app.ui.WifiAccess.problem(ctx) else null
        val note = when {
            blocked != null -> blocked
            !started -> "Android refused a new scan (throttling: ~4 scans / 2 min in foreground, or scan disabled). Showing cached results from the system."
            !fresh -> "The scan did not complete in time; results may be cached or incomplete."
            else -> "Fresh scan completed. Android may still omit hidden SSIDs and some access points."
        }
        ScanOutcome(results.sortedByDescending { it.rssi }, fresh, !started, note)
    }

    fun congestion24(aps: List<Ap>): Map<Int, Double> = (1..13).associateWith { ch ->
        aps.filter { it.band == "2.4 GHz" && it.channel > 0 && kotlin.math.abs(it.channel - ch) < 5 }
            .sumOf { 10.0.pow(it.rssi / 10.0) * 1e9 }
    }

    fun recommend(aps: List<Ap>, band: String): String {
        if (aps.none { it.band == band }) return "No networks seen on $band."
        return if (band == "2.4 GHz") {
            val c = congestion24(aps).filterKeys { it in listOf(1, 6, 11) }
            val best = c.minByOrNull { it.value }!!
            "Least congested non-overlapping channel: ${best.key} (based on the networks seen right now)."
        } else {
            val cands = if (band == "5 GHz") listOf(36, 40, 44, 48, 149, 153, 157, 161) else listOf(1, 5, 9, 13, 17, 21, 25, 29)
            val best = cands.minByOrNull { ch -> aps.filter { it.band == band && it.channel == ch }.sumOf { 10.0.pow(it.rssi / 10.0) } }!!
            "Least used candidate channel: $best (width and DFS rules may restrict some channels in your region)."
        }
    }
}
