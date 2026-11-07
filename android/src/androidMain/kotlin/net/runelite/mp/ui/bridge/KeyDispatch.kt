package net.runelite.mp.ui.bridge

import android.view.KeyEvent as AndroidKeyEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/**
 * Dispatches key events from hardware keyboards, soft keyboards (IME), and quick-action
 * UI buttons into the AWT component hierarchy and RuneLite's input pipeline.
 */
object KeyDispatch {
    private const val TAG = "KeyDispatch"

    private val callbacksClass: Class<*>? by lazy {
        try {
            Class.forName("net.runelite.api.hooks.Callbacks")
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Send a single character typed by the user (from soft keyboard, IME commit, or hardware key).
     */
    fun sendChar(c: Char, extraModifiers: Int = 0) {
        val vk = KeyEvent.getExtendedKeyCodeForChar(c.code)
        val mods = ModifierState.modifierMask() or extraModifiers or
                (if (Character.isUpperCase(c)) InputEvent.SHIFT_DOWN_MASK else 0)

        // 1. KEY_PRESSED
        dispatchAwtEvent(KeyEvent(
            dummyComponent(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            mods,
            if (vk != KeyEvent.VK_UNDEFINED) vk else c.code,
            c
        ))

        // 2. KEY_TYPED
        dispatchAwtEvent(KeyEvent(
            dummyComponent(),
            KeyEvent.KEY_TYPED,
            System.currentTimeMillis(),
            mods,
            KeyEvent.VK_UNDEFINED,
            c
        ))

        // 3. KEY_RELEASED
        dispatchAwtEvent(KeyEvent(
            dummyComponent(),
            KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            mods,
            if (vk != KeyEvent.VK_UNDEFINED) vk else c.code,
            c
        ))
    }

    /**
     * Send a full string of characters sequentially.
     */
    fun sendString(text: String) {
        for (c in text) {
            sendChar(c)
        }
    }

    /**
     * Send a specific AWT key by VK code (with optional keyChar).
     */
    fun sendKey(vkCode: Int, keyChar: Char = KeyEvent.CHAR_UNDEFINED, modifiers: Int = 0) {
        val mods = ModifierState.modifierMask() or modifiers
        dispatchAwtEvent(KeyEvent(
            dummyComponent(),
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            mods,
            vkCode,
            keyChar
        ))

        if (keyChar != KeyEvent.CHAR_UNDEFINED) {
            dispatchAwtEvent(KeyEvent(
                dummyComponent(),
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                mods,
                KeyEvent.VK_UNDEFINED,
                keyChar
            ))
        }

        dispatchAwtEvent(KeyEvent(
            dummyComponent(),
            KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            mods,
            vkCode,
            keyChar
        ))
    }

    fun sendBackspace() {
        sendKey(KeyEvent.VK_BACK_SPACE, '\b')
    }

    fun sendGameModifier(canvas: Canvas, key: Int, pressed: Boolean, modifiers: Int) {
        val id = if (pressed) KeyEvent.KEY_PRESSED else KeyEvent.KEY_RELEASED
        deliverToComponent(canvas, KeyEvent(canvas, id, System.currentTimeMillis(), modifiers,
            key, KeyEvent.CHAR_UNDEFINED, KeyEvent.KEY_LOCATION_LEFT))
    }

    fun sendEnter() {
        sendKey(KeyEvent.VK_ENTER, '\n')
    }

    fun sendEscape() {
        sendKey(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
    }

    fun sendTab() {
        sendKey(KeyEvent.VK_TAB, '\t')
    }

    fun sendSpace() {
        sendKey(KeyEvent.VK_SPACE, ' ')
    }

    /**
     * Keys that belong to the platform, never to the game. The activity funnels *every*
     * key through [sendAndroidKeyEvent]; without this list a press of volume-up would be
     * reported as handled and never reach AudioManager, so the hardware rocker stopped
     * changing the media stream while RuneLite was in the foreground.
     *
     * BACK is in here too: [net.runelite.mp.AppAndroid] installs a Compose `BackHandler`
     * that closes the open sidebar/config panel and otherwise swallows the press, and
     * that handler only runs if we let the event continue to `super.dispatchKeyEvent`.
     */
    private val SYSTEM_KEYS = intArrayOf(
        AndroidKeyEvent.KEYCODE_VOLUME_UP,
        AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
        AndroidKeyEvent.KEYCODE_VOLUME_MUTE,
        AndroidKeyEvent.KEYCODE_MUTE,
        AndroidKeyEvent.KEYCODE_POWER,
        AndroidKeyEvent.KEYCODE_SLEEP,
        AndroidKeyEvent.KEYCODE_WAKEUP,
        AndroidKeyEvent.KEYCODE_SOFT_SLEEP,
        AndroidKeyEvent.KEYCODE_BACK,
        AndroidKeyEvent.KEYCODE_HOME,
        AndroidKeyEvent.KEYCODE_APP_SWITCH,
        AndroidKeyEvent.KEYCODE_MENU,
        AndroidKeyEvent.KEYCODE_SEARCH,
        AndroidKeyEvent.KEYCODE_ASSIST,
        AndroidKeyEvent.KEYCODE_VOICE_ASSIST,
        AndroidKeyEvent.KEYCODE_SETTINGS,
        AndroidKeyEvent.KEYCODE_NOTIFICATION,
        AndroidKeyEvent.KEYCODE_BRIGHTNESS_UP,
        AndroidKeyEvent.KEYCODE_BRIGHTNESS_DOWN,
        AndroidKeyEvent.KEYCODE_CAMERA,
        AndroidKeyEvent.KEYCODE_FOCUS,
        AndroidKeyEvent.KEYCODE_HEADSETHOOK,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_STOP,
        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
    )

    private fun isSystemKey(keyCode: Int): Boolean {
        for (k in SYSTEM_KEYS) {
            if (k == keyCode) return true
        }
        return false
    }

    /**
     * Translates an Android KeyEvent (from hardware keyboard or IME sendKeyEvent)
     * and dispatches it into the AWT pipeline.
     *
     * Returns true only when the event was actually consumed by the game. Anything we
     * have no AWT mapping for — and every key in [SYSTEM_KEYS] — is left alone so the
     * platform's own handling (volume rocker, back gesture, media buttons) still runs.
     */
    fun sendAndroidKeyEvent(event: AndroidKeyEvent): Boolean {
        if (isSystemKey(event.keyCode)) {
            return false
        }
        val numLock = (event.metaState and AndroidKeyEvent.META_NUM_LOCK_ON) != 0
        val vk = androidKeyCodeToAwt(event.keyCode, numLock)
        val mods = androidMetaToAwtModifiers(event.metaState) or ModifierState.modifierMask()
        val keyChar = desktopKeyChar(event, vk, mods)

        // Nothing the game can do with a key we can't name and that types no character
        // (KEYCODE_TV_INPUT, gamepad buttons, vendor hotkeys, …). Decline it so whatever
        // the platform or an OEM overlay wanted to do with it still happens.
        if (vk == KeyEvent.VK_UNDEFINED && keyChar == KeyEvent.CHAR_UNDEFINED && event.action != AndroidKeyEvent.ACTION_MULTIPLE) {
            return false
        }

        val source = dummyComponent()
        val whenMs = event.eventTime
        val location = keyLocation(event.keyCode)

        when (event.action) {
            // Auto-repeat arrives as further ACTION_DOWNs, which is also what desktop
            // delivers (repeated PRESSED + TYPED, one RELEASED at the end).
            AndroidKeyEvent.ACTION_DOWN -> {
                dispatchAwtEvent(KeyEvent(source, KeyEvent.KEY_PRESSED, whenMs, mods, vk, keyChar, location))
                // Desktop emits KEY_TYPED for every key that yields a character, control
                // characters included (Enter, Backspace, Tab, Escape, Delete, Ctrl+letter).
                if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                    dispatchAwtEvent(KeyEvent(source, KeyEvent.KEY_TYPED, whenMs, mods, KeyEvent.VK_UNDEFINED, keyChar))
                }
                return true
            }
            AndroidKeyEvent.ACTION_UP -> {
                dispatchAwtEvent(KeyEvent(source, KeyEvent.KEY_RELEASED, whenMs, mods, vk, keyChar, location))
                return true
            }
            AndroidKeyEvent.ACTION_MULTIPLE -> {
                if (event.characters != null) {
                    sendString(event.characters)
                    return true
                }
            }
        }
        return false
    }

    private fun dummyComponent(): Component {
        return Canvas.latest() ?: Window.primaryFrame() ?: object : Component() {}
    }

    /**
     * Dispatch to global hotkeys, then one input target. Never broadcast to plugin fields.
     */
    fun dispatchAwtEvent(ev: KeyEvent) {
        if (!java.awt.EventQueue.isDispatchThread()) {
            java.awt.EventQueue.invokeLater { dispatchAwtEvent(ev) }
            return
        }
        // 1. KeyboardFocusManager dispatchers (e.g. ClientUI hotkeys)
        try {
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(ev) || ev.isConsumed) return
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "KeyboardFocusManager dispatch failed", t)
        }

        val focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        // A top-level window can own activation focus without being a text/key target.
        val target = focused?.takeIf { it !is Window && acceptsKeys(it) }
            ?: ev.component.takeIf { acceptsKeys(it) }
            ?: return
        val delivered = if (ev.component === target) ev else KeyEvent(target, ev.id, ev.`when`,
            ev.modifiersEx, ev.keyCode, ev.keyChar, ev.keyLocation)
        deliverToComponent(target, delivered)
        if (!delivered.isConsumed && target is javax.swing.JComponent && target.hostProcessKeyBinding(delivered))
            delivered.consume()
        if (delivered.isConsumed) ev.consume()
    }

    private fun acceptsKeys(component: Component): Boolean {
        var node: Component? = component
        while (node != null) {
            if (!node.isVisible || !node.isEnabled) return false
            node = node.parent
        }
        return true
    }

    private fun deliverToComponent(c: Component, ev: KeyEvent) {
        for (listener in c.keyListeners) {
            if (ev.isConsumed) break
            if (callbacksClass != null && callbacksClass!!.isInstance(listener)) {
                // Skip direct Callbacks listener since OSRS client's KeyListener calls Callbacks
                continue
            }
            try {
                when (ev.id) {
                    KeyEvent.KEY_PRESSED -> listener.keyPressed(ev)
                    KeyEvent.KEY_RELEASED -> listener.keyReleased(ev)
                    KeyEvent.KEY_TYPED -> listener.keyTyped(ev)
                }
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "listener threw on key dispatch", t)
            }
        }
    }

    private fun androidMetaToAwtModifiers(meta: Int): Int {
        var mods = 0
        if ((meta and AndroidKeyEvent.META_SHIFT_ON) != 0) mods = mods or InputEvent.SHIFT_DOWN_MASK
        if ((meta and AndroidKeyEvent.META_ALT_ON) != 0) mods = mods or InputEvent.ALT_DOWN_MASK
        if ((meta and AndroidKeyEvent.META_CTRL_ON) != 0) mods = mods or InputEvent.CTRL_DOWN_MASK
        if ((meta and AndroidKeyEvent.META_META_ON) != 0) mods = mods or InputEvent.META_DOWN_MASK
        return mods
    }

    /**
     * The keyChar desktop AWT reports for this key: the typed character, the ASCII control
     * character for Enter/Backspace/Tab/Escape/Delete and Ctrl+letter (Ctrl+A = 0x01), and
     * [KeyEvent.CHAR_UNDEFINED] for keys that type nothing (modifiers, arrows, F-keys).
     */
    private fun desktopKeyChar(event: AndroidKeyEvent, vk: Int, mods: Int): Char {
        when (vk) {
            KeyEvent.VK_ENTER -> return '\n'
            KeyEvent.VK_BACK_SPACE -> return '\b'
            KeyEvent.VK_TAB -> return '\t'
            KeyEvent.VK_ESCAPE -> return '\u001b'
            KeyEvent.VK_DELETE -> return '\u007f'
        }
        // Android's keymap resolves Ctrl/Meta chords to no character at all, so look the key
        // up without them and apply desktop's Ctrl rule ourselves.
        val stripped = event.metaState and
            (AndroidKeyEvent.META_CTRL_MASK or AndroidKeyEvent.META_META_MASK).inv()
        val u = event.getUnicodeChar(stripped)
        // Dead keys (accent prefixes) type nothing until the following key.
        if (u == 0 || (u and android.view.KeyCharacterMap.COMBINING_ACCENT) != 0) {
            return KeyEvent.CHAR_UNDEFINED
        }
        val c = u.toChar()
        if ((mods and InputEvent.CTRL_DOWN_MASK) != 0) {
            val lower = c.lowercaseChar()
            return if (lower in 'a'..'z') (lower - 'a' + 1).toChar() else KeyEvent.CHAR_UNDEFINED
        }
        return c
    }

    private fun keyLocation(keyCode: Int): Int = when (keyCode) {
        AndroidKeyEvent.KEYCODE_SHIFT_LEFT, AndroidKeyEvent.KEYCODE_CTRL_LEFT,
        AndroidKeyEvent.KEYCODE_ALT_LEFT, AndroidKeyEvent.KEYCODE_META_LEFT -> KeyEvent.KEY_LOCATION_LEFT
        AndroidKeyEvent.KEYCODE_SHIFT_RIGHT, AndroidKeyEvent.KEYCODE_CTRL_RIGHT,
        AndroidKeyEvent.KEYCODE_ALT_RIGHT, AndroidKeyEvent.KEYCODE_META_RIGHT -> KeyEvent.KEY_LOCATION_RIGHT
        // NUM_LOCK through NUMPAD_RIGHT_PAREN are one contiguous block of keycodes.
        in AndroidKeyEvent.KEYCODE_NUM_LOCK..AndroidKeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> KeyEvent.KEY_LOCATION_NUMPAD
        else -> KeyEvent.KEY_LOCATION_STANDARD
    }

    /** With Num Lock off the numpad is a second navigation cluster, as on desktop. */
    private fun numpadNavigation(keyCode: Int): Int = when (keyCode) {
        AndroidKeyEvent.KEYCODE_NUMPAD_0 -> KeyEvent.VK_INSERT
        AndroidKeyEvent.KEYCODE_NUMPAD_1 -> KeyEvent.VK_END
        AndroidKeyEvent.KEYCODE_NUMPAD_2 -> KeyEvent.VK_DOWN
        AndroidKeyEvent.KEYCODE_NUMPAD_3 -> KeyEvent.VK_PAGE_DOWN
        AndroidKeyEvent.KEYCODE_NUMPAD_4 -> KeyEvent.VK_LEFT
        AndroidKeyEvent.KEYCODE_NUMPAD_5 -> KeyEvent.VK_CLEAR
        AndroidKeyEvent.KEYCODE_NUMPAD_6 -> KeyEvent.VK_RIGHT
        AndroidKeyEvent.KEYCODE_NUMPAD_7 -> KeyEvent.VK_HOME
        AndroidKeyEvent.KEYCODE_NUMPAD_8 -> KeyEvent.VK_UP
        AndroidKeyEvent.KEYCODE_NUMPAD_9 -> KeyEvent.VK_PAGE_UP
        AndroidKeyEvent.KEYCODE_NUMPAD_DOT -> KeyEvent.VK_DELETE
        else -> KeyEvent.VK_UNDEFINED
    }

    private fun androidKeyCodeToAwt(keyCode: Int, numLock: Boolean): Int {
        if (!numLock) {
            val nav = numpadNavigation(keyCode)
            if (nav != KeyEvent.VK_UNDEFINED) return nav
        }
        return when (keyCode) {
            AndroidKeyEvent.KEYCODE_A -> KeyEvent.VK_A
            AndroidKeyEvent.KEYCODE_B -> KeyEvent.VK_B
            AndroidKeyEvent.KEYCODE_C -> KeyEvent.VK_C
            AndroidKeyEvent.KEYCODE_D -> KeyEvent.VK_D
            AndroidKeyEvent.KEYCODE_E -> KeyEvent.VK_E
            AndroidKeyEvent.KEYCODE_F -> KeyEvent.VK_F
            AndroidKeyEvent.KEYCODE_G -> KeyEvent.VK_G
            AndroidKeyEvent.KEYCODE_H -> KeyEvent.VK_H
            AndroidKeyEvent.KEYCODE_I -> KeyEvent.VK_I
            AndroidKeyEvent.KEYCODE_J -> KeyEvent.VK_J
            AndroidKeyEvent.KEYCODE_K -> KeyEvent.VK_K
            AndroidKeyEvent.KEYCODE_L -> KeyEvent.VK_L
            AndroidKeyEvent.KEYCODE_M -> KeyEvent.VK_M
            AndroidKeyEvent.KEYCODE_N -> KeyEvent.VK_N
            AndroidKeyEvent.KEYCODE_O -> KeyEvent.VK_O
            AndroidKeyEvent.KEYCODE_P -> KeyEvent.VK_P
            AndroidKeyEvent.KEYCODE_Q -> KeyEvent.VK_Q
            AndroidKeyEvent.KEYCODE_R -> KeyEvent.VK_R
            AndroidKeyEvent.KEYCODE_S -> KeyEvent.VK_S
            AndroidKeyEvent.KEYCODE_T -> KeyEvent.VK_T
            AndroidKeyEvent.KEYCODE_U -> KeyEvent.VK_U
            AndroidKeyEvent.KEYCODE_V -> KeyEvent.VK_V
            AndroidKeyEvent.KEYCODE_W -> KeyEvent.VK_W
            AndroidKeyEvent.KEYCODE_X -> KeyEvent.VK_X
            AndroidKeyEvent.KEYCODE_Y -> KeyEvent.VK_Y
            AndroidKeyEvent.KEYCODE_Z -> KeyEvent.VK_Z

            AndroidKeyEvent.KEYCODE_0 -> KeyEvent.VK_0
            AndroidKeyEvent.KEYCODE_1 -> KeyEvent.VK_1
            AndroidKeyEvent.KEYCODE_2 -> KeyEvent.VK_2
            AndroidKeyEvent.KEYCODE_3 -> KeyEvent.VK_3
            AndroidKeyEvent.KEYCODE_4 -> KeyEvent.VK_4
            AndroidKeyEvent.KEYCODE_5 -> KeyEvent.VK_5
            AndroidKeyEvent.KEYCODE_6 -> KeyEvent.VK_6
            AndroidKeyEvent.KEYCODE_7 -> KeyEvent.VK_7
            AndroidKeyEvent.KEYCODE_8 -> KeyEvent.VK_8
            AndroidKeyEvent.KEYCODE_9 -> KeyEvent.VK_9

            AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> KeyEvent.VK_ENTER
            AndroidKeyEvent.KEYCODE_DEL -> KeyEvent.VK_BACK_SPACE
            AndroidKeyEvent.KEYCODE_FORWARD_DEL -> KeyEvent.VK_DELETE
            AndroidKeyEvent.KEYCODE_TAB -> KeyEvent.VK_TAB
            AndroidKeyEvent.KEYCODE_SPACE -> KeyEvent.VK_SPACE
            AndroidKeyEvent.KEYCODE_ESCAPE -> KeyEvent.VK_ESCAPE

            AndroidKeyEvent.KEYCODE_DPAD_UP -> KeyEvent.VK_UP
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.VK_DOWN
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.VK_LEFT
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.VK_RIGHT

            AndroidKeyEvent.KEYCODE_PAGE_UP -> KeyEvent.VK_PAGE_UP
            AndroidKeyEvent.KEYCODE_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN
            AndroidKeyEvent.KEYCODE_MOVE_HOME -> KeyEvent.VK_HOME
            AndroidKeyEvent.KEYCODE_MOVE_END -> KeyEvent.VK_END

            AndroidKeyEvent.KEYCODE_SHIFT_LEFT, AndroidKeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.VK_SHIFT
            AndroidKeyEvent.KEYCODE_CTRL_LEFT, AndroidKeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.VK_CONTROL
            AndroidKeyEvent.KEYCODE_ALT_LEFT, AndroidKeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.VK_ALT
            AndroidKeyEvent.KEYCODE_META_LEFT, AndroidKeyEvent.KEYCODE_META_RIGHT -> KeyEvent.VK_WINDOWS
            AndroidKeyEvent.KEYCODE_CAPS_LOCK -> KeyEvent.VK_CAPS_LOCK
            AndroidKeyEvent.KEYCODE_NUM_LOCK -> KeyEvent.VK_NUM_LOCK
            AndroidKeyEvent.KEYCODE_SCROLL_LOCK -> KeyEvent.VK_SCROLL_LOCK
            AndroidKeyEvent.KEYCODE_INSERT -> KeyEvent.VK_INSERT
            AndroidKeyEvent.KEYCODE_BREAK -> KeyEvent.VK_PAUSE
            AndroidKeyEvent.KEYCODE_SYSRQ -> KeyEvent.VK_PRINTSCREEN
            AndroidKeyEvent.KEYCODE_CLEAR -> KeyEvent.VK_CLEAR
            AndroidKeyEvent.KEYCODE_HELP -> KeyEvent.VK_HELP

            AndroidKeyEvent.KEYCODE_F1 -> KeyEvent.VK_F1
            AndroidKeyEvent.KEYCODE_F2 -> KeyEvent.VK_F2
            AndroidKeyEvent.KEYCODE_F3 -> KeyEvent.VK_F3
            AndroidKeyEvent.KEYCODE_F4 -> KeyEvent.VK_F4
            AndroidKeyEvent.KEYCODE_F5 -> KeyEvent.VK_F5
            AndroidKeyEvent.KEYCODE_F6 -> KeyEvent.VK_F6
            AndroidKeyEvent.KEYCODE_F7 -> KeyEvent.VK_F7
            AndroidKeyEvent.KEYCODE_F8 -> KeyEvent.VK_F8
            AndroidKeyEvent.KEYCODE_F9 -> KeyEvent.VK_F9
            AndroidKeyEvent.KEYCODE_F10 -> KeyEvent.VK_F10
            AndroidKeyEvent.KEYCODE_F11 -> KeyEvent.VK_F11
            AndroidKeyEvent.KEYCODE_F12 -> KeyEvent.VK_F12

            AndroidKeyEvent.KEYCODE_MINUS -> KeyEvent.VK_MINUS
            AndroidKeyEvent.KEYCODE_EQUALS -> KeyEvent.VK_EQUALS
            AndroidKeyEvent.KEYCODE_LEFT_BRACKET -> KeyEvent.VK_OPEN_BRACKET
            AndroidKeyEvent.KEYCODE_RIGHT_BRACKET -> KeyEvent.VK_CLOSE_BRACKET
            AndroidKeyEvent.KEYCODE_BACKSLASH -> KeyEvent.VK_BACK_SLASH
            AndroidKeyEvent.KEYCODE_SEMICOLON -> KeyEvent.VK_SEMICOLON
            AndroidKeyEvent.KEYCODE_APOSTROPHE -> KeyEvent.VK_QUOTE
            AndroidKeyEvent.KEYCODE_GRAVE -> KeyEvent.VK_BACK_QUOTE
            AndroidKeyEvent.KEYCODE_COMMA -> KeyEvent.VK_COMMA
            AndroidKeyEvent.KEYCODE_PERIOD -> KeyEvent.VK_PERIOD
            AndroidKeyEvent.KEYCODE_SLASH -> KeyEvent.VK_SLASH

            AndroidKeyEvent.KEYCODE_NUMPAD_0 -> KeyEvent.VK_NUMPAD0
            AndroidKeyEvent.KEYCODE_NUMPAD_1 -> KeyEvent.VK_NUMPAD1
            AndroidKeyEvent.KEYCODE_NUMPAD_2 -> KeyEvent.VK_NUMPAD2
            AndroidKeyEvent.KEYCODE_NUMPAD_3 -> KeyEvent.VK_NUMPAD3
            AndroidKeyEvent.KEYCODE_NUMPAD_4 -> KeyEvent.VK_NUMPAD4
            AndroidKeyEvent.KEYCODE_NUMPAD_5 -> KeyEvent.VK_NUMPAD5
            AndroidKeyEvent.KEYCODE_NUMPAD_6 -> KeyEvent.VK_NUMPAD6
            AndroidKeyEvent.KEYCODE_NUMPAD_7 -> KeyEvent.VK_NUMPAD7
            AndroidKeyEvent.KEYCODE_NUMPAD_8 -> KeyEvent.VK_NUMPAD8
            AndroidKeyEvent.KEYCODE_NUMPAD_9 -> KeyEvent.VK_NUMPAD9
            AndroidKeyEvent.KEYCODE_NUMPAD_ADD -> KeyEvent.VK_ADD
            AndroidKeyEvent.KEYCODE_NUMPAD_SUBTRACT -> KeyEvent.VK_SUBTRACT
            AndroidKeyEvent.KEYCODE_NUMPAD_MULTIPLY -> KeyEvent.VK_MULTIPLY
            AndroidKeyEvent.KEYCODE_NUMPAD_DIVIDE -> KeyEvent.VK_DIVIDE
            AndroidKeyEvent.KEYCODE_NUMPAD_DOT -> KeyEvent.VK_DECIMAL
            AndroidKeyEvent.KEYCODE_NUMPAD_COMMA -> KeyEvent.VK_SEPARATOR
            AndroidKeyEvent.KEYCODE_NUMPAD_EQUALS -> KeyEvent.VK_EQUALS
            AndroidKeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> KeyEvent.VK_LEFT_PARENTHESIS
            AndroidKeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> KeyEvent.VK_RIGHT_PARENTHESIS

            AndroidKeyEvent.KEYCODE_AT -> KeyEvent.VK_AT
            AndroidKeyEvent.KEYCODE_POUND -> KeyEvent.VK_NUMBER_SIGN
            AndroidKeyEvent.KEYCODE_PLUS -> KeyEvent.VK_PLUS
            AndroidKeyEvent.KEYCODE_STAR -> KeyEvent.VK_ASTERISK

            else -> KeyEvent.VK_UNDEFINED
        }
    }
}
