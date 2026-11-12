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

import java.awt.Font;
import java.awt.FontMetrics;

/**
 * Font metrics backed by Android's {@link android.graphics.Paint}. Real layouts (BoxLayout,
 * GridLayout, ClientUI.Layout) read stringWidth/getHeight/getAscent to size labels; with
 * the default JDK FontMetrics base class everything would report 0 and children collapse.
 */
public final class BufferedImageFontMetrics extends FontMetrics {
    private static final java.util.concurrent.ConcurrentHashMap<Font, BufferedImageFontMetrics> CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static BufferedImageFontMetrics forFont(Font font) {
        if (font == null) return null;
        BufferedImageFontMetrics cached = CACHE.get(font);
        if (cached != null) return cached;
        BufferedImageFontMetrics fm = new BufferedImageFontMetrics(font);
        // putIfAbsent, not put: this is now the single process-wide metrics cache
        // (java.awt.Component.getFontMetrics delegates here), so two threads racing on the
        // same font should end up sharing one instance rather than each keeping its own
        // Paint + Typeface + width table alive.
        BufferedImageFontMetrics prev = CACHE.putIfAbsent(font, fm);
        return prev != null ? prev : fm;
    }

    private final android.graphics.Paint paint;
    private final int ascent;
    private final int descent;
    private final int leading;
    private final int height;
    /**
     * Widths of U+0000..U+007F, measured once. charWidth() used to allocate a
     * {@code char[1]} and a {@code float[1]} and cross into Minikin on every call, and
     * Swing text layout calls it per character — so a single label measure was dozens of
     * allocations. Everything the UI actually renders is in this range.
     */
    private final int[] asciiWidths = new int[128];
    /** Scratch for the non-ASCII path. Guarded by {@code this} because the Paint is
     *  shared across callers anyway. */
    private final char[] charScratch = new char[1];
    private final float[] widthScratch = new float[1];
    /** U+0000..U+00FF widths for getWidths(), built on first use; races only rebuild it. */
    private volatile int[] latin1Widths;
    private volatile int maxAdvance = -1;

    /** Android's Paint.setTextSize takes pixels; Java Font.size is conventionally points
     *  but the OSRS/RuneLite code paths treat it as pixels too, so pass through. */
    public static float pxSize(Font f) {
        return Math.max(1f, f.getSize2D());
    }

    public static android.graphics.Paint paintFor(Font font){
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(pxSize(font));paint.setTypeface(typefaceFor(font));paint.setLetterSpacing(font.getTracking());return paint;
    }

    public BufferedImageFontMetrics(Font font) {
        super(font);
        paint = paintFor(font);
        android.graphics.Paint.FontMetrics afm = paint.getFontMetrics();
        this.ascent = Math.max(0, (int) Math.ceil(-afm.ascent));
        this.descent = Math.max(0, (int) Math.ceil(afm.descent));
        this.leading = Math.max(0, (int) Math.ceil(afm.leading));
        this.height = this.ascent + this.descent + this.leading;

        char[] ascii = new char[128];
        for (int i = 0; i < 128; i++) ascii[i] = (char) i;
        float[] w = new float[128];
        paint.getTextWidths(ascii, 0, 128, w);
        for (int i = 0; i < 128; i++) asciiWidths[i] = (int) Math.ceil(w[i]);
    }

    public static int canDisplayUpTo(Font font, String text) {
        android.graphics.Paint probe = new android.graphics.Paint();
        probe.setTypeface(typefaceFor(font));
        probe.setTextSize(font.getSize2D());
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            int end = index + Character.charCount(codePoint);
            if (!probe.hasGlyph(text.substring(index, end))) return index;
            index = end;
        }
        return -1;
    }

    /** Synthesized variants of registered faces, keyed by face then missing style bits. */
    private static final java.util.concurrent.ConcurrentHashMap<android.graphics.Typeface, android.graphics.Typeface[]> SYNTH =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Resolve an AWT Font down to an Android Typeface. A registered family (e.g.
     *  runescape.ttf / runescape_bold.ttf via Font.createFont) resolves to its member for
     *  the requested style; style bits that member doesn't carry itself are synthesized
     *  (fake bold / skew), as the JDK does — so {@code deriveFont(Font.BOLD, 16)} on a
     *  face with no bold member gets emboldened, while the real bold face is used untouched.
     *  Metrics come from the same Typeface, so stringWidth matches what is drawn. */
    static android.graphics.Typeface typefaceFor(Font font) {
        int style = font.getStyle() & (Font.BOLD | Font.ITALIC);
        android.graphics.Typeface registered = net.runelite.awt.impl.AwtFontRegistry.lookup(font.getName(), style);
        if (registered != null) {
            int missing = style & ~net.runelite.awt.impl.AwtFontRegistry.faceStyle(registered);
            if (missing == 0) return registered;
            android.graphics.Typeface[] v = SYNTH.get(registered);
            if (v == null) {
                v = new android.graphics.Typeface[4];
                android.graphics.Typeface[] prev = SYNTH.putIfAbsent(registered, v);
                if (prev != null) v = prev;
            }
            android.graphics.Typeface t = v[missing];
            if (t == null) {
                int as = missing == (Font.BOLD | Font.ITALIC) ? android.graphics.Typeface.BOLD_ITALIC
                    : missing == Font.BOLD ? android.graphics.Typeface.BOLD : android.graphics.Typeface.ITALIC;
                t = android.graphics.Typeface.create(registered, as);
                v[missing] = t;
            }
            return t;
        }
        if (style == Font.BOLD) return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        if (style == Font.ITALIC) return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC);
        if (style == (Font.BOLD | Font.ITALIC)) return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC);
        return android.graphics.Typeface.DEFAULT;
    }

    @Override
    public int getAscent()  { return ascent; }
    @Override
    public int getDescent() { return descent; }
    @Override
    public int getLeading() { return leading; }
    @Override
    public int getHeight()  { return height; }

    @Override
    public int getMaxAdvance() {
        int max = maxAdvance;
        if (max < 0) {
            // Android exposes no hmtx maximum, so the widest Latin-1 glyph stands in for it:
            // that is the range the client and Swing actually draw.
            max = 0;
            for (int w : getWidths()) max = Math.max(max, w);
            maxAdvance = max;
        }
        return max;
    }

    @Override
    public int charWidth(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) codePoint = 0xffff;
        if (codePoint < 0x10000) return charWidth((char) codePoint);
        return charsWidth(Character.toChars(codePoint), 0, 2);
    }

    @Override
    public int charWidth(char ch) {
        if (ch < 128) return asciiWidths[ch];
        synchronized (this) {
            charScratch[0] = ch;
            paint.getTextWidths(charScratch, 0, 1, widthScratch);
            return (int) Math.ceil(widthScratch[0]);
        }
    }

    /*
     * Widths are summed from per-glyph advances, each rounded up the way charWidth rounds it,
     * so stringWidth equals the sum of the charWidths as it does on desktop (where hinted
     * advances are whole pixels). Rounding up rather than to nearest keeps a measured label at
     * least as wide as what Canvas paints, so Swing never truncates it by a pixel.
     */
    @Override
    public int charsWidth(char[] data, int off, int len) {
        if (data == null || len <= 0) return 0;
        if (off < 0 || len > data.length - off) throw new IndexOutOfBoundsException("off " + off + ", len " + len);
        int sum = 0;
        for (int i = off, end = off + len; i < end; i++) {
            char ch = data[i];
            if (ch >= 128) return sum + measuredWidth(data, i, end - i);
            sum += asciiWidths[ch];
        }
        return sum;
    }

    @Override
    public int stringWidth(String str) {
        // The JDK throws on null; the shadow Swing components measure an unset text (null)
        // through here and have always got 0, so that stays.
        if (str == null) return 0;
        int len = str.length();
        int sum = 0;
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch >= 128) {
                char[] rest = new char[len - i];
                str.getChars(i, len, rest, 0);
                return sum + measuredWidth(rest, 0, rest.length);
            }
            sum += asciiWidths[ch];
        }
        return sum;
    }

    @Override
    public int bytesWidth(byte[] data, int off, int len) {
        if (len <= 0) return 0;
        if (off < 0 || len > data.length - off) throw new IndexOutOfBoundsException("off " + off + ", len " + len);
        // The JDK reads the bytes as ISO-8859-1 (String(byte[], 0, off, len)).
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) chars[i] = (char) (data[off + i] & 0xff);
        return charsWidth(chars, 0, len);
    }

    @Override
    public int[] getWidths() {
        int[] widths = latin1Widths;
        if (widths == null) {
            widths = new int[256];
            for (int i = 0; i < 256; i++) widths[i] = charWidth((char) i);
            latin1Widths = widths;
        }
        return widths.clone();
    }

    /** Sum of the rounded-up advances of a run Minikin shapes (surrogates carry their width
     *  on the high char, the low one reports 0). */
    private int measuredWidth(char[] data, int off, int len) {
        float[] w = new float[len];
        paint.getTextWidths(data, off, len, w);
        int sum = 0;
        for (float f : w) sum += (int) Math.ceil(f);
        return sum;
    }
}

