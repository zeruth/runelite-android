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
 * Maps action-map keys to Actions through a parent chain, with the JDK's contract: {@link #keys()}
 * and {@link #allKeys()} answer {@code null} when nothing is bound, and putting a {@code null}
 * action removes the key.
 */
public class ActionMap implements Serializable {
    private static final long serialVersionUID = -2294474015281814929L;
    private transient Map<Object, Action> map;
    private ActionMap parent;

    public ActionMap() {
    }

    public void setParent(ActionMap map) { this.parent = map; }
    public ActionMap getParent() { return parent; }

    public void put(Object key, Action action) {
        if (key == null) return;
        if (action == null) {
            remove(key);
        } else {
            if (map == null) map = new HashMap<>();
            map.put(key, action);
        }
    }

    public Action get(Object key) {
        Action value = map == null ? null : map.get(key);
        if (value == null && parent != null) return parent.get(key);
        return value;
    }

    public void remove(Object key) {
        if (map != null && key != null) map.remove(key);
    }

    public void clear() {
        if (map != null) map.clear();
    }

    public Object[] keys() {
        if (map == null) return null;
        return map.keySet().toArray();
    }

    public int size() {
        return map == null ? 0 : map.size();
    }

    public Object[] allKeys() {
        int count = size();
        ActionMap parent = getParent();
        if (count == 0) return parent != null ? parent.allKeys() : keys();
        if (parent == null) return keys();
        Object[] parentKeys = parent.allKeys();
        if (parentKeys == null) return keys();
        java.util.Set<Object> all = new java.util.LinkedHashSet<>(map.keySet());
        java.util.Collections.addAll(all, parentKeys);
        return all.toArray();
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(map == null ? null : new HashMap<>(map));
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws ClassNotFoundException, java.io.IOException {
        s.defaultReadObject();
        map = (Map<Object, Action>) s.readObject();
    }
}
