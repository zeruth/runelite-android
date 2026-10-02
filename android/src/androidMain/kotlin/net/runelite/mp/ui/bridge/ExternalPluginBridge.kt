package net.runelite.mp.ui.bridge

import android.util.Log
import net.runelite.client.externalplugins.ExternalPluginClient
import net.runelite.client.externalplugins.ExternalPluginManager
import net.runelite.client.externalplugins.PluginHubManifest

/**
 * Exposes the Android DEX plugin hub to the Compose UI. Its URL, catalog version and
 * signing certificate are configured by RuneLiteLauncher independently of the desktop hub.
 *
 * Three surfaces matter:
 *  - {@link #installed()} — which externals the user has opted into. Cheap; reads the
 *    configManager key {@code runelite.externalPlugins}.
 *  - {@link #available()} — the full manifest + install counts. Expensive (HTTP + signature
 *    verify), so the call is one-shot and cached by the caller in a Compose state.
 *  - {@link #downloadIcon} — fetches a single plugin's PNG icon bytes from the hub mirror.
 *    Per-row; cached by the caller.
 *
 * Install / remove just update the configManager list; ExternalPluginManager listens for
 * the resulting {@link net.runelite.client.events.ExternalPluginsChanged} and does the
 * actual jar download / classloader setup. UI doesn't need to wait — the row's
 * "installed" state flips on the next [installed] poll.
 */
internal data class ExternalPluginEntry(
    val internalName: String,
    val displayName: String,
    val author: String,
    val description: String,
    val version: String,
    val warning: String?,
    val unavailableReason: String?,
    val installed: Boolean,
    val iconHash: String?,
    val userCount: Int,
)
{
    val rowKey: String get() = "ext/$internalName"
    val available: Boolean get() = unavailableReason == null
}

internal object ExternalPluginBridge
{
    fun installed(): Set<String>
    {
        val mgr = RuneLiteAccess.instance(ExternalPluginManager::class.java) ?: return emptySet()
        return try { mgr.installedExternalPlugins.toSet() } catch (t: Throwable) { emptySet() }
    }

    /** Downloads the FULL manifest (display metadata for every published plugin) + install
     *  counts from api.runelite.net. Blocks — caller should run on a background coroutine.
     *  Preserves failures so the UI can explain them and offer a retry.
     *  Install counts that don't resolve (eg. plugin internal name not
     *  known to the upstream API) default to -1, which sorts below all real counts. */
    fun available(): Result<List<ExternalPluginEntry>>
    {
        val client = RuneLiteAccess.instance(ExternalPluginClient::class.java)
            ?: return Result.failure(IllegalStateException("Client is still starting. Try again in a moment."))
        val installed = installed()
        return try
        {
            val full: PluginHubManifest.ManifestFull = client.downloadManifestFull()
            val counts: Map<String, Int> = try { client.pluginCounts } catch (t: Throwable) { emptyMap() }
            val entries = full.display.orEmpty().map { d ->
                ExternalPluginEntry(
                    internalName = d.internalName,
                    displayName = d.displayName ?: d.internalName,
                    author = d.author ?: "",
                    description = d.description ?: "",
                    version = d.version ?: "",
                    warning = d.warning,
                    unavailableReason = d.unavailableReason,
                    installed = installed.contains(d.internalName),
                    iconHash = d.iconHash,
                    userCount = counts[d.internalName] ?: -1,
                )
            }
            Log.i("ExternalPluginBridge", "Loaded ${entries.size} plugins from the signed Android catalog")
            Result.success(entries)
        }
        catch (e: Exception)
        {
            Log.w("ExternalPluginBridge", "Unable to load Android plugin catalog", e)
            Result.failure(e)
        }
    }

    fun install(internalName: String)
    {
        val mgr = RuneLiteAccess.instance(ExternalPluginManager::class.java) ?: return
        try { mgr.install(internalName) } catch (t: Throwable) { /* host logs */ }
    }

    fun remove(internalName: String)
    {
        val mgr = RuneLiteAccess.instance(ExternalPluginManager::class.java) ?: return
        try { mgr.remove(internalName) } catch (t: Throwable) { /* host logs */ }
    }

    /** Fetch icon PNG bytes via the same OkHttpClient RuneLite uses, hitting the hub
     *  mirror path `<base>/icon/<internalName>_<iconHash>.png`. Blocks; returns null on
     *  any failure so the row can fall back to its placeholder. */
    fun downloadIcon(internalName: String, iconHash: String): ByteArray?
    {
        val hubBase = System.getProperty("runelite.pluginhub.url") ?: return null
        val client = RuneLiteAccess.instance(okhttp3.OkHttpClient::class.java) ?: return null
        val url = "$hubBase/icon/${internalName}_${iconHash}.png"
        return try
        {
            val req = okhttp3.Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body()?.bytes()
            }
        }
        catch (t: Throwable) { null }
    }

}
