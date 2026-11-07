package net.runelite.mp.ui.bridge

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

object ModifierState
{
    val controlActive = mutableStateOf(false)
    val shiftActive = mutableStateOf(false)
    val altActive = mutableStateOf(false)
    private var windowFocused = true

    fun modifierMask(): Int
    {
        var mask = 0
        if (controlActive.value) mask = mask or InputEvent.CTRL_DOWN_MASK
        if (shiftActive.value) mask = mask or InputEvent.SHIFT_DOWN_MASK
        if (altActive.value) mask = mask or InputEvent.ALT_DOWN_MASK
        return mask
    }

    fun toggleControl() = toggle(controlActive, KeyEvent.VK_CONTROL)
    fun toggleShift() = toggle(shiftActive, KeyEvent.VK_SHIFT)
    fun toggleAlt() = toggle(altActive, KeyEvent.VK_ALT)

    private fun toggle(active: MutableState<Boolean>, key: Int)
    {
        active.value = !active.value
        if (windowFocused) KeyDispatch.sendGameModifier(key, active.value, modifierMask())
    }

    // Keep the toolbar selection across a focus change, but hold keys only while the game has focus.
    fun onWindowFocusChanged(focused: Boolean)
    {
        if (focused == windowFocused) return
        windowFocused = focused
        val mask = if (focused) modifierMask() else 0
        if (controlActive.value) KeyDispatch.sendGameModifier(KeyEvent.VK_CONTROL, focused, mask)
        if (shiftActive.value) KeyDispatch.sendGameModifier(KeyEvent.VK_SHIFT, focused, mask)
        if (altActive.value) KeyDispatch.sendGameModifier(KeyEvent.VK_ALT, focused, mask)
    }
}
