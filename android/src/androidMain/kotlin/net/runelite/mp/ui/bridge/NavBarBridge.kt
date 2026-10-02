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
    fun panel(key: String): net.runelite.client.ui.PluginPanel?
    {
        val ui = RuneLiteAccess.instance(Class.forName("net.runelite.client.ui.ClientUI")) ?: return null
        val field = ui.javaClass.getDeclaredField("sidebarEntries").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val entries = field.get(ui) as Collection<NavigationButton>
        return entries.firstOrNull { identityKey(it) == key }?.panel
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
            val field = ui.javaClass.getDeclaredField("sidebarEntries").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (field.get(ui) as? Collection<NavigationButton>) ?: emptyList()
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
        if (btn.panel != null)
        {
            selectedKey = identityKey(btn)
            try
            {
                val m = ui.javaClass.getDeclaredMethod(
                    "openPanel",
                    NavigationButton::class.java,
                    Boolean::class.javaPrimitiveType,
                )
                m.isAccessible = true
                javax.swing.SwingUtilities.invokeLater { m.invoke(ui, btn, true) }
            }
            catch (t: Throwable) { /* swallow — Compose panel host falls back to placeholder */ }
            return
        }
        // Action-only button — invoke its Runnable on the AWT thread.
        val click = btn.onClick ?: return
        javax.swing.SwingUtilities.invokeLater {
            try { click.run() } catch (t: Throwable) { /* host logs */ }
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
