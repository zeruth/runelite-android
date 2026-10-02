package net.runelite.mp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.util.Log
import net.runelite.awt.AwtCompat
import net.runelite.awt.AwtNative
import net.runelite.mp.crash.AndroidCrashReporter

private const val TAG = "RuneLiteLauncher"

/**
 * Where ExternalPluginClient pulls plugin manifests / icons / jars from. Override at
 * build time by editing this constant — `runelite.pluginhub.url` from a properties file
 * would also work, but a constant keeps the override visible. The path layout below the
 * base must match the upstream contract:
 *   <base>/manifest/<version>_lite.js
 *   <base>/manifest/<version>_full.js
 *   <base>/icon/<internalName>_<iconHash>.png
 *   <base>/jar/<internalName>_<jarHash>.jar
 * Produced by `pluginhub-lib :publishHub` under data/output/site/, then pushed to a
 * public GitHub repo whose raw URL goes here.
 */
private const val ANDROID_PLUGIN_HUB_URL = "https://raw.githubusercontent.com/zeruth/plugin-hub-dex/main"

/**
 * Drives RuneLite's headless boot from a Compose UI: kicks off `net.runelite.client.RuneLite.main`
 * on a background thread, captures everything that escapes (and everything `System.out` /
 * `System.err` print), and exposes it as state so the UI can render progress + failures.
 */
class RuneLiteLauncher(private val context: android.content.Context? = null) {
    enum class State { Idle, Starting, Running, Failed }

    var state: State by mutableStateOf(State.Idle)
        private set
    var error: Throwable? by mutableStateOf(null)
        private set

    val log: SnapshotStateList<String> = mutableStateListOf()

    private var job: Job? = null

    /** `JX_*` values for a Jagex account signed in through
     *  [net.runelite.mp.account.JagexLoginScreen]. Set by the Compose
     *  [net.runelite.mp.account.AccountPicker] before calling launch(); takes precedence
     *  over [accountFile] when both are set. Held in memory only — the tokens are already
     *  persisted by [net.runelite.mp.account.JagexAccountStore]. */
    var credentials: Map<String, String>? = null

    /** Account whose credentials.properties the launcher should seed into the env on
     *  the next [launch]. The Compose [net.runelite.mp.account.AccountPicker] sets this
     *  before calling launch(); null falls back to the legacy `assets/credentials.properties`
     *  path so older builds keep working. */
    var accountFile: java.io.File? = null

    /** Runs on the launcher's IO thread just before the env is seeded, so a caller can resolve
     *  [credentials] / [accountFile] with work that must not block the UI (a session renewal). */
    var beforeSeed: (() -> Unit)? = null

    fun launch(scope: CoroutineScope, vararg args: String) {
        if (job?.isActive == true) return
        state = State.Starting
        error = null
        log.clear()
        log += "$ runelite ${args.joinToString(" ")}"

        job = scope.launch(Dispatchers.IO) {
            try {
                AwtCompat.ensureReady()
                logLine("android-awt native ABI ready")
                state = State.Running

                installLogTaps()

                // Point the external plugin hub at our GitHub-hosted bundle. The unmodified
                // ExternalPluginClient reads this via System.getProperty before its Guice
                // injector binds the @Named("runelite.pluginhub.url") HttpUrl — must be set
                // before RuneLite.main runs. Set to a raw.githubusercontent.com URL whose
                // contents are produced by `pluginhub-lib :publishHub` (manifest/, jar/, icon/).
                System.setProperty("runelite.pluginhub.url", ANDROID_PLUGIN_HUB_URL)
                logLine("pluginhub.url → $ANDROID_PLUGIN_HUB_URL")
                System.setProperty("runelite.pluginhub.version", BuildConfig.PLUGIN_HUB_VERSION)
                logLine("pluginhub.version → ${BuildConfig.PLUGIN_HUB_VERSION}")
                // ClientUI checks this static-init time — must be set *before* RuneLite.main
                // touches that class. With this on, ClientUI never adds its JTabbedPane
                // sidebar or ClientToolbarPanel to the visible component tree and
                // toggleSidebar becomes a no-op. The Compose chrome is the sole UI on
                // Android; without this flag the AWT sidebar would race ChromeSuppressor
                // and occasionally paint into the bitmap blit.
                System.setProperty("runelite.headlessChrome", "true")
                logLine("headlessChrome → true (AWT sidebar/toolbar suppressed at source)")
                // The bundled trust anchor is upstream RL's 4096-bit cert that signs the
                // official hub. Our manifest is signed by the 2048-bit keystore that
                // `pluginhub-lib :generateKeys` produces — ship that cert as a sibling
                // resource (`externalplugins-android.crt`) and point ExternalPluginClient
                // at it. Desktop never sets this property and stays on upstream's cert.
                System.setProperty("runelite.pluginhub.cert", "externalplugins-android.crt")
                logLine("pluginhub.cert → externalplugins-android.crt")

                try {
                    beforeSeed?.invoke()
                } catch (t: Throwable) {
                    logLine("account resolution failed: ${t.message}; starting without an account")
                    credentials = null
                    accountFile = null
                }
                seedLauncherCredentials()
                runNetSelfTest()

                val cls = Class.forName("net.runelite.client.RuneLite")
                val main = cls.getDeclaredMethod("main", Array<String>::class.java)
                logLine("invoking ${cls.name}.main(${args.size} args)")
                main.invoke(null, args)
                logLine("main returned cleanly")

                val safeMode = args.contains("--safe-mode")
                if (safeMode) {
                    logLine("--safe-mode: skipping PluginBootstrap entirely (no plugins will load)")
                } else if (context != null) {
                    logLine("bootstrapping plugins (RuneLite.loadCorePlugins doesn't see Android dex)")
                    PluginBootstrap.bootstrap(context)
                    logLine("plugin bootstrap done")
                } else {
                    logLine("context not provided to launcher; skipping plugin bootstrap")
                }
            } catch (t: Throwable) {
                val root = unwrap(t)
                error = root
                state = State.Failed
                logLine("FATAL: ${root.javaClass.name}: ${root.message}")
                Log.e(TAG, "RuneLite.main failed", root)
                root.stackTrace.take(20).forEach { logLine("  at $it") }
                // RuneLite.main failures aren't uncaught (the launcher catches them
                // here for UI display), so Crashlytics' uncaught handler never sees
                // them. Report as a non-fatal so the failure still surfaces.
                AndroidCrashReporter.nonFatal(root)
            }
        }
    }

    private fun logLine(line: String) {
        log += line
        Log.i(TAG, line)
        // Mirror into Crashlytics' rolling breadcrumb log — these are the highest-signal
        // events for diagnosing a crash (netcheck results, credential seed, plugin
        // bootstrap stage). Cheap; no-op when Crashlytics isn't installed.
        AndroidCrashReporter.breadcrumb(line)
    }

    private fun intToBE(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    // Pre-flight check: verify Android's network stack does what the patched OSRS client
    // expects before we launch into the game. Failures here narrow down whether the
    // "RuneScape has been updated" dialog is network-layer or protocol-layer.
    private fun runNetSelfTest() {
        try {
            val t0 = System.currentTimeMillis()
            val addrs = java.net.InetAddress.getAllByName("oldschool.runescape.com")
            logLine("netcheck DNS oldschool.runescape.com → ${addrs.size} addr(s) in ${System.currentTimeMillis() - t0}ms")
            for (a in addrs) logLine("  · ${a.hostAddress}")

            val configT = System.currentTimeMillis()
            val javConfigUrl = java.net.URL("https://oldschool.runescape.com/jav_config.ws")
            val conn = javConfigUrl.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            logLine("netcheck jav_config GET ${code} (${bytes.size}B) in ${System.currentTimeMillis() - configT}ms")
            val preview = String(bytes, 0, minOf(bytes.size, 200), Charsets.US_ASCII).replace("\n", " | ")
            logLine("  jav_config preview: ${preview}")

            val socketT = System.currentTimeMillis()
            val host = addrs[0].hostAddress
            val sock = java.net.Socket()
            sock.connect(java.net.InetSocketAddress(host, 443), 5000)
            // Local port is kept (useful for diagnosing port-exhaustion / interface-pinning
            // bugs) but the local IP is redacted — that value identifies the user's home or
            // mobile network and would land in every Crashlytics report via the breadcrumb
            // mirror. Server-side IP we resolved is already logged above.
            logLine("netcheck Socket TCP $host:443 connected in ${System.currentTimeMillis() - socketT}ms (local-port=${sock.localPort})")
            sock.close()

            // Test the actual game/js5 endpoints the patched client connects to.
            // The codebase from jav_config is "oldschool69.runescape.com" (world 69);
            // game = 43594, js5 = 43595. If these fail on Android but work on desktop
            // (same network), that's the netcode delta we're chasing.
            for (port in intArrayOf(43594, 43595)) {
                val gt = System.currentTimeMillis()
                try {
                    val gameAddrs = java.net.InetAddress.getAllByName("oldschool69.runescape.com")
                    val gsock = java.net.Socket()
                    gsock.connect(java.net.InetSocketAddress(gameAddrs[0], port), 5000)
                    logLine("netcheck Socket TCP oldschool69.runescape.com (${gameAddrs[0].hostAddress}):$port → OK in ${System.currentTimeMillis() - gt}ms")
                    // Try sending the OSRS js5 handshake (opcode 15, then revision int)
                    // and read 1 byte response. Should return 0 (OK) or a status code.
                    if (port == 43595) {
                        val out = gsock.getOutputStream()
                        // js5 handshake: opcode 15, 4-byte revision, 4-byte sub-revision
                        out.write(15)
                        out.write(intToBE(226))  // recent OSRS revision (just a probe)
                        out.write(intToBE(1))
                        out.flush()
                        val inp = gsock.getInputStream()
                        val resp = inp.read()
                        logLine("netcheck js5 handshake response byte: $resp (0=OK, 6=updated, 5=server full, 9=ip limit)")
                    }
                    gsock.close()
                } catch (e: Throwable) {
                    logLine("netcheck Socket TCP :$port FAILED: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        } catch (t: Throwable) {
            logLine("netcheck FAILED: ${t.javaClass.simpleName}: ${t.message}")
            Log.w(TAG, "netcheck failed", t)
        }
    }

    // The injected RS client reads JX_ACCESS_TOKEN / JX_REFRESH_TOKEN / JX_SESSION_ID /
    // JX_CHARACTER_ID / JX_DISPLAY_NAME from System.getenv(). On desktop these are set
    // by the Jagex launcher; on Android we ship them as an asset (credentials.properties,
    // gitignored) and seed them into the process env via Os.setenv before the client class
    // is loaded.
    private fun seedLauncherCredentials() {
        val ctx = context ?: return
        // Order of preference: a Jagex account signed in on-device, then an imported
        // credentials.properties, then the legacy asset (which preserves the historical
        // single-account boot path for builds that still ship one).
        val direct = credentials
        if (direct != null && direct.isNotEmpty()) {
            var seeded = 0
            for ((key, value) in direct) {
                if (value.isEmpty()) continue
                System.setProperty(key, value)
                seeded++
            }
            logLine("seeded $seeded launcher env var(s) from signed-in Jagex account")
            return
        }
        val source = accountFile
        val props = try {
            if (source != null && source.exists()) {
                source.inputStream().use {
                    java.util.Properties().apply { load(it) }
                }
            } else {
                ctx.assets.open("credentials.properties").use { stream ->
                    java.util.Properties().apply { load(stream) }
                }
            }
        } catch (e: java.io.FileNotFoundException) {
            logLine("no credentials.properties available; skipping JX_* env seed")
            return
        } catch (e: Throwable) {
            logLine("failed to read credentials.properties: ${e.message}")
            return
        }
        var seeded = 0
        for (key in props.stringPropertyNames()) {
            val value = props.getProperty(key) ?: continue
            if (value.isEmpty()) continue
            System.setProperty(key, value)
            seeded++
        }
        // Source label is intentionally generic — `source.name` is the .properties file's
        // basename which equals the account display-name slug. Logging it would identify
        // the user in every Crashlytics breadcrumb stream. The keys we just enumerated
        // (already logged by name only, never value) are enough context for debugging.
        val srcLabel = if (source != null) "selected account" else "assets/credentials.properties"
        logLine("seeded $seeded launcher env var(s) from $srcLabel")
    }

    private fun installLogTaps() {
        System.setOut(LineTap(System.out, "[stdout] "))
        System.setErr(LineTap(System.err, "[stderr] "))
    }

    private inner class LineTap(private val downstream: java.io.PrintStream, private val prefix: String)
        : java.io.PrintStream(downstream) {
        private val buf = StringBuilder()
        override fun write(b: Int) {
            downstream.write(b)
            if (b == '\n'.code) {
                appendLine()
            } else {
                buf.append(b.toChar())
            }
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            downstream.write(b, off, len)
            for (i in 0 until len) {
                val c = b[off + i].toInt().toChar()
                if (c == '\n') appendLine() else buf.append(c)
            }
        }
        private fun appendLine() {
            val line = buf.toString().trimEnd('\r')
            buf.setLength(0)
            log += prefix + line
        }
    }

    private fun unwrap(t: Throwable): Throwable {
        var cur: Throwable = t
        while (cur is java.lang.reflect.InvocationTargetException && cur.cause != null) {
            cur = cur.cause!!
        }
        return cur
    }
}
