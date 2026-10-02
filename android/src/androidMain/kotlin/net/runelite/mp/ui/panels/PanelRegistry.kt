package net.runelite.mp.ui.panels

import androidx.compose.runtime.Composable

/**
 * Maps an RL nav-button tooltip to a Compose-native replacement for its Swing panel.
 * These are optional Android views; the shared host defaults to the original Swing panel.
 *
 * Tooltips are matched exactly because that's the only stable identifier we have
 * from `NavigationButton` — the underlying plugin instance varies by build and the
 * priority field is ordering metadata, not identity.
 */
internal object PanelRegistry
{
    private val byTooltip: Map<String, @Composable () -> Unit> = mapOf(
        "Notes" to { NotesPanel() },
        "Screen Markers" to { ScreenMarkersPanel() },
        "XP Tracker" to { XpTrackerPanel() },
        // Upstream RL's nav tooltips don't match the plugin display names:
        //   WorldHopperPlugin → "World Switcher"
        //   HiscorePlugin     → "Hiscore"  (no second 's')
        //   TimeTrackingPlugin → "Time Tracking"  (already correct)
        // Exact tooltips make the optional Android views available in the shared host.
        "World Switcher" to { WorldHopperPanel() },
        "Hiscore" to { HiscoresPanel() },
        "Grand Exchange" to { GrandExchangePanel() },
        "Time Tracking" to { TimersPanel() },
        "Quest Helper" to { QuestHelperPanel() },
        // External resource-packs plugin (melky.resourcepacks) — its nav button tooltip is
        // "Resource packs hub"; route it to our Compose hub browser.
        "Resource packs hub" to { ResourcePacksPanel() },
    )

    fun hasPanel(key: String): Boolean = byTooltip.containsKey(key)

    @Composable
    fun render(key: String)
    {
        val composable = byTooltip[key] ?: return
        composable()
    }
}
