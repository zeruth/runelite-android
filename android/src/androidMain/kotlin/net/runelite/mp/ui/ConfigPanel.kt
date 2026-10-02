package net.runelite.mp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.runelite.client.config.ConfigItemDescriptor
import net.runelite.client.config.Range
import net.runelite.mp.ui.bridge.ConfigBridge
import net.runelite.mp.ui.bridge.PluginBridge
import net.runelite.mp.ui.bridge.PluginRow
import net.runelite.mp.ui.widgets.IntSliderRow
import net.runelite.mp.ui.widgets.SectionHeader
import net.runelite.mp.ui.widgets.SegmentedRow
import net.runelite.mp.ui.widgets.TextRow
import net.runelite.mp.ui.widgets.ToggleRow

/**
 * Renders a single plugin's config interface as a Compose form. Each `@ConfigItem`-annotated
 * method on the interface produces one row; type drives the widget choice (boolean → toggle,
 * int with [Range] → slider, enum → dropdown, String → text field, etc.).
 *
 * Form state lives in a `mutableStateMapOf<key, value>` cache so the slider thumb feels
 * responsive (the underlying [ConfigBridge] write happens on every change but Compose
 * recomposition reads from the cache first). The bridge persists via ConfigManager and
 * RuneLite's existing change-event plumbing notifies the plugin.
 */
@Composable
internal fun PluginConfigPanel(
    pluginName: String,
    bridge: ConfigBridge?,
    onBack: () -> Unit,
)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RlPalette.DarkGray),
    ) {
        // Header: back chip, plugin name, room on the right for actions later.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(RlPalette.DarkerGray)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(RlPalette.MediumGray)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(10.dp))
            Text(pluginName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        if (bridge == null)
        {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "This plugin has no configurable settings.",
                    color = RlPalette.TextSecondary,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        // Materialise the live values once into a state map keyed by ConfigItem.keyName.
        // Edits write through the bridge AND update the map in the same gesture, so a
        // slider drag doesn't fight a delayed read on every frame.
        val cache = remember(bridge) { mutableStateMapOf<String, Any?>() }
        val collapsed = remember(bridge) { mutableStateMapOf<String, Boolean>() }
        val visibleItems = remember(bridge) { bridge.descriptor.items.filter { !it.item.hidden && it.name().isNotBlank() } }
        LaunchedEffect(bridge)
        {
            for (item in visibleItems) cache[item.key()] = bridge.getValue(item)
        }

        // Rows own their padding so section backgrounds span the panel width.
        LazyColumn(modifier = Modifier.fillMaxSize())
        {
            // Section-less items first (RuneLite renders them above any sections too).
            val orphans = visibleItems.filter { it.item.section.isEmpty() }
            if (orphans.isNotEmpty()) items(orphans, key = { it.key() }) { ConfigRow(bridge, it, cache) }
            // Then one section header per section, with its items beneath.
            for (section in bridge.descriptor.sections)
            {
                val secItems = visibleItems.filter { it.item.section == section.key() }
                if (secItems.isEmpty()) continue
                val isClosed = collapsed[section.key()] ?: section.section.closedByDefault
                item(key = "sec:${section.key()}") {
                    SectionHeader(section.name(), !isClosed) { collapsed[section.key()] = !isClosed }
                }
                if (!isClosed) {
                    items(secItems, key = { "${section.key()}/${it.key()}" }) {
                        ConfigRow(bridge, it, cache)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Choose the right widget for a single [ConfigItemDescriptor] and bind it to the bridge. */
@Composable
private fun ConfigRow(
    bridge: ConfigBridge,
    item: ConfigItemDescriptor,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Any?>,
)
{
    val label = item.name()
    val desc = item.item.description.takeIf { it.isNotBlank() }
    val current = cache[item.key()]

    val type = item.type
    val clazz = type as? Class<*>
    when
    {
        clazz == Boolean::class.javaPrimitiveType || clazz == java.lang.Boolean::class.java ->
        {
            val v = current as? Boolean ?: false
            ToggleRow(label, desc, v) {
                cache[item.key()] = it
                bridge.setValue(item, it)
            }
        }
        clazz == Int::class.javaPrimitiveType || clazz == java.lang.Integer::class.java ->
        {
            val range: Range? = item.range
            val v = (current as? Int) ?: 0
            if (range != null)
            {
                IntSliderRow(
                    label = label,
                    description = desc,
                    value = v.coerceIn(range.min, range.max),
                    min = range.min,
                    max = range.max,
                    unit = item.units?.value,
                ) {
                    cache[item.key()] = it
                    bridge.setValue(item, it)
                }
            }
            else
            {
                // No range — render as text field. Int parsing happens at write time.
                TextRow(label, desc, v.toString()) { txt ->
                    val parsed = txt.toIntOrNull()
                    if (parsed != null)
                    {
                        cache[item.key()] = parsed
                        bridge.setValue(item, parsed)
                    }
                }
            }
        }
        clazz == String::class.java ->
        {
            val v = (current as? String) ?: ""
            TextRow(label, desc, v) {
                cache[item.key()] = it
                bridge.setValue(item, it)
            }
        }
        clazz != null && clazz.isEnum ->
        {
            @Suppress("UNCHECKED_CAST")
            val enumClass = clazz as Class<out Enum<*>>
            val constants = enumClass.enumConstants ?: emptyArray()
            val names = constants.map { it.toString().replace('_', ' ') }
            val selected = (current as? Enum<*>)?.ordinal ?: 0
            SegmentedRow(label, desc, names, selected.coerceAtLeast(0)) { idx ->
                val pick = constants.getOrNull(idx) ?: return@SegmentedRow
                cache[item.key()] = pick
                bridge.setValue(item, pick)
            }
        }
        else ->
        {
            // Fallback: show as disabled text row so the user at least sees the key.
            // Color, Keybind, Instant, etc. land here for now.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(label, color = RlPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Type ${clazz?.simpleName ?: type} not yet editable in the Compose UI.",
                        color = RlPalette.TextDisabled,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** Reverse lookup: which Plugin owns the provided ConfigBridge? Stub kept here so the
 *  sidebar can hand a `PluginRow` to the panel and trust the panel knows how to look up
 *  the bridge on demand without importing PluginManager. */
@Composable
internal fun PluginConfigPanelFor(row: PluginRow, onBack: () -> Unit)
{
    val bridge by remember(row.rowKey) { mutableStateOf(PluginBridge.configBridge(row)) }
    PluginConfigPanel(row.displayName, bridge, onBack)
}
