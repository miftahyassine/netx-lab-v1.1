package com.netxlab.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

data class LocalNet(
    val ip: String?, val prefix: Int?, val gateway: String?, val dns: List<String>,
    val iface: String?, val routes: List<String>, val validated: Boolean, val transport: String, val mtu: Int?
)

data class WifiConn(
    val connected: Boolean, val ssid: String?, val bssid: String?, val rssi: Int?, val freq: Int?,
    val linkSpeed: Int?, val rxSpeed: Int?, val security: String?, val ssidHidden: Boolean
)

object NetInfo {
    fun localNet(ctx: Context): LocalNet {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork
        val lp = cm.getLinkProperties(n)
        val caps = cm.getNetworkCapabilities(n)
        val v4 = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val gw = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val transport = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "other"
        }
        val mtu = try { lp?.interfaceName?.let { NetworkInterface.getByName(it)?.mtu } } catch (e: Exception) { null }
        return LocalNet(
            v4?.address?.hostAddress, v4?.prefixLength, gw, lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
            lp?.interfaceName, lp?.routes?.map { it.toString() } ?: emptyList(),
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true, transport, mtu
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun wifi(ctx: Context): WifiConn {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!onWifi) return WifiConn(false, null, null, null, null, null, null, null, false)
        val i: WifiInfo = try { wm.connectionInfo } catch (e: SecurityException) { return WifiConn(true, null, null, null, null, null, null, null, true) }
        val rawSsid = i.ssid
        val hidden = rawSsid == null || rawSsid == "<unknown ssid>" || rawSsid == WifiManager.UNKNOWN_SSID
        val bssid = i.bssid?.takeIf { it != "02:00:00:00:00:00" }
        var sec: String? = null
        if (Build.VERSION.SDK_INT >= 31) {
            sec = when (i.currentSecurityType) {
                0 -> "Open"; 1 -> "WEP"; 2 -> "WPA/WPA2-Personal (PSK)"; 3 -> "Enterprise (EAP)"; 4 -> "WPA3-Personal (SAE)"
                5 -> "Enhanced Open (OWE)"; -1 -> null; else -> "Security type ${i.currentSecurityType}"
            }
        }
        if (sec == null && bssid != null) {
            sec = try { wm.scanResults.firstOrNull { it.BSSID.equals(bssid, true) }?.capabilities?.let { WifiTools.securityOf(it).first } } catch (e: Exception) { null }
        }
        return WifiConn(
            true, if (hidden) null else rawSsid.trim('"'), bssid, i.rssi.takeIf { it > -127 }, i.frequency.takeIf { it > 0 },
            i.linkSpeed.takeIf { it > 0 }, if (Build.VERSION.SDK_INT >= 29) i.rxLinkSpeedMbps.takeIf { it > 0 } else null, sec, hidden
        )
    }

    fun interfaces(): String = buildString {
        val list = try { NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        for (n in list.sortedBy { it.name }) {
            if (!n.isUp) continue
            appendLine("${n.name}: mtu ${n.mtu}${if (n.isLoopback) " loopback" else ""}")
            val mac = try { n.hardwareAddress?.joinToString(":") { "%02x".format(it) } } catch (e: Exception) { null }
            appendLine("    link/ether ${mac ?: "(hidden by Android)"}")
            n.interfaceAddresses.forEach {
                val a = it.address
                appendLine("    ${if (a is Inet4Address) "inet" else "inet6"} ${a.hostAddress?.substringBefore('%')}/${it.networkPrefixLength}")
            }
        }
    }

    /** Reads the kernel ARP cache. Android 10+ usually blocks this for apps, so it may be empty. */
    fun arpTable(): Map<String, String> = try {
        java.io.File("/proc/net/arp").readLines().drop(1).mapNotNull {
            val p = it.split(Regex("\\s+"))
            if (p.size >= 4 && p[3] != "00:00:00:00:00:00") p[0] to p[3] else null
        }.toMap()
    } catch (e: Exception) { emptyMap() }
}
