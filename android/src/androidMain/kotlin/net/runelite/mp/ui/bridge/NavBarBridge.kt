package net.runelite.mp.ui.bridge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.runelite.client.ui.NavigationButton

/**
 * Reads the list of side-toolbar buttons that plugins have registered via
 * {@link net.runelite.client.ui.ClientToolbar#addNavigation}. Each entry yields a
 * compose-friendly row containing the icon (converted from {@link java.awt.image.BufferedImage}
 * to an Android {@link Bitmap}), the tooltip, and a click handler that either runs
 * the button's onClick callback or opens its panel via reflection into ClientUI.
 *
 * Sorted by {@link NavigationButton#priority} — the same order ClientUI uses for the
 * native sidebar — so icons land in their familiar positions.
 */
internal data class NavEntry(
    val key: String,
    val tooltip: String,
    val icon: ImageBitmap?,
    val hasPanel: Boolean,
    val onClick: () -> Unit,
)
{
    val initial: String get() = tooltip.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

internal object NavBarBridge
{
    const val PROFILES_PANEL_KEY = "runelite:profiles"

    private var profilesPanel: net.runelite.client.ui.PluginPanel? = null // EDT only

    fun panel(key: String): net.runelite.client.ui.PluginPanel?
    {
        return try {
            onEdt {
                val ui = RuneLiteAccess.instance(Class.forName("net.runelite.client.ui.ClientUI"))
                ui?.let {
                    if (key == PROFILES_PANEL_KEY) {
                        // Own the Swing tree; the hidden desktop tab must not relayout it.
                        profilesPanel ?: run {
                            val manager = RuneLiteAccess.instance(net.runelite.client.config.ConfigManager::class.java)
                            if (manager?.profile == null) return@run null
                            val panel = RuneLiteAccess.instance(Class.forName(
                                "net.runelite.client.plugins.config.ProfilePanel")) as? net.runelite.client.ui.PluginPanel
                            panel?.let { created ->
                                RuneLiteAccess.instance(net.runelite.client.eventbus.EventBus::class.java)?.register(created)
                                profilesPanel = created
                            }
                            panel
                        }
                    } else buttons(it).firstOrNull { button -> identityKey(button) == key }?.panel
                }
            }
        } catch (_: Throwable) { null }
    }

    private fun <T> onEdt(read: () -> T): T {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) return read()
        var result: T? = null
        javax.swing.SwingUtilities.invokeAndWait { result = read() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun buttons(ui: Any): List<NavigationButton> {
        val field = ui.javaClass.getDeclaredField("sidebarEntries").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val panels = (field.get(ui) as? Collection<NavigationButton>)?.toList().orEmpty()
        // RuneLite stores action-only buttons (including Account login/logout) separately.
        val toolbar = ui.javaClass.getDeclaredField("toolbarPanel").apply { isAccessible = true }.get(ui)
        val entries = toolbar.javaClass.getDeclaredField("entries").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val actions = (entries.get(toolbar) as? Map<NavigationButton, *>)?.keys.orEmpty()
        return (panels + actions).distinct()
    }
    /** Currently-selected button's key, or null if the user hasn't picked one yet
     *  (we surface the Compose plugin list as the default). Lives outside Compose
     *  so it survives composition tear-down. */
    @Volatile var selectedKey: String? = null

    fun list(): List<NavEntry>
    {
        val ui = try { RuneLiteAccess.instance(Class.forName("net.runelite.client.ui.ClientUI")) }
        catch (t: Throwable) { return emptyList() } ?: return emptyList()
        val entries = try
        {
            onEdt { buttons(ui) }
        }
        catch (t: Throwable) { return emptyList() }

        val out = ArrayList<NavEntry>(entries.size)
        for (btn in entries)
        {
            val tt = btn.tooltip ?: btn.javaClass.simpleName
            // RL's ConfigPlugin registers a "Configuration" nav whose panel is the
            // TopLevelConfigPanel — same data our Compose plugin list shows. Keeping
            // both would surface two icons that do the same thing.
            if (tt == "Configuration") continue
            val key = identityKey(btn)
            val bitmap = convertIcon(btn)
            val hasPanel = btn.panel != null
            out += NavEntry(
                key = key,
                tooltip = tt,
                icon = bitmap?.asImageBitmap(),
                hasPanel = hasPanel,
                onClick = { invoke(ui, btn) },
            )
        }
        return out
    }

    /** Mirror the user re-tapping the active icon to the AWT side too: collapse the
     *  native sidebar so its JTabbedPane stops painting the previously-active panel
     *  in the bitmap. Reflects into the private {@code toggleSidebar(boolean, boolean)}. */
    fun closePanels()
    {
        val ui = try { RuneLiteAccess.instance(Class.forName("net.runelite.client.ui.ClientUI")) }
        catch (t: Throwable) { return } ?: return
        try
        {
            val m = ui.javaClass.getDeclaredMethod(
                "toggleSidebar",
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            )
            m.isAccessible = true
            javax.swing.SwingUtilities.invokeLater { m.invoke(ui, false, true) }
        }
        catch (t: Throwable) { /* swallow */ }
        selectedKey = null
    }

    /** Click handler: when the button has a panel, call `ClientUI.openPanel(btn, true)`
     *  via reflection (the method is private). When the button is action-only (no
     *  panel), invoke its onClick Runnable. Either path keeps native RL state in sync. */
    private fun invoke(ui: Any, btn: NavigationButton)
    {
        val key = identityKey(btn)
        javax.swing.SwingUtilities.invokeLater {
            try {
                // A Compose row can outlive the NavigationButton that supplied it.
                val current = buttons(ui).firstOrNull { identityKey(it) == key } ?: return@invokeLater
                if (current.panel != null) {
                    selectedKey = key
                    val method = ui.javaClass.getDeclaredMethod("openPanel", NavigationButton::class.java, Boolean::class.javaPrimitiveType)
                    method.isAccessible = true
                    method.invoke(ui, current, true)
                } else current.onClick?.run()
            } catch (_: Throwable) { /* the host falls back to its unavailable-panel message */ }
        }
    }

    private fun identityKey(btn: NavigationButton): String =
        btn.tooltip ?: "${btn.javaClass.name}@${System.identityHashCode(btn)}"

    /** BufferedImage → Android Bitmap. Walks the AWT image as an int[] of ARGB pixels
     *  and copies straight into an ARGB_8888 bitmap. ~32 × 32 icons so the copy is
     *  free; doing this once per nav rebuild is fine. */
    private fun convertIcon(btn: NavigationButton): Bitmap?
    {
        val img = btn.icon ?: return null
        val w = img.width
        val h = img.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        try
        {
            img.getRGB(0, 0, w, h, pixels, 0, w)
        }
        catch (t: Throwable)
        {
            return null
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }
}
