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

import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.text.CharacterIterator;

/**
 * Rendering metrics of a font. The JDK's base class only defines the measurements in terms of
 * each other (stringWidth through charsWidth and back), so an anonymous
 * {@code new FontMetrics(font) {}} measures nothing useful on desktop; here every base
 * measurement delegates to the Paint-backed {@link net.runelite.awt.impl.BufferedImageFontMetrics}
 * of the same font, so such subclasses report the widths and heights that drawString paints.
 * A subclass that overrides a measurement still wins for that method. The line metrics and
 * string bounds go through {@link Font}, as on the JDK.
 */
public abstract class FontMetrics implements java.io.Serializable {
    private static final long serialVersionUID = 1681126225205050147L;

    private static final FontRenderContext DEFAULT_FRC = new FontRenderContext(null, false, false);

    /**
     * The actual {@link Font} from which the font metrics are created.
     */
    protected Font font;

    /** The measured metrics this base class delegates to; never {@code this}, because
     *  BufferedImageFontMetrics overrides every delegating method. */
    private transient FontMetrics measured;

    protected FontMetrics(Font font) {
        this.font = font;
    }

    private FontMetrics measured() {
        FontMetrics m = measured;
        // font is protected and may be swapped by a subclass after construction.
        if (m == null || m.font != font) {
            m = net.runelite.awt.impl.BufferedImageFontMetrics.forFont(font);
            measured = m;
        }
        return m;
    }

    public Font getFont() {
        return font;
    }

    public FontRenderContext getFontRenderContext() {
        return DEFAULT_FRC;
    }

    public int getLeading() {
        return measured().getLeading();
    }

    public int getAscent() {
        return measured().getAscent();
    }

    public int getDescent() {
        return measured().getDescent();
    }

    public int getHeight() {
        return getLeading() + getAscent() + getDescent();
    }

    public int getMaxAscent() {
        return getAscent();
    }

    public int getMaxDescent() {
        return getDescent();
    }

    /**
     * @deprecated As of JDK version 1.1.1, replaced by {@code getMaxDescent()}.
     */
    @Deprecated
    public int getMaxDecent() {
        return getMaxDescent();
    }

    public int getMaxAdvance() {
        return measured().getMaxAdvance();
    }

    public int charWidth(int codePoint) {
        return measured().charWidth(codePoint);
    }

    public int charWidth(char ch) {
        return measured().charWidth(ch);
    }

    public int stringWidth(String str) {
        return measured().stringWidth(str);
    }

    public int charsWidth(char[] data, int off, int len) {
        return measured().charsWidth(data, off, len);
    }

    public int bytesWidth(byte[] data, int off, int len) {
        return measured().bytesWidth(data, off, len);
    }

    public int[] getWidths() {
        return measured().getWidths();
    }

    public boolean hasUniformLineMetrics() {
        return font.hasUniformLineMetrics();
    }

    public LineMetrics getLineMetrics(String str, Graphics context) {
        return font.getLineMetrics(str, myFRC(context));
    }

    public LineMetrics getLineMetrics(String str, int beginIndex, int limit, Graphics context) {
        return font.getLineMetrics(str, beginIndex, limit, myFRC(context));
    }

    public LineMetrics getLineMetrics(char[] chars, int beginIndex, int limit, Graphics context) {
        return font.getLineMetrics(chars, beginIndex, limit, myFRC(context));
    }

    public LineMetrics getLineMetrics(CharacterIterator ci, int beginIndex, int limit, Graphics context) {
        return font.getLineMetrics(ci, beginIndex, limit, myFRC(context));
    }

    public Rectangle2D getStringBounds(String str, Graphics context) {
        return font.getStringBounds(str, myFRC(context));
    }

    public Rectangle2D getStringBounds(String str, int beginIndex, int limit, Graphics context) {
        return font.getStringBounds(str, beginIndex, limit, myFRC(context));
    }

    public Rectangle2D getStringBounds(char[] chars, int beginIndex, int limit, Graphics context) {
        return font.getStringBounds(chars, beginIndex, limit, myFRC(context));
    }

    public Rectangle2D getStringBounds(CharacterIterator ci, int beginIndex, int limit, Graphics context) {
        return font.getStringBounds(ci, beginIndex, limit, myFRC(context));
    }

    public Rectangle2D getMaxCharBounds(Graphics context) {
        return font.getMaxCharBounds(myFRC(context));
    }

    private FontRenderContext myFRC(Graphics context) {
        if (context instanceof Graphics2D) {
            return ((Graphics2D) context).getFontRenderContext();
        }
        return DEFAULT_FRC;
    }

    public String toString() {
        return getClass().getName() +
            "[font=" + getFont() +
            "ascent=" + getAscent() +
            ", descent=" + getDescent() +
            ", height=" + getHeight() + "]";
    }
}
