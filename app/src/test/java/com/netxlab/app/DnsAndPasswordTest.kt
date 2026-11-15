package com.netxlab.app

import com.netxlab.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsAndPasswordTest {
    @Test fun buildQueryLayout() {
        val q = DnsClient.buildQuery("example.com", 1, 0x1234)
        assertEquals(0x12, q[0].toInt()); assertEquals(0x34, q[1].toInt()); assertEquals(7, q[12].toInt())
        assertEquals(12 + 1 + 7 + 1 + 3 + 1 + 4, q.size)
    }
    @Test fun reverseNames() {
        assertEquals("4.3.2.1.in-addr.arpa", DnsClient.reverseName("1.2.3.4"))
        assertTrue(DnsClient.reverseName("::1")!!.endsWith(".ip6.arpa"))
    }
    @Test fun parsesARecordWithCompression() {
        val o = ByteArrayOutputStream()
        fun w16(v: Int) { o.write(v shr 8); o.write(v and 255) }
        w16(1); w16(0x8180); w16(1); w16(1); w16(0); w16(0)
        o.write(byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0)); w16(1); w16(1)
        w16(0xC00C); w16(1); w16(1); w16(0); w16(300); w16(4); o.write(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        val r = DnsClient.parse(o.toByteArray(), "test", 5, "UDP")
        assertEquals("NOERROR", r.rcode); assertEquals(1, r.answers.size)
        assertEquals("93.184.216.34", r.answers[0].data); assertEquals("example.com.", r.answers[0].name); assertEquals(300L, r.answers[0].ttl)
    }
    @Test fun portParsing() {
        assertEquals(listOf(22, 80, 8000, 8001, 8002), NetTools.parsePorts("22, 80,8000-8002,99999,abc"))
        assertTrue(NetTools.validHost("example.com")); assertFalse(NetTools.validHost("-c 100")); assertFalse(NetTools.validHost("a b"))
    }
    @Test fun passwordWeakAndStrong() {
        assertEquals("Very weak", PasswordLab.analyze("password").rating)
        assertTrue(PasswordLab.analyze("123456").adjustedBits < 28)
        assertTrue(PasswordLab.analyze("k7#Qv9!pLm2@xZr8Tn4&").adjustedBits > 80)
        assertEquals(24, PasswordLab.generate(24, true).length)
    }
    @Test fun wifiHelpers() {
        assertEquals(1, WifiTools.channelOf(2412)); assertEquals(6, WifiTools.channelOf(2437)); assertEquals(36, WifiTools.channelOf(5180)); assertEquals(1, WifiTools.channelOf(5955))
        assertEquals("WPA3-Personal", WifiTools.securityOf("[RSN-SAE-CCMP][ESS]").first)
        assertEquals("WPA2-Personal", WifiTools.securityOf("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]").first)
        assertEquals("Open", WifiTools.securityOf("[ESS]").first); assertEquals("WEP", WifiTools.securityOf("[WEP][ESS]").first)
    }
    @Test fun oui() {
        assertEquals("Raspberry Pi Foundation", Oui.lookup("b8:27:eb:11:22:33")); assertTrue(Oui.isRandomized("da:a1:19:00:00:00"))
    }
}
