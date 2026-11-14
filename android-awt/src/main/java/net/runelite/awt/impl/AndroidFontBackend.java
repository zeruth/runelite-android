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
package net.runelite.awt.impl;

import android.graphics.Paint;
import android.graphics.Typeface;

import java.awt.Font;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/** {@link FontBackend} over Paint/Typeface; loaded by name, see {@link FontBackend#get()}. */
public final class AndroidFontBackend extends FontBackend {
    /** Glyph probes per resolved Typeface: coverage does not depend on size. */
    private final ConcurrentHashMap<Typeface, Paint> probes = new ConcurrentHashMap<>();
    /** BMP coverage counts of faces whose 'maxp' could not be read. */
    private final ConcurrentHashMap<Typeface, Integer> probedCounts = new ConcurrentHashMap<>();

    public AndroidFontBackend() {}

    @Override
    public String register(InputStream in) throws IOException {
        return AwtFontRegistry.register(in);
    }

    private Paint probe(Font font) {
        Typeface tf = BufferedImageFontMetrics.typefaceFor(font);
        Paint p = probes.get(tf);
        if (p == null) {
            p = new Paint();
            p.setTypeface(tf);
            Paint prev = probes.putIfAbsent(tf, p);
            if (prev != null) p = prev;
        }
        return p;
    }

    @Override
    public boolean canDisplay(Font font, int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) return false;
        Paint p = probe(font);
        // Paint is not thread-safe; the probe is shared by every Font on this face.
        synchronized (p) {
            return p.hasGlyph(new String(Character.toChars(codePoint)));
        }
    }

    /**
     * The face's 'maxp' glyph count: registered faces from the bytes given to createFont,
     * logical and system families from their regular file under /system/fonts. A logical
     * family reports its primary face only; desktop sums the glyphs of every slot of the
     * composite, which Android's fallback chain does not expose. When no table can be
     * read the BMP coverage of the face plus .notdef is counted once and cached.
     */
    @Override
    public int numGlyphs(Font font) {
        int n = AwtFontRegistry.numGlyphs(font.getName(), font.getStyle());
        if (n > 0) return n;
        Paint p = probe(font);
        Typeface tf = p.getTypeface();
        Integer cached = probedCounts.get(tf);
        if (cached != null) return cached;
        int count = 1;
        synchronized (p) {
            char[] one = new char[1];
            for (int c = 0x20; c <= 0xFFFF; c++) {
                if (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE) continue;
                one[0] = (char) c;
                if (p.hasGlyph(new String(one))) count++;
            }
        }
        probedCounts.put(tf, count);
        return count;
    }

    /**
     * Inverse caret slope (run over rise, positive leaning right) of the face the font
     * renders with: 0 for upright styles; a registered italic face's 'post' italicAngle;
     * Skia's fake-italic skew (0.25) when the italic is synthesized from an upright
     * registered face; TextAttribute.POSTURE_OBLIQUE (0.2) for a system italic, whose
     * table is not reachable through Typeface.
     */
    @Override
    public float italicAngle(Font font) {
        if (!font.isItalic()) return 0f;
        Typeface base = AwtFontRegistry.lookup(font.getName(), font.getStyle());
        if (base != null && AwtFontRegistry.isRegistered(base)) {
            if ((AwtFontRegistry.faceStyle(base) & Font.ITALIC) == 0) return 0.25f;
            float slope = AwtFontRegistry.italicSlope(base);
            return Float.isNaN(slope) ? 0.2f : slope;
        }
        return 0.2f;
    }

    @Override
    public Rectangle2D stringBounds(Font font, String text) {
        Paint paint = BufferedImageFontMetrics.paintFor(font);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        Rectangle2D bounds = new Rectangle2D.Float(0, metrics.ascent, paint.measureText(text),
            metrics.descent - metrics.ascent + metrics.leading);
        return font.isTransformed() ? font.getTransform().createTransformedShape(bounds).getBounds2D() : bounds;
    }

    @Override
    public Rectangle2D maxCharBounds(Font font) {
        return stringBounds(font, "W");
    }

    @Override
    public LineMetrics lineMetrics(Font font, int numChars) {
        return new AndroidLineMetrics(font, numChars);
    }

    @Override
    public GlyphFace glyphFace(Font font, java.awt.font.FontRenderContext frc) {
        return new AndroidGlyphFace(font, frc);
    }
}
