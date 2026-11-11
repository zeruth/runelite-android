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

import java.awt.color.ColorSpace;
import java.io.Serializable;

public class Color implements Paint, Serializable {
    private static final long serialVersionUID = 118526816881161077L;

    public static final Color white     = new Color(255, 255, 255);
    public static final Color WHITE     = white;
    public static final Color lightGray = new Color(192, 192, 192);
    public static final Color LIGHT_GRAY = lightGray;
    public static final Color gray      = new Color(128, 128, 128);
    public static final Color GRAY      = gray;
    public static final Color darkGray  = new Color(64, 64, 64);
    public static final Color DARK_GRAY = darkGray;
    public static final Color black     = new Color(0, 0, 0);
    public static final Color BLACK     = black;
    public static final Color red       = new Color(255, 0, 0);
    public static final Color RED       = red;
    public static final Color pink      = new Color(255, 175, 175);
    public static final Color PINK      = pink;
    public static final Color orange    = new Color(255, 200, 0);
    public static final Color ORANGE    = orange;
    public static final Color yellow    = new Color(255, 255, 0);
    public static final Color YELLOW    = yellow;
    public static final Color green     = new Color(0, 255, 0);
    public static final Color GREEN     = green;
    public static final Color magenta   = new Color(255, 0, 255);
    public static final Color MAGENTA   = magenta;
    public static final Color cyan      = new Color(0, 255, 255);
    public static final Color CYAN      = cyan;
    public static final Color blue      = new Color(0, 0, 255);
    public static final Color BLUE      = blue;

    private final int value;

    public Color(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public Color(int r, int g, int b, int a) {
        checkRange(r, "Red");
        checkRange(g, "Green");
        checkRange(b, "Blue");
        checkRange(a, "Alpha");
        this.value = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public Color(int rgb) {
        this.value = 0xFF000000 | rgb;
    }

    public Color(int rgba, boolean hasAlpha) {
        this.value = hasAlpha ? rgba : (0xFF000000 | rgba);
    }

    public Color(float r, float g, float b) {
        this((int) (r * 255f + 0.5f), (int) (g * 255f + 0.5f), (int) (b * 255f + 0.5f));
    }

    public Color(float r, float g, float b, float a) {
        this((int) (r * 255f + 0.5f), (int) (g * 255f + 0.5f),
             (int) (b * 255f + 0.5f), (int) (a * 255f + 0.5f));
    }

    public int getRed()   { return (value >> 16) & 0xFF; }
    public int getGreen() { return (value >> 8) & 0xFF; }
    public int getBlue()  { return value & 0xFF; }
    public int getAlpha() { return (value >> 24) & 0xFF; }
    public int getRGB()   { return value; }

    public static Color decode(String nm) throws NumberFormatException {
        Integer intval = Integer.decode(nm);
        int i = intval.intValue();
        return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
    }

    public Color brighter() {
        int r = getRed();
        int g = getGreen();
        int b = getBlue();
        int alpha = getAlpha();
        int i = (int) (1.0 / (1.0 - FACTOR));
        if (r == 0 && g == 0 && b == 0) {
            return new Color(i, i, i, alpha);
        }
        if (r > 0 && r < i) r = i;
        if (g > 0 && g < i) g = i;
        if (b > 0 && b < i) b = i;
        return new Color(Math.min((int) (r / FACTOR), 255),
                         Math.min((int) (g / FACTOR), 255),
                         Math.min((int) (b / FACTOR), 255),
                         alpha);
    }

    public Color darker() {
        return new Color(Math.max((int) (getRed() * FACTOR), 0),
                         Math.max((int) (getGreen() * FACTOR), 0),
                         Math.max((int) (getBlue() * FACTOR), 0),
                         getAlpha());
    }

    public float[] getRGBComponents(float[] compArray) {
        float[] f = (compArray == null) ? new float[4] : compArray;
        f[0] = getRed() / 255f;
        f[1] = getGreen() / 255f;
        f[2] = getBlue() / 255f;
        f[3] = getAlpha() / 255f;
        return f;
    }

    public float[] getRGBColorComponents(float[] compArray) {
        float[] f = (compArray == null) ? new float[3] : compArray;
        f[0] = getRed() / 255f;
        f[1] = getGreen() / 255f;
        f[2] = getBlue() / 255f;
        return f;
    }

    public ColorSpace getColorSpace() {
        return ColorSpace.getInstance(ColorSpace.CS_sRGB);
    }

    public int getTransparency() {
        return getAlpha() == 0xFF ? OPAQUE : TRANSLUCENT;
    }

    public static int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1f - saturation);
            float q = brightness * (1f - saturation * f);
            float t = brightness * (1f - (saturation * (1f - f)));
            switch ((int) h) {
                case 0: r = (int) (brightness * 255f + 0.5f); g = (int) (t * 255f + 0.5f); b = (int) (p * 255f + 0.5f); break;
                case 1: r = (int) (q * 255f + 0.5f); g = (int) (brightness * 255f + 0.5f); b = (int) (p * 255f + 0.5f); break;
                case 2: r = (int) (p * 255f + 0.5f); g = (int) (brightness * 255f + 0.5f); b = (int) (t * 255f + 0.5f); break;
                case 3: r = (int) (p * 255f + 0.5f); g = (int) (q * 255f + 0.5f); b = (int) (brightness * 255f + 0.5f); break;
                case 4: r = (int) (t * 255f + 0.5f); g = (int) (p * 255f + 0.5f); b = (int) (brightness * 255f + 0.5f); break;
                case 5: r = (int) (brightness * 255f + 0.5f); g = (int) (p * 255f + 0.5f); b = (int) (q * 255f + 0.5f); break;
                default:
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static Color getHSBColor(float h, float s, float b) {
        return new Color(HSBtoRGB(h, s, b));
    }

    public static float[] RGBtoHSB(int r, int g, int b, float[] hsbvals) {
        float hue, saturation, brightness;
        if (hsbvals == null) hsbvals = new float[3];
        int cmax = Math.max(r, Math.max(g, b));
        int cmin = Math.min(r, Math.min(g, b));
        brightness = cmax / 255f;
        saturation = (cmax != 0) ? ((float) (cmax - cmin)) / cmax : 0f;
        if (saturation == 0f) {
            hue = 0f;
        } else {
            float redc   = (cmax - r) / (float) (cmax - cmin);
            float greenc = (cmax - g) / (float) (cmax - cmin);
            float bluec  = (cmax - b) / (float) (cmax - cmin);
            if (r == cmax)      hue = bluec - greenc;
            else if (g == cmax) hue = 2f + redc - bluec;
            else                hue = 4f + greenc - redc;
            hue /= 6f;
            if (hue < 0f) hue += 1f;
        }
        hsbvals[0] = hue;
        hsbvals[1] = saturation;
        hsbvals[2] = brightness;
        return hsbvals;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Color && ((Color) obj).value == this.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[r=" + getRed() + ",g=" + getGreen() + ",b=" + getBlue() + "]";
    }

    private static void checkRange(int v, String name) {
        if (v < 0 || v > 255) {
            throw new IllegalArgumentException(name + " value " + v + " outside range 0..255");
        }
    }

    private static final double FACTOR = 0.7;
}
