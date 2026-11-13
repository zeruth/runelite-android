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
package javax.swing.text;

import java.text.CharacterIterator;

public class Segment implements Cloneable, CharacterIterator, CharSequence {
    public char[] array;
    public int offset;
    public int count;
    private boolean partialReturn;
    private int pos;
    /**
     * The JDK's package-private flag that the array is a scratch copy rather than the content's
     * own buffer; GapContent sets it, so a JDK GapContent running over this Segment needs it.
     */
    boolean copy;

    public Segment() { this(null, 0, 0); }
    public Segment(char[] array, int offset, int count) {
        this.array = array; this.offset = offset; this.count = count;
    }

    public void setPartialReturn(boolean p) { this.partialReturn = p; }
    public boolean isPartialReturn() { return partialReturn; }

    @Override public char first() { pos = offset; return current(); }
    @Override public char last() { pos = offset + count - 1; return current(); }
    @Override public char current() { return (pos >= offset && pos < offset + count && array != null) ? array[pos] : DONE; }
    @Override public char next() { pos++; return current(); }
    @Override public char previous() { pos--; return pos < offset ? DONE : current(); }
    @Override public char setIndex(int p) { pos = p; return current(); }
    @Override public int getBeginIndex() { return offset; }
    @Override public int getEndIndex() { return offset + count; }
    @Override public int getIndex() { return pos; }

    @Override public int length() { return count; }
    @Override public char charAt(int idx) { return array[offset + idx]; }
    @Override public CharSequence subSequence(int s, int e) { return new String(array, offset + s, e - s); }

    @Override public Object clone() {
        try { return super.clone(); } catch (CloneNotSupportedException e) { throw new InternalError(e); }
    }
}
