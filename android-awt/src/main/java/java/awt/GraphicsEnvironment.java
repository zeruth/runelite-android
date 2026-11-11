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

public abstract class GraphicsEnvironment {
    private static GraphicsEnvironment localGE;

    protected GraphicsEnvironment() {}

    public static synchronized GraphicsEnvironment getLocalGraphicsEnvironment() {
        if (localGE == null) localGE = new HeadlessGraphicsEnvironment();
        return localGE;
    }

    public abstract GraphicsDevice[] getScreenDevices();
    public abstract GraphicsDevice getDefaultScreenDevice();
    public abstract Graphics2D createGraphics(java.awt.image.BufferedImage img);
    public abstract Font[] getAllFonts();
    public abstract String[] getAvailableFontFamilyNames();

    public static boolean isHeadless() { return true; }
    public boolean isHeadlessInstance() { return true; }

    public Rectangle getMaximumWindowBounds() {
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        return new Rectangle(0, 0, size.width, size.height);
    }
    public boolean registerFont(Font font) { return true; }

    public Point getCenterPoint() { Rectangle bounds = getMaximumWindowBounds(); return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2); }
    public String[] getAvailableFontFamilyNames(java.util.Locale locale) { java.util.Objects.requireNonNull(locale); return getAvailableFontFamilyNames(); }

    private static final class HeadlessGraphicsEnvironment extends GraphicsEnvironment {
        @Override public GraphicsDevice[] getScreenDevices() { return new GraphicsDevice[]{getDefaultScreenDevice()}; }
        @Override public GraphicsDevice getDefaultScreenDevice() {
            return net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE.getDevice();
        }
        @Override public Graphics2D createGraphics(java.awt.image.BufferedImage img) { return img.createGraphics(); }
        @Override public Font[] getAllFonts() { return new Font[]{new Font(Font.DIALOG, Font.PLAIN, 12)}; }
        @Override public String[] getAvailableFontFamilyNames() {
            return new String[]{Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
        }
    }
}
