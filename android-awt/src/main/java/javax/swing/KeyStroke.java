/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing;

import java.awt.AWTKeyStroke;
import java.awt.event.KeyEvent;

/**
 * A KeyStroke represents a key action on the keyboard, or equivalent input
 * device. KeyStrokes are immutable, and are intended to be unique: client code
 * cannot create a KeyStroke, but must instead use a variant of
 * <code>getKeyStroke</code>, which lets the implementation cache and share
 * instances.
 *
 * @see java.awt.AWTKeyStroke
 * @author Arnaud Weber
 * @author David Mendenhall
 */
public class KeyStroke extends AWTKeyStroke {
    private static final long serialVersionUID = -9060180771037902077L;

    private KeyStroke() {
    }

    private KeyStroke(char keyChar, int keyCode, int modifiers,
                      boolean onKeyRelease) {
        super(keyChar, keyCode, modifiers, onKeyRelease);
    }

    /**
     * Returns a shared instance of a <code>KeyStroke</code>
     * that represents a <code>KEY_TYPED</code> event for the
     * specified character.
     *
     * @param keyChar the character value for a keyboard key
     * @return a KeyStroke object for that key
     */
    public static KeyStroke getKeyStroke(char keyChar) {
        synchronized (AWTKeyStroke.class) {
            registerSubclass(KeyStroke.class);
            return (KeyStroke)getAWTKeyStroke(keyChar);
        }
    }

    /**
     * Returns an instance of a KeyStroke, specifying whether the key is
     * considered to be activated when it is pressed or released.
     *
     * @param keyChar the character value for a keyboard key
     * @param onKeyRelease <code>true</code> if this KeyStroke corresponds to a
     *        key release; <code>false</code> otherwise.
     * @return a KeyStroke object for that key
     * @deprecated use getKeyStroke(char)
     */
    @Deprecated
    public static KeyStroke getKeyStroke(char keyChar, boolean onKeyRelease) {
        return new KeyStroke(keyChar, KeyEvent.VK_UNDEFINED, 0, onKeyRelease);
    }

    /**
     * Returns a shared instance of a {@code KeyStroke}
     * that represents a {@code KEY_TYPED} event for the
     * specified Character object and a set of modifiers.
     *
     * @param keyChar the Character object for a keyboard character
     * @param modifiers a bitwise-ored combination of any modifiers
     * @return an KeyStroke object for that key
     * @throws IllegalArgumentException if keyChar is null
     */
    public static KeyStroke getKeyStroke(Character keyChar, int modifiers) {
        synchronized (AWTKeyStroke.class) {
            registerSubclass(KeyStroke.class);
            return (KeyStroke)getAWTKeyStroke(keyChar, modifiers);
        }
    }

    /**
     * Returns a shared instance of a KeyStroke, given a numeric key code and a
     * set of modifiers, specifying whether the key is activated when it is
     * pressed or released.
     *
     * @param keyCode an int specifying the numeric code for a keyboard key
     * @param modifiers a bitwise-ored combination of any modifiers
     * @param onKeyRelease <code>true</code> if the KeyStroke should represent
     *        a key release; <code>false</code> otherwise.
     * @return a KeyStroke object for that key
     */
    public static KeyStroke getKeyStroke(int keyCode, int modifiers,
                                         boolean onKeyRelease) {
        synchronized (AWTKeyStroke.class) {
            registerSubclass(KeyStroke.class);
            return (KeyStroke)getAWTKeyStroke(keyCode, modifiers,
                                              onKeyRelease);
        }
    }

    /**
     * Returns a shared instance of a KeyStroke, given a numeric key code and a
     * set of modifiers. The returned KeyStroke will correspond to a key press.
     *
     * @param keyCode an int specifying the numeric code for a keyboard key
     * @param modifiers a bitwise-ored combination of any modifiers
     * @return a KeyStroke object for that key
     */
    public static KeyStroke getKeyStroke(int keyCode, int modifiers) {
        synchronized (AWTKeyStroke.class) {
            registerSubclass(KeyStroke.class);
            return (KeyStroke)getAWTKeyStroke(keyCode, modifiers);
        }
    }

    /**
     * Returns a KeyStroke which represents the stroke which generated a given
     * KeyEvent.
     *
     * @param anEvent the KeyEvent from which to obtain the KeyStroke
     * @throws NullPointerException if <code>anEvent</code> is null
     * @return the KeyStroke that precipitated the event
     */
    public static KeyStroke getKeyStrokeForEvent(KeyEvent anEvent) {
        synchronized (AWTKeyStroke.class) {
            registerSubclass(KeyStroke.class);
            return (KeyStroke)getAWTKeyStrokeForEvent(anEvent);
        }
    }

    /**
     * Parses a string and returns a <code>KeyStroke</code>, in the syntax of
     * {@link AWTKeyStroke#getAWTKeyStroke(String)}.
     *
     * @param s a String formatted as described above
     * @return a KeyStroke object for that String, or <code>null</code> if the
     *         specified String is <code>null</code>, or is formatted incorrectly
     */
    public static KeyStroke getKeyStroke(String s) {
        if (s == null || s.length() == 0) {
            return null;
        }
        synchronized (AWTKeyStroke.class) {
            registerSubclass(KeyStroke.class);
            try {
                return (KeyStroke)getAWTKeyStroke(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
