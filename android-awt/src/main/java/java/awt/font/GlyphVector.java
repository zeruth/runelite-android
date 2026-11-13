/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.awt.font;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * A <code>GlyphVector</code> object is a collection of glyphs
 * containing geometric information for the placement of each glyph
 * in a transformed coordinate space which corresponds to the
 * device on which the <code>GlyphVector</code> is ultimately
 * displayed.
 * <p>
 * On Android the concrete vector returned by {@link Font#createGlyphVector}
 * is {@code net.runelite.awt.impl.AndroidGlyphVector}.
 */
public abstract class GlyphVector implements Cloneable {

    //
    // methods associated with creation-time state
    //

    /** Returns the <code>Font</code> associated with this <code>GlyphVector</code>. */
    public abstract Font getFont();

    /** Returns the {@link FontRenderContext} associated with this <code>GlyphVector</code>. */
    public abstract FontRenderContext getFontRenderContext();

    //
    // methods associated with the GlyphVector as a whole
    //

    /**
     * Assigns default positions to each glyph in this <code>GlyphVector</code>.
     * This can destroy information generated during initial layout of this
     * <code>GlyphVector</code>.
     */
    public abstract void performDefaultLayout();

    /** Returns the number of glyphs in this <code>GlyphVector</code>. */
    public abstract int getNumGlyphs();

    /**
     * Returns the glyphcode of the specified glyph.
     * @throws IndexOutOfBoundsException if <code>glyphIndex</code> is out of range
     */
    public abstract int getGlyphCode(int glyphIndex);

    /**
     * Returns an array of glyphcodes for the specified glyphs.
     * @throws IndexOutOfBoundsException if the range is out of bounds
     * @throws IllegalArgumentException if <code>numEntries</code> is negative
     */
    public abstract int[] getGlyphCodes(int beginGlyphIndex, int numEntries,
                                        int[] codeReturn);

    /**
     * Returns the character index of the specified glyph.  The default
     * implementation assumes a one-to-one, left-to-right mapping of glyphs
     * to characters.
     * @since 1.4
     */
    public int getGlyphCharIndex(int glyphIndex) {
        return glyphIndex;
    }

    /**
     * Returns the character indices of the specified glyphs.
     * @since 1.4
     */
    public int[] getGlyphCharIndices(int beginGlyphIndex, int numEntries,
                                     int[] codeReturn) {
        if (codeReturn == null) {
            codeReturn = new int[numEntries];
        }
        for (int i = 0, j = beginGlyphIndex; i < numEntries; ++i, ++j) {
            codeReturn[i] = getGlyphCharIndex(j);
        }
        return codeReturn;
    }

    /**
     * Returns the logical bounds of this <code>GlyphVector</code>.
     * This method is used when positioning this <code>GlyphVector</code>
     * in relation to visually adjacent <code>GlyphVector</code> objects.
     */
    public abstract Rectangle2D getLogicalBounds();

    /** Returns the visual bounds of this <code>GlyphVector</code>. */
    public abstract Rectangle2D getVisualBounds();

    /**
     * Returns the pixel bounds of this <code>GlyphVector</code> when
     * rendered in a graphics with the given <code>FontRenderContext</code>
     * at the given location.
     * @since 1.4
     */
    public Rectangle getPixelBounds(FontRenderContext renderFRC, float x, float y) {
        Rectangle2D rect = getVisualBounds();
        int l = (int)Math.floor(rect.getX() + x);
        int t = (int)Math.floor(rect.getY() + y);
        int r = (int)Math.floor(rect.getMaxX() + x);
        int b = (int)Math.floor(rect.getMaxY() + y);
        return new Rectangle(l, t, r - l, b - t);
    }

    /** Returns a <code>Shape</code> whose interior corresponds to the visual representation of this <code>GlyphVector</code>. */
    public abstract Shape getOutline();

    /**
     * Returns a <code>Shape</code> whose interior corresponds to the
     * visual representation of this <code>GlyphVector</code> when
     * rendered at x,&nbsp;y.
     */
    public abstract Shape getOutline(float x, float y);

    /** Returns a <code>Shape</code> whose interior corresponds to the visual representation of the specified glyph. */
    public abstract Shape getGlyphOutline(int glyphIndex);

    /**
     * Returns a <code>Shape</code> whose interior corresponds to the
     * visual representation of the specified glyph offset by x,&nbsp;y.
     * @since 1.4
     */
    public Shape getGlyphOutline(int glyphIndex, float x, float y) {
        Shape s = getGlyphOutline(glyphIndex);
        AffineTransform at = AffineTransform.getTranslateInstance(x, y);
        return at.createTransformedShape(s);
    }

    /**
     * Returns the position of the specified glyph relative to the origin of
     * this <code>GlyphVector</code>.  If <code>glyphIndex</code> equals the
     * number of glyphs, this returns the position after the last glyph.
     */
    public abstract Point2D getGlyphPosition(int glyphIndex);

    /** Sets the position of the specified glyph within this <code>GlyphVector</code>. */
    public abstract void setGlyphPosition(int glyphIndex, Point2D newPos);

    /** Returns the transform of the specified glyph, or <code>null</code> when it has none. */
    public abstract AffineTransform getGlyphTransform(int glyphIndex);

    /** Sets the transform of the specified glyph; <code>null</code> or identity resets it. */
    public abstract void setGlyphTransform(int glyphIndex, AffineTransform newTX);

    /**
     * Returns flags describing the global state of the GlyphVector.
     * The default implementation returns 0.
     * @since 1.4
     */
    public int getLayoutFlags() {
        return 0;
    }

    /** A flag used with getLayoutFlags that indicates that this <code>GlyphVector</code> has per-glyph transforms. @since 1.4 */
    public static final int FLAG_HAS_TRANSFORMS = 1;

    /** A flag used with getLayoutFlags that indicates that this <code>GlyphVector</code> has position adjustments. @since 1.4 */
    public static final int FLAG_HAS_POSITION_ADJUSTMENTS = 2;

    /** A flag used with getLayoutFlags that indicates that this <code>GlyphVector</code> has a right-to-left run direction. @since 1.4 */
    public static final int FLAG_RUN_RTL = 4;

    /** A flag used with getLayoutFlags that indicates that this <code>GlyphVector</code> has a complex glyph-to-char mapping. @since 1.4 */
    public static final int FLAG_COMPLEX_GLYPHS = 8;

    /** A mask for supported flags from getLayoutFlags. @since 1.4 */
    public static final int FLAG_MASK =
        FLAG_HAS_TRANSFORMS |
        FLAG_HAS_POSITION_ADJUSTMENTS |
        FLAG_RUN_RTL |
        FLAG_COMPLEX_GLYPHS;

    /**
     * Returns an array of glyph positions for the specified glyphs, as
     * x,&nbsp;y pairs.  The range may include the position after the last glyph.
     */
    public abstract float[] getGlyphPositions(int beginGlyphIndex, int numEntries,
                                              float[] positionReturn);

    /** Returns the logical bounds of the specified glyph. */
    public abstract Shape getGlyphLogicalBounds(int glyphIndex);

    /** Returns the visual bounds of the specified glyph. */
    public abstract Shape getGlyphVisualBounds(int glyphIndex);

    /**
     * Returns the pixel bounds of the glyph at index when this
     * <code>GlyphVector</code> is rendered in a <code>Graphics</code> with the
     * given <code>FontRenderContext</code> at the given location.
     * @since 1.4
     */
    public Rectangle getGlyphPixelBounds(int index, FontRenderContext renderFRC, float x, float y) {
        Rectangle2D rect = getGlyphVisualBounds(index).getBounds2D();
        int l = (int)Math.floor(rect.getX() + x);
        int t = (int)Math.floor(rect.getY() + y);
        int r = (int)Math.floor(rect.getMaxX() + x);
        int b = (int)Math.floor(rect.getMaxY() + y);
        return new Rectangle(l, t, r - l, b - t);
    }

    /** Returns the metrics of the glyph at the specified index. */
    public abstract GlyphMetrics getGlyphMetrics(int glyphIndex);

    /** Returns the justification information for the glyph at the specified index. */
    public abstract GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex);

    //
    // general utility methods
    //

    /** Tests if the specified <code>GlyphVector</code> exactly equals this <code>GlyphVector</code>. */
    public abstract boolean equals(GlyphVector set);
}
