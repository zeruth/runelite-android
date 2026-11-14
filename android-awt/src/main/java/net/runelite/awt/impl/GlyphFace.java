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

import java.awt.geom.GeneralPath;

/**
 * The glyph data {@link AndroidGlyphVector} is built from, for one font under one
 * {@code FontRenderContext}. {@link FontBackend#glyphFace} hands it out: on Android it is
 * {@code AndroidGlyphFace} over Paint and Typeface, so the vector itself stays free of
 * {@code android.*} and the host contracts run it over a face of their own.
 *
 * <p>Every length is in user space before the font's transform, which the vector applies.
 */
public interface GlyphFace {
    /** Ascent above the baseline, positive. */
    float ascent();

    float descent();

    float leading();

    /**
     * The advance of each char of {@code text[start, start + count)} as the run is laid out
     * in its context {@code text[contextStart, contextStart + contextCount)}, kerning and
     * tracking included, into {@code out[0, count)}. A surrogate pair may put its advance
     * on either half; the vector moves it onto the high one.
     */
    void advances(char[] text, int start, int count, int contextStart, int contextCount,
                  boolean rtl, float[] out);

    /** Advance of the characters of one glyph laid out alone. */
    float advance(String cluster);

    /** Glyph code of {@code codePoint}; see the implementation for what the codes are. */
    int glyphCode(int codePoint);

    /** Inverse of {@link #glyphCode}, or -1 when the code is unknown. */
    int codePointForGlyph(int glyphCode);

    /**
     * A fresh outline of the characters of one glyph with its origin on the baseline,
     * tracking excluded (tracking only spaces glyphs apart).
     */
    GeneralPath outline(String cluster);
}
