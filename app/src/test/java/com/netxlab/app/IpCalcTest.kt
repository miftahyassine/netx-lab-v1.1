package com.netxlab.app

import com.netxlab.app.core.IpCalc
import org.junit.Assert.*
import org.junit.Test

class IpCalcTest {
    @Test fun ipv4Basic() {
        val i = IpCalc.calc("192.168.1.10/24")!!
        assertEquals("192.168.1.0", i.network); assertEquals("192.168.1.255", i.broadcast)
        assertEquals("192.168.1.1", i.first); assertEquals("192.168.1.254", i.last)
        assertEquals(254L, i.hosts); assertEquals("255.255.255.0", i.mask); assertEquals("0.0.0.255", i.wildcard)
    }
    @Test fun maskNotation() { assertEquals(26, IpCalc.calc("10.0.0.5 255.255.255.192")!!.cidr) }
    @Test fun edgePrefixes() {
        assertEquals(2L, IpCalc.calc("10.0.0.0/31")!!.hosts); assertEquals(1L, IpCalc.calc("10.0.0.1/32")!!.hosts)
        assertEquals(62L, IpCalc.calc("10.0.0.0/26")!!.hosts)
    }
    @Test fun invalid() { assertNull(IpCalc.calc("300.1.1.1/24")); assertNull(IpCalc.calc("1.2.3.4/33")); assertNull(IpCalc.calc("abc")); assertNull(IpCalc.calc("1.2.3.4 255.0.255.0")) }
    @Test fun split() {
        assertEquals(listOf("192.168.0.0/26", "192.168.0.64/26", "192.168.0.128/26", "192.168.0.192/26"), IpCalc.split("192.168.0.0/24", 26))
        assertNull(IpCalc.split("192.168.0.0/24", 20))
    }
    @Test fun vlsm() {
        val r = IpCalc.vlsm("10.0.0.0/24", listOf(50, 100, 20, 2))!!
        assertEquals("10.0.0.0/25", r[0].cidr); assertEquals("10.0.0.128/26", r[1].cidr)
        assertEquals("10.0.0.192/27", r[2].cidr); assertEquals("10.0.0.224/30", r[3].cidr)
        assertNull(IpCalc.vlsm("10.0.0.0/30", listOf(10)))
    }
    @Test fun ipv6() {
        val p = IpCalc.parseV6("2001:db8:0:0:0:0:0:1/64")!!
        assertEquals("2001:db8::1", p.compressed); assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", p.expanded)
        assertEquals("2001:db8::/64", p.network); assertEquals("Documentation (2001:db8::/32)", p.type)
        assertEquals("Loopback (::1)", IpCalc.parseV6("::1")!!.type); assertEquals("Link-local (fe80::/10)", IpCalc.parseV6("fe80::1")!!.type)
        assertNull(IpCalc.parseV6("1.2.3.4"))
    }
    @Test fun macAndNumbers() {
        assertEquals("AA:BB:CC:DD:EE:FF", IpCalc.formatMac("aa-bb-cc-dd-ee-ff")); assertNull(IpCalc.formatMac("xyz"))
        assertEquals("255", IpCalc.convertNumber("ff", 16)!!.toString())
        assertEquals("11000000.10101000.00000001.00000001", IpCalc.toBinary(IpCalc.parseV4("192.168.1.1")!!))
    }
}
