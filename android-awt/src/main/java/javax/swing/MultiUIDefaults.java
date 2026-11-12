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

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The table UIManager.getDefaults() returns: its own entries are the user
 * defaults, then each backing table (look and feel, then system) is searched
 * in order, so a value put through UIManager overrides the look and feel
 * without touching its table.
 */
@SuppressWarnings("serial")
class MultiUIDefaults extends UIDefaults {
    private final UIDefaults[] tables;

    MultiUIDefaults(UIDefaults[] defaults) {
        tables = defaults;
    }

    @Override
    public Object get(Object key) {
        Object value = super.get(key);
        if (value != null) {
            return value;
        }
        for (UIDefaults table : tables) {
            value = (table != null) ? table.get(key) : null;
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public Object get(Object key, Locale l) {
        Object value = super.get(key, l);
        if (value != null) {
            return value;
        }
        for (UIDefaults table : tables) {
            value = (table != null) ? table.get(key, l) : null;
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return entrySet().size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Enumeration<Object> keys() {
        return Collections.enumeration(merged().keySet());
    }

    @Override
    public Enumeration<Object> elements() {
        return Collections.enumeration(merged().values());
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return new LinkedHashSet<Map.Entry<Object, Object>>(merged().entrySet());
    }

    /** Raw entries, earlier tables shadowing later ones like get() does. */
    private Map<Object, Object> merged() {
        Map<Object, Object> result = new HashMap<Object, Object>();
        for (int i = tables.length - 1; i >= 0; i--) {
            if (tables[i] != null) {
                result.putAll(tables[i]);
            }
        }
        synchronized (this) {
            for (Map.Entry<Object, Object> entry : super.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    @Override
    protected void getUIError(String msg) {
        if (tables.length > 0 && tables[0] != null) {
            tables[0].getUIError(msg);
        } else {
            super.getUIError(msg);
        }
    }

    @Override
    public Object remove(Object key) {
        Object value = null;
        for (int i = tables.length - 1; i >= 0; i--) {
            if (tables[i] != null) {
                Object v = tables[i].remove(key);
                if (v != null) {
                    value = v;
                }
            }
        }
        Object v = super.remove(key);
        return (v != null) ? v : value;
    }

    @Override
    public void clear() {
        super.clear();
        for (UIDefaults table : tables) {
            if (table != null) {
                table.clear();
            }
        }
    }

    @Override
    public synchronized String toString() {
        return merged().toString();
    }
}
