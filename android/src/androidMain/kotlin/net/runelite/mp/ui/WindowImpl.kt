package net.runelite.mp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import net.runelite.mp.MainActivity
import net.runelite.mp.ui.bridge.PluginRow

/**
 * Three-zone chrome. The icon strip is pinned to the FAR right so the chosen panel's
 * content lives immediately to its left (closest to the game viewport) — the same
 * mental model as RuneLite desktop's east sidebar.
 *
 *   ┌─────────────────────────────┬───────────┬──┐
 *   │                             │           │I │
 *   │       game viewport         │  content  │c │
 *   │                             │   panel   │o │
 *   │                             │           │n │
 *   │                             │           │  │
 *   └─────────────────────────────┴───────────┴──┘
 *
 * The strip stays visible. Plugin panels open in the shared Swing host; the
 * built-in Plugins screen uses Compose.
 */
object WindowImpl
{
    private val ICON_WIDTH = 36.dp
    private val CONTENT_WIDTH = 256.dp

    /** First launch shows the game with NO panel open — user explicitly taps a nav
     *  icon to reveal one. Avoids slamming the plugin list on top of every fresh boot. */
    private val selectedKey = mutableStateOf<String?>(null)
    private val configTarget = mutableStateOf<PluginRow?>(null)

    /**
     * Open a panel by its RuneLite navigation tooltip, including ClientUI.openPanel calls.
     */
    fun showPanel(key: String)
    {
        selectedKey.value = key
        if (key != NAV_KEY_PLUGINS) configTarget.value = null
    }

    /**
     * Hardware/gesture back-button reducer. Always consumes the press once we're booted,
     * so an accidental swipe-back doesn't kill the activity (and the logged-in session
     * with it). Step order matches what the user would intuitively expect: drill OUT of
     * the open config first, then close the panel, then no-op.
     */
    fun handleBack()
    {
        when
        {
            configTarget.value != null -> configTarget.value = null
            selectedKey.value != null -> selectedKey.value = null
            else -> { /* swallow — refuse to exit the activity from in-game */ }
        }
    }

    /** Flipped to true by [net.runelite.mp.AppAndroidKt.ComposeSplash] once the AWT
     *  splash has come and gone. The chrome (icon strip + content panel) stays
     *  hidden until boot completes so users don't see a sidebar pinned beside the
     *  splash during the load. */
    val bootComplete = mutableStateOf(false)

    @Composable
    fun Window(gameContent: @Composable () -> Unit)
    {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    gameContent()
                }
                // Chrome (content panel + icon strip) doesn't render at all until
                // the splash dismisses — otherwise the user sees a pinned sidebar
                // floating beside the boot splash during the long initial load.
                if (bootComplete.value)
                {
                    RuneLiteMenuTheme {
                        // Reserve a content column for the selected plugin panel.
                        val key = selectedKey.value
                        val renderCompose = key != null
                        if (renderCompose)
                        {
                            Box(Modifier.width(CONTENT_WIDTH).fillMaxHeight()) {
                                ContentPanel()
                            }
                        }
                        NavIconStrip(
                            width = ICON_WIDTH,
                            selected = selectedKey.value,
                            onSelect = { key ->
                                selectedKey.value = key
                                if (key != NAV_KEY_PLUGINS) configTarget.value = null
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ContentPanel()
    {
        val key = selectedKey.value
        when
        {
            key == NAV_KEY_PLUGINS ->
            {
                PluginSidebar(
                    width = CONTENT_WIDTH,
                    onConfigure = { configTarget.value = it },
                )
                val target = configTarget.value
                if (target != null)
                {
                    PluginConfigPanelFor(
                        row = target,
                        onBack = {
                            configTarget.value = null
                            MainActivity.hideSystemUI()
                                 },
                    )
                }
            }
            key != null ->
            {
                net.runelite.mp.ui.panels.PluginPanelHost(key)
            }
        }
    }
}
