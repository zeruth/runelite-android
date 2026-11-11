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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RenderingHints as a plain map. RuneLite reads/writes the map but our Graphics2D ignores the
 * values — anti-aliasing/interpolation hints are applied at the Rust drawing layer based
 * on built-in heuristics, not these flags. Provided for API compatibility.
 */
@net.runelite.awt.ShadowStub("stored but ignored: the Rust rasterizer chooses anti-aliasing and interpolation itself, and Key.isCompatibleValue accepts anything")
public class RenderingHints implements Map<Object, Object>, Cloneable {
    public static class Key {
        private final int privateKey;
        protected Key(int privateKey) { this.privateKey = privateKey; }
        public int intKey() { return privateKey; }
        public boolean isCompatibleValue(Object val) { return true; }
        @Override public final int hashCode() { return super.hashCode(); }
        @Override public final boolean equals(Object o) { return this == o; }
    }

    public static final Key KEY_ANTIALIASING = new Key(1);
    public static final Object VALUE_ANTIALIAS_ON = "VALUE_ANTIALIAS_ON";
    public static final Object VALUE_ANTIALIAS_OFF = "VALUE_ANTIALIAS_OFF";
    public static final Object VALUE_ANTIALIAS_DEFAULT = "VALUE_ANTIALIAS_DEFAULT";

    public static final Key KEY_TEXT_ANTIALIASING = new Key(2);
    public static final Object VALUE_TEXT_ANTIALIAS_ON = "VALUE_TEXT_ANTIALIAS_ON";
    public static final Object VALUE_TEXT_ANTIALIAS_OFF = "VALUE_TEXT_ANTIALIAS_OFF";
    public static final Object VALUE_TEXT_ANTIALIAS_DEFAULT = "VALUE_TEXT_ANTIALIAS_DEFAULT";

    public static final Key KEY_RENDERING = new Key(3);
    public static final Object VALUE_RENDER_SPEED = "VALUE_RENDER_SPEED";
    public static final Object VALUE_RENDER_QUALITY = "VALUE_RENDER_QUALITY";
    public static final Object VALUE_RENDER_DEFAULT = "VALUE_RENDER_DEFAULT";

    public static final Key KEY_INTERPOLATION = new Key(4);
    public static final Object VALUE_INTERPOLATION_NEAREST_NEIGHBOR = "VALUE_INTERPOLATION_NEAREST_NEIGHBOR";
    public static final Object VALUE_INTERPOLATION_BILINEAR = "VALUE_INTERPOLATION_BILINEAR";
    public static final Object VALUE_INTERPOLATION_BICUBIC = "VALUE_INTERPOLATION_BICUBIC";

    public static final Key KEY_ALPHA_INTERPOLATION = new Key(5);
    public static final Object VALUE_ALPHA_INTERPOLATION_SPEED = "VALUE_ALPHA_INTERPOLATION_SPEED";
    public static final Object VALUE_ALPHA_INTERPOLATION_QUALITY = "VALUE_ALPHA_INTERPOLATION_QUALITY";
    public static final Object VALUE_ALPHA_INTERPOLATION_DEFAULT = "VALUE_ALPHA_INTERPOLATION_DEFAULT";

    public static final Key KEY_COLOR_RENDERING = new Key(6);
    public static final Object VALUE_COLOR_RENDER_SPEED = "VALUE_COLOR_RENDER_SPEED";
    public static final Object VALUE_COLOR_RENDER_QUALITY = "VALUE_COLOR_RENDER_QUALITY";
    public static final Object VALUE_COLOR_RENDER_DEFAULT = "VALUE_COLOR_RENDER_DEFAULT";

    public static final Key KEY_DITHERING = new Key(7);
    public static final Object VALUE_DITHER_DISABLE = "VALUE_DITHER_DISABLE";
    public static final Object VALUE_DITHER_ENABLE = "VALUE_DITHER_ENABLE";
    public static final Object VALUE_DITHER_DEFAULT = "VALUE_DITHER_DEFAULT";

    public static final Key KEY_FRACTIONALMETRICS = new Key(8);
    public static final Object VALUE_FRACTIONALMETRICS_OFF = "VALUE_FRACTIONALMETRICS_OFF";
    public static final Object VALUE_FRACTIONALMETRICS_ON = "VALUE_FRACTIONALMETRICS_ON";
    public static final Object VALUE_FRACTIONALMETRICS_DEFAULT = "VALUE_FRACTIONALMETRICS_DEFAULT";

    public static final Key KEY_STROKE_CONTROL = new Key(9);
    // VALUE_STROKE_PURE is read by RuneLite's RegenMeterOverlay (and other overlays);
    // a missing constant here crashes the very first render after login.
    public static final Object VALUE_STROKE_NORMALIZE = "VALUE_STROKE_NORMALIZE";
    public static final Object VALUE_STROKE_PURE = "VALUE_STROKE_PURE";
    public static final Object VALUE_STROKE_DEFAULT = "VALUE_STROKE_DEFAULT";

    public static final Key KEY_TEXT_LCD_CONTRAST = new Key(10);

    public static final Object VALUE_TEXT_ANTIALIAS_GASP = "VALUE_TEXT_ANTIALIAS_GASP";
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_HRGB = "VALUE_TEXT_ANTIALIAS_LCD_HRGB";
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_HBGR = "VALUE_TEXT_ANTIALIAS_LCD_HBGR";
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_VRGB = "VALUE_TEXT_ANTIALIAS_LCD_VRGB";
    public static final Object VALUE_TEXT_ANTIALIAS_LCD_VBGR = "VALUE_TEXT_ANTIALIAS_LCD_VBGR";

    private final HashMap<Object, Object> map = new HashMap<>();

    public RenderingHints(Map<Key, ?> init) {
        if (init != null) map.putAll(init);
    }

    public RenderingHints(Key key, Object value) {
        map.put(key, value);
    }

    public void add(RenderingHints hints) { map.putAll(hints.map); }
    @Override public int size() { return map.size(); }
    @Override public boolean isEmpty() { return map.isEmpty(); }
    @Override public boolean containsKey(Object key) { return map.containsKey(key); }
    @Override public boolean containsValue(Object value) { return map.containsValue(value); }
    @Override public Object get(Object key) { return map.get(key); }
    @Override public Object put(Object key, Object value) { return map.put(key, value); }
    @Override public Object remove(Object key) { return map.remove(key); }
    @Override @SuppressWarnings("unchecked")
    public void putAll(Map<?, ?> m) { map.putAll((Map<? extends Object, ? extends Object>) m); }
    @Override public void clear() { map.clear(); }
    @Override public Set<Object> keySet() { return map.keySet(); }
    @Override public Collection<Object> values() { return map.values(); }
    @Override public Set<Entry<Object, Object>> entrySet() { return map.entrySet(); }

    @Override
    public Object clone() {
        RenderingHints copy = new RenderingHints(null);
        copy.map.putAll(this.map);
        return copy;
    }

    @Override public String toString() { return map.toString(); }
}
