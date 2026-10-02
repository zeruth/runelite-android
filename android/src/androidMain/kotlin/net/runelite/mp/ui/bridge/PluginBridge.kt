package net.runelite.mp.ui.bridge

import net.runelite.client.config.Config
import net.runelite.client.config.ConfigManager
import net.runelite.client.plugins.Plugin
import net.runelite.client.plugins.PluginDescriptor
import net.runelite.client.plugins.PluginManager

/**
 * Exposes the host plugin registry + per-plugin config to the Compose UI in a stable
 * shape, hiding the host's reflection ceremony.
 *
 * A row can be either:
 *  - A real {@link Plugin} loaded by {@link PluginManager} (toggleable on/off).
 *  - A "fake" row backed by a {@link Config} interface only — used for the client-wide
 *    RuneLite + Chat Color settings that RuneLite's own Swing `ConfigPlugin` registers
 *    via {@code addFakePlugin}. These have no on/off; the toggle column is suppressed.
 */
internal data class PluginRow(
    val plugin: Plugin?,
    val configProxy: Config?,
    val displayName: String,
    val description: String,
    val tags: List<String>,
    val enabled: Boolean,
    val toggleable: Boolean,
    val hasConfig: Boolean,
)
{
    /** Stable identity for Compose's list `key` — uses the class name of whichever side
     *  is present (plugin or config). */
    val rowKey: String get() = (plugin?.javaClass?.name ?: configProxy?.javaClass?.interfaces?.firstOrNull()?.name ?: displayName)
}

internal object PluginBridge
{
    fun list(): List<PluginRow>
    {
        val pm = RuneLiteAccess.instance(PluginManager::class.java) ?: return emptyList()
        val out = ArrayList<PluginRow>()
        for (plugin in pm.plugins)
        {
            val descAnn = plugin.javaClass.getAnnotation(PluginDescriptor::class.java) ?: continue
            val cfg = safeGetConfigProxy(pm, plugin)
            out += PluginRow(
                plugin = plugin,
                configProxy = cfg,
                displayName = descAnn.name,
                description = descAnn.description,
                tags = descAnn.tags.toList(),
                enabled = pm.isPluginEnabled(plugin),
                toggleable = true,
                hasConfig = cfg != null,
            )
        }
        // Client-wide configs surfaced as "fake" rows — same as Swing ConfigPlugin's
        // addFakePlugin entries. Failing to find one is non-fatal; the list just doesn't
        // include it.
        addFake(out, "RuneLite", "RuneLite client settings", "net.runelite.client.config.RuneLiteConfig")
        addFake(out, "Chat Color", "Recolor chat text", "net.runelite.client.plugins.config.ChatColorConfig")
        out.sortBy { it.displayName.lowercase() }
        // Dedupe by rowKey. pm.plugins can briefly contain two instances of the same
        // plugin during a hub install/uninstall reload (the outgoing copy lingers until
        // ExternalPluginsChanged fires). Compose LazyColumn throws IAE on duplicate
        // keys mid-frame, which is fatal — so guarantee uniqueness at the source. The
        // next poll will see the cleaned-up registry.
        return out.distinctBy { it.rowKey }
    }

    private fun addFake(out: MutableList<PluginRow>, name: String, desc: String, fqcn: String)
    {
        val cm = RuneLiteAccess.instance(ConfigManager::class.java) ?: return
        val cls = try { Class.forName(fqcn) } catch (t: Throwable) { return }
        if (!Config::class.java.isAssignableFrom(cls)) return
        @Suppress("UNCHECKED_CAST")
        val proxy = RuneLiteAccess.instance(cls as Class<Config>) ?: return
        out += PluginRow(
            plugin = null,
            configProxy = proxy,
            displayName = name,
            description = desc,
            tags = emptyList(),
            enabled = true,
            toggleable = false,
            hasConfig = true,
        )
    }

    fun setEnabled(plugin: Plugin, enabled: Boolean)
    {
        val pm = RuneLiteAccess.instance(PluginManager::class.java) ?: return
        // PluginManager.startPlugin/stopPlugin assert they run on the AWT EDT, and a plugin's
        // startUp()/shutDown() touch Swing/overlays that are EDT-confined. The Compose toggle
        // fires on the Android main thread, so marshal the whole enable+start onto android-awt's
        // single AWT-InvokeLater thread (where SwingUtilities.isEventDispatchThread() is true).
        javax.swing.SwingUtilities.invokeLater {
            try
            {
                pm.setPluginEnabled(plugin, enabled)
                if (enabled) pm.startPlugin(plugin) else pm.stopPlugin(plugin)
            }
            catch (t: Throwable)
            {
                android.util.Log.e("PluginToggle", "setEnabled ${plugin.javaClass.name} failed", t)
            }
        }
    }

    /** Build a [ConfigBridge] for `row`. Works for both real plugin rows (resolves the
     *  config proxy via PluginManager) and fake rows (uses the Config interface directly). */
    fun configBridge(row: PluginRow): ConfigBridge?
    {
        val cm = RuneLiteAccess.instance(ConfigManager::class.java) ?: return null
        val proxy: Config = row.configProxy ?: run {
            val pm = RuneLiteAccess.instance(PluginManager::class.java) ?: return null
            val p = row.plugin ?: return null
            safeGetConfigProxy(pm, p) ?: return null
        }
        return try
        {
            val descriptor = cm.getConfigDescriptor(proxy)
            ConfigBridge(cm, descriptor, proxy)
        }
        catch (t: Throwable)
        {
            null
        }
    }

    private fun safeGetConfigProxy(pm: PluginManager, plugin: Plugin): Config?
    {
        return try { pm.getPluginConfigProxy(plugin) } catch (t: Throwable) { null }
    }
}

/**
 * Persisted favorites for both the Plugins tab and the External tab. Stored as a
 * comma-separated set under group `runelite-mp` / key `favorites` in [ConfigManager],
 * so the list follows the active config profile (the same way upstream RL profiles carry
 * the user's plugin enabled-set). Identifiers are whichever key the caller already uses
 * for list membership — class names for internal plugins, internal-name slugs for hub
 * plugins — so the two namespaces don't collide.
 */
internal object FavoritesBridge
{
    private const val GROUP = "runelite-mp"
    private const val KEY = "favorites"

    fun list(): Set<String>
    {
        val cm = RuneLiteAccess.instance(ConfigManager::class.java) ?: return emptySet()
        val raw = try { cm.getConfiguration(GROUP, KEY) } catch (t: Throwable) { null }
            ?: return emptySet()
        return raw.split(',').asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setFavorite(id: String, favorite: Boolean)
    {
        val cm = RuneLiteAccess.instance(ConfigManager::class.java) ?: return
        val current = list().toMutableSet()
        val changed = if (favorite) current.add(id) else current.remove(id)
        if (!changed) return
        try
        {
            if (current.isEmpty()) cm.unsetConfiguration(GROUP, KEY)
            else cm.setConfiguration(GROUP, KEY, current.sorted().joinToString(","))
        }
        catch (t: Throwable) { /* host logs */ }
    }
}
