# OREFRONT v3.0 — Test Report

| Component | Result | Actual coverage |
|---|---|---|
| Strategy / AI | PASS (local Java) | 105 AI simulations, six slots, resources, purchases, ownership, movement, reconnect |
| Host + 5 clients LAN | PASS (local sockets) | Six genuine TCP endpoints on one machine; room cap, ready/start, updates, takeover, reconnect |
| Results | PASS | Game result and timer serialization |
| Turret defenses | PASS | Construction, attack, serialized ownership and intelligence |
| Isometric camera | PASS | 216 inverse tile hit tests, zoom anchor, drag, reset, zoom clamp |
| Android activity source | PASS (stubs) | Javac type/syntax check against temporary Android API stubs, not a real Android SDK |
| GitHub/Android APK build | NOT YET RUN | Requires workflow execution; compilation not verified here |
| Android 2.5D rendering/gesture runtime | NOT YET TESTED | Requires physical phone or Android device emulator |
| Six physical Android phones | NOT YET TESTED | Required before claiming final multiplayer acceptance |

## Manual acceptance checklist

- [ ] GitHub Actions green; APK extracted and installed
- [ ] AI match launches without crashes, resource actions and units work
- [ ] Drag-to-pan does not accidentally order a unit
- [ ] Pinch-to-zoom works; zoomed taps correctly select units and tiles
- [ ] On-map + / − / FIT controls work; UI remains visible when zoomed
- [ ] At least two phones connect to the host over hotspot
- [ ] Six different Android phones join one room and show six human identities
- [ ] The seventh phone is refused
- [ ] Host starts 3v3; six players build separate bases, collect resources and command distinct units
- [ ] All six receive consistent combat results and final match result
- [ ] Disconnect an active player; keep base alive; reconnect by same device token
- [ ] Test 1v1, 2v2, FFA, custom teams and both room discovery and manual IP
- [ ] Observe FPS, CPU heat, and battery consumption on actual target phones

This is a student prototype, not a certified or production-ready multiplayer game. No physical device test results are fabricated.
