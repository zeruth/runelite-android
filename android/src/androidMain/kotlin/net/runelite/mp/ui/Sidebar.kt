package net.runelite.mp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.runelite.client.RuneLiteProperties
import net.runelite.mp.ui.bridge.ExternalPluginBridge
import net.runelite.mp.ui.bridge.ExternalPluginEntry
import net.runelite.mp.ui.bridge.FavoritesBridge
import net.runelite.mp.ui.bridge.PluginBridge
import net.runelite.mp.ui.bridge.PluginRow
import net.runelite.mp.ui.bridge.ProfileBridge
import net.runelite.mp.ui.bridge.ProfileRow

/**
 * Three top-level surfaces inside the same sidebar shell:
 *  - {@link SidebarTab#PLUGINS}: list of installed plugins with toggles + gear shortcut.
 *    Identical to the pre-tabbed sidebar.
 *  - {@link SidebarTab#PROFILES}: list of config profiles; tap to activate, "+" creates,
 *    inline pencil/x for rename and delete. Backed by [ProfileBridge].
 *  - {@link SidebarTab#EXTERNAL}: plugin hub view. First open triggers a one-shot
 *    manifest download; subsequent renders reuse the cached list. Tap installs/removes.
 *
 * Selected tab is hoisted into a `remember` so it persists across sidebar open/close
 * within the same session.
 */
internal enum class SidebarTab(val label: String)
{
    PLUGINS("Plugins"),
    PROFILES("Profiles"),
    EXTERNAL("External"),
}

@Composable
internal fun PluginSidebar(
    width: androidx.compose.ui.unit.Dp,
    onConfigure: (PluginRow) -> Unit,
)
{
    var tab by remember { mutableStateOf(SidebarTab.PLUGINS) }
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(RlPalette.DarkerGray)
            .border(1.dp, RlPalette.SurfaceBorder),
    ) {
        SidebarHeader(tab = tab, onTabChange = { tab = it })
        when (tab)
        {
            SidebarTab.PLUGINS -> PluginsTab(onConfigure)
            SidebarTab.PROFILES -> ProfilesTab()
            SidebarTab.EXTERNAL -> ExternalTab()
        }
    }
}

/** Brand header + the three-segment tab strip. The header is gentler than the previous
 *  big "Plugins" title since the tab labels do the same labelling work. */
@Composable
private fun SidebarHeader(tab: SidebarTab, onTabChange: (SidebarTab) -> Unit)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RlPalette.DarkGray)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(RlPalette.DarkerGray)
                .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(4.dp)),
        ) {
            for (entry in SidebarTab.values())
            {
                val active = entry == tab
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (active) RlPalette.AccentSurface else Color.Transparent)
                        .clickable { onTabChange(entry) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        entry.label,
                        color = if (active) Color.White else RlPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Plugins tab — same surface the sidebar had before tabs landed.
// --------------------------------------------------------------------------------------

@Composable
private fun PluginsTab(onConfigure: (PluginRow) -> Unit)
{
    val plugins = remember { mutableStateListOf<PluginRow>() }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(emptySet<String>()) }

    // Refresh from host every second — cheap (just a list of fields). Catches the case
    // where a plugin enables/disables itself (eg. on login), or where the panel
    // appeared before PluginManager finished registering. Also re-reads favorites here
    // so a config-profile switch eventually surfaces the new pinned-set without needing
    // a sidebar reopen.
    LaunchedEffect(Unit)
    {
        favorites = FavoritesBridge.list()
        while (true)
        {
            val next = PluginBridge.list()
            val same = next.size == plugins.size && next.indices.all {
                plugins[it].plugin === next[it].plugin && plugins[it].enabled == next[it].enabled
            }
            if (!same)
            {
                plugins.clear()
                plugins.addAll(next)
            }
            val liveFavs = FavoritesBridge.list()
            if (liveFavs != favorites) favorites = liveFavs
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize())
    {
        SearchField(query, "Search plugins…") { query = it }
        // Pinned favorites sort to the top — within each group, fall back to the host's
        // existing alphabetical order from PluginBridge.list().
        val source = if (query.isBlank()) plugins.toList()
        else plugins.filter { it.displayName.contains(query, ignoreCase = true) }
        val ordered = source.sortedWith(
            compareByDescending<PluginRow> { favorites.contains(it.rowKey) }
                .thenBy { it.displayName.lowercase() }
        )
        if (ordered.isEmpty())
        {
            EmptyState(if (plugins.isEmpty()) "Loading plugins…" else "No plugins match \"$query\".")
        }
        else
        {
            LazyColumn(modifier = Modifier.fillMaxSize())
            {
                items(ordered, key = { it.rowKey }) { row ->
                    PluginRowItem(
                        row = row,
                        favorite = favorites.contains(row.rowKey),
                        onToggleFavorite = {
                            val next = !favorites.contains(row.rowKey)
                            FavoritesBridge.setFavorite(row.rowKey, next)
                            favorites = if (next) favorites + row.rowKey else favorites - row.rowKey
                        },
                        onConfigure = onConfigure,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRowItem(
    row: PluginRow,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onConfigure: (PluginRow) -> Unit,
)
{
    var enabled by remember(row.rowKey) { mutableStateOf(row.enabled) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (row.hasConfig) onConfigure(row) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        FavoriteStar(favorite = favorite, onToggle = onToggleFavorite)
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.displayName,
                color = if (enabled || !row.toggleable) RlPalette.TextPrimary else RlPalette.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.description.isNotEmpty())
            {
                Text(
                    row.description,
                    color = RlPalette.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Gear sits to the LEFT of the toggle so the toggle column is always the
        // rightmost affordance. Rows without a config still reserve the gear's
        // width as a spacer so the x positions of toggles line up across rows.
        if (row.hasConfig)
        {
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(RlPalette.MediumGray)
                    .clickable { onConfigure(row) },
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", color = RlPalette.TextPrimary, fontSize = 11.sp)
            }
        }
        else
        {
            Spacer(Modifier.size(28.dp))  // 8dp gap + 20dp gear placeholder
        }
        Spacer(Modifier.size(8.dp))
        // Toggle pinned to the right edge — suppressed for fake rows (always-on configs
        // like RuneLite client settings) but the column is reserved as a spacer so
        // widths stay uniform.
        if (row.toggleable)
        {
            val pluginRef = row.plugin
            Box(
                Modifier
                    .size(width = 30.dp, height = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (enabled) RlPalette.Accent else Color(0xFF3A3A3A))
                    .clickable(enabled = pluginRef != null) {
                        if (pluginRef == null) return@clickable
                        val next = !enabled
                        enabled = next
                        PluginBridge.setEnabled(pluginRef, next)
                    },
                contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .padding(start = if (enabled) 0.dp else 2.dp, end = if (enabled) 2.dp else 0.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White),
                )
            }
        }
        else
        {
            Spacer(Modifier.size(30.dp))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RlPalette.DividerSoft),
    )
}

// --------------------------------------------------------------------------------------
// Profiles tab — list profiles, switch active, create / rename / delete.
// --------------------------------------------------------------------------------------

@Composable
private fun ProfilesTab()
{
    val profiles = remember { mutableStateListOf<ProfileRow>() }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit)
    {
        while (true)
        {
            val next = ProfileBridge.list()
            val same = next.size == profiles.size && next.indices.all {
                profiles[it].id == next[it].id && profiles[it].active == next[it].active &&
                    profiles[it].name == next[it].name
            }
            if (!same)
            {
                profiles.clear()
                profiles.addAll(next)
            }
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize())
    {
        // Top toolbar — "New profile" affordance. When tapped, swaps to an inline name
        // field; submitting (enter / blur tap on +) commits.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(RlPalette.DarkGray)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (creating)
            {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RlPalette.DarkerGray)
                        .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    if (newName.isEmpty())
                    {
                        Text("Profile name…", color = RlPalette.TextDisabled, fontSize = 12.sp)
                    }
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(RlPalette.Accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(6.dp))
                ToolButton("✓") {
                    if (newName.isNotBlank())
                    {
                        ProfileBridge.create(newName)
                        newName = ""
                        creating = false
                    }
                }
                Spacer(Modifier.size(4.dp))
                ToolButton("✕") { newName = ""; creating = false }
            }
            else
            {
                Text("Profiles", color = RlPalette.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                ToolButton("＋", tooltip = "New profile") { creating = true }
            }
        }

        if (profiles.isEmpty())
        {
            EmptyState("No profiles yet.")
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize())
        {
            items(profiles, key = { it.rowKey }) { row ->
                ProfileRowItem(
                    row = row,
                    renaming = row.id == renamingId,
                    renameText = renameText,
                    onRenameTextChange = { renameText = it },
                    onActivate = { if (!row.active) ProfileBridge.switchTo(row.id) },
                    onStartRename = { renamingId = row.id; renameText = row.name },
                    onCommitRename = {
                        if (renameText.isNotBlank())
                        {
                            ProfileBridge.rename(row.id, renameText)
                        }
                        renamingId = null
                    },
                    onCancelRename = { renamingId = null },
                    onDelete = { ProfileBridge.delete(row.id) },
                )
            }
        }
    }
}

@Composable
private fun ProfileRowItem(
    row: ProfileRow,
    renaming: Boolean,
    renameText: String,
    onRenameTextChange: (String) -> Unit,
    onActivate: () -> Unit,
    onStartRename: () -> Unit,
    onCommitRename: () -> Unit,
    onCancelRename: () -> Unit,
    onDelete: () -> Unit,
)
{
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (row.active) RlPalette.AccentSurface else Color.Transparent)
            .clickable(enabled = !renaming, onClick = onActivate)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (renaming)
        {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(RlPalette.DarkerGray)
                    .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                BasicTextField(
                    value = renameText,
                    onValueChange = onRenameTextChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    cursorBrush = SolidColor(RlPalette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.size(6.dp))
            ToolButton("✓") { onCommitRename() }
            Spacer(Modifier.size(4.dp))
            ToolButton("✕") { onCancelRename() }
        }
        else
        {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    color = if (row.active) Color.White else RlPalette.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (row.active) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (row.active) "Active" else if (row.sync) "Synced" else "Local",
                    color = RlPalette.TextSecondary,
                    fontSize = 11.sp,
                )
            }
            ToolButton("✎", tooltip = "Rename") { onStartRename() }
            Spacer(Modifier.size(4.dp))
            // Active profile can't be deleted — disable visually so the user understands.
            ToolButton(
                label = "🗑",
                tooltip = if (row.active) "Switch profiles first" else "Delete",
                enabled = !row.active,
            ) { onDelete() }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RlPalette.DividerSoft),
    )
}

// --------------------------------------------------------------------------------------
// External (plugin hub) tab — installed + available externals.
// --------------------------------------------------------------------------------------

@Composable
private fun ExternalTab()
{
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var entries by remember { mutableStateOf<List<ExternalPluginEntry>>(emptyList()) }
    val installed = remember { mutableStateOf(setOf<String>()) }
    // PNG bytes -> decoded ImageBitmap, keyed by internalName. Compose drops the cache
    // when the tab leaves composition; that's fine, the icon URLs are still cached by
    // OkHttp on the next visit.
    val icons = remember { mutableStateMapOf<String, ImageBitmap>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh)
    {
        loading = true
        loadError = null
        val fetched = withContext(Dispatchers.IO) { ExternalPluginBridge.available() }
        entries = fetched.getOrDefault(emptyList())
        loadError = fetched.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        installed.value = ExternalPluginBridge.installed()
        loading = false
    }
    // Poll local install state without repeatedly downloading the catalog.
    LaunchedEffect(Unit)
    {
        while (true)
        {
            delay(1500)
            val live = ExternalPluginBridge.installed()
            if (live != installed.value) installed.value = live
        }
    }

    Column(modifier = Modifier.fillMaxSize())
    {
        SearchField(query, "Search external plugins…") { query = it }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Android catalog ${RuneLiteProperties.getPluginHubVersion()}",
                color = RlPalette.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (loadError != null) "Retry" else "Refresh",
                color = if (loading) RlPalette.TextDisabled else RlPalette.Accent,
                fontSize = 12.sp,
                modifier = Modifier.clickable(enabled = !loading) { refresh++ }.padding(8.dp),
            )
        }
        when
        {
            loading -> EmptyState("Loading manifest…")
            loadError != null -> EmptyState("Couldn't load Plugin Hub.\n\n$loadError\n\nTap Retry to try again.")
            entries.isEmpty() -> EmptyState("This catalog contains no plugins.")
            else ->
            {
                val live = installed.value
                // RuneLite's desktop hub panel sort, mirrored: installed first, then
                // descending install count (so plugins lots of users run float up),
                // then alphabetical. Unknown counts (-1 — internal name not present
                // in api.runelite.net's tally because we're on a self-hosted mirror)
                // fall through to the alphabetical pass.
                val filtered = entries.asSequence()
                    .filter {
                        query.isBlank() || it.displayName.contains(query, ignoreCase = true) ||
                            it.author.contains(query, ignoreCase = true)
                    }
                    .sortedWith(
                        compareByDescending<ExternalPluginEntry> { live.contains(it.internalName) }
                            .thenByDescending { it.userCount }
                            .thenBy { it.displayName.lowercase() }
                    )
                    .toList()
                LazyColumn(modifier = Modifier.fillMaxSize())
                {
                    items(filtered, key = { it.rowKey }) { row ->
                        ExternalRowItem(
                            row = row.copy(installed = live.contains(row.internalName)),
                            icon = icons[row.internalName],
                            onInstall = {
                                ExternalPluginBridge.install(row.internalName)
                                scope.launch { installed.value = ExternalPluginBridge.installed() }
                            },
                            onRemove = {
                                ExternalPluginBridge.remove(row.internalName)
                                scope.launch { installed.value = ExternalPluginBridge.installed() }
                            },
                            onIconLoaded = { bitmap -> icons[row.internalName] = bitmap },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalRowItem(
    row: ExternalPluginEntry,
    icon: ImageBitmap?,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onIconLoaded: (ImageBitmap) -> Unit,
)
{
    // Per-row async icon fetch. Re-keys on (internalName, iconHash) so a hub-side icon
    // update flips us back into loading; the cache lookup at the top of ExternalTab
    // skips this entirely for rows we've already decoded.
    val iconHash = row.iconHash
    if (icon == null && iconHash != null)
    {
        LaunchedEffect(row.internalName, iconHash)
        {
            val bytes = withContext(Dispatchers.IO) {
                ExternalPluginBridge.downloadIcon(row.internalName, iconHash)
            } ?: return@LaunchedEffect
            val bitmap = withContext(Dispatchers.Default) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } ?: return@LaunchedEffect
            onIconLoaded(bitmap.asImageBitmap())
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        // 40dp icon box — dim background so still-loading rows aren't visually empty.
        // Plugins without an iconHash (rare; most hubs ship one) keep the placeholder
        // permanently rather than show a broken-image symbol.
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(RlPalette.DarkGray),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null)
            {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.displayName,
                    color = if (row.available) RlPalette.TextPrimary else RlPalette.TextDisabled,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.version.isNotEmpty())
                {
                    Text(
                        "v${row.version}",
                        color = RlPalette.TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            val author = row.author.ifEmpty { "—" }
            val users = if (row.userCount >= 0) " · ${formatInstallCount(row.userCount)} users" else ""
            Text(
                "by $author$users",
                color = RlPalette.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.description.isNotEmpty())
            {
                Text(
                    row.description,
                    color = RlPalette.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Warnings (hub-flagged risky/AGS) and unavailable reasons get their own line
            // in the accent colour so users can see them before tapping install.
            val notice = row.warning ?: row.unavailableReason
            if (notice != null)
            {
                Text(
                    notice,
                    color = RlPalette.Accent,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        if (row.installed)
        {
            // Installed pill — tap to remove. We use a tinted background so the user has
            // a clear visual that something's already enabled here.
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(RlPalette.AccentSurface)
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Remove", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        else
        {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(RlPalette.MediumGray)
                    .clickable(enabled = row.available, onClick = onInstall)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    if (row.available) "Install" else "Unavailable",
                    color = if (row.available) Color.White else RlPalette.TextDisabled,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RlPalette.DividerSoft),
    )
}

/** Mirrors the desktop hub's "123" / "1.2k" / "12k" install-count formatting. */
private fun formatInstallCount(count: Int): String = when
{
    count < 1000 -> count.toString()
    count < 10_000 -> "%.1fk".format(count / 1000.0)
    else -> "${count / 1000}k"
}

// --------------------------------------------------------------------------------------
// Shared widgets used by more than one tab.
// --------------------------------------------------------------------------------------

@Composable
private fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit)
{
    Box(
        Modifier
            .fillMaxWidth()
            .background(RlPalette.DarkGray)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(RlPalette.DarkerGray)
                .border(1.dp, RlPalette.SurfaceBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            if (value.isEmpty())
            {
                Text(placeholder, color = RlPalette.TextDisabled, fontSize = 12.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                cursorBrush = SolidColor(RlPalette.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyState(message: String)
{
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(message, color = RlPalette.TextSecondary, fontSize = 12.sp)
    }
}

/** Yellow filled ★ when pinned, dim outline ☆ otherwise. Sized to fit beside the
 *  text column without pushing the rest of the row's affordances around. */
@Composable
private fun FavoriteStar(favorite: Boolean, onToggle: () -> Unit)
{
    Box(
        Modifier
            .size(22.dp)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (favorite) "★" else "☆",
            color = if (favorite) Color(0xFFFFC857) else RlPalette.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToolButton(
    label: String,
    tooltip: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit,
)
{
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) RlPalette.MediumGray else Color(0xFF2A2A2A))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) RlPalette.TextPrimary else RlPalette.TextDisabled,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
