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
package java.awt;

import java.io.Serializable;

public class AWTKeyStroke implements Serializable {
    private static final long serialVersionUID = -6430539691155161871L;

    private char keyChar = (char) 0xFFFF;
    private int keyCode;
    private int modifiers;
    private boolean onKeyRelease;

    protected AWTKeyStroke() {}

    protected void init(char keyChar, int keyCode, int modifiers, boolean onKeyRelease) {
        this.keyChar = keyChar;
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        this.onKeyRelease = onKeyRelease;
    }

    public char getKeyChar() { return keyChar; }
    public int getKeyCode() { return keyCode; }
    public int getModifiers() { return modifiers; }
    public boolean isOnKeyRelease() { return onKeyRelease; }
    public int getKeyEventType() { return keyChar != java.awt.event.KeyEvent.CHAR_UNDEFINED ? java.awt.event.KeyEvent.KEY_TYPED : onKeyRelease ? java.awt.event.KeyEvent.KEY_RELEASED : java.awt.event.KeyEvent.KEY_PRESSED; }

    @Override public int hashCode() { return (keyChar + keyCode + modifiers) * 31 + (onKeyRelease ? 1 : 0); }
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof AWTKeyStroke)) return false;
        AWTKeyStroke o = (AWTKeyStroke) obj;
        return keyChar == o.keyChar && keyCode == o.keyCode && modifiers == o.modifiers && onKeyRelease == o.onKeyRelease;
    }
}
