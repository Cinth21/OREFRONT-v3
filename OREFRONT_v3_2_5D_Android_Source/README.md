# OREFRONT v3.0 — AI COMMAND WARS (2.5D Android project)

A **new, code-complete Android source project** upgraded from OREFRONT Enhanced v2.0. The graphics are now a more detailed, lightweight, **2.5D isometric RTS** (rendered on Android Canvas, not a true 3D engine). It retains the Java strategy core, offline AI, and phone-hosted LAN networking.

## V3 changes in source

- Multi-touch **pinch-to-zoom** (75–225%), one-finger **drag-to-pan**, on-screen **+ / − / FIT** camera controls
- Pure Java `IsoCamera` with exact tile-screen coordinate conversions; building placement and unit selection account for zoom and pan
- Battlefield is clipped to its panel; zoom no longer paints over the resource HUD or battle controls
- Terrain rebuilt with **extruded isometric tiles**, different biome palettes, roads, ridges, rock elevation, trees, shadows and faction capture pylons
- Individual 2.5D designs for **HQ, Mine, Refinery, Generator, Barracks, Factory, Airfield, Turret**
- 2.5D soldier, tank, aircraft and hero shapes, soft shadows, health bars, faction accents, subtle aircraft motion and selection indication
- Smooth local interpolation of moving unit display positions (network-authoritative gameplay positions remain unchanged)
- Subtle local muzzle flashes where the existing unit attack cooldown is active
- New automated `IsoCameraTest` and extended GitHub Actions workflow for v3
- Independent Android ID `com.student.orefront.v3`, versionCode 3 / versionName `3.0.0`, so it can install beside v2

The UI is a **stylized vector illustration**: this is *not* the photorealistic 3D gameplay shown in the previously generated concept poster. Textures, imported 3D models and realistic lighting are deliberately excluded to keep APK size and phone performance manageable.

## Game systems preserved

- Offline AI mode: Easy / Medium / Hard and 2–5 AI opponent factions
- Shared Gold, Oil, Iron, Energy economies and player-specific ownership
- Place and upgrade structures, recruit soldiers/tanks/aircraft/heroes, issue movement orders, capture sectors and win matches
- Up to 6 total human players (one host + 5 clients) on same Wi-Fi / phone hotspot; no public servers
- Lobby, six-digit room code, UDP discovery, manual host IP, ready state, team configuration, host start
- 1v1, 2v2, 3v3, Free-for-All, CUSTOM teams; host-authoritative events and snapshots
- Disconnect preservation, reconnect, optional host AI takeover; results synchronized

## Build APK through GitHub — no Android Studio on your MacBook

1. Download and **extract** `OREFRONT_v3_2_5D_Android_Source.zip`.
2. In GitHub create a repository such as `OREFRONT-v3`. (Recommended: keep the existing v2 repository unchanged.)
3. Use **Add file → Upload files**. Upload the **CONTENTS** of this extracted folder: `app/`, `tests/`, `settings.gradle`, `build.gradle`, `gradle.properties`, etc. Do not upload only the ZIP.
4. Upload `.github/workflows/build-apk.yml`. To display `.github` in Mac Finder, press **Command + Shift + period**. If Finder still hides it, GitHub **Add file → Create new file** and set the name exactly to `.github/workflows/build-apk.yml`. Copy the contents of the included, visible `COPY_WORKFLOW_TO_GITHUB.yml` into the editor and commit. **Do not create duplicates.**
5. Open **Actions → Build OREFRONT v3 2.5D Android APK**, then start **Run workflow** if necessary. When it passes, open the run **Summary → Artifacts → OREFRONT-v3-Android-APK**.
6. Extract the downloaded artifact ZIP to get **`OREFRONT-v3-2.5D.apk`**. This is a debug APK suitable for classroom testing.
7. To share via Messenger, go to **Releases → Draft a new release** in the same GitHub repo; create tag `v3.0.0`, title `OREFRONT 2.5D v3.0`, attach **`OREFRONT-v3-2.5D.apk`**, and click **Publish release**. Copy the public release page link or the APK asset URL.

The workflow searches automatically for `settings.gradle` either in the repository root or in an extracted subfolder (up to three levels deep). However, **the `.github/workflows` file itself must be in the actual repository root** so GitHub can detect the workflow.

## How to play / gesture changes

**AI:** Open the installed APK → PLAY AGAINST AI → choose difficulty. Tap BUILD and place buildings near your HQ; tap UNITS to train; tap your unit then a destination to move. The AI handles automated economy and army decisions.

**Camera:** Drag in the battlefield to pan. Pinch inside the battlefield to zoom. Alternatively use **+**, **−**, **FIT** at the right edge of the map. Simple taps on the map still select and order units; the hit test accounts for camera position.

**LAN:** Enable hotspot on one Android phone (or connect all to the same Wi-Fi). Host taps Local LAN Multiplayer → Create Room, then shares displayed room code and host IP. Up to five other Android phones join via Search LAN Rooms or Join By Code + IP. All mark Ready. Host starts. Keep the hosting phone unlocked and the game open during the match.

## Tests and remaining risks

**Passed locally in Java:** strategy/AI game rules (including 105 simulations), 1-host + 5-client genuine TCP socket test on one machine, results sync, turret defenses, and 216 isometric tile inverse-projection camera cases, zoom/clamp/pan checks. The Android UI code compiled against **minimal temporary Android API stubs** only (type/syntax check).

**NOT performed here:** Actual Gradle/Android SDK APK build, Android rendering at runtime, and six separate physical Android devices connected on a hotspot. These require GitHub Actions and manual devices. An online build succeeding **does not prove** the six-phone final acceptance case.

**Device constraints:** Some hotspots limit peer devices or isolate connected phones; switch to a Wi-Fi access point supporting 6 devices when needed. UDP discovery can fail even when manual IP + room code works. Host migration does not exist; host must remain in the match.

## Main files

- `app/src/main/java/com/student/orefront/MainActivity.java` — menus, battle art, animations, camera gestures and controls
- `app/src/main/java/com/student/orefront/IsoCamera.java` — pure-Java camera projection and inverse touch hit test
- `app/src/main/java/com/student/orefront/GameCore.java` — unchanged authoritative economy, AI, combat, units, victory
- `app/src/main/java/com/student/orefront/LanRoom.java` — unchanged host-phone LAN networking
- `tests/` — strategy, network, results, turret, v3 camera tests
- `.github/workflows/build-apk.yml` — cloud APK builder
- `COPY_WORKFLOW_TO_GITHUB.yml` — visible duplicate for manual GitHub setup if the hidden folder does not show in Finder
- `docs/Concept_Reference_Only.png` — image of long-term art direction, **not** a screenshot of this app
