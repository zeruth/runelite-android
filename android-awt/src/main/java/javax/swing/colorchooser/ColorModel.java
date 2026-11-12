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
package javax.swing.colorchooser;

/**
 * A color space shown by {@link ColorChooserPanel}: named components with
 * integer ranges, converted to and from packed ARGB. The last component is
 * always alpha (0-255). Mirrors the JDK's package-private ColorModel family
 * (RGB, HSV, HSL, CMYK) that backs the chooser's tabs.
 */
class ColorModel {

    private final String name;
    private final String[] labels;
    private final int[] maxima;

    ColorModel() {
        this("RGB", new String[] {"Red", "Green", "Blue", "Alpha"}, new int[] {255, 255, 255, 255});
    }

    ColorModel(String name, String[] labels, int[] maxima) {
        this.name = name;
        this.labels = labels;
        this.maxima = maxima;
    }

    String getName() {
        return name;
    }

    int getCount() {
        return labels.length;
    }

    String getLabel(int index) {
        return labels[index];
    }

    int getMinimum(int index) {
        return 0;
    }

    int getMaximum(int index) {
        return maxima[index];
    }

    /** Fills {@code values} with the components of {@code argb}. */
    void setColor(int argb, int[] values) {
        values[0] = (argb >> 16) & 0xFF;
        values[1] = (argb >> 8) & 0xFF;
        values[2] = argb & 0xFF;
        values[3] = (argb >>> 24);
    }

    /** Packs {@code values} back into ARGB. */
    int getColor(int[] values) {
        return (clamp(values[3], 255) << 24) | (clamp(values[0], 255) << 16)
                | (clamp(values[1], 255) << 8) | clamp(values[2], 255);
    }

    static int clamp(int value, int max) {
        return value < 0 ? 0 : value > max ? max : value;
    }

    static int rgb(float r, float g, float b) {
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    static final class HSV extends ColorModel {
        HSV() {
            super("HSV", new String[] {"Hue", "Saturation", "Value", "Transparency"},
                  new int[] {360, 100, 100, 255});
        }

        void setColor(int argb, int[] values) {
            float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
            values[0] = Math.round(hsb[0] * 360f) % 360;
            values[1] = Math.round(hsb[1] * 100f);
            values[2] = Math.round(hsb[2] * 100f);
            values[3] = argb >>> 24;
        }

        int getColor(int[] values) {
            int rgb = java.awt.Color.HSBtoRGB(values[0] / 360f, clamp(values[1], 100) / 100f,
                                              clamp(values[2], 100) / 100f);
            return (clamp(values[3], 255) << 24) | (rgb & 0xFFFFFF);
        }
    }

    static final class HSL extends ColorModel {
        HSL() {
            super("HSL", new String[] {"Hue", "Saturation", "Lightness", "Transparency"},
                  new int[] {360, 100, 100, 255});
        }

        void setColor(int argb, int[] values) {
            float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
            float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            float l = (max + min) / 2f, h = 0f, s = 0f;
            if (max != min) {
                float d = max - min;
                s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
                h = max == r ? (g - b) / d + (g < b ? 6f : 0f) : max == g ? (b - r) / d + 2f : (r - g) / d + 4f;
                h /= 6f;
            }
            values[0] = Math.round(h * 360f) % 360;
            values[1] = Math.round(s * 100f);
            values[2] = Math.round(l * 100f);
            values[3] = argb >>> 24;
        }

        int getColor(int[] values) {
            float h = (values[0] % 360) / 360f, s = clamp(values[1], 100) / 100f, l = clamp(values[2], 100) / 100f;
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s, p = 2f * l - q;
            int rgb = rgb(hue(p, q, h + 1f / 3f), hue(p, q, h), hue(p, q, h - 1f / 3f));
            return (clamp(values[3], 255) << 24) | rgb;
        }

        private static float hue(float p, float q, float t) {
            if (t < 0f) t += 1f;
            if (t > 1f) t -= 1f;
            if (t < 1f / 6f) return p + (q - p) * 6f * t;
            if (t < 1f / 2f) return q;
            if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
            return p;
        }
    }

    static final class CMYK extends ColorModel {
        CMYK() {
            super("CMYK", new String[] {"Cyan", "Magenta", "Yellow", "Black", "Alpha"},
                  new int[] {100, 100, 100, 100, 255});
        }

        void setColor(int argb, int[] values) {
            float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
            float k = 1f - Math.max(r, Math.max(g, b));
            float c = k >= 1f ? 0f : (1f - r - k) / (1f - k);
            float m = k >= 1f ? 0f : (1f - g - k) / (1f - k);
            float y = k >= 1f ? 0f : (1f - b - k) / (1f - k);
            values[0] = Math.round(c * 100f);
            values[1] = Math.round(m * 100f);
            values[2] = Math.round(y * 100f);
            values[3] = Math.round(k * 100f);
            values[4] = argb >>> 24;
        }

        int getColor(int[] values) {
            float k = clamp(values[3], 100) / 100f;
            float r = (1f - clamp(values[0], 100) / 100f) * (1f - k);
            float g = (1f - clamp(values[1], 100) / 100f) * (1f - k);
            float b = (1f - clamp(values[2], 100) / 100f) * (1f - k);
            return (clamp(values[4], 255) << 24) | rgb(r, g, b);
        }
    }
}
