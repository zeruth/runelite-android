# runelite-android

An unofficial Android port of [RuneLite](https://runelite.net) — the open-source Old School RuneScape client — packaged as a standalone APK (`net.runelite.mp`).

---  
  
## Info
`android` runs the upstream RuneLite client — the same `runelite-client`, the same plugin system, the same `runelite-api` surface — on Android, against the live OSRS gamepack. Behavior is effectively 1:1 with desktop RuneLite where the platform allows.

The base GPU plugin is intact via GLES. **117HD is not supported yet**

The **RuneLite plugin hub is mostly untested** on this client. The loader is wired up and individual plugins do load, but the catalogue as a whole hasn't been exercised — expect rough edges and please file an issue (with the plugin name) if something misbehaves.

---

## Toolchain

| Tool | Version |
|---|---|
| JDK | 17 |
| Kotlin | 2.1.0 |
| Android Gradle Plugin | 8.6.1 |
| compileSdk | 34 |
| minSdk | 26 |
| targetSdk | 34 |
| Jetpack Compose | 1.7.3 |
| Android NDK | 28.2.13676358 |
| Rust | stable, via `rust-android-gradle` 0.9.6 (target: `arm64-v8a` only) |
| ASM | 9.6 (used by build-time desugarers) |
| Firebase BoM | 34.14.0 |

---

## Install

### Building RuneLite 1.13.1

Before running `:android:assembleDebug`, download the official
[injected-client 1.13.1 JAR](https://repo.runelite.net/net/runelite/injected-client/1.13.1/injected-client-1.13.1.jar)
to `data/injected-client-1.13.1.jar`. This local build input is ignored by Git.
Its SHA-256 is `981eba4888f4cf0ec8fd74f09b9ed85728d7b2bc8c1f57194883aed64dec768a`.

### Installing an APK

1. Grab the latest APK from [Releases](../../releases).
2. Enable "Install unknown apps" for your browser / file manager on Android.
3. Install.

`minSdk 26` (Android 8.0). `arm64-v8a` only — no `x86_64`/emulator build is shipped.

---

## Logging in (importing credentials from desktop RuneLite)

OSRS login on the modern client goes through Jagex Accounts — there's no username/password prompt in the game itself anymore. `runelite-android` reuses the same `credentials.properties` file that desktop RuneLite generates, so the flow is "log in once on desktop, copy the file to your phone, import it."

If you've never set up a Jagex Account against desktop RuneLite, follow the upstream guide first: <https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>.

You must copy the credentials.properties file to your phone to import an account.  

**Notes:**

- The file is stored in the app's private storage (`/data/data/net.runelite.mp/files/accounts/`) — not readable by other apps and not backed up to Google Drive.
- The tokens in `credentials.properties` are long-lived but not eternal; if a login starts failing after weeks/months, re-run the desktop login and re-import.
- Deleting the app deletes the imported accounts. There's no cloud sync.

---

## Reporting issues

File a GitHub issue with:

- Device model + Android version
- The full crash log (the Crashlytics console will already have a deobfuscated stack trace if it's a release build — including the issue ID from the in-app crash dialog is enough)
- Reproducer steps and which plugins are enabled

---

## Source access / contributing

Ping me on Discord at **@therealnull**.

---

## Legal

RuneLite's own code is BSD 2 licensed; the Android-specific layer is mine.

This is not affiliated with, endorsed by, or supported by Jagex Ltd. or RuneLite. "RuneScape", "Old School RuneScape", and related marks are trademarks of Jagex Ltd. The RuneLite name and logo are trademarks of RuneLite.
