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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import net.runelite.awt.impl.AndroidGlyphFace;
import net.runelite.awt.impl.BufferedImageFontMetrics;

/**
 * Single-font text layout in baseline-relative user coordinates.
 * Uses the existing Android text path backend; attributed runs, bidi layout,
 * justification and caret navigation are not implemented here.
 */
public final class TextLayout {
    private final GeneralPath outline = new GeneralPath();
    private final LineMetrics metrics;
    private final int characterCount;
    private final float advance;
    private final float visibleAdvance;

    public TextLayout(String text, Font font, FontRenderContext context) {
        if (text == null || text.isEmpty() || font == null || context == null)
            throw new IllegalArgumentException("Text, font and render context are required");
        Paint paint = BufferedImageFontMetrics.paintFor(font);
        paint.setAntiAlias(context.isAntiAliased());
        paint.setSubpixelText(context.usesFractionalMetrics());
        characterCount = text.length();
        metrics = font.getLineMetrics(text, context);
        int visibleLimit = text.length();
        while (visibleLimit > 0 && Character.isWhitespace(text.codePointBefore(visibleLimit))) {
            visibleLimit -= Character.charCount(text.codePointBefore(visibleLimit));
        }
        Point2D.Float full = new Point2D.Float(paint.measureText(text), 0);
        Point2D.Float visible = new Point2D.Float(paint.measureText(text, 0, visibleLimit), 0);
        if (font.isTransformed()) {
            font.getTransform().deltaTransform(full, full);
            font.getTransform().deltaTransform(visible, visible);
        }
        advance = full.x;
        visibleAdvance = visible.x;
        Path glyphs = new Path();
        paint.getTextPath(text, 0, text.length(), 0, 0, glyphs);
        AndroidGlyphFace.appendAndroidPath(glyphs, outline);
        if (font.isTransformed()) outline.transform(font.getTransform());
    }

    public int getCharacterCount() { return characterCount; }
    public float getAdvance() { return advance; }
    public float getVisibleAdvance() { return visibleAdvance; }
    public float getAscent() { return metrics.getAscent(); }
    public float getDescent() { return metrics.getDescent(); }
    public float getLeading() { return metrics.getLeading(); }
    public byte getBaseline() { return (byte)metrics.getBaselineIndex(); }
    public float[] getBaselineOffsets() { return metrics.getBaselineOffsets().clone(); }

    public Rectangle2D getBounds() {
        return outline.getBounds2D();
    }

    public void draw(Graphics2D graphics, float x, float y) {
        if (graphics == null) throw new IllegalArgumentException("graphics == null");
        graphics.fill(getOutline(AffineTransform.getTranslateInstance(x, y)));
    }

    public Shape getOutline(AffineTransform transform) {
        return outline.createTransformedShape(transform);
    }
}
