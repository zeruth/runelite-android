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
import android.graphics.Path;
import android.graphics.Typeface;
import androidx.graphics.path.PathIterator;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.GeneralPath;

/**
 * {@link GlyphFace} over the Paint drawString renders with
 * ({@code BufferedImageFontMetrics.paintFor}), so a vector's advances are the ones the
 * TextCache path places glyphs at. Glyph codes are {@link AwtFontRegistry#glyphCode}: real
 * glyph ids on API 31+ ({@code TextRunShaper}), Unicode code points below it.
 *
 * <p>Advances stay fractional whatever the context's fractional-metrics flag says:
 * drawString on Android positions glyphs fractionally, and a vector's positions must
 * agree with the pixels drawGlyphVector produces.
 */
public final class AndroidGlyphFace implements GlyphFace {
    private final Paint paint;
    private final Typeface typeface;
    private final float ascent, descent, leading;
    /** For outlines: the same face without tracking. */
    private Paint outlinePaint;

    AndroidGlyphFace(Font font, FontRenderContext frc) {
        paint = BufferedImageFontMetrics.paintFor(font);
        paint.setAntiAlias(frc.isAntiAliased());
        paint.setSubpixelText(frc.usesFractionalMetrics());
        typeface = BufferedImageFontMetrics.typefaceFor(font);
        Paint.FontMetrics fm = paint.getFontMetrics();
        ascent = -fm.ascent;
        descent = fm.descent;
        leading = fm.leading;
    }

    @Override public float ascent() { return ascent; }

    @Override public float descent() { return descent; }

    @Override public float leading() { return leading; }

    // Paint is not thread-safe and a vector may be measured from any thread.
    @Override
    public synchronized void advances(char[] text, int start, int count, int contextStart, int contextCount,
                                      boolean rtl, float[] out) {
        if (count > 0) paint.getTextRunAdvances(text, start, count, contextStart, contextCount, rtl, out, 0);
    }

    @Override
    public synchronized float advance(String cluster) {
        return paint.measureText(cluster);
    }

    @Override
    public int glyphCode(int codePoint) {
        return AwtFontRegistry.glyphCode(typeface, codePoint);
    }

    @Override
    public int codePointForGlyph(int glyphCode) {
        return AwtFontRegistry.codePointForGlyph(typeface, glyphCode);
    }

    @Override
    public synchronized GeneralPath outline(String cluster) {
        if (outlinePaint == null) {
            outlinePaint = new Paint(paint);
            outlinePaint.setLetterSpacing(0);
        }
        GeneralPath outline = new GeneralPath();
        Path path = new Path();
        outlinePaint.getTextPath(cluster, 0, cluster.length(), 0, 0, path);
        appendAndroidPath(path, outline);
        return outline;
    }

    /** Appends an Android path to {@code dst}, conics as quadratics; takes the fill type over. */
    public static void appendAndroidPath(Path src, GeneralPath dst) {
        if (src.getFillType() == Path.FillType.EVEN_ODD) dst.setWindingRule(GeneralPath.WIND_EVEN_ODD);
        PathIterator segments = new PathIterator(src, PathIterator.ConicEvaluation.AsQuadratics, 0.1f);
        float[] points = new float[8];
        while (segments.hasNext()) {
            switch (segments.next(points, 0)) {
                case Move: dst.moveTo(points[0], points[1]); break;
                case Line: dst.lineTo(points[2], points[3]); break;
                case Quadratic: dst.quadTo(points[2], points[3], points[4], points[5]); break;
                case Cubic: dst.curveTo(points[2], points[3], points[4], points[5], points[6], points[7]); break;
                case Close: dst.closePath(); break;
                case Done: break;
                default: throw new IllegalStateException("Unexpected glyph segment");
            }
        }
    }
}
