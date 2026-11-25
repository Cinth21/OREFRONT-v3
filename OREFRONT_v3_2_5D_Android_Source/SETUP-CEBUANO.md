# OREFRONT Enhanced v2.0 — Cebuano GitHub APK Guide

**Bro, same ra ni sa imong previous Snake ug Rift Beasts GitHub build. Dili na kinahanglan Android Studio sa MacBook.**

## 1. Source files

- I-download ug i-extract ang `OREFRONT_Enhanced_Android_Source.zip`.
- Create new GitHub repository `OREFRONT-Enhanced` (suggested; separate from old OREFRONT).
- Sa repository, **Add file → Upload files** ug i-drag ang tanang **sulod** sa extracted folder, dili ang ZIP mismo.
- Dapat naa sa root ang: `app/`, `tests/`, `build.gradle`, `gradle.properties`, `settings.gradle`.
- Dapat naa ang hidden file nga `.github/workflows/build-apk.yml`. Sa Finder, press **Command + Shift + .** aron makita ang hidden files.
- Kung wala, sa GitHub: **Add file → Create new file**, filename = `.github/workflows/build-apk.yml`, unya copy/paste ang code gikan sa extracted file nga parehas og filename. Ayaw paghimo ug duplicate kung naa na.

## 2. Build

- Click **Commit changes**.
- Click **Actions → Build OREFRONT Enhanced Android APK**.
- Kung red X, click build job ug send screenshot sa **exact error**; ayaw i-delete ang repo.
- Kung green check, click **Summary → Artifacts → OREFRONT-Enhanced-Android-APK**.
- Download artifact ZIP ug extract para makuha ang **`OREFRONT-Enhanced.apk`**.

## 3. Messenger link

- Sa GitHub: **Releases → Draft new release**.
- Tag = `v2.0.0`; Release title = `OREFRONT Enhanced v2.0`.
- Upload **`OREFRONT-Enhanced.apk`** ngadto sa **Attach binaries / Assets**.
- Click **Publish release**. Tan-awa nga ang **Assets** naglakip sa `.apk`, dili lang `Source code (zip)`.
- I-copy ang release link ug isend sa Messenger; sa Android phones ra ang `.apk` pwede ma-install.

## 4. Play ug testing

1. Test **Play Against AI** una sa usa ka Android phone.
2. Para LAN, Phone 1 mag-enable hotspot; Phones 2–6 mo-connect sa same Wi-Fi/hotspot.
3. Phone 1 taps **LOCAL LAN MULTIPLAYER** ug displays **Room Code** + **Host IP**.
4. Other phones → **SEARCH LAN ROOMS** or **JOIN BY CODE + IP**. Enter room code and IP if discovery fails.
5. Everyone taps **I'M READY**, host chooses **3v3** for six players and clicks **START MATCH**.
6. Each phone must control a different color/base, buy units, move, capture sectors, and see the same match result.
7. Disconnect and rejoin test. Base must remain; host can assign AI to a disconnected faction.

**Important:** Ang desktop-socket tests passed, pero wala pa na-test ang final version sa six actual Android phones. Need actual phone testing aron ma-claim nga fully accepted ni Sir.
