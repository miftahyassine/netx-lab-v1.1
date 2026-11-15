package com.netxlab.app.core

data class Lesson(
    val id: String, val title: String, val body: String, val question: String,
    val options: List<String>, val answer: Int, val hint: String, val solution: String
)

object Lessons {
    val OSI = listOf(
        "7 Application" to "Network services used by apps: HTTP, DNS, SMTP.",
        "6 Presentation" to "Data formats, encryption, compression (TLS is often placed here).",
        "5 Session" to "Starting, keeping and ending conversations between hosts.",
        "4 Transport" to "End-to-end delivery: TCP (reliable) and UDP (fast, no guarantees). Ports live here.",
        "3 Network" to "Logical addressing and routing: IP, ICMP.",
        "2 Data Link" to "Frames between neighbours on one link: Ethernet, Wi-Fi (802.11), ARP, MAC addresses.",
        "1 Physical" to "Bits on the medium: radio, copper, fibre."
    )

    val ALL = listOf(
        Lesson("tcpip", "TCP/IP fundamentals",
            "The Internet uses four practical layers: Link, Internet (IP), Transport (TCP/UDP) and Application. Each layer wraps the data from the layer above in its own header (encapsulation).",
            "Which layer decides how a packet is routed between networks?", listOf("Link", "Internet (IP)", "Transport", "Application"), 1,
            "Routers look at logical addresses.", "The Internet layer (IP) carries source/destination IP addresses used for routing."),
        Lesson("osi", "OSI model",
            "The OSI model splits networking into seven layers. Tap the layers in the interactive diagram on this screen to read what each does.",
            "Ethernet frames and MAC addresses belong to which OSI layer?", listOf("Layer 1", "Layer 2", "Layer 3", "Layer 4"), 1,
            "Think 'data link'.", "Layer 2 (Data Link) uses MAC addresses and frames."),
        Lesson("dns", "DNS",
            "DNS translates names to records. A resolver asks root, TLD and authoritative servers (or answers from cache). Records: A/AAAA (addresses), MX (mail), CNAME (alias), TXT, NS, SOA, PTR, SRV. Try them in Network Tools → DNS.",
            "Which record maps a name to an IPv6 address?", listOf("A", "AAAA", "MX", "PTR"), 1, "Four times the size of A.", "AAAA records hold IPv6 addresses."),
        Lesson("dhcp", "DHCP",
            "DHCP hands out IP address, mask, gateway and DNS using four steps: Discover, Offer, Request, Acknowledge (DORA).",
            "What does the 'O' in DORA stand for?", listOf("Open", "Offer", "Origin", "Option"), 1, "The server proposes a lease.", "Offer: the server offers an address lease."),
        Lesson("arp", "ARP",
            "ARP maps an IPv4 address to a MAC address on the local link. A host broadcasts 'who has 192.168.1.1?' and the owner replies with its MAC. ARP has no authentication, which is why ARP spoofing is possible on unprotected LANs.",
            "ARP is used to find a…", listOf("MAC for a known IPv4 address", "IP for a hostname", "route to the Internet", "port for a service"), 0,
            "It works inside one broadcast domain.", "ARP resolves IPv4 → MAC on the local network."),
        Lesson("tcpudp", "TCP and UDP",
            "TCP is connection-oriented (3-way handshake SYN, SYN-ACK, ACK), ordered and retransmitting. UDP is connectionless and has less overhead; DNS, VoIP and games often use it.",
            "Which flag pair starts a TCP connection?", listOf("FIN/ACK", "SYN then SYN-ACK", "RST/PSH", "URG/ACK"), 1, "Three-way handshake.", "SYN → SYN-ACK → ACK."),
        Lesson("routing", "Routing",
            "Routers forward packets using a routing table and the longest-prefix match. A default route (0.0.0.0/0) sends everything unknown to the gateway. Each router decrements TTL; traceroute uses that.",
            "Which route wins for 10.1.2.3: 10.0.0.0/8 or 10.1.2.0/24?", listOf("10.0.0.0/8", "10.1.2.0/24", "Either", "Neither"), 1, "Most specific prefix.", "Longest-prefix match picks /24."),
        Lesson("vlan", "VLANs",
            "VLANs (802.1Q) split one physical switch into separate broadcast domains by tagging frames with a 12-bit VLAN ID. Devices in different VLANs need a router to communicate, which lets you apply firewall rules between them.",
            "How many bits is the VLAN ID in an 802.1Q tag?", listOf("8", "10", "12", "16"), 2, "Gives 4094 usable IDs.", "12 bits."),
        Lesson("subnet", "Subnetting",
            "A /24 has 256 addresses (254 usable). Each extra prefix bit halves the block. Use the IP Toolkit to check your answers.",
            "How many usable hosts are in a /26?", listOf("30", "62", "126", "64"), 1, "2^(32-26) - 2.", "64 addresses minus network and broadcast = 62."),
        Lesson("wifisec", "Wi-Fi security",
            "WEP is broken. WPA2-Personal is safe with a long random passphrase. WPA3-Personal (SAE) resists offline guessing. Disable WPS and keep router firmware updated.",
            "Which Wi-Fi security mode is the most modern for home use?", listOf("WEP", "WPA", "WPA2-TKIP", "WPA3-SAE"), 3, "Newest standard.", "WPA3-Personal (SAE)."),
        Lesson("scan", "Network scanning",
            "Scanning checks which hosts and ports respond. Only scan systems you own or have written permission to test. 'Open' means a service accepted a connection; 'closed' means refused; 'filtered' usually means a firewall dropped the probe.",
            "A connection that times out with no reply is usually reported as…", listOf("open", "closed", "filtered", "encrypted"), 2, "Firewalls often drop silently.", "Filtered."),
        Lesson("pcap", "Packet analysis",
            "A capture file stores packets with timestamps. Start with protocol statistics, then look at conversations, then individual packets. In Packet Lab you can import PCAP/PCAPNG files and inspect headers and bytes.",
            "In a TCP packet, which flag marks a connection reset?", listOf("SYN", "ACK", "RST", "PSH"), 2, "Abrupt close.", "RST."),
        Lesson("fw", "Firewall concepts",
            "A stateful firewall tracks connections: replies to outbound traffic are allowed, unsolicited inbound traffic is dropped by default. Good rules follow least privilege and default deny.",
            "Default-deny means…", listOf("Allow everything unless blocked", "Block everything unless allowed", "Block only known malware", "Allow only HTTP"), 1,
            "Think allow-list.", "Anything not explicitly allowed is blocked.")
    )
}
