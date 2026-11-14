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
package java.awt.image;

import java.awt.Transparency;
import java.awt.color.ColorSpace;

/**
 * RGB (and optional alpha) packed into one int, short or byte pixel. Components that
 * are not 8 bits wide are scaled with the JDK's float rounding,
 * {@code (int) (c * 255f / max + 0.5f)}, so a 565 or 555 round trip lands on the same
 * values as on the desktop.
 */
public class DirectColorModel extends PackedColorModel {
    private final int red_mask;
    private final int green_mask;
    private final int blue_mask;
    private final int alpha_mask;

    public DirectColorModel(int bits, int rmask, int gmask, int bmask) {
        this(bits, rmask, gmask, bmask, 0);
    }

    public DirectColorModel(int bits, int rmask, int gmask, int bmask, int amask) {
        this(ColorSpace.getInstance(ColorSpace.CS_sRGB), bits, rmask, gmask, bmask, amask, false,
            getDefaultTransferType(bits));
    }

    public DirectColorModel(ColorSpace space, int bits, int rmask, int gmask, int bmask, int amask,
                            boolean isAlphaPremultiplied, int transferType) {
        super(space, bits, rmask, gmask, bmask, amask, isAlphaPremultiplied,
            amask == 0 ? Transparency.OPAQUE : Transparency.TRANSLUCENT, transferType);
        if (transferType != DataBuffer.TYPE_BYTE && transferType != DataBuffer.TYPE_USHORT
                && transferType != DataBuffer.TYPE_INT) {
            throw new IllegalArgumentException("Bad transfer type: " + transferType);
        }
        red_mask = rmask;
        green_mask = gmask;
        blue_mask = bmask;
        alpha_mask = amask;
    }

    public final int getRedMask()   { return red_mask; }
    public final int getGreenMask() { return green_mask; }
    public final int getBlueMask()  { return blue_mask; }
    public final int getAlphaMask() { return alpha_mask; }

    private int raw(int pixel, int idx) {
        return (pixel & maskArray[idx]) >>> maskOffsets[idx];
    }

    /** The 8-bit, non-premultiplied component of an sRGB pixel. */
    private int srgbComponent(int pixel, int idx) {
        int c = raw(pixel, idx);
        if (isAlphaPremultiplied()) {
            int a = raw(pixel, 3);
            return a == 0 ? 0 : (int) (((c * scaleFactors[idx]) * 255.0f / (a * scaleFactors[3])) + 0.5f);
        }
        return scaleFactors[idx] != 1.0f ? (int) ((c * scaleFactors[idx]) + 0.5f) : c;
    }

    /** Non-sRGB spaces go through the color space with normalized components. */
    private int converted(int pixel, int channel) {
        float[] comps = new float[3];
        float alpha = hasAlpha() ? raw(pixel, 3) / (float) ((1L << getComponentSize(3)) - 1) : 1.0f;
        for (int i = 0; i < 3; i++) {
            comps[i] = raw(pixel, i) / (float) ((1L << getComponentSize(i)) - 1);
            if (isAlphaPremultiplied()) comps[i] = alpha == 0.0f ? 0.0f : comps[i] / alpha;
        }
        float[] rgb = getColorSpace().toRGB(comps);
        return Math.max(0, Math.min(255, (int) (rgb[channel] * 255.0f + 0.5f)));
    }

    public final int getRed(int pixel) {
        return getColorSpace().isCS_sRGB() ? srgbComponent(pixel, 0) : converted(pixel, 0);
    }

    public final int getGreen(int pixel) {
        return getColorSpace().isCS_sRGB() ? srgbComponent(pixel, 1) : converted(pixel, 1);
    }

    public final int getBlue(int pixel) {
        return getColorSpace().isCS_sRGB() ? srgbComponent(pixel, 2) : converted(pixel, 2);
    }

    public final int getAlpha(int pixel) {
        if (!hasAlpha()) {
            return 0xff;
        }
        int a = raw(pixel, 3);
        return scaleFactors[3] != 1.0f ? (int) (a * scaleFactors[3] + 0.5f) : a;
    }

    public final int getRGB(int pixel) {
        return (getAlpha(pixel) << 24) | (getRed(pixel) << 16) | (getGreen(pixel) << 8) | getBlue(pixel);
    }

    public int getRed(Object inData)   { return getRed(singlePixel(inData)); }
    public int getGreen(Object inData) { return getGreen(singlePixel(inData)); }
    public int getBlue(Object inData)  { return getBlue(singlePixel(inData)); }
    public int getAlpha(Object inData) { return getAlpha(singlePixel(inData)); }
    public int getRGB(Object inData)   { return getRGB(singlePixel(inData)); }

    public Object getDataElements(int rgb, Object pixel) {
        int red = (rgb >> 16) & 0xff;
        int grn = (rgb >> 8) & 0xff;
        int blu = rgb & 0xff;
        int value = 0;
        float factor = 1.0f / 255.0f;
        int precision = 8;
        if (!getColorSpace().isCS_sRGB()) {
            float[] comps = getColorSpace().fromRGB(new float[]{red * factor, grn * factor, blu * factor});
            red = Math.round(comps[0] * 255.0f);
            grn = Math.round(comps[1] * 255.0f);
            blu = Math.round(comps[2] * 255.0f);
        }
        if (hasAlpha()) {
            int alp = (rgb >> 24) & 0xff;
            if (isAlphaPremultiplied()) {
                factor *= alp * (1.0f / 255.0f);
                precision = -1;
            }
            int bits = getComponentSize(3);
            if (bits != 8) {
                int max = (1 << bits) - 1;
                alp = Math.min(max, (int) ((alp * (1.0f / 255.0f) * max) + 0.5f));
            }
            value = alp << maskOffsets[3];
        }
        value |= scale(red, 0, factor, precision) << maskOffsets[0];
        value |= scale(grn, 1, factor, precision) << maskOffsets[1];
        value |= scale(blu, 2, factor, precision) << maskOffsets[2];
        return storePixel(value, pixel);
    }

    private int scale(int c, int idx, float factor, int precision) {
        int bits = getComponentSize(idx);
        if (bits == precision) {
            return c;
        }
        int max = (int) ((1L << bits) - 1);
        return Math.min(max, (int) ((c * factor * max) + 0.5f));
    }

    public final int[] getComponents(int pixel, int[] components, int offset) {
        if (components == null) {
            components = new int[offset + getNumComponents()];
        }
        for (int i = 0; i < getNumComponents(); i++) {
            components[offset + i] = raw(pixel, i);
        }
        return components;
    }

    public final int[] getComponents(Object pixel, int[] components, int offset) {
        return getComponents(singlePixel(pixel), components, offset);
    }

    public int getDataElement(int[] components, int offset) {
        int pixel = 0;
        for (int i = 0; i < getNumComponents(); i++) {
            pixel |= (components[offset + i] << maskOffsets[i]) & maskArray[i];
        }
        return pixel;
    }

    public Object getDataElements(int[] components, int offset, Object obj) {
        return storePixel(getDataElement(components, offset), obj);
    }

    public final WritableRaster createCompatibleWritableRaster(int w, int h) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width (" + w + ") and height (" + h + ") cannot be <= 0");
        }
        int[] masks = hasAlpha()
            ? new int[]{red_mask, green_mask, blue_mask, alpha_mask}
            : new int[]{red_mask, green_mask, blue_mask};
        int type = pixel_bits > 16 ? DataBuffer.TYPE_INT : pixel_bits > 8 ? DataBuffer.TYPE_USHORT : DataBuffer.TYPE_BYTE;
        return Raster.createPackedRaster(type, w, h, masks, null);
    }

    public boolean isCompatibleRaster(Raster raster) {
        SampleModel sm = raster.getSampleModel();
        if (!(sm instanceof SinglePixelPackedSampleModel) || sm.getNumBands() != getNumComponents()) {
            return false;
        }
        int[] bitMasks = ((SinglePixelPackedSampleModel) sm).getBitMasks();
        for (int i = 0; i < getNumComponents(); i++) {
            if (bitMasks[i] != maskArray[i]) {
                return false;
            }
        }
        return raster.getTransferType() == transferType;
    }

    public final ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
        if (!hasAlpha() || isAlphaPremultiplied() == isAlphaPremultiplied) {
            return this;
        }
        coerceRaster(raster, isAlphaPremultiplied);
        return new DirectColorModel(getColorSpace(), pixel_bits, red_mask, green_mask, blue_mask, alpha_mask,
            isAlphaPremultiplied, transferType);
    }

    public String toString() {
        return "DirectColorModel: rmask=" + Integer.toHexString(red_mask) + " gmask=" + Integer.toHexString(green_mask)
            + " bmask=" + Integer.toHexString(blue_mask) + " amask=" + Integer.toHexString(alpha_mask);
    }
}
