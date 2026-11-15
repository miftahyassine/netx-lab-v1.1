package com.netxlab.app

import com.netxlab.app.core.PcapParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapParserTest {
    private fun tcpSynFrame(): ByteArray {
        val b = ByteBuffer.allocate(54)
        b.put(ByteArray(6) { 0x11 }); b.put(ByteArray(6) { 0x22 }); b.putShort(0x0800)
        b.put(0x45); b.put(0); b.putShort(40); b.putShort(1); b.putShort(0); b.put(64); b.put(6); b.putShort(0)
        b.put(byteArrayOf(192.toByte(), 168.toByte(), 1, 2)); b.put(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        b.putShort(12345); b.putShort(80); b.putInt(1); b.putInt(0); b.put(0x50); b.put(0x02); b.putShort(1000); b.putShort(0); b.putShort(0)
        return b.array()
    }
    private fun pcap(frame: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(24 + 16 + frame.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0xa1b2c3d4.toInt()); b.putShort(2); b.putShort(4); b.putInt(0); b.putInt(0); b.putInt(65535); b.putInt(1)
        b.putInt(100); b.putInt(500000); b.putInt(frame.size); b.putInt(frame.size); b.put(frame)
        return b.array()
    }
    @Test fun parsesTcpSyn() {
        val r = PcapParser.parse(pcap(tcpSynFrame()))
        assertEquals(1, r.packets.size)
        val p = r.packets[0]
        assertEquals("TCP", p.proto); assertEquals("192.168.1.2", p.src); assertEquals("93.184.216.34", p.dst)
        assertEquals(12345, p.sport); assertEquals(80, p.dport); assertEquals("SYN", p.flags); assertEquals(100.5, p.ts, 0.0001)
        assertTrue(PcapParser.matches(p, "tcp")); assertFalse(PcapParser.matches(p, "udp")); assertTrue(PcapParser.matches(p, "80")); assertTrue(PcapParser.matches(p, "ipv4"))
    }
    @Test fun rejectsGarbage() { assertThrows(IllegalArgumentException::class.java) { PcapParser.parse(ByteArray(64)) } }
    @Test fun truncatedFileDoesNotCrash() {
        val full = pcap(tcpSynFrame()); val r = PcapParser.parse(full.copyOf(full.size - 10)); assertEquals(0, r.packets.size)
    }
    @Test fun hexDump() { assertTrue(PcapParser.hexDump(byteArrayOf(0x41, 0x42)).contains("41 42")) }
}
