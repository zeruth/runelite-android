/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.awt.event;

import java.awt.Component;

public class KeyEvent extends InputEvent {
    private static final long serialVersionUID = -2352130953028126954L;

    public static final int KEY_FIRST = 400;
    public static final int KEY_LAST = 402;
    public static final int KEY_TYPED = 400;
    public static final int KEY_PRESSED = 401;
    public static final int KEY_RELEASED = 402;

    public static final int VK_UNDEFINED = 0;
    public static final int VK_ENTER = 10;
    public static final int VK_BACK_SPACE = 8;
    public static final int VK_TAB = 9;
    public static final int VK_CANCEL = 3;
    public static final int VK_CLEAR = 12;
    public static final int VK_SHIFT = 16;
    public static final int VK_CONTROL = 17;
    public static final int VK_ALT = 18;
    public static final int VK_PAUSE = 19;
    public static final int VK_CAPS_LOCK = 20;
    public static final int VK_ESCAPE = 27;
    public static final int VK_SPACE = 32;
    public static final int VK_PAGE_UP = 33;
    public static final int VK_PAGE_DOWN = 34;
    public static final int VK_END = 35;
    public static final int VK_HOME = 36;
    public static final int VK_LEFT = 37;
    public static final int VK_UP = 38;
    public static final int VK_RIGHT = 39;
    public static final int VK_DOWN = 40;
    public static final int VK_COMMA = 44;
    public static final int VK_MINUS = 45;
    public static final int VK_PERIOD = 46;
    public static final int VK_SLASH = 47;
    public static final int VK_0 = 48;
    public static final int VK_1 = 49;
    public static final int VK_2 = 50;
    public static final int VK_3 = 51;
    public static final int VK_4 = 52;
    public static final int VK_5 = 53;
    public static final int VK_6 = 54;
    public static final int VK_7 = 55;
    public static final int VK_8 = 56;
    public static final int VK_9 = 57;
    public static final int VK_SEMICOLON = 59;
    public static final int VK_EQUALS = 61;
    public static final int VK_A = 65;
    public static final int VK_B = 66;
    public static final int VK_C = 67;
    public static final int VK_D = 68;
    public static final int VK_E = 69;
    public static final int VK_F = 70;
    public static final int VK_G = 71;
    public static final int VK_H = 72;
    public static final int VK_I = 73;
    public static final int VK_J = 74;
    public static final int VK_K = 75;
    public static final int VK_L = 76;
    public static final int VK_M = 77;
    public static final int VK_N = 78;
    public static final int VK_O = 79;
    public static final int VK_P = 80;
    public static final int VK_Q = 81;
    public static final int VK_R = 82;
    public static final int VK_S = 83;
    public static final int VK_T = 84;
    public static final int VK_U = 85;
    public static final int VK_V = 86;
    public static final int VK_W = 87;
    public static final int VK_X = 88;
    public static final int VK_Y = 89;
    public static final int VK_Z = 90;
    public static final int VK_OPEN_BRACKET = 91;
    public static final int VK_BACK_SLASH = 92;
    public static final int VK_CLOSE_BRACKET = 93;
    public static final int VK_NUMPAD0 = 96;
    public static final int VK_NUMPAD1 = 97;
    public static final int VK_NUMPAD2 = 98;
    public static final int VK_NUMPAD3 = 99;
    public static final int VK_NUMPAD4 = 100;
    public static final int VK_NUMPAD5 = 101;
    public static final int VK_NUMPAD6 = 102;
    public static final int VK_NUMPAD7 = 103;
    public static final int VK_NUMPAD8 = 104;
    public static final int VK_NUMPAD9 = 105;
    public static final int VK_MULTIPLY = 106;
    public static final int VK_ADD = 107;
    public static final int VK_SEPARATOR = 108;
    public static final int VK_SUBTRACT = 109;
    public static final int VK_DECIMAL = 110;
    public static final int VK_DIVIDE = 111;
    public static final int VK_DELETE = 127;
    public static final int VK_F1 = 112;
    public static final int VK_F2 = 113;
    public static final int VK_F3 = 114;
    public static final int VK_F4 = 115;
    public static final int VK_F5 = 116;
    public static final int VK_F6 = 117;
    public static final int VK_F7 = 118;
    public static final int VK_F8 = 119;
    public static final int VK_F9 = 120;
    public static final int VK_F10 = 121;
    public static final int VK_F11 = 122;
    public static final int VK_F12 = 123;
    public static final int VK_F13 = 0xF000;
    public static final int VK_F14 = 0xF001;
    public static final int VK_F15 = 0xF002;
    public static final int VK_F16 = 0xF003;
    public static final int VK_F17 = 0xF004;
    public static final int VK_F18 = 0xF005;
    public static final int VK_F19 = 0xF006;
    public static final int VK_F20 = 0xF007;
    public static final int VK_F21 = 0xF008;
    public static final int VK_F22 = 0xF009;
    public static final int VK_F23 = 0xF00A;
    public static final int VK_F24 = 0xF00B;
    public static final int VK_NUM_LOCK = 144;
    public static final int VK_SCROLL_LOCK = 145;
    public static final int VK_ASTERISK = 151;
    public static final int VK_PRINTSCREEN = 154;
    public static final int VK_INSERT = 155;
    public static final int VK_HELP = 156;
    public static final int VK_META = 157;
    public static final int VK_BACK_QUOTE = 192;
    public static final int VK_QUOTE = 222;
    public static final int VK_AT = 512;
    public static final int VK_COLON = 513;
    public static final int VK_LEFT_PARENTHESIS = 519;
    public static final int VK_NUMBER_SIGN = 520;
    public static final int VK_PLUS = 521;
    public static final int VK_RIGHT_PARENTHESIS = 522;
    public static final int VK_WINDOWS = 524;
    public static final int VK_CONTEXT_MENU = 525;
    public static final int VK_ALT_GRAPH = 65406;

    public static int getExtendedKeyCodeForChar(int c) {
        if (c >= '0' && c <= '9') return VK_0 + (c - '0');
        if (c >= 'a' && c <= 'z') return VK_A + (c - 'a');
        if (c >= 'A' && c <= 'Z') return VK_A + (c - 'A');
        switch (c) {
            case '\n': return VK_ENTER;
            case '\b': return VK_BACK_SPACE;
            case '\t': return VK_TAB;
            case ' ': return VK_SPACE;
            case ',': return VK_COMMA;
            case '-': return VK_MINUS;
            case '.': return VK_PERIOD;
            case '/': return VK_SLASH;
            case ';': return VK_SEMICOLON;
            case '=': return VK_EQUALS;
            case '[': return VK_OPEN_BRACKET;
            case '\\': return VK_BACK_SLASH;
            case ']': return VK_CLOSE_BRACKET;
            case '`': return VK_BACK_QUOTE;
            case '\'': return VK_QUOTE;
            default: return VK_UNDEFINED;
        }
    }

    public static final char CHAR_UNDEFINED = 0xFFFF;
    public static final int KEY_LOCATION_UNKNOWN  = 0;
    public static final int KEY_LOCATION_STANDARD = 1;
    public static final int KEY_LOCATION_LEFT     = 2;
    public static final int KEY_LOCATION_RIGHT    = 3;
    public static final int KEY_LOCATION_NUMPAD   = 4;

    private int keyCode;
    private char keyChar;
    private int keyLocation;

    public KeyEvent(Component source, int id, long when, int modifiers, int keyCode, char keyChar) {
        this(source, id, when, modifiers, keyCode, keyChar, KEY_LOCATION_UNKNOWN);
    }

    public KeyEvent(Component source, int id, long when, int modifiers, int keyCode, char keyChar, int keyLocation) {
        super(source, id, when, modifiers);
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.keyLocation = keyLocation;
    }

    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    /**
     * JDK API used by {@link net.runelite.client.config.Keybind#matches}. The desktop
     * JDK returns a Unicode-aware "extended" code that differs from {@link #getKeyCode}
     * for non-ASCII layouts; we don't have a keymap, so for our synthesised events the
     * raw key code is fine — Keybind treats it as identity for matching against the
     * configured hotkey, which is also stored as a single int.
     */
    public int getExtendedKeyCode() { return keyCode; }

    public char getKeyChar() { return keyChar; }
    public void setKeyChar(char keyChar) { this.keyChar = keyChar; }

    public int getKeyLocation() { return keyLocation; }

    public boolean isActionKey() {
        switch (keyCode) {
            case VK_HOME:
            case VK_END:
            case VK_PAGE_UP:
            case VK_PAGE_DOWN:
            case VK_UP:
            case VK_DOWN:
            case VK_LEFT:
            case VK_RIGHT:
            case VK_F1:
            case VK_F2:
            case VK_F3:
            case VK_F4:
            case VK_F5:
            case VK_F6:
            case VK_F7:
            case VK_F8:
            case VK_F9:
            case VK_F10:
            case VK_F11:
            case VK_F12:
            case VK_PAUSE:
            case VK_CAPS_LOCK:
            case VK_NUM_LOCK:
            case VK_SCROLL_LOCK:
            case VK_PRINTSCREEN:
            case VK_INSERT:
            case VK_HELP:
            case VK_WINDOWS:
            case VK_CONTEXT_MENU:
                return true;
            default:
                return keyCode >= VK_F13 && keyCode <= VK_F24;
        }
    }

    /**
     * The JDK's default (English awt.properties) key names, so hotkeys shown in RuneLite's
     * config (Keybind.toString) read exactly as they do on desktop.
     */
    public static String getKeyText(int keyCode) {
        if ((keyCode >= VK_0 && keyCode <= VK_9) || (keyCode >= VK_A && keyCode <= VK_Z)) {
            return String.valueOf((char) keyCode);
        }
        if (keyCode >= VK_NUMPAD0 && keyCode <= VK_NUMPAD9) {
            return "NumPad-" + (keyCode - VK_NUMPAD0);
        }
        if (keyCode >= VK_F1 && keyCode <= VK_F12) {
            return "F" + (keyCode - VK_F1 + 1);
        }
        if (keyCode >= VK_F13 && keyCode <= VK_F24) {
            return "F" + (keyCode - VK_F13 + 13);
        }
        switch (keyCode) {
            case VK_ENTER: return "Enter";
            case VK_BACK_SPACE: return "Backspace";
            case VK_TAB: return "Tab";
            case VK_CANCEL: return "Cancel";
            case VK_CLEAR: return "Clear";
            case VK_SHIFT: return "Shift";
            case VK_CONTROL: return "Ctrl";
            case VK_ALT: return "Alt";
            case VK_PAUSE: return "Pause";
            case VK_CAPS_LOCK: return "Caps Lock";
            case VK_ESCAPE: return "Escape";
            case VK_SPACE: return "Space";
            case VK_PAGE_UP: return "Page Up";
            case VK_PAGE_DOWN: return "Page Down";
            case VK_END: return "End";
            case VK_HOME: return "Home";
            case VK_LEFT: return "Left";
            case VK_UP: return "Up";
            case VK_RIGHT: return "Right";
            case VK_DOWN: return "Down";
            case VK_COMMA: return "Comma";
            case VK_MINUS: return "Minus";
            case VK_PERIOD: return "Period";
            case VK_SLASH: return "Slash";
            case VK_SEMICOLON: return "Semicolon";
            case VK_EQUALS: return "Equals";
            case VK_OPEN_BRACKET: return "Open Bracket";
            case VK_BACK_SLASH: return "Back Slash";
            case VK_CLOSE_BRACKET: return "Close Bracket";
            case VK_MULTIPLY: return "NumPad *";
            case VK_ADD: return "NumPad +";
            case VK_SEPARATOR: return "NumPad ,";
            case VK_SUBTRACT: return "NumPad -";
            case VK_DECIMAL: return "NumPad .";
            case VK_DIVIDE: return "NumPad /";
            case VK_DELETE: return "Delete";
            case VK_NUM_LOCK: return "Num Lock";
            case VK_SCROLL_LOCK: return "Scroll Lock";
            case VK_ASTERISK: return "Asterisk";
            case VK_PRINTSCREEN: return "Print Screen";
            case VK_INSERT: return "Insert";
            case VK_HELP: return "Help";
            case VK_META: return "Meta";
            case VK_BACK_QUOTE: return "Back Quote";
            case VK_QUOTE: return "Quote";
            case VK_AT: return "At";
            case VK_COLON: return "Colon";
            case VK_LEFT_PARENTHESIS: return "Left Parenthesis";
            case VK_NUMBER_SIGN: return "Number Sign";
            case VK_PLUS: return "Plus";
            case VK_RIGHT_PARENTHESIS: return "Right Parenthesis";
            case VK_WINDOWS: return "Windows";
            case VK_CONTEXT_MENU: return "Context Menu";
            case VK_ALT_GRAPH: return "Alt Graph";
            default:
                return "Unknown keyCode: 0x" + Integer.toHexString(keyCode);
        }
    }
}
