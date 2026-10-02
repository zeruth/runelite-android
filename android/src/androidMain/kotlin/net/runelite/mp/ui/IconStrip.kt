package net.runelite.mp.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.runelite.mp.ui.bridge.NavBarBridge
import net.runelite.mp.ui.bridge.NavEntry

/** Special key used for the built-in "Plugins" icon (our Compose plugin list).
 *  Not registered through RuneLite's ClientToolbar — we synthesise it locally. */
internal const val NAV_KEY_PLUGINS = "__compose__plugins"

/**
 * Vertical icon column that lists every nav button RuneLite plugins have registered,
 * plus a built-in "Plugins" wrench at the top for our Compose plugin list. The
 * currently-selected key drives what the right-hand content area renders (see
 * [WindowImpl]).
 *
 * Click behaviour:
 *  - Our synthetic "Plugins" icon → flip the selection to it; content area shows the
 *    Compose plugin list.
 *  - Any RL [NavEntry] with a panel opens in the shared Swing host.
 *  - Action-only RL nav (no panel) → fire its onClick. Selection state is unchanged.
 */
@Composable
internal fun NavIconStrip(
    width: androidx.compose.ui.unit.Dp,
    selected: String?,
    onSelect: (String?) -> Unit,
)
{
    val pluginsIcon = remember {
        NavBarBridge::class.java.getResourceAsStream("/net/runelite/client/plugins/config/config_icon.png")
            ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }
    val entries = remember { mutableStateListOf<NavEntry>() }
    LaunchedEffect(Unit)
    {
        // Re-poll the entry list periodically — plugins that register late (Adventure
        // Guide on QH boot, etc.) appear without a restart this way.
        while (true)
        {
            val next = NavBarBridge.list()
            val same = next.size == entries.size && next.indices.all { entries[it].key == next[it].key }
            if (!same)
            {
                entries.clear()
                entries.addAll(next)
            }
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(RlPalette.DarkerGray)
            .border(1.dp, RlPalette.SurfaceBorder),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        // Our built-in Plugins icon — always at the top. Re-tapping the active icon
        // collapses the panel (state → null) so the user can reclaim the screen
        // without losing context.
        IconButton(
            label = "P",
            tooltip = "Plugins",
            active = selected == NAV_KEY_PLUGINS,
            iconBitmap = pluginsIcon,
        ) {
            if (selected == NAV_KEY_PLUGINS)
            {
                onSelect(null)
            }
            else
            {
                // No AWT panel to open for the Compose-native Plugins screen, but if
                // a native panel WAS open we still want to close it on the AWT side
                // so it stops bleeding through into the bitmap.
                net.runelite.mp.ui.bridge.NavBarBridge.closePanels()
                onSelect(NAV_KEY_PLUGINS)
            }
        }
        Spacer(Modifier.height(4.dp))
        // Sticky-modifier chips. There's no hardware keyboard on Android, so OSRS
        // interactions that require Shift (drop items) or Alt (alt-action menu) get a
        // tap-to-hold affordance pinned directly under the Plugins wrench. Amber marks
        // an engaged modifier; inactive controls blend into the strip.
        ModifierChip(label = "Shift", state = net.runelite.mp.ui.bridge.ModifierState.shiftActive.value) {
            net.runelite.mp.ui.bridge.ModifierState.toggleShift()
        }
        Spacer(Modifier.height(4.dp))
        ModifierChip(label = "Alt", state = net.runelite.mp.ui.bridge.ModifierState.altActive.value) {
            net.runelite.mp.ui.bridge.ModifierState.toggleAlt()
        }
        Spacer(Modifier.height(4.dp))
        ModifierChip(label = "⌨️", state = net.runelite.mp.ui.bridge.SoftKeyboardController.isKeyboardVisible.value) {
            net.runelite.mp.ui.bridge.SoftKeyboardController.toggle()
        }
        Spacer(Modifier.height(4.dp))
        // RL-registered nav buttons. The list scrolls when it overflows the column height.
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(entries, key = { it.key }) { entry ->
                val isActive = selected == entry.key
                IconButton(
                    label = entry.initial,
                    tooltip = entry.tooltip,
                    active = isActive,
                    iconBitmap = entry.icon,
                ) {
                    if (isActive)
                    {
                        // Re-tapping closes the selected panel.
                        net.runelite.mp.ui.bridge.NavBarBridge.closePanels()
                        onSelect(null)
                    }
                    else
                    {
                        if (entry.hasPanel)
                        {
                            // The shared host owns rendering and activation of the original panel.
                            net.runelite.mp.ui.bridge.NavBarBridge.closePanels()
                            onSelect(entry.key)
                        }
                        else
                        {
                            entry.onClick()
                            if (entry.hasPanel) onSelect(entry.key)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Two-state chip used for the Shift / Alt sticky-modifier toggles in the icon strip.
 * Amber fill when engaged so the user can see the modifier is "down" at a glance.
 * Tap toggles. Sized the same width as [IconButton] so the strip stays
 * visually aligned even when the chip text is wider than a glyph.
 */
@Composable
private fun ModifierChip(label: String, state: Boolean, onClick: () -> Unit)
{
    val bg = if (state) RlPalette.AccentSurface else Color.Transparent
    val borderColor = if (state) RlPalette.Accent else Color.Transparent
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (state) RlPalette.Accent else RlPalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun IconButton(
    label: String,
    tooltip: String,
    active: Boolean,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
)
{
    val bg = if (active) RlPalette.AccentSurface else Color.Transparent
    val border = if (active) RlPalette.Accent else Color.Transparent
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (iconBitmap != null)
        {
            Image(
                bitmap = iconBitmap,
                contentDescription = tooltip,
                contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                modifier = Modifier.size(20.dp),
            )
        }
        else
        {
            // Fallback to the first letter of the tooltip when the BufferedImage→Bitmap
            // pass returned null (icon resource missing on the host side, etc.).
            Text(
                label,
                color = if (active) Color.White else RlPalette.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
