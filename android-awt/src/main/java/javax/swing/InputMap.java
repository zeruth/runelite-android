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

public class InputMap implements Serializable {
    private static final long serialVersionUID = 2454458884813506064L;
    private final Map<KeyStroke, Object> map = new HashMap<>();
    private InputMap parent;

    public void put(KeyStroke keyStroke, Object actionMapKey) {
        if (actionMapKey == null) map.remove(keyStroke);
        else map.put(keyStroke, actionMapKey);
    }

    public Object get(KeyStroke keyStroke) {
        Object o = map.get(keyStroke);
        return o != null ? o : (parent != null ? parent.get(keyStroke) : null);
    }

    public void remove(KeyStroke key) { map.remove(key); }
    public void clear() { map.clear(); }
    public int size() { return map.size(); }
    public KeyStroke[] keys() { return map.keySet().toArray(new KeyStroke[0]); }
    public KeyStroke[] allKeys() { java.util.Set<KeyStroke> all = new java.util.LinkedHashSet<>(map.keySet()); if (parent != null && parent.allKeys() != null) java.util.Collections.addAll(all, parent.allKeys()); return all.toArray(new KeyStroke[0]); }

    public void setParent(InputMap parent) { this.parent = parent; }
    public InputMap getParent() { return parent; }
}
