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

/** A character index plus the edge (leading or trailing) a caret hit falls on. */
public final class TextHitInfo {
    private final int charIndex;
    private final boolean isLeadingEdge;

    private TextHitInfo(int charIndex, boolean isLeadingEdge) {
        this.charIndex = charIndex;
        this.isLeadingEdge = isLeadingEdge;
    }

    public int getCharIndex() {
        return charIndex;
    }

    public boolean isLeadingEdge() {
        return isLeadingEdge;
    }

    public int getInsertionIndex() {
        return isLeadingEdge ? charIndex : charIndex + 1;
    }

    @Override
    public int hashCode() {
        return charIndex;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof TextHitInfo) && equals((TextHitInfo) obj);
    }

    public boolean equals(TextHitInfo hitInfo) {
        return hitInfo != null && charIndex == hitInfo.charIndex && isLeadingEdge == hitInfo.isLeadingEdge;
    }

    @Override
    public String toString() {
        return "TextHitInfo[" + charIndex + (isLeadingEdge ? "L" : "T") + "]";
    }

    public static TextHitInfo leading(int charIndex) {
        return new TextHitInfo(charIndex, true);
    }

    public static TextHitInfo trailing(int charIndex) {
        return new TextHitInfo(charIndex, false);
    }

    public static TextHitInfo beforeOffset(int offset) {
        return new TextHitInfo(offset - 1, false);
    }

    public static TextHitInfo afterOffset(int offset) {
        return new TextHitInfo(offset, true);
    }

    public TextHitInfo getOtherHit() {
        return isLeadingEdge ? trailing(charIndex - 1) : leading(charIndex + 1);
    }

    public TextHitInfo getOffsetHit(int delta) {
        return new TextHitInfo(charIndex + delta, isLeadingEdge);
    }
}
