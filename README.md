# runelite-android

An unofficial Android port of [RuneLite](https://runelite.net), the open-source Old School RuneScape client, packaged as a standalone APK (`net.runelite.mp`).

---

## What it is

`runelite-android` runs upstream RuneLite on Android: the same `runelite-client`, plugin system and `runelite-api`, against the live OSRS game. It's built to behave 1:1 with desktop RuneLite wherever the platform allows.

- **Official sources only.** On first launch of each RuneLite version, the app downloads the official RuneLite game client from RuneLite's servers, verifies it, and prepares it for Android on your device. It's cached after that, so later launches work offline.
- **The official Plugin Hub.** External plugins come straight from RuneLite's Plugin Hub, the same list desktop shows, so it's never behind. Each plugin is prepared on your device the first time you install or update it.
- **Rendering.** The stock GPU plugin runs on GLES. [117 HD](android-hd/README.md) is available as an opt-in renderer (experimental).
- **Touch-first UI.** A native sidebar, plugin panels, configuration and the Plugin Hub, with mouse, Bluetooth and stylus support.
- **Wear OS.** A companion watch app can mirror and configure plugins.

A handful of hub plugins can't work on Android (desktop OpenGL renderers, microphone capture). They're marked as unavailable in the hub.

---

## Install

1. Grab the latest APK from [Releases](../../releases).
2. Allow "Install unknown apps" for your browser or file manager.
3. Install, then open it. The first launch downloads and prepares the game client, which takes about 20 seconds.

The app checks for updates on launch and installs them before starting.

Requires Android 8.0+ (API 26). Ships `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`.

---

## Logging in

OSRS uses Jagex Accounts. Sign in from the **Accounts** screen on the splash; the login runs in-app and your session is saved on the device.

You can also import a `credentials.properties` file from desktop RuneLite, if you already have one ([Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)).

- Accounts are stored in the app's private storage. Other apps can't read them, and they aren't backed up.
- If a saved session stops working, use **Re-authorize** on the account.
- Uninstalling the app removes saved accounts.

---

## Reporting issues

File a GitHub issue with:

- Device model and Android version
- The crash ID from the in-app crash dialog (release builds report to Crashlytics), or a logcat
- Steps to reproduce, and which plugins are enabled (name the plugin if one misbehaves)

---

## Building

| Tool | Version |
|---|---|
| JDK | 21 (for Gradle) |
| Kotlin | 2.4.20 |
| Android Gradle Plugin | 9.1.0 |
| Compose Multiplatform | 1.12.0 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 |
| Android NDK | 27.1.12297006 (app), 28.2.13676358 (`android-awt`) |
| Rust | stable, via `rust-android-gradle` 0.10.0 |
| Firebase BoM | 34.19.0 |

```
./gradlew :android:assembleRelease
```

Release signing reads `runeliteMp.signing.*` from `local.properties`; without it the build falls back to the debug key.

---

## Community

Questions, bug reports and contributing: join us on [Discord](https://discord.gg/Edkba6MRgq).

---

## Legal

RuneLite's code is BSD 2-Clause licensed; the Android-specific layer is mine, under the same BSD 2-Clause license (`LICENSE`).

### Third-party notices

- **OpenJDK.** The `android-awt` module supplies the desktop JDK surface Android lacks (`java.awt`, `javax.swing`, `javax.imageio`, `javax.sound`, `java.beans`, `sun.*`). Part of it is ported from [OpenJDK](https://github.com/openjdk/jdk8u) and is therefore a derivative work of OpenJDK, licensed under the GNU General Public License version 2 **with the Classpath Exception**. Each ported file keeps its OpenJDK header; files that are original work of this project carry a BSD 2-Clause header. `android-awt/LICENSE` has the full GPLv2 + Classpath Exception text and explains which files are which, and `python tools-src/check-license-headers.py` verifies that every source file in the module carries one of the two headers.

  The Classpath Exception is what OpenJDK itself relies on so that Java programs linked against the JDK class library are not GPL works. It applies here the same way: the ported files, and modifications to them, are GPLv2 + Classpath Exception, while the RuneLite client, the Plugin Hub, the Android app and the APK as a whole are independent modules that link against `android-awt` and keep their own licenses. The APK is not GPL-licensed by virtue of including `android-awt`.
- **117 HD** (`third-party/rlhd`, the `android-hd` module) is BSD 2-Clause, copyright its authors.
- **RuneLite** and the **Plugin Hub** plugins downloaded on device are BSD 2-Clause, copyright their respective authors.

This project is not affiliated with, endorsed by, or supported by Jagex Ltd. or RuneLite. "RuneScape", "Old School RuneScape" and related marks are trademarks of Jagex Ltd. The RuneLite name and logo are trademarks of RuneLite.
