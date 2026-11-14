# Adventure Guide — product and architecture spec

Status: draft for review (card #104, phase F #103). Two decisions in this document need the person's
sign-off before M1 starts; they are marked **DECISION** (licensing, §8.5; module placement, §9).

The Adventure Guide is one progression plugin that merges two things players already use side by side:

- the long-form route of the **B0aty HCIM guide** hub plugin (`com.b0atyguide.*`: chapters, sections,
  checklists, manual progress; exercised headless by `tools-src/TestBoatyGuide.java`), and
- the step execution of **quest-helper** (`com.questhelper.*`: in-scene highlights, arrows, requirement
  lists, per-quest step machines).

On Android it is a touch-first Compose experience; on desktop it is a plain Swing `PluginPanel` with the same
data and the same progress file, so a profile moves between the two without loss.

---

## 1. Goals and non-goals

### Goals

1. **One route, executed in place.** A guide is a sequence of steps; a quest step hands off to quest-helper,
   so the player never switches between two sidebars.
2. **Progress that ticks itself.** Quests, skills, unlocks and item counts are detected from the client
   (`Quest.getState`, varbits/varps, `Skill` levels, inventory/bank/GE/collection-log counts). Manual ticks
   are for what cannot be detected.
3. **Content is data.** Guides are versioned JSON packs. The app bundles one starter guide, and the
   community publishes routes for mains, ironmen, HCIM and GIM without a plugin release.
4. **Account-type aware.** One guide carries variants (main / ironman / HCIM / GIM); steps and sources that
   do not apply to the active account type are hidden, not just greyed.
5. **Touch-first on Android.** Progress rings, swipeable step cards, item checklists, map pins and haptics on
   completion. Every action fits a one-thumb tap target (≥ 48 dp).
6. **Desktop parity.** The same plugin runs on desktop RuneLite with a Swing panel. Behaviour, progress
   file and detection are identical; only presentation differs.
7. **Degrade cleanly.** Without quest-helper the guide still works: quest steps show requirements and a wiki
   link instead of in-scene help.

### Non-goals

- **No automation.** The guide never clicks, walks, or sends input. It only displays and highlights, which
  stays within Jagex's third-party client rules, the same as quest-helper.
- **No quest step machines of our own.** Per-quest step logic stays quest-helper's. We do not fork or vendor
  its quest classes.
- **No guide editor in M1–M4.** Packs are authored as JSON, and a schema plus a validator ship with the
  repo. An in-app editor is a later idea.
- **No server.** Packs download from static URLs (GitHub releases / raw). No accounts, no telemetry, no
  sync service; progress lives in RuneLite's ConfigManager and syncs the way profiles already do.
- **No bundling of third-party guide text** whose license forbids it (§8).
- **No gamepack changes.** Everything uses `runelite-api` and the event bus, as an upstream plugin would.

---

## 2. Data model

```
Guide 1─* Chapter 1─* Section 1─* Step
                                   └─ kind ∈ {quest, skill, item, travel, unlock, bossPrep, note}
Step *─* Step   (prerequisites, by step id, may cross sections and chapters)
Guide 1─* Variant (main | ironman | hcim | gim) — filters steps/sources, may override text
```

### 2.1 Guide pack (JSON, schema version 1)

```json
{
  "schema": 1,
  "id": "starter-route",
  "version": "1.3.0",
  "title": "Starter Route",
  "author": "runelite-mp",
  "license": "CC-BY-4.0",
  "attribution": "Original text; game data from the OSRS Wiki (link only).",
  "minClient": "1.11.0",
  "variants": ["main", "ironman", "hcim", "gim"],
  "chapters": [
    {
      "id": "ch1", "title": "Tutorial Island to Lumbridge",
      "sections": [
        {
          "id": "ch1.s1", "title": "Early quests",
          "steps": [
            { "id": "cooks", "kind": "quest", "questId": "COOKS_ASSISTANT",
              "text": "Complete Cook's Assistant for 300 Cooking xp." },
            { "id": "att20", "kind": "skill", "skill": "ATTACK", "level": 20,
              "requires": ["cooks"] },
            { "id": "bones", "kind": "item", "items": [526], "quantity": 30,
              "sources": [{ "type": "drop", "npc": "Chicken", "region": 12850 }],
              "variants": ["ironman", "hcim", "gim"] },
            { "id": "draynor", "kind": "travel",
              "point": { "x": 3093, "y": 3244, "plane": 0 }, "region": 12338 },
            { "id": "fairy", "kind": "unlock",
              "unlock": { "type": "varbit", "id": 4875, "op": ">=", "value": 1 } },
            { "id": "obor", "kind": "bossPrep",
              "gear": [
                { "slot": "WEAPON", "items": [1323, 1325], "note": "Any scimitar" },
                { "slot": "FOOD", "items": [333], "quantity": 10 }
              ] },
            { "id": "tip", "kind": "note", "text": "HCIM: never fight Obor below 30 HP.",
              "variants": ["hcim"] }
          ]
        }
      ]
    }
  ]
}
```

### 2.2 Common step fields

| field | type | meaning |
|---|---|---|
| `id` | string | unique within the guide; the progress key. Never reused across versions (§4.5). |
| `kind` | enum | one of the seven kinds below |
| `title` / `text` | string | short label / body (plain text with `[[wiki:Page]]` and `[[item:526]]` links) |
| `requires` | string[] | step ids that must be complete before this step is *current* (soft gate: it can still be ticked) |
| `variants` | string[] | account types this step applies to; absent = all |
| `override` | map variant→partial step | per-variant replacement of `text`, `quantity`, `sources`, `gear` |
| `optional` | bool | excluded from section/chapter percentages |
| `manualOnly` | bool | disables detection for this step (the author knows detection would be wrong) |

### 2.3 Step kinds

| kind | payload | detection |
|---|---|---|
| `quest` | `questId` — `net.runelite.api.Quest` enum name; optional `questHelper` override when quest-helper's enum name differs | `Quest.getState(client) == FINISHED` |
| `skill` | `skill` (`net.runelite.api.Skill` name), `level`, optional `boosted: false` | `client.getRealSkillLevel(skill) >= level` |
| `item` | `items` (int[] of item ids, any-of; noted/placeholder ids canonicalised via `ItemManager.canonicalize`), `quantity`, `sources[]` (drop / shop / spawn / skill / ge, each with optional region/npc/text), `where` ⊆ {inventory, equipment, bank, ge, clog}, default {inventory, equipment, bank} | count across `where` ≥ `quantity` (§3.2) |
| `travel` | `point` (WorldPoint x,y,plane), `region` (region id), optional `radius` (default 5) | player `WorldPoint` within `radius` of `point`, or inside `region` when no point |
| `unlock` | `unlock`: `{type: varbit|varp|quest, id \| questId, op: == != >= <= > <, value \| state}` | varbit/varp compare, or `Quest.getState` compared to `NOT_STARTED/IN_PROGRESS/FINISHED` |
| `bossPrep` | `gear[]`: rows of `{slot, items[] (any-of), quantity?, note?}`; slot is an `EquipmentInventorySlot` name or `FOOD`/`POTION`/`OTHER` | each row is an item check over inventory+equipment (+bank for "owned"); the step is complete when every non-optional row is satisfied |
| `note` | text only | never detected; manual tick or `optional` |

### 2.4 Account-type variants

The active variant comes from `client.getVar(VarbitID.ACCOUNT_TYPE)` (`AccountType`/`IRONMAN`,
`HARDCORE_IRONMAN`, `ULTIMATE_IRONMAN`→ironman, `GROUP_IRONMAN`/`HARDCORE_GROUP_IRONMAN`→gim). The player can
pin a variant in settings, for example to read the ironman route on a main. Resolution:

1. Drop steps whose `variants` excludes the active one.
2. Apply `override[variant]` field by field.
3. Drop `sources` entries tagged with an excluded variant. For example, `ge` sources are main-only by
   default.
4. Prerequisites that point at a dropped step count as satisfied, so a variant never deadlocks.

### 2.5 Prerequisites

`requires` forms a DAG over step ids. The loader rejects a pack with a cycle or a dangling id and names the
step. A step's state is one of `locked` (a prerequisite is incomplete), `current` (first unlocked, incomplete
step in reading order), `available`, `complete` or `skipped` (§3.3). Locking is advisory: the player can tick
a locked step, and the UI asks once ("Cook's Assistant isn't done yet — tick anyway?").

### 2.6 Runtime types (Java, plugin module)

```java
record-like final classes, immutable after load:
Guide { String id, version, title, author, license, attribution; List<Chapter> chapters; }
Chapter { String id, title; List<Section> sections; }
Section { String id, title; List<Step> steps; }
abstract Step { String id, title, text; List<String> requires; Set<Variant> variants; boolean optional, manualOnly; }
QuestStep, SkillStep, ItemStep, TravelStep, UnlockStep, BossPrepStep, NoteStep extends Step
ResolvedGuide = Guide × Variant → flattened step list + index by id + prerequisite DAG
```

The model is plain Java 11 with Gson, the same baseline as hub plugins. Nothing in it references Swing or
Android, so the Compose UI on Android and the Swing panel on desktop consume the same objects.

---

## 3. Progress model

### 3.1 Storage

- **Per RuneLite profile and per account.** The config group is `adventureguide`, and the key is
  `progress.<guideId>`, scoped with `ConfigManager.setRSProfileConfiguration`. Progress therefore follows
  the logged-in account (an HCIM and a main on one profile don't share ticks) and syncs with RuneLite
  profile sync like any RS-profile key.
- **Value:** one JSON object, written debounced (≤ 1 write / 2 s) from the client thread:

```json
{
  "guideVersion": "1.3.0",
  "variant": "hcim",
  "pinnedVariant": null,
  "steps": {
    "cooks":  { "manual": null,   "detected": "complete", "at": 1731400000 },
    "tip":    { "manual": "complete", "at": 1731400100 },
    "bones":  { "manual": "skipped" },
    "att20":  { "manual": "incomplete", "detected": "complete" }
  },
  "itemCache": { "bank": { "526": 31 }, "ge": {}, "clog": { "526": 1 }, "bankSeenAt": 1731400200 }
}
```

- `itemCache` exists because bank, GE and collection-log contents are only readable while their interface
  is open or after the matching event. The last-seen counts persist so item steps stay ticked across
  sessions.

### 3.2 Detection sources

| source | API | refresh trigger |
|---|---|---|
| quests | `Quest.getState(client)` (client thread) | `VarbitChanged`, `GameStateChanged(LOGGED_IN)`, chat "Congratulations, you've completed a quest" |
| varbits / varps | `client.getVarbitValue`, `client.getVarpValue` | `VarbitChanged` filtered to ids referenced by the resolved guide |
| skills | `client.getRealSkillLevel` | `StatChanged` |
| inventory / equipment | `ItemContainer` `InventoryID.INVENTORY` / `EQUIPMENT` | `ItemContainerChanged` |
| bank | `InventoryID.BANK` container, cached in `itemCache.bank` | `ItemContainerChanged(BANK)`; for GIM also `GROUP_STORAGE` |
| GE | `client.getGrandExchangeOffers()` — bought quantity of in-progress/complete buy offers | `GrandExchangeOfferChanged` |
| collection log | `clog` counts from the collection log interface / `ScriptPreFired` of the clog item script, cached in `itemCache.clog` | opening the collection log; `ChatMessage` "New item added to your collection log" increments |
| location | `client.getLocalPlayer().getWorldLocation()` | `GameTick`, throttled to every 5 ticks |

Detection runs on the client thread and is incremental. Each event recomputes only the steps that reference
the changed key, using an index of key → step ids built at resolve time. A full recompute runs at login and
when the guide or variant changes.

### 3.3 Conflict rules between manual and detected

Each step keeps two independent fields, `manual ∈ {null, complete, incomplete, skipped}` and
`detected ∈ {null, complete, incomplete}`. The **effective** state is:

1. `manual == skipped` → **skipped**. The player opted out of a step; it counts as done for prerequisites
   and is excluded from percentages.
2. `manual == incomplete` and `detected == complete` → **incomplete**. An explicit untick wins. This covers
   "I want to redo this" and false positives such as a borrowed item in the inventory. The UI shows a small
   "detected" badge so the disagreement is visible, and offers "Clear override".
3. `manual == complete` → **complete**, whatever detection says. Detection can be blind: the bank was not
   opened since the reinstall, or the clog was never opened.
4. otherwise → `detected`, else **incomplete**.

The rules also say what happens over time:

- **Detection never writes `manual`.** A detected completion does not become sticky. If detection later
  says incomplete (items were used up), the step un-ticks, *except* for `quest`, `skill` and `unlock`
  kinds. Their completions are monotonic in-game, so the first detected `complete` is latched into
  `detected` and never downgraded. Item steps are deliberately non-latching: "have 30 bones" is a
  current-state goal, and the author marks `latch: true` on item steps that mean "have obtained once".
- **A manual tick on a detectable step clears itself** once detection agrees. When
  `manual == complete && detected == complete`, the manual value is dropped on save, so a later untick by
  detection is not masked forever. `manual == incomplete` is kept until the player clears it.
- `manualOnly` steps ignore `detected` entirely.

### 3.4 Percentages and rings

Section, chapter and guide progress = complete ÷ (applicable − optional − skipped) over the resolved variant.
Rings on Android and progress bars on desktop read the same numbers.

### 3.5 Guide upgrades

Progress keys are step ids, so a pack update keeps every tick whose id still exists. Removed ids are kept in
the JSON for one more version, so a downgrade restores them, and are then pruned. A pack may declare
`"renamed": {"oldId": "newId"}` to migrate ticks.

---

## 4. Content sources

1. **Bundled starter guide.** The plugin jar ships one pack under
   `src/main/resources/net/runelite/mp/adventureguide/packs/starter-route.json`. Its text is written by this
   project (see §8): a general early-game route with main/ironman/HCIM/GIM variants, about 150 steps. It
   is always available offline and is the default guide.
2. **Downloadable packs.** `Packs` → `Add pack…` takes a URL or picks from a curated index at a static URL
   (`packs/index.json` in a GitHub repo: `{id, title, author, license, version, url, sha256, variants}`).
   Downloads go through RuneLite's injected `OkHttpClient`. The sha256 is verified when the index gives one.
   Packs are cached under `RuneLite.RUNELITE_DIR/adventure-guide/packs/<id>-<version>.json` and validated
   against the schema before they are offered. Packs are pure data; there is no code in a pack.
3. **Runtime adapter for the B0aty hub plugin (optional, M4).** When the B0aty guide hub plugin is
   installed, an adapter reads its bundled guide in memory through the plugin's own loader
   (`com.b0atyguide.data.GuideLoader.loadBundled()`, the call `TestBoatyGuide` already exercises) and maps
   it onto our model as manual-tick `note`/`item`/`quest` steps. We *link* to an installed copy: nothing is
   copied into our jar, cache or repo (§8.3). The adapter is gated on the license check in §8.5.
4. **Local file import** (Android: SAF document picker; desktop: `JFileChooser`). This is for authors
   testing a pack.

The pack index and all packs are listed with their `license` and `attribution` in the guide's About sheet.

---

## 5. UI surfaces

### 5.1 Android — Compose via `PanelRegistry`

The sidebar today is a fixed `SidebarTab` enum (`PLUGINS`, `PROFILES`, `EXTERNAL` in
`android/.../ui/Sidebar.kt`), and plugin `NavigationButton` panels go through `SwingPanelHost`, which
rasterises the Swing panel. The Adventure Guide is the first plugin to get a **native Compose panel**. Card
work introduces:

- `net.runelite.mp.ui.panels.PanelRegistry`, a small registry keyed by plugin class name:
  `register(pluginFqcn, title, icon, content: @Composable () -> Unit)`. When the icon strip opens a
  plugin's navigation button and the registry has a Compose panel for that plugin, it shows the Compose
  panel instead of `SwingPanelHost`. Otherwise nothing changes, so every other plugin keeps the
  rasterised Swing path.
- `net.runelite.mp.ui.bridge.AdventureGuideBridge`, which reaches the running plugin with
  `RuneLiteAccess.registeredPluginByName(...)`. It never uses `injector.getInstance`, because plugins are
  not singletons (see the KDoc in `RuneLiteAccess.kt`). It exposes a `StateFlow<GuideUiState>` that the
  plugin pushes from the client thread through a listener interface. Mutations (tick, skip, select step,
  start quest helper) are marshalled the same way `PluginBridge.setEnabled` does it: Swing/plugin state on
  `SwingUtilities.invokeLater`, game state on `ClientThread.invoke`.

Compose surfaces:

- **Overview**: guide picker, variant chip, a big ring for the whole guide, and a ring per chapter.
- **Chapter**: a list of section cards, each with a mini ring and its current step preview.
- **Step card pager**: one card per step, swiped horizontally. The card shows the kind icon, text,
  requirement chips (green or red), an item checklist with icons from `ItemManager` that tick live from
  inventory/bank, a "Start in Quest Helper" button for quest steps, and a "Show on map" button for travel
  steps. Ticking a step plays haptic `CONFIRM`. Completing a section plays a ring-fill animation and haptic
  `LONG_PRESS`.
- **Map pin**: for travel and item sources with a region. This sets a `WorldMapPoint` pin through
  `WorldMapPointManager` and a hint arrow (`client.setHintArrow`) while the step is current. Both are
  cleared when the step completes or is deselected.
- **Settings**: account variant pin, detection toggles per source, and pack management.

### 5.2 Desktop — Swing `PluginPanel`

`AdventureGuidePanel extends PluginPanel`, added with a `NavigationButton` exactly like upstream plugins. It
shows a guide/variant combo, a chapter `JProgressBar`, collapsible sections (the same shape as the B0aty
`GuidePanel` that `TestBoatyGuide` drives), step rows with checkboxes and detected badges, a search filter,
and a "Start in Quest Helper" button. It uses only stock Swing plus RuneLite's `ColorScheme`/`FontManager`,
so on Android it would also render through `SwingPanelHost` if the Compose panel were absent. That is the
fallback when `PanelRegistry` registration fails.

### 5.3 Overlays (both platforms)

- `GuideStepOverlay`, an `OverlayPanel` with the current step title and its unmet requirements. It hides
  while quest-helper's own panel overlay is active for the same quest.
- Travel and item-source tiles highlighted with `OverlayUtil.renderTileOverlay` when they are in the scene.

---

## 6. Integration contract with quest-helper (`QuestHelperBridge`)

quest-helper stays a separately installed hub plugin, loaded by its own classloader (`DexClassLoader` on
Android, the hub `PluginClassLoader` on desktop). We never take a compile-time dependency on it. All access
is by reflection, with class-name string matching, which is the pattern `registeredPluginByName` documents
for `com.questhelper.QuestHelperPlugin`.

`QuestHelperBridge` lives in the plugin module, so desktop and Android share it:

```java
interface QuestHelperBridge {
    Availability availability();              // NOT_INSTALLED, INSTALLED_DISABLED, RUNNING, INCOMPATIBLE(reason)
    boolean start(Quest quest);               // open the helper for this quest; false if unknown/unavailable
    void stop();                              // close the active helper (only if we started it, see below)
    Optional<Mirror> current();               // the active helper's quest, step text, requirements
    void addListener(Listener l);             // onStepChanged(Mirror), onQuestCompleted(Quest), onAvailabilityChanged
}
Mirror { Quest quest; String stepText; List<Req> requirements; }   // Req { String text; boolean met; }
```

### 6.1 Lookup

- Plugin: walk `PluginManager.getPlugins()` and match `getClass().getName().equals("com.questhelper.QuestHelperPlugin")`.
  `PluginManager.isPluginEnabled` decides between `INSTALLED_DISABLED` and `RUNNING`. Lookup is redone on
  `ExternalPluginsChanged` and `PluginChanged`. The bridge never caches a `Class` or `Method` across those
  events, because a hub reinstall swaps the classloader.
- Every member is resolved through the plugin instance's own classloader (`plugin.getClass().getClassLoader()`).
- Members used (names as on quest-helper master at the time of writing; **M2 pins a quest-helper commit and a
  host test asserts each one**, the same way `TestBoatyGuide` asserts the B0aty members):
  - `QuestHelperPlugin.getSelectedQuest()` → the active `QuestHelper` or null
  - `QuestHelperPlugin` field `questManager` → `QuestManager.startUpQuest(QuestHelper, boolean)` and
    `QuestManager.shutDownQuestFromSidebar()`
  - enum `com.questhelper.questinfo.QuestHelperQuest`: `values()`, `getName()`, `getQuestHelper()`. It is
    matched to `net.runelite.api.Quest` by the step's `questHelper` override, then by enum name, then by
    display name.
  - `QuestHelper.getCurrentStep()` → `QuestStep.getText()`/`getOverlayText()`, and
    `QuestHelper.getGeneralRequirements()` → `Requirement.check(Client)`, `getDisplayText()`
- If any member is missing, the bridge reports `INCOMPATIBLE(member)` once in the log and once in the UI
  ("Quest Helper vX changed; in-scene help unavailable"). It never throws into the guide.

### 6.2 Threading

`startUpQuest`/`shutDown…` touch quest-helper's Swing panel and overlays, so they run on the EDT
(`SwingUtilities.invokeLater`), the same rule `PluginBridge.setEnabled` documents for `startPlugin`.
Requirement checks call `Client` and run on `ClientThread.invoke`. The mirror is polled on `GameTick`
(cheap: one reflective call plus a string compare) and emitted only when it changes.

### 6.3 Behaviour

- **Start**: "Start in Quest Helper" on a quest step calls `start(quest)`. The guide records
  `startedByGuide = quest`.
- **Mirror**: while the active helper's quest equals the current guide step's quest, the step card shows
  quest-helper's step text and requirement list, with met/unmet coming from quest-helper's own checks. The
  guide's own overlay steps back (§5.3).
- **Completion**: the guide hears `onQuestCompleted` from the bridge and also `Quest.getState` going to
  `FINISHED` through its own detection. Either source marks the step detected-complete. If
  `startedByGuide` matches, the bridge calls `stop()` and the guide advances to the next current step.
  Quest-helper is started for that step only on a tap, never automatically.
- **Manual switches**: if the player picks another quest inside quest-helper, the guide does not fight it.
  The mirror just stops matching, and `startedByGuide` is cleared so we never stop a helper we didn't start.
- **Not installed**: the quest card shows the requirements from the pack (`requires`, plus skill/quest
  prerequisites derived from `Quest` where the pack lists them), a wiki link, and an "Install Quest Helper"
  button. On Android that button opens the External tab filtered to `quest-helper`. On desktop it opens the
  Plugin Hub panel search. Detection of quest completion is unaffected.
- **Installed but disabled**: the button reads "Enable Quest Helper" and enables it through
  `PluginManager.setPluginEnabled` + `startPlugin` on the EDT. The guide never does this without a tap.

---

## 7. Detection and API usage summary

All game reads go through `runelite-api` (`Client`, `Quest`, `Skill`, `VarbitID`/`VarPlayerID`,
`InventoryID`, `GrandExchangeOffer`, `WorldPoint`), `ItemManager`, `ConfigManager`, `WorldMapPointManager`,
`OverlayManager` and `EventBus`. There are no gamepack hooks, no mixins and no Android-only APIs in the
plugin module. That is what keeps it hub-submittable (§9) and 1:1 on desktop.

---

## 8. Licensing review

### 8.1 quest-helper — BSD 2-Clause

Copying is permitted with the copyright notice kept. We **neither copy nor bundle** it anyway. The guide
calls an installed copy by reflection, so no quest-helper code is in our jar or repo. BSD-2 places no
conditions on calling an installed copy. If a later card wants to vendor a quest-helper type (for example
`Requirement` text helpers), BSD-2 allows it with the notice kept in the file and in `NOTICE`.

### 8.2 OSRS Wiki text — CC BY-NC-SA 3.0

Wiki prose may be redistributed only with attribution, **non-commercially**, and **under the same license**
(ShareAlike). Consequences:

- Wiki prose cannot go into the BSD-2 plugin jar as if it were ours. A bundled pack containing wiki prose
  would itself have to be CC BY-NC-SA, carry attribution, and the app would need to stay non-commercial.
- **Facts are not copyrightable**: quest ids, item ids, level requirements, coordinates and drop sources.
  The bundled starter guide uses those facts with **original prose written for this project**, and links to
  wiki pages (`[[wiki:Page]]` opens a browser/Custom Tab). It does not quote them.
- Downloadable community packs declare their own `license`. A pack that quotes the wiki must say
  `CC-BY-NC-SA-3.0` and give attribution; the loader shows both in About. We host none of those
  ourselves unless the person decides otherwise.

### 8.3 B0aty guide hub plugin

The plugin code (`com.b0atyguide.*`) is published on the Plugin Hub, which requires plugins to be open
source with a license file in the repo. The **guide text** is B0aty's route, written up by the plugin
author, and is a separate copyright question from the code license. Unless the repo's LICENSE explicitly
covers the guide data and grants redistribution, assume the text is **not ours to bundle**.

- **Bundle:** nothing from the B0aty plugin.
- **Link:** the optional runtime adapter (§4.3) reads the guide from the user's own installed copy of the
  hub plugin. The text then stays in the plugin the user installed, with our code reading it in memory as
  quest-helper's data is read. Even so, it is gated on the license check below and on courtesy contact with
  the plugin author.
- **Rewrite:** the *structure* of a progression route (chapters, checklists) is an idea, not expression, and
  is free to use. The starter guide's order of activities is our own, not a paraphrase of B0aty's.

### 8.4 Jagex assets

Item icons, map and names are read at runtime from the client (`ItemManager.getImage`, world map), as every
RuneLite plugin does. None are bundled in packs; packs carry ids only.

### 8.5 **DECISION — licensing conclusion (for the person)**

1. The plugin code is BSD-2 like RuneLite. The bundled starter guide is original text under CC BY 4.0 (or
   BSD-2 if the person prefers one license). It contains **no wiki prose and no B0aty text**, only facts
   and wiki links.
2. quest-helper is **linked at runtime via reflection, never bundled.**
3. B0aty guide text is **never bundled**. The runtime adapter over an installed copy ships only after the
   person checks the B0aty plugin repo's LICENSE (does it cover the guide data?) and, ideally, gets the
   author's OK. Until then the adapter is behind a disabled-by-default config flag, or is not built (M4).
4. Community packs carry their own license field. Packs quoting the wiki must be CC BY-NC-SA with
   attribution. The curated index lists only packs whose license allows redistribution.

Items to verify by the person (no web access in this session):

- the exact LICENSE of the B0aty guide hub plugin repo, and whether it covers `guide.json`/data;
- the current Plugin Hub license requirement, so that BSD-2 for our plugin satisfies it;
- that the wiki's current license is still CC BY-NC-SA 3.0.

---

## 9. Module placement

Options considered:

| option | hub-submittable | desktop parity | Android Compose | upstream diff |
|---|---|---|---|---|
| A. plugin inside `android/` | no | no (Android-only) | trivial | none |
| B. plugin inside `runelite-client/.../plugins/adventureguide` | no (built-in only, the hub does not take core plugins) | yes, but only in our fork | via bridge | **grows our upstream diff** against the "minimal Android seams" rule |
| C. new module `adventure-guide/` (plain Java plugin, `compileOnly runelite-client`), Compose UI in `android/` | **yes**, the same sources build as a hub plugin repo | yes | via `PanelRegistry` + bridge | none |

### **DECISION — module placement (for the person): option C.**

- `adventure-guide/` is a new Gradle module, Java 11, depending `compileOnly` on `runelite-client` and
  `runelite-api` the way a hub plugin's `build.gradle` does. It contains `AdventureGuidePlugin`
  (`@PluginDescriptor(name = "Adventure Guide")`), the model, the pack loader, detection, progress,
  `QuestHelperBridge`, the overlays, `AdventureGuidePanel` (Swing) and the bundled starter pack. It has no
  Android imports, and its layout mirrors a plugin-hub repo, so submitting it is a copy plus a
  `plugin-hub/plugins/adventure-guide` manifest.
- On Android the app depends on `adventure-guide` and loads it as a **built-in** plugin through the same
  path built-ins use today, so it is present without a hub install and not dexed at runtime. If the app
  also finds the hub copy installed, the built-in wins and the hub copy is skipped, deduplicated by plugin
  class name as `PluginBridge.list()` already does by `rowKey`.
- `android/` holds only `ui/panels/AdventureGuideScreen.kt` (Compose), `ui/bridge/AdventureGuideBridge.kt`
  and the `PanelRegistry` hook in the icon strip/sidebar. It talks to the plugin through a tiny listener
  interface in `adventure-guide` (`GuideUiListener`), referenced at compile time because the module is a
  direct dependency. Reflection is only needed for quest-helper and B0aty, which are separately loaded.
- Desktop users get it from the hub (after submission). The module is also runnable from the repo's
  desktop launcher for development.

---

## 10. Milestones and screens

### M1 — Model, starter pack, manual progress, Swing panel (desktop and Android via SwingPanelHost)

- `adventure-guide/` module, schema v1, loader + validator (cycle, dangling id, unknown kind), and a
  ~40-step slice of the starter pack.
- Manual tick/skip, RS-profile progress JSON, percentages, Swing `AdventureGuidePanel`.
- Host test `tools-src/TestAdventureGuide.java`: load bundled pack, resolve each variant, conflict-rule
  table (§3.3), progress round-trip.

```
┌ Adventure Guide ──────────────┐
│ [Starter Route ▾] [HCIM ▾]    │
│ ███████░░░░░░░░  38%          │
│ ▾ 1 Tutorial Island → Lumb.   │
│   ☑ Cook's Assistant      ⓓ   │
│   ☐ Attack 20                 │
│   ☐ 30 Bones (bank 12/30)     │
│ ▸ 2 Draynor & Port Sarim      │
│ [search……………………………]           │
└───────────────────────────────┘
ⓓ = detected
```

### M2 — Detection + quest-helper bridge

- Quest/skill/varbit/varp/inventory/equipment/bank/GE/clog detection with an incremental index, item
  cache, and latching rules.
- `QuestHelperBridge` with the pinned quest-helper commit. Host test (like `TestBoatyGuide`) that loads the
  quest-helper hub jar headless and asserts every reflected member.
- "Start in Quest Helper", mirroring, completion → advance; not-installed/disabled states.

```
┌ Step 4/12 · Quest ────────────┐
│ ⚔ The Restless Ghost          │
│ Quest Helper: "Talk to Father │
│ Aereck in Lumbridge church."  │
│  ✔ Ghostspeak amulet          │
│  ✘ 31 Prayer (or none)        │
│ [■ Stop helper]  [Wiki ↗]     │
└───────────────────────────────┘
not installed →  [Install Quest Helper]
```

### M3 — Android Compose experience

- `PanelRegistry`, `AdventureGuideBridge`, Compose overview/chapter/step pager, rings, haptics, item
  checklist with live icons, map pin + hint arrow, travel tile overlay.
- Checked on device by the person: the worker has no device.

```
Overview                         Step card (swipe ◀ ▶)
┌──────────────────────────┐     ┌──────────────────────────┐
│  Starter Route · HCIM ▾  │     │ ◉ Item · 3 of 7          │
│        ╭──────╮          │     │ 30 × Bones               │
│        │ 38%  │          │     │ ┌──┐ inventory 18        │
│        ╰──────╯          │     │ │🦴│ bank      12  ✔ 30   │
│ ◔ Ch1 Tutorial→Lumb  72% │     │ └──┘                     │
│ ◑ Ch2 Draynor        41% │     │ Sources: Chickens (Lumb) │
│ ○ Ch3 Varrock         0% │     │ [📍 Map]  [☐ Mark done]  │
│ [Continue: Attack 20 ▶]  │     │ ● ● ◉ ○ ○ ○ ○            │
└──────────────────────────┘     └──────────────────────────┘
```

### M4 — Content: full starter guide, downloadable packs, B0aty adapter (gated)

- The full ~150-step starter route with all four variants, pack index + download + sha256, file import,
  About/licenses sheet, guide upgrade/rename migration.
- B0aty runtime adapter behind its config flag, only after the §8.5 license check.

```
┌ Packs ────────────────────────┐
│ ● Starter Route   1.3.0  CC-BY│
│ ○ GIM Fast Start  0.4.1  BSD-2│  [Add pack… (URL / file)]
│ ○ B0aty HCIM (installed hub   │
│   plugin, read in place)  ⚠  │
└───────────────────────────────┘
```

### M5 — Hub submission

- Copy `adventure-guide/` into a plugin-hub repo shape, add the plugin-hub manifest, and verify with the
  desktop client. Run `tools-src/check-swing-linkage.py` on the built jar, so the Swing panel also links on
  android-awt when installed from the hub.
