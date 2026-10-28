package net.runelite.mp.ui.bridge

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * Sticky keyboard-modifier state for mobile touch input. Compose chips call
 * [toggleShift] / [toggleAlt] to engage; the chip stays armed until tapped again.
 *
 * Input route is identical in shape to the mouse path that already works
 * ([net.runelite.mp.AppAndroidKt]'s `fireMouse` + `walkUpToListener`): find the
 * Component whose registered listener wants the event, then call its `dispatchKeyEvent`
 * (the shim's analog to AWT's "deliver to focused component"). The injected client's
 * own KeyListener picks the event up, updates whatever internal state it maintains,
 * and forwards through `Callbacks.keyPressed` to RuneLite's KeyManager — same flow as
 * a real hardware key on desktop. Zero touching of injected/obfuscated internals.
 *
 * Because there's no hardware key to release, we re-fire `KEY_PRESSED` on a heartbeat
 * for any chip currently armed. That keeps the engine's per-tick input drain seeing
 * the key as still-held even after a focus-lost clear or any other periodic reset.
 */
object ModifierState
{
    /** AWT VK codes — explicit because our android-awt shim doesn't ship `VK_*`. */
    private const val VK_CONTROL = 17
    private const val VK_SHIFT = 16
    private const val VK_ALT = 18

    val controlActive = mutableStateOf(false)
    val shiftActive = mutableStateOf(false)
    val altActive = mutableStateOf(false)

    fun modifierMask(): Int
    {
        var m = 0
        if (controlActive.value) m = m or InputEvent.CTRL_DOWN_MASK
        if (shiftActive.value) m = m or InputEvent.SHIFT_DOWN_MASK
        if (altActive.value) m = m or InputEvent.ALT_DOWN_MASK
        return m
    }

    fun toggleControl()
    {
        val next = !controlActive.value
        controlActive.value = next
        dispatch(VK_CONTROL, next)
        ensureHeartbeat()
    }

    fun toggleShift()
    {
        val next = !shiftActive.value
        shiftActive.value = next
        dispatch(VK_SHIFT, next)
        ensureHeartbeat()
    }

    fun toggleAlt()
    {
        val next = !altActive.value
        altActive.value = next
        dispatch(VK_ALT, next)
        ensureHeartbeat()
    }

    /**
     * Fire the synthetic KeyEvent at every Component in the active window's tree
     * that has registered KeyListeners — same "find any listener" pattern the mouse
     * dispatch uses. Engine listener picks it up; any other listener (Hooks, plugins)
     * gets it through the engine's `Callbacks.keyPressed` forward, so we skip
     * `Callbacks` instances here to avoid a double-fire of plugin KeyListeners.
     */
    private fun dispatch(vk: Int, pressed: Boolean)
    {
        val id = if (pressed) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED
        val mods = modifierMask()
        val callbacksClass = try { Class.forName("net.runelite.api.hooks.Callbacks") }
            catch (t: Throwable) { null }
        val fired = mutableListOf<String>()
        // Walk every Window the shim tracks, not just primaryFrame(). The OSRS
        // engine's KeyListener can live on a Component whose top-level Window isn't
        // the "biggest visible game frame" the primary-frame heuristic picks.
        val roots: List<java.awt.Component> = run {
            val all = java.awt.Window.getAllWindows().toMutableList<java.awt.Component>()
            java.awt.Canvas.latest()?.let { all += it }
            if (all.isEmpty()) java.awt.Window.primaryFrame()?.let { all += it }
            all
        }
        // Also dump the KeyListener inventory on the first toggle of each session
        // so we can SEE what's actually attached and where.
        if (!isHeartbeat.get())
        {
            for (root in roots)
            {
                val inv = mutableListOf<String>()
                inventoryKeyListeners(root, "", inv)
                if (inv.isNotEmpty())
                {
                    android.util.Log.i("ModifierState", "Window ${root.javaClass.name} key inventory:")
                    for (line in inv) android.util.Log.i("ModifierState", "  $line")
                }
            }
        }
        for (root in roots) deliver(root, id, mods, vk, callbacksClass, pressed, fired)
        // Diagnostic — only on toggle (heartbeat doesn't log to avoid spam). Lets us
        // see exactly which KeyListeners receive the event vs how the engine state
        // reacts, which is the diff that'll tell us whether the route or the engine
        // is the problem.
        if (fired.isEmpty())
        {
            val rootNames = roots.joinToString { it.javaClass.simpleName }
            android.util.Log.w("ModifierState", "vk=$vk pressed=$pressed delivered to ZERO listeners (walked ${roots.size} window(s): $rootNames)")
        }
        else if (!isHeartbeat.get())
        {
            android.util.Log.i("ModifierState", "vk=$vk pressed=$pressed → ${fired.size} listener(s): $fired")
            // Did the engine actually see the state change?
            try
            {
                val client = RuneLiteAccess.instance(net.runelite.api.Client::class.java)
                if (client != null)
                {
                    val kc = if (vk == VK_SHIFT) net.runelite.api.KeyCode.KC_SHIFT
                        else if (vk == VK_CONTROL) net.runelite.api.KeyCode.KC_CONTROL
                        else net.runelite.api.KeyCode.KC_ALT
                    android.util.Log.i("ModifierState", "  client.isKeyPressed($kc) = ${client.isKeyPressed(kc)}")
                }
            }
            catch (t: Throwable) { /* swallow */ }
        }
    }

    private val isHeartbeat = ThreadLocal.withInitial { false }

    /** Depth-first listing of every Component in [c]'s tree that has ≥1 KeyListener.
     *  Used on the first toggle of each session to dump the inventory to logcat so we
     *  can see whether the OSRS engine's listener is reachable at all. */
    private fun inventoryKeyListeners(c: java.awt.Component, indent: String, out: MutableList<String>)
    {
        if (c.keyListeners.isNotEmpty())
        {
            val names = c.keyListeners.joinToString { it.javaClass.name }
            out += "$indent${c.javaClass.name}: $names"
        }
        if (c is java.awt.Container)
        {
            for (child in c.components) if (child != null)
                inventoryKeyListeners(child, "$indent  ", out)
        }
    }

    private fun deliver(
        c: java.awt.Component,
        id: Int,
        mods: Int,
        vk: Int,
        callbacksClass: Class<*>?,
        pressed: Boolean,
        fired: MutableList<String>,
    )
    {
        for (listener in c.keyListeners)
        {
            if (callbacksClass != null && callbacksClass.isInstance(listener)) continue
            try
            {
                val ev = KeyEvent(c, id, System.currentTimeMillis(), mods, vk, KeyEvent.CHAR_UNDEFINED)
                if (pressed) listener.keyPressed(ev) else listener.keyReleased(ev)
                fired += "${listener.javaClass.simpleName}@${c.javaClass.simpleName}"
            }
            catch (t: Throwable)
            {
                // No longer silently swallow — the engine's KeyListener was being
                // hit and throwing here, leaving the dispatch invisible in `fired`.
                android.util.Log.w("ModifierState", "listener ${listener.javaClass.name} on ${c.javaClass.name} threw on key dispatch", t)
            }
        }
        if (c is java.awt.Container)
        {
            for (child in c.components) if (child != null)
                deliver(child, id, mods, vk, callbacksClass, pressed, fired)
        }
    }

    // ------------------------------------------------------------------
    // Heartbeat: re-fire KEY_PRESSED on each tick for armed modifiers.
    // The engine's input pipeline expects continuous "held" signal —
    // without re-firing, a focusLost clear or a per-tick state reset
    // would silently drop our held state with no way to recover until
    // the user re-taps. 100ms is well under the 600ms game tick.
    // ------------------------------------------------------------------

    @Volatile private var heartbeatStarted = false
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private fun ensureHeartbeat()
    {
        if (heartbeatStarted) return
        heartbeatStarted = true
        val tick = object : Runnable
        {
            override fun run()
            {
                isHeartbeat.set(true)
                try
                {
                    if (controlActive.value) dispatch(VK_CONTROL, true)
                    if (shiftActive.value) dispatch(VK_SHIFT, true)
                    if (altActive.value) dispatch(VK_ALT, true)
                }
                finally { isHeartbeat.set(false) }
                handler.postDelayed(this, 100)
            }
        }
        handler.post(tick)
    }
}
