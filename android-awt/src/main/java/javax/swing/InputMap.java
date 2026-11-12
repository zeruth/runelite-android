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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Binds KeyStrokes to action-map keys, resolving through a parent chain. The JDK's contract
 * is kept to the letter: {@link #keys()} and {@link #allKeys()} answer {@code null} rather
 * than an empty array when nothing is bound, and putting a {@code null} key removes the
 * binding through {@link #remove}, which {@code ComponentInputMap} overrides.
 */
public class InputMap implements Serializable {
    private static final long serialVersionUID = 2454458884813506064L;
    // Lazily created, as the JDK's ArrayTable: most components never bind a key.
    private transient Map<KeyStroke, Object> map;
    private InputMap parent;

    public InputMap() {
    }

    public void setParent(InputMap map) { this.parent = map; }
    public InputMap getParent() { return parent; }

    public void put(KeyStroke keyStroke, Object actionMapKey) {
        if (keyStroke == null) return;
        if (actionMapKey == null) {
            remove(keyStroke);
        } else {
            if (map == null) map = new HashMap<>();
            map.put(keyStroke, actionMapKey);
        }
    }

    public Object get(KeyStroke keyStroke) {
        if (map == null) {
            return parent != null ? parent.get(keyStroke) : null;
        }
        Object value = map.get(keyStroke);
        if (value == null && parent != null) return parent.get(keyStroke);
        return value;
    }

    public void remove(KeyStroke key) {
        if (map != null && key != null) map.remove(key);
    }

    public void clear() {
        if (map != null) map.clear();
    }

    public KeyStroke[] keys() {
        if (map == null) return null;
        return map.keySet().toArray(new KeyStroke[0]);
    }

    public int size() {
        return map == null ? 0 : map.size();
    }

    public KeyStroke[] allKeys() {
        int count = size();
        InputMap parent = getParent();
        if (count == 0) return parent != null ? parent.allKeys() : keys();
        if (parent == null) return keys();
        KeyStroke[] parentKeys = parent.allKeys();
        if (parentKeys == null) return keys();
        java.util.Set<KeyStroke> all = new java.util.LinkedHashSet<>(map.keySet());
        java.util.Collections.addAll(all, parentKeys);
        return all.toArray(new KeyStroke[0]);
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(map == null ? null : new HashMap<>(map));
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws ClassNotFoundException, java.io.IOException {
        s.defaultReadObject();
        map = (Map<KeyStroke, Object>) s.readObject();
    }
}
