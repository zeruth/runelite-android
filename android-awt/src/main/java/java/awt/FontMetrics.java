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

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/**
 * FontMetrics whose base measurements are estimated from font size assuming a fixed-width
 * 0.6em glyph. Wire to Android Paint.measureText for accuracy once a Typeface is
 * available on the rendering path.
 */
@net.runelite.awt.ShadowStub("base-class metrics are a fixed-width 0.6em estimate; only the Typeface-backed subclass measures real glyphs")
public abstract class FontMetrics implements Serializable {
    private static final long serialVersionUID = 1681126225205050147L;

    protected Font font;

    protected FontMetrics(Font font) {
        this.font = font;
    }

    public Font getFont() { return font; }
    public java.awt.font.FontRenderContext getFontRenderContext() { return new java.awt.font.FontRenderContext(null,false,false); }

    public int getLeading()   { return Math.max(1, font.getSize() / 8); }
    public int getAscent()    { return (int) Math.ceil(font.getSize() * 0.8); }
    public int getDescent()   { return (int) Math.ceil(font.getSize() * 0.2); }
    public int getHeight()    { return getLeading() + getAscent() + getDescent(); }
    public int getMaxAscent() { return getAscent(); }
    public int getMaxDescent() { return getDescent(); }
    public int getMaxAdvance() { return charWidth('W'); }

    public int charWidth(int codePoint) {
        return (int) Math.ceil(font.getSize() * 0.6);
    }

    public int charWidth(char ch) {
        return charWidth((int) ch);
    }

    public int stringWidth(String str) {
        if (str == null || str.isEmpty()) return 0;
        return (int) Math.ceil(str.length() * font.getSize() * 0.6);
    }

    public int charsWidth(char[] data, int off, int len) {
        return (int) Math.ceil(len * font.getSize() * 0.6);
    }

    public int bytesWidth(byte[] data, int off, int len) {
        return (int) Math.ceil(len * font.getSize() * 0.6);
    }

    public int[] getWidths() {
        int[] widths = new int[256];
        int w = (int) Math.ceil(font.getSize() * 0.6);
        for (int i = 0; i < widths.length; i++) widths[i] = w;
        return widths;
    }

    public Rectangle2D getStringBounds(String str, Graphics context) {
        return new Rectangle2D.Float(0f, -getAscent(), stringWidth(str), getHeight());
    }
}
