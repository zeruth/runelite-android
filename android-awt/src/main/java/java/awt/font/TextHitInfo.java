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

/**
 * The {@code TextHitInfo} class represents a character position in a
 * text model, and a <b>bias</b>, or "side," of the character.  Biases are
 * either <EM>leading</EM> (the left edge, for a left-to-right character)
 * or <EM>trailing</EM> (the right edge, for a left-to-right character).
 * Instances of {@code TextHitInfo} are used to specify caret and
 * insertion positions within text.
 */
public final class TextHitInfo {
    private int charIndex;
    private boolean isLeadingEdge;

    private TextHitInfo(int charIndex, boolean isLeadingEdge) {
        this.charIndex = charIndex;
        this.isLeadingEdge = isLeadingEdge;
    }

    /** Returns the index of the character hit. */
    public int getCharIndex() {
        return charIndex;
    }

    /** Returns {@code true} if the leading edge of the character was hit. */
    public boolean isLeadingEdge() {
        return isLeadingEdge;
    }

    /**
     * Returns the insertion index.  This is the character index if
     * the leading edge of the character was hit, and one greater
     * than the character index if the trailing edge was hit.
     */
    public int getInsertionIndex() {
        return isLeadingEdge ? charIndex : charIndex + 1;
    }

    /** Returns the hash code, which is the character index. */
    public int hashCode() {
        return charIndex;
    }

    /**
     * Returns {@code true} if the specified {@code Object} is a
     * {@code TextHitInfo} and equals this {@code TextHitInfo}.
     */
    public boolean equals(Object obj) {
        return (obj instanceof TextHitInfo) && equals((TextHitInfo)obj);
    }

    /**
     * Returns {@code true} if the specified {@code TextHitInfo}
     * has the same {@code charIndex} and {@code isLeadingEdge}
     * as this {@code TextHitInfo}.
     */
    public boolean equals(TextHitInfo hitInfo) {
        return hitInfo != null && charIndex == hitInfo.charIndex &&
            isLeadingEdge == hitInfo.isLeadingEdge;
    }

    /** Returns a {@code String} representing the hit for debugging use only. */
    public String toString() {
        return "TextHitInfo[" + charIndex + (isLeadingEdge ? "L" : "T")+"]";
    }

    /** Creates a {@code TextHitInfo} on the leading edge of the character at {@code charIndex}. */
    public static TextHitInfo leading(int charIndex) {
        return new TextHitInfo(charIndex, true);
    }

    /** Creates a hit on the trailing edge of the character at {@code charIndex}. */
    public static TextHitInfo trailing(int charIndex) {
        return new TextHitInfo(charIndex, false);
    }

    /**
     * Creates a {@code TextHitInfo} at the specified offset, associated with
     * the character before the offset.
     */
    public static TextHitInfo beforeOffset(int offset) {
        return new TextHitInfo(offset-1, false);
    }

    /**
     * Creates a {@code TextHitInfo} at the specified offset, associated with
     * the character after the offset.
     */
    public static TextHitInfo afterOffset(int offset) {
        return new TextHitInfo(offset, true);
    }

    /**
     * Creates a {@code TextHitInfo} on the other side of the insertion point.
     * This {@code TextHitInfo} remains unchanged.
     */
    public TextHitInfo getOtherHit() {
        if (isLeadingEdge) {
            return trailing(charIndex - 1);
        } else {
            return leading(charIndex + 1);
        }
    }

    /**
     * Creates a {@code TextHitInfo} whose character index is offset
     * by {@code delta} from the {@code charIndex} of this
     * {@code TextHitInfo}, with the same bias.
     */
    public TextHitInfo getOffsetHit(int delta) {
        return new TextHitInfo(charIndex + delta, isLeadingEdge);
    }
}
