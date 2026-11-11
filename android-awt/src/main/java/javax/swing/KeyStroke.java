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
package javax.swing;

import java.awt.AWTKeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class KeyStroke extends AWTKeyStroke {
    private static final long serialVersionUID = -9060180771037902077L;
    protected KeyStroke() {}
    private static KeyStroke stroke(char character, int code, int modifiers, boolean release) {
        int[] oldMasks = {InputEvent.SHIFT_MASK, InputEvent.CTRL_MASK, InputEvent.META_MASK, InputEvent.ALT_MASK, InputEvent.ALT_GRAPH_MASK};
        int[] newMasks = {InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK, InputEvent.ALT_DOWN_MASK, InputEvent.ALT_GRAPH_DOWN_MASK};
        for (int i = 0; i < oldMasks.length; i++) if ((modifiers & (oldMasks[i] | newMasks[i])) != 0) modifiers |= oldMasks[i] | newMasks[i];
        KeyStroke key = new KeyStroke(); key.init(character, code, modifiers, release); return key;
    }
    public static KeyStroke getKeyStroke(char character) { return stroke(character, KeyEvent.VK_UNDEFINED, 0, false); }
    public static KeyStroke getKeyStroke(char character, boolean release) { return stroke(character, KeyEvent.VK_UNDEFINED, 0, release); }
    public static KeyStroke getKeyStroke(Character character, int modifiers) { if (character == null) throw new IllegalArgumentException("null character"); return stroke(character, 0, modifiers, false); }
    public static KeyStroke getKeyStroke(int code, int modifiers) { return getKeyStroke(code, modifiers, false); }
    public static KeyStroke getKeyStroke(int code, int modifiers, boolean release) { return stroke(KeyEvent.CHAR_UNDEFINED, code, modifiers, release); }
    public static KeyStroke getKeyStroke(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String[] tokens = value.trim().split("\\s+"); int modifiers = 0; boolean release = false, typed = false;
        for (int i = 0; i < tokens.length - 1; i++) {
            switch (tokens[i]) {
                case "shift": modifiers |= InputEvent.SHIFT_DOWN_MASK; break;
                case "control": case "ctrl": modifiers |= InputEvent.CTRL_DOWN_MASK; break;
                case "meta": modifiers |= InputEvent.META_DOWN_MASK; break;
                case "alt": modifiers |= InputEvent.ALT_DOWN_MASK; break;
                case "altGraph": modifiers |= InputEvent.ALT_GRAPH_DOWN_MASK; break;
                case "released": release = true; break;
                case "pressed": break;
                case "typed": typed = true; break;
                default: return null;
            }
        }
        String key = tokens[tokens.length - 1];
        if (typed) return key.length() == 1 ? stroke(key.charAt(0), 0, modifiers, false) : null;
        try { return getKeyStroke(KeyEvent.class.getField("VK_" + key).getInt(null), modifiers, release); }
        catch (ReflectiveOperationException e) { return null; }
    }
    public static KeyStroke getKeyStrokeForEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_TYPED) return stroke(event.getKeyChar(), 0, event.getModifiersEx() | event.getModifiers(), false);
        return getKeyStroke(event.getKeyCode(), event.getModifiersEx() | event.getModifiers(), event.getID() == KeyEvent.KEY_RELEASED);
    }
}
