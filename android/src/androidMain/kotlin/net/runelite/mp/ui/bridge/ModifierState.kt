package net.runelite.mp.ui.bridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import net.runelite.api.Client
import net.runelite.api.KeyCode
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

object ModifierState {
    enum class Key(val vk: Int, val kc: Int, val mask: Int, val label: String) {
        SHIFT(KeyEvent.VK_SHIFT, KeyCode.KC_SHIFT, InputEvent.SHIFT_DOWN_MASK, "Shift"),
        CTRL(KeyEvent.VK_CONTROL, KeyCode.KC_CONTROL, InputEvent.CTRL_DOWN_MASK, "Ctrl"),
        ALT(KeyEvent.VK_ALT, KeyCode.KC_ALT, InputEvent.ALT_DOWN_MASK, "Alt");

        // A second tap can arrive before the game has drained the first event.
        internal var pendingUntil = 0L
    }

    private var held by mutableIntStateOf(0)
    private var owned = 0
    private var tick = 0L
    private var focusGeneration = 0L
    private var windowFocused = true
    private var canvasFocused = true
    private var canvas: Canvas? = null

    fun isHeld(key: Key) = held and key.mask != 0

    @Synchronized fun modifierMask() = held and owned

    @Synchronized fun afterClientTick(client: Client) {
        val target = client.canvas
        if (target !== canvas) {
            clear()
            canvas = target
            canvasFocused = true
        }
        tick++
        if (!windowFocused || !canvasFocused || target == null) return
        var mask = 0
        for (key in Key.entries) {
            if (client.isKeyPressed(key.kc)) mask = mask or key.mask
            else if (tick >= key.pendingUntil) owned = owned and key.mask.inv()
        }
        if (held != mask) held = mask
    }

    @Synchronized fun toggle(key: Key) {
        val target = canvas ?: return
        if (!windowFocused) return
        val generation = focusGeneration
        EventQueue.invokeLater {
            val needsFocus = synchronized(this) {
                if (generation != focusGeneration) return@invokeLater
                !canvasFocused
            }
            if (needsFocus && !target.requestFocusInWindow()) return@invokeLater
            val press: Boolean
            val modifiers: Int
            synchronized(this) {
                if (generation != focusGeneration || !canvasFocused || key.label !in PinnedKeys.settings.value.keys) return@invokeLater
                val pending = tick < key.pendingUntil
                val wasOurs = owned and key.mask != 0
                if (!pending && isHeld(key) && !wasOurs) return@invokeLater // Physical key is down.
                press = if (pending) !wasOurs else !isHeld(key)
                owned = if (press) owned or key.mask else owned and key.mask.inv()
                // Allow one complete input drain after EDT delivery. Only the observed state lights the chip.
                key.pendingUntil = tick + 2
                modifiers = if (press) held or owned else (held or owned) and key.mask.inv()
            }
            KeyDispatch.sendGameModifier(target, key.vk, press, modifiers)
        }
    }

    @Synchronized fun releaseUnpinned(keys: List<String>) {
        for (key in Key.entries) if (key.label !in keys) release(key)
    }

    private fun release(key: Key) {
        key.pendingUntil = 0
        if (owned and key.mask == 0) return
        owned = owned and key.mask.inv()
        val target = canvas ?: return
        val modifiers = owned
        EventQueue.invokeLater { KeyDispatch.sendGameModifier(target, key.vk, false, modifiers) }
    }

    @Synchronized fun onCanvasFocusChanged(focused: Boolean) {
        canvasFocused = focused
        if (!focused) clear()
    }

    @Synchronized fun onWindowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (!focused) clear()
    }

    @Synchronized fun reset() {
        clear()
        canvas = null
    }

    private fun clear() {
        focusGeneration++
        held = 0
        for (key in Key.entries) release(key)
    }
}
