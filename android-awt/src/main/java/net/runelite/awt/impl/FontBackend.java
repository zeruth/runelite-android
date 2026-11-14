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

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;

/**
 * Glyph side of {@link java.awt.Font}: everything that needs a real face (Paint and
 * Typeface on Android). {@code Font} only reaches it through {@link #get()}, which loads
 * {@code AndroidFontBackend} by name. The indirection keeps {@code Font} free of
 * {@code android.*}, so the host contract tests compile and patch the shadow {@code Font}
 * over the JDK's; on the host the Android class is absent and every member here throws.
 * {@code consumer-rules.pro} keeps the Android class's name for R8.
 */
public abstract class FontBackend {
    private static final String ANDROID = "net.runelite.awt.impl.AndroidFontBackend";
    private static volatile FontBackend instance;

    protected FontBackend() {}

    public static FontBackend get() {
        FontBackend b = instance;
        if (b == null) {
            synchronized (FontBackend.class) {
                b = instance;
                if (b == null) {
                    b = load();
                    instance = b;
                }
            }
        }
        return b;
    }

    /**
     * Replaces the backend; for the host contracts, which have no Android face and run the
     * shadow's glyph code over a face of their own. Nothing on Android calls it.
     */
    public static void install(FontBackend backend) {
        synchronized (FontBackend.class) {
            instance = backend;
        }
    }

    private static FontBackend load() {
        try {
            return (FontBackend) Class.forName(ANDROID).getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            return new Unavailable();
        }
    }

    /** Registers a TrueType stream; returns the family it was registered under, or null. */
    public abstract String register(InputStream in) throws IOException;

    public abstract boolean canDisplay(Font font, int codePoint);

    public abstract int numGlyphs(Font font);

    public abstract float italicAngle(Font font);

    public abstract Rectangle2D stringBounds(Font font, String text);

    public abstract Rectangle2D maxCharBounds(Font font);

    public abstract LineMetrics lineMetrics(Font font, int numChars);

    /** The glyph data GlyphVectors of {@code font} under {@code frc} are built from. */
    public abstract GlyphFace glyphFace(Font font, FontRenderContext frc);

    /** Off Android (the host contract runs) there is no face to measure. */
    private static final class Unavailable extends FontBackend {
        private static UnsupportedOperationException none(String member) {
            return new UnsupportedOperationException("Font." + member + ": no font backend on this platform");
        }

        @Override public String register(InputStream in) { throw none("createFont"); }
        @Override public boolean canDisplay(Font font, int codePoint) { throw none("canDisplay"); }
        @Override public int numGlyphs(Font font) { throw none("getNumGlyphs"); }
        @Override public float italicAngle(Font font) { throw none("getItalicAngle"); }
        @Override public Rectangle2D stringBounds(Font font, String text) { throw none("getStringBounds"); }
        @Override public Rectangle2D maxCharBounds(Font font) { throw none("getMaxCharBounds"); }
        @Override public LineMetrics lineMetrics(Font font, int numChars) { throw none("getLineMetrics"); }
        @Override public GlyphFace glyphFace(Font font, FontRenderContext frc) { throw none("createGlyphVector"); }
    }
}
