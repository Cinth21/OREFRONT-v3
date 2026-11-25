# Setup Guide — OREFRONT v3.0 2.5D

1. Download and unzip `OREFRONT_v3_2_5D_Android_Source.zip` on your Mac.
2. Go to https://github.com/new and create an empty public repository named `OREFRONT-v3`.
3. Choose **Add file → Upload files** and drag the **contents of the unzipped folder** into GitHub, not the original ZIP. `app`, `tests`, `build.gradle`, `settings.gradle`, and `gradle.properties` must be present.
4. Confirm `.github/workflows/build-apk.yml` exists at the **repository root**. To show `.github` in Mac Finder press **Command + Shift + .** (period). If you cannot find it, create the file in GitHub manually using **Add file → Create new file**. Paste the full code from `COPY_WORKFLOW_TO_GITHUB.yml` in the downloaded project. DO NOT name the file `main.yml` or place it in `github/` without the leading dot.
5. Commit all changes and open **Actions**. Choose **Build OREFRONT v3 2.5D Android APK**. The build can start automatically on pushes to `main`, or via **Run workflow**.
6. If green, open that run → **Summary → Artifacts → OREFRONT-v3-Android-APK**. Download artifact ZIP, extract `OREFRONT-v3-2.5D.apk`.
7. To make a public link, choose GitHub **Releases → Draft a new release**, set the tag `v3.0.0`, title `OREFRONT 2.5D v3.0`, attach the `OREFRONT-v3-2.5D.apk` file, and publish. Share the resulting release page or the APK asset link in Messenger.
8. On Android: install and test AI first. Then test host + 1 client, then up to 6 separate phones using the same hotspot/Wi-Fi. Manual IP + code is the fallback to discovery.

**Important:** VS Code does not create a public APK link by itself. GitHub Actions creates the APK; GitHub Releases hosts the downloadable APK. All devices must install the same APK.
