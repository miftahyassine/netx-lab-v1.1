# NETX LAB

Portable Android networking & Wi-Fi security laboratory (Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Room, DataStore, Coroutines/Flow).

> **Build status: not yet verified.** This source was written without access to the Android SDK, so it had **not been compiled or run** when it was delivered. Push it to GitHub, let the Actions workflow build it, and fix any compile errors reported in the log. Nothing in this README should be read as "tested on a device".

## Build
* **GitHub:** push the repo → *Actions → Android CI*. It runs unit tests, builds the debug APK and uploads it as artifact `netx-lab-debug-apk`.
* **Locally:** open in Android Studio (Koala+, JDK 17) and run `./gradlew assembleDebug`.
* **Release signing:** add secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, then run the workflow manually with *build_release* ticked. No credentials are stored in the repo.

## Compatibility
Min SDK 26 (Android 8.0), target/compile SDK 34. No root required.

## What is implemented (real operations only)
| Area | Notes |
|---|---|
| Dashboard | Wi-Fi/IP/gateway/DNS/MTU, quick actions, recent scans, favorites |
| Wi-Fi Lab | Real `WifiManager` scan, channel graph + congestion + channel recommendation, live RSSI history, export |
| Network Tools | Ping (system ping), traceroute (TTL pings), TCP port scanner (scope confirmation), LAN discovery (ICMP/TCP + rDNS + ARP cache), DNS client (own UDP/TCP implementation, 9 record types), IPv4/IPv6/VLSM calculators, converters, speed test (Cloudflare), HTTP/TLS inspector, connectivity tests |
| Packet Lab | Import PCAP/PCAPNG, filters, hex/ASCII view, stats, conversations, CSV export |
| Network map | Pan/zoom canvas, observed vs inferred links, SVG export |
| Security Lab | Wi-Fi security inspection, router checklist + report, gateway exposure check, password strength/entropy/generator, education, **clearly-labelled simulations**, 13-lesson offline learning center, authorized-target list |
| Terminal | Internal interpreter: help, ip/ifconfig, ping, traceroute, nslookup/dns, scan, ports, wifi, subnet, route, history, clear (+autocomplete, highlighting, history) |
| More | OUI lookup (small offline subset), Wi-Fi QR generator/parser, URL analyzer, public IP/geolocation/WHOIS (opt-in consent), JSON/Base64/hex, log viewer, projects, history, JSON export/import |
| Settings | Dark/Grey/White themes, information-icon toggle, haptics, timeout, privacy, developer mode |

## Android limitations (by design, not bugs)
* Wi-Fi scans are throttled (~4 per 2 min); results may be cached. SSID/BSSID need location/nearby-devices permission. Hidden SSIDs are shown as `<hidden>`.
* MAC addresses of LAN hosts are usually unavailable on Android 10+, and many devices use randomized MACs.
* **No monitor mode, raw 802.11 capture, or promiscuous capture.** VPNService live capture is **not implemented**; the Packet Lab analyses capture files only.
* ICMP uses the system `ping` binary; if a vendor ROM blocks it the app falls back to Java reachability.
* Not implemented on purpose: password cracking, deauthentication, evil twin, handshake capture, exploitation. The "Simulation" tab is fictional teaching material.
* Camera QR scanning is not included (paste the `WIFI:` text instead).
* The OUI table is a small subset, not the IEEE registry.

## Testing
`./gradlew testDebugUnitTest` runs JVM unit tests for subnet math, IPv6, VLSM, PCAP parsing, DNS packet building/parsing, port parsing, Wi-Fi helpers and password analysis. UI, Wi-Fi scanning, LAN discovery, permissions and speed test **require a real device and have not been run**.
