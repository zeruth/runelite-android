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
package java.awt.font;

import android.graphics.Paint;
import android.graphics.Path;
import androidx.graphics.path.PathIterator;
import java.awt.Font;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import net.runelite.awt.impl.BufferedImageFontMetrics;

/** Single-font text outline in baseline-relative user coordinates. */
public final class TextLayout {
    private final GeneralPath outline = new GeneralPath();

    public TextLayout(String text, Font font, FontRenderContext context) {
        if (text == null || text.isEmpty() || font == null || context == null)
            throw new IllegalArgumentException("Text, font and render context are required");
        Paint paint = BufferedImageFontMetrics.paintFor(font);
        paint.setAntiAlias(context.isAntiAliased());
        paint.setSubpixelText(context.usesFractionalMetrics());
        Path glyphs = new Path();
        paint.getTextPath(text, 0, text.length(), 0, 0, glyphs);
        if (glyphs.getFillType() == Path.FillType.EVEN_ODD) outline.setWindingRule(GeneralPath.WIND_EVEN_ODD);
        PathIterator segments = new PathIterator(glyphs, PathIterator.ConicEvaluation.AsQuadratics, 0.1f);
        float[] points = new float[8];
        while (segments.hasNext()) {
            switch (segments.next(points, 0)) {
                case Move: outline.moveTo(points[0], points[1]); break;
                case Line: outline.lineTo(points[2], points[3]); break;
                case Quadratic: outline.quadTo(points[2], points[3], points[4], points[5]); break;
                case Cubic: outline.curveTo(points[2], points[3], points[4], points[5], points[6], points[7]); break;
                case Close: outline.closePath(); break;
                case Done: break;
                default: throw new IllegalStateException("Unexpected glyph segment");
            }
        }
        if (font.isTransformed()) outline.transform(font.getTransform());
    }

    public Shape getOutline(AffineTransform transform) {
        return outline.createTransformedShape(transform);
    }
}
