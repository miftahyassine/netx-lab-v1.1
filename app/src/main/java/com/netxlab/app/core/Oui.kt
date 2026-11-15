package com.netxlab.app.core

/** Small offline subset of well-known OUI prefixes. It is NOT the full IEEE registry. */
object Oui {
    private val table = mapOf(
        "B827EB" to "Raspberry Pi Foundation", "DCA632" to "Raspberry Pi Trading", "E45F01" to "Raspberry Pi Trading",
        "005056" to "VMware", "000C29" to "VMware", "080027" to "VirtualBox (PCS Systemtechnik)",
        "00155D" to "Microsoft (Hyper-V)", "525400" to "QEMU/KVM virtual NIC",
        "3C5AB4" to "Google", "F4F5D8" to "Google", "001A11" to "Google",
        "001B63" to "Apple", "000393" to "Apple", "0017F2" to "Apple", "000A95" to "Apple", "001CB3" to "Apple", "002500" to "Apple", "001EC2" to "Apple",
        "001247" to "Samsung", "0007AB" to "Samsung",
        "001B21" to "Intel", "0002B3" to "Intel",
        "00000C" to "Cisco", "00095B" to "Netgear", "00055D" to "D-Link", "000625" to "Linksys"
    )

    fun isRandomized(mac: String): Boolean {
        val first = mac.filter { it.isLetterOrDigit() }.take(2).toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }

    fun lookup(mac: String): String? {
        val hex = mac.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length < 6) return null
        return table[hex.take(6)]
    }

    fun describe(mac: String): String {
        val v = lookup(mac)
        return when {
            v != null -> v
            isRandomized(mac) -> "Locally administered (likely a randomized/private MAC)"
            else -> "Not in the built-in subset"
        }
    }
}
