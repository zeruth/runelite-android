/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.geom.Rectangle2D;

/**
 * The <code>GlyphMetrics</code> class represents information for a
 * single glyph: its advance, its visual bounds relative to the glyph
 * origin, and its type (standard, ligature, combining, component or
 * whitespace).
 */
public final class GlyphMetrics {
    /** Indicates whether the metrics are for a horizontal or vertical baseline. */
    private boolean horizontal;

    /** The x-component of the advance. */
    private float advanceX;

    /** The y-component of the advance. */
    private float advanceY;

    /** The bounds of the associated glyph. */
    private Rectangle2D.Float bounds;

    /** Additional information about the glyph encoded as a byte. */
    private byte glyphType;

    /** Indicates a glyph that represents a single standard character. */
    public static final byte STANDARD = 0;

    /** Indicates a glyph that represents multiple characters as a ligature. */
    public static final byte LIGATURE = 1;

    /** Indicates a glyph that represents a combining character, such as an umlaut. */
    public static final byte COMBINING = 2;

    /** Indicates a glyph with no corresponding character in the backing store. */
    public static final byte COMPONENT = 3;

    /** Indicates a glyph with no visual representation. */
    public static final byte WHITESPACE = 4;

    /**
     * Constructs a <code>GlyphMetrics</code> object.
     * @param advance the advance width of the glyph
     * @param bounds the black box bounds of the glyph
     * @param glyphType the type of the glyph
     */
    public GlyphMetrics(float advance, Rectangle2D bounds, byte glyphType) {
        this.horizontal = true;
        this.advanceX = advance;
        this.advanceY = 0;
        this.bounds = new Rectangle2D.Float();
        this.bounds.setRect(bounds);
        this.glyphType = glyphType;
    }

    /**
     * Constructs a <code>GlyphMetrics</code> object.
     * @param horizontal if true, metrics are for a horizontal baseline,
     *   otherwise they are for a vertical baseline
     * @param advanceX the X-component of the glyph's advance
     * @param advanceY the Y-component of the glyph's advance
     * @param bounds the visual bounds of the glyph
     * @param glyphType the type of the glyph
     * @since 1.4
     */
    public GlyphMetrics(boolean horizontal, float advanceX, float advanceY,
                        Rectangle2D bounds, byte glyphType) {
        this.horizontal = horizontal;
        this.advanceX = advanceX;
        this.advanceY = advanceY;
        this.bounds = new Rectangle2D.Float();
        this.bounds.setRect(bounds);
        this.glyphType = glyphType;
    }

    /** Returns the advance of the glyph along the baseline (either horizontal or vertical). */
    public float getAdvance() {
        return horizontal ? advanceX : advanceY;
    }

    /** Returns the x-component of the advance of the glyph. @since 1.4 */
    public float getAdvanceX() {
        return advanceX;
    }

    /** Returns the y-component of the advance of the glyph. @since 1.4 */
    public float getAdvanceY() {
        return advanceY;
    }

    /** Returns the bounds of the glyph, relative to the glyph origin. */
    public Rectangle2D getBounds2D() {
        return new Rectangle2D.Float(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /** Returns the left (top) side bearing of the glyph. */
    public float getLSB() {
        return horizontal ? bounds.x : bounds.y;
    }

    /** Returns the right (bottom) side bearing of the glyph. */
    public float getRSB() {
        return horizontal ?
            advanceX - bounds.x - bounds.width :
            advanceY - bounds.y - bounds.height;
    }

    /** Returns the raw glyph type code. */
    public int getType() {
        return glyphType;
    }

    /** Returns <code>true</code> if this is a standard glyph. */
    public boolean isStandard() {
        return (glyphType & 0x3) == STANDARD;
    }

    /** Returns <code>true</code> if this is a ligature glyph. */
    public boolean isLigature() {
        return (glyphType & 0x3) == LIGATURE;
    }

    /** Returns <code>true</code> if this is a combining glyph. */
    public boolean isCombining() {
        return (glyphType & 0x3) == COMBINING;
    }

    /** Returns <code>true</code> if this is a component glyph. */
    public boolean isComponent() {
        return (glyphType & 0x3) == COMPONENT;
    }

    /** Returns <code>true</code> if this is a whitespace glyph. */
    public boolean isWhitespace() {
        return (glyphType & 0x4) == WHITESPACE;
    }
}
