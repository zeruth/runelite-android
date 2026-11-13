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
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.CharacterIterator;
import java.util.Arrays;

/**
 * The concrete {@link GlyphVector} behind {@code Font.createGlyphVector} and
 * {@code Font.layoutGlyphVector}, shaped like the JDK's {@code sun.font.StandardGlyphVector}.
 *
 * <p>As on desktop there is one glyph per UTF-16 char: the low half of a surrogate pair
 * gets the invisible glyph {@code 0xFFFF} with no advance and no outline, so
 * {@code getNumGlyphs() == text.length()} and char indices stay the identity for
 * left-to-right text. Glyph codes are real font glyph ids on API 31+
 * ({@code TextRunShaper}) and Unicode code points below it; see
 * {@link AwtFontRegistry#glyphCode}. Each glyph keeps the characters it stands for, so
 * outlines come from {@code Paint.getTextPath} of those characters on every API level.
 *
 * <p>The face data comes from {@link FontBackend#glyphFace} ({@code AndroidGlyphFace} on
 * Android), so this class names nothing from {@code android.*} and the host contracts run
 * it. Advances are the per-char run advances Android itself lays the string out with
 * ({@code Paint.getTextRunAdvances}, kerning and tracking included), fractional, so the
 * vector's positions agree with the pixels drawGlyphVector produces. Layout is per char:
 * no ligatures and no bidi reordering beyond reversing the run for
 * {@code LAYOUT_RIGHT_TO_LEFT} (mirrored characters are not swapped).
 */
public final class AndroidGlyphVector extends GlyphVector {
    /** The JDK's {@code CharToGlyphMapper.INVISIBLE_GLYPH_ID}. */
    public static final int INVISIBLE_GLYPH = 0xFFFF;

    private final Font font;
    private final FontRenderContext frc;
    private final AffineTransform fontTx;
    /** Logical text for vectors built from characters, null for ones built from glyph codes. */
    private final String text;
    private final boolean rtl;
    private int[] glyphs;
    /** Logical char index per glyph; null for the identity mapping. */
    private int[] charIndices;
    /** The characters each glyph outlines; empty for invisible or unknown glyphs. */
    private String[] clusters;
    /** Advance of each glyph along the baseline, before the font transform. */
    private float[] advances;
    /** x,y pairs for every glyph plus the position after the last one. */
    private float[] positions;
    private boolean positionsAdjusted;
    /** Per-glyph transforms, null when no glyph has one. */
    private AffineTransform[] transforms;
    /** Glyph-local outlines (font transform applied), built on demand. */
    private GeneralPath[] outlines;
    private final float ascent, descent, leading;
    /** Shared by clones: a face is immutable apart from its own synchronized caches. */
    private final GlyphFace face;

    private AndroidGlyphVector(Font font, FontRenderContext frc, String text, boolean rtl) {
        this.font = font;
        this.frc = frc;
        this.fontTx = font.getTransform();
        this.text = text;
        this.rtl = rtl;
        this.face = FontBackend.get().glyphFace(font, frc);
        this.ascent = face.ascent();
        this.descent = face.descent();
        this.leading = face.leading();
    }

    /**
     * Lays out {@code text[start, limit)} with {@code java.awt.Font} layout flags. The flags
     * {@code LAYOUT_NO_START_CONTEXT} / {@code LAYOUT_NO_LIMIT_CONTEXT} keep the text outside
     * the range out of the advance measurement, as they keep it out of shaping on desktop.
     */
    public static AndroidGlyphVector layout(Font font, FontRenderContext frc, char[] text,
                                            int start, int limit, int flags) {
        if (font == null || frc == null || text == null) throw new NullPointerException();
        if (start < 0 || limit > text.length || start > limit) {
            throw new ArrayIndexOutOfBoundsException("start: " + start + " limit: " + limit + " length: " + text.length);
        }
        boolean rtl = (flags & Font.LAYOUT_RIGHT_TO_LEFT) != 0;
        int n = limit - start;
        AndroidGlyphVector v = new AndroidGlyphVector(font, frc, new String(text, start, n), rtl);
        GlyphFace face = v.face;
        float[] logical = new float[n];
        if (n > 0) {
            int ctxStart = (flags & Font.LAYOUT_NO_START_CONTEXT) != 0 ? start : 0;
            int ctxLimit = (flags & Font.LAYOUT_NO_LIMIT_CONTEXT) != 0 ? limit : text.length;
            face.advances(text, start, n, ctxStart, ctxLimit - ctxStart, rtl, logical);
        }
        int[] codes = new int[n];
        String[] clusters = new String[n];
        for (int i = 0; i < n; i++) {
            char c = text[start + i];
            if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(text[start + i + 1])) {
                codes[i] = face.glyphCode(Character.toCodePoint(c, text[start + i + 1]));
                clusters[i] = new String(text, start + i, 2);
                codes[i + 1] = INVISIBLE_GLYPH;
                clusters[i + 1] = "";
                logical[i] += logical[i + 1];
                logical[i + 1] = 0;
                i++;
            } else {
                codes[i] = face.glyphCode(c);
                clusters[i] = String.valueOf(c);
            }
        }
        v.glyphs = new int[n];
        v.clusters = new String[n];
        v.advances = new float[n];
        if (rtl) v.charIndices = new int[n];
        for (int g = 0; g < n; g++) {
            int i = rtl ? n - 1 - g : g;
            v.glyphs[g] = codes[i];
            v.clusters[g] = clusters[i];
            v.advances[g] = logical[i];
            if (rtl) v.charIndices[g] = i;
        }
        v.performDefaultLayout();
        return v;
    }

    /** {@code Font.createGlyphVector(frc, CharacterIterator)}: the iterator's whole range, left to right. */
    public static AndroidGlyphVector layout(Font font, FontRenderContext frc, CharacterIterator ci) {
        StringBuilder sb = new StringBuilder(Math.max(0, ci.getEndIndex() - ci.getBeginIndex()));
        for (char c = ci.first(); c != CharacterIterator.DONE; c = ci.next()) sb.append(c);
        char[] text = sb.toString().toCharArray();
        return layout(font, frc, text, 0, text.length, Font.LAYOUT_LEFT_TO_RIGHT);
    }

    /**
     * A vector of given glyph codes. A code is outlined and measured through the character
     * it was produced for ({@link AwtFontRegistry#codePointForGlyph}); a code with no known
     * character keeps zero advance and an empty outline.
     */
    public static AndroidGlyphVector fromGlyphCodes(Font font, FontRenderContext frc, int[] glyphCodes) {
        if (font == null || frc == null || glyphCodes == null) throw new NullPointerException();
        AndroidGlyphVector v = new AndroidGlyphVector(font, frc, null, false);
        int n = glyphCodes.length;
        v.glyphs = glyphCodes.clone();
        v.clusters = new String[n];
        v.advances = new float[n];
        for (int g = 0; g < n; g++) {
            int cp = glyphCodes[g] == INVISIBLE_GLYPH ? -1 : v.face.codePointForGlyph(glyphCodes[g]);
            v.clusters[g] = cp < 0 ? "" : new String(Character.toChars(cp));
            v.advances[g] = cp < 0 ? 0f : v.face.advance(v.clusters[g]);
        }
        v.performDefaultLayout();
        return v;
    }

    /**
     * The logical text when this vector still has the layout drawString would give it
     * (left to right, default positions, no glyph transforms), else null; drawGlyphVector
     * then draws the run through the cached text path so its pixels equal drawString's.
     */
    public String defaultRun() {
        return text != null && !rtl && !positionsAdjusted && transforms == null ? text : null;
    }

    /** The characters glyph {@code ix} is drawn from; empty for invisible or unknown glyphs. */
    public String glyphText(int ix) {
        checkGlyph(ix);
        return clusters[ix];
    }

    @Override public Font getFont() { return font; }

    @Override public FontRenderContext getFontRenderContext() { return frc; }

    @Override
    public void performDefaultLayout() {
        int n = glyphs.length;
        float[] p = new float[2 * (n + 1)];
        double x = 0, y = 0;
        boolean identity = fontTx.isIdentity();
        for (int g = 0; g < n; g++) {
            p[2 * g] = (float) x;
            p[2 * g + 1] = (float) y;
            if (identity) {
                x += advances[g];
            } else {
                Point2D d = fontTx.deltaTransform(new Point2D.Float(advances[g], 0), null);
                x += d.getX();
                y += d.getY();
            }
        }
        p[2 * n] = (float) x;
        p[2 * n + 1] = (float) y;
        positions = p;
        positionsAdjusted = false;
    }

    @Override public int getNumGlyphs() { return glyphs.length; }

    @Override
    public int getGlyphCode(int glyphIndex) {
        checkGlyph(glyphIndex);
        return glyphs[glyphIndex];
    }

    @Override
    public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, int[] codeReturn) {
        if (numEntries < 0) throw new IllegalArgumentException("count = " + numEntries);
        if (beginGlyphIndex < 0) throw new IndexOutOfBoundsException("start = " + beginGlyphIndex);
        if (beginGlyphIndex > glyphs.length - numEntries) {
            throw new IndexOutOfBoundsException("start + count = " + (beginGlyphIndex + numEntries));
        }
        if (codeReturn == null) codeReturn = new int[numEntries];
        System.arraycopy(glyphs, beginGlyphIndex, codeReturn, 0, numEntries);
        return codeReturn;
    }

    @Override
    public int getGlyphCharIndex(int glyphIndex) {
        checkGlyph(glyphIndex);
        return charIndices == null ? glyphIndex : charIndices[glyphIndex];
    }

    @Override
    public int[] getGlyphCharIndices(int beginGlyphIndex, int numEntries, int[] codeReturn) {
        if (numEntries < 0) throw new IllegalArgumentException("count = " + numEntries);
        if (beginGlyphIndex < 0 || beginGlyphIndex > glyphs.length - numEntries) {
            throw new IndexOutOfBoundsException("start = " + beginGlyphIndex + " count = " + numEntries);
        }
        return super.getGlyphCharIndices(beginGlyphIndex, numEntries, codeReturn);
    }

    @Override
    public Rectangle2D getLogicalBounds() {
        int n = glyphs.length;
        if (n == 0) return new Rectangle2D.Float(0, -ascent, 0, ascent + descent + leading);
        Rectangle2D result = null;
        for (int g = 0; g < n; g++) {
            Rectangle2D r = getGlyphLogicalBounds(g).getBounds2D();
            if (result == null) result = r;
            else result.add(r);
        }
        return result;
    }

    @Override
    public Rectangle2D getVisualBounds() {
        return getOutline().getBounds2D();
    }

    @Override
    public Shape getOutline() {
        return getOutline(0, 0);
    }

    @Override
    public Shape getOutline(float x, float y) {
        GeneralPath result = new GeneralPath();
        for (int g = 0; g < glyphs.length; g++) {
            if (clusters[g].isEmpty()) continue;
            result.append(placedOutline(g, x, y), false);
        }
        return result;
    }

    @Override
    public Shape getGlyphOutline(int glyphIndex) {
        checkGlyph(glyphIndex);
        return placedOutline(glyphIndex, 0, 0);
    }

    @Override
    public Shape getGlyphOutline(int glyphIndex, float x, float y) {
        checkGlyph(glyphIndex);
        return placedOutline(glyphIndex, x, y);
    }

    @Override
    public Point2D getGlyphPosition(int glyphIndex) {
        checkPosition(glyphIndex);
        return new Point2D.Float(positions[2 * glyphIndex], positions[2 * glyphIndex + 1]);
    }

    @Override
    public void setGlyphPosition(int glyphIndex, Point2D newPos) {
        checkPosition(glyphIndex);
        positions[2 * glyphIndex] = (float) newPos.getX();
        positions[2 * glyphIndex + 1] = (float) newPos.getY();
        positionsAdjusted = true;
    }

    @Override
    public AffineTransform getGlyphTransform(int glyphIndex) {
        checkGlyph(glyphIndex);
        AffineTransform tx = transforms == null ? null : transforms[glyphIndex];
        return tx == null ? null : new AffineTransform(tx);
    }

    @Override
    public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {
        checkGlyph(glyphIndex);
        if (newTX == null || newTX.isIdentity()) {
            if (transforms == null) return;
            transforms[glyphIndex] = null;
            for (AffineTransform t : transforms) if (t != null) return;
            transforms = null;
        } else {
            if (transforms == null) transforms = new AffineTransform[glyphs.length];
            transforms[glyphIndex] = new AffineTransform(newTX);
        }
    }

    @Override
    public int getLayoutFlags() {
        int flags = 0;
        if (transforms != null) flags |= FLAG_HAS_TRANSFORMS;
        if (positionsAdjusted) flags |= FLAG_HAS_POSITION_ADJUSTMENTS;
        if (rtl) flags |= FLAG_RUN_RTL;
        if (charIndices != null) flags |= FLAG_COMPLEX_GLYPHS;
        return flags;
    }

    @Override
    public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, float[] positionReturn) {
        if (numEntries < 0) throw new IllegalArgumentException("count = " + numEntries);
        if (beginGlyphIndex < 0) throw new IndexOutOfBoundsException("start = " + beginGlyphIndex);
        if (beginGlyphIndex > glyphs.length + 1 - numEntries) {
            throw new IndexOutOfBoundsException("start + count = " + (beginGlyphIndex + numEntries));
        }
        if (positionReturn == null) positionReturn = new float[2 * numEntries];
        System.arraycopy(positions, 2 * beginGlyphIndex, positionReturn, 0, 2 * numEntries);
        return positionReturn;
    }

    @Override
    public Shape getGlyphLogicalBounds(int glyphIndex) {
        checkGlyph(glyphIndex);
        Rectangle2D local = new Rectangle2D.Float(0, -ascent, advances[glyphIndex], ascent + descent + leading);
        AffineTransform at = placement(glyphIndex, 0, 0);
        at.concatenate(fontTx);
        return at.isIdentity() ? local : at.createTransformedShape(local);
    }

    @Override
    public Shape getGlyphVisualBounds(int glyphIndex) {
        checkGlyph(glyphIndex);
        return placedOutline(glyphIndex, 0, 0).getBounds2D();
    }

    /** As the JDK: the visual bounds relative to the glyph's position, its transformed advance, STANDARD. */
    @Override
    public GlyphMetrics getGlyphMetrics(int glyphIndex) {
        checkGlyph(glyphIndex);
        Rectangle2D vb = getGlyphVisualBounds(glyphIndex).getBounds2D();
        vb.setRect(vb.getMinX() - positions[2 * glyphIndex], vb.getMinY() - positions[2 * glyphIndex + 1],
            vb.getWidth(), vb.getHeight());
        Point2D adv = fontTx.deltaTransform(new Point2D.Float(advances[glyphIndex], 0), null);
        return new GlyphMetrics(true, (float) adv.getX(), (float) adv.getY(), vb, GlyphMetrics.STANDARD);
    }

    /**
     * Null, as {@code StandardGlyphVector} returns: the JDK has no per-glyph justification
     * data either (TextLayout assigns its own from the character class).
     */
    @Override
    public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex) {
        checkGlyph(glyphIndex);
        return null;
    }

    @Override
    public boolean equals(GlyphVector set) {
        if (this == set) return true;
        if (!(set instanceof AndroidGlyphVector)) return false;
        AndroidGlyphVector other = (AndroidGlyphVector) set;
        return Arrays.equals(glyphs, other.glyphs)
            && font.equals(other.font)
            && frc.equals(other.frc)
            && rtl == other.rtl
            && Arrays.equals(charIndices, other.charIndices)
            && Arrays.equals(positions, other.positions)
            && Arrays.equals(transforms, other.transforms);
    }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof GlyphVector && equals((GlyphVector) rhs);
    }

    /** As the JDK: cheap, and stable across position and transform edits. */
    @Override
    public int hashCode() {
        return font.hashCode() ^ glyphs.length;
    }

    @Override
    public Object clone() {
        try {
            AndroidGlyphVector copy = (AndroidGlyphVector) super.clone();
            copy.glyphs = glyphs.clone();
            copy.charIndices = charIndices == null ? null : charIndices.clone();
            copy.clusters = clusters.clone();
            copy.advances = advances.clone();
            copy.positions = positions.clone();
            if (transforms != null) {
                copy.transforms = new AffineTransform[transforms.length];
                for (int i = 0; i < transforms.length; i++) {
                    if (transforms[i] != null) copy.transforms[i] = new AffineTransform(transforms[i]);
                }
            }
            // Glyph-local outlines never change and are not handed out, so they are shared.
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e.toString());
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[font=" + font + ",glyphs=" + Arrays.toString(glyphs) + "]";
    }

    private void checkGlyph(int ix) {
        if (ix < 0 || ix >= glyphs.length) throw new IndexOutOfBoundsException("ix = " + ix);
    }

    private void checkPosition(int ix) {
        if (ix < 0 || ix > glyphs.length) throw new IndexOutOfBoundsException("ix = " + ix);
    }

    /** Translation to the glyph's position (plus x, y) followed by its own transform. */
    private AffineTransform placement(int g, float x, float y) {
        AffineTransform at = AffineTransform.getTranslateInstance(x + positions[2 * g], y + positions[2 * g + 1]);
        if (transforms != null && transforms[g] != null) at.concatenate(transforms[g]);
        return at;
    }

    private Shape placedOutline(int g, float x, float y) {
        return placement(g, x, y).createTransformedShape(glyphOutline(g));
    }

    private synchronized GeneralPath glyphOutline(int g) {
        if (outlines == null) outlines = new GeneralPath[glyphs.length];
        GeneralPath outline = outlines[g];
        if (outline != null) return outline;
        String cluster = clusters[g];
        outline = cluster.isEmpty() ? new GeneralPath() : face.outline(cluster);
        if (!fontTx.isIdentity()) outline.transform(fontTx);
        outlines[g] = outline;
        return outline;
    }
}
