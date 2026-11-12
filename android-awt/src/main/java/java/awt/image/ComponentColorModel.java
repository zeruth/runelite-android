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

import java.awt.color.ColorSpace;

public class ComponentColorModel extends ColorModel {
    private final int[] sizes;
    public ComponentColorModel(ColorSpace space, boolean alpha, boolean premultiplied, int transparency, int type) {
        this(space, defaultSizes(space.getNumComponents() + (alpha ? 1 : 0), type), alpha, premultiplied, transparency, type);
    }
    public ComponentColorModel(ColorSpace space, int[] bits, boolean alpha, boolean premultiplied, int transparency, int type) {
        super(sum(bits), bits, space, alpha, premultiplied, transparency, type);
        if (bits.length != getNumComponents()) throw new IllegalArgumentException("Component sizes");
        sizes = bits.clone();
    }
    private static int sum(int[] bits) { int sum = 0; for (int bit : bits) sum += bit; return sum; }
    private static int[] defaultSizes(int count, int type) { int[] bits = new int[count]; java.util.Arrays.fill(bits, type == DataBuffer.TYPE_BYTE ? 8 : type == DataBuffer.TYPE_INT ? 32 : 16); return bits; }
    public int getRed(int pixel) { return getRGB(one(pixel)) >>> 16 & 255; }
    public int getGreen(int pixel) { return getRGB(one(pixel)) >>> 8 & 255; }
    public int getBlue(int pixel) { return getRGB(one(pixel)) & 255; }
    public int getAlpha(int pixel) { return getRGB(one(pixel)) >>> 24; }
    private Object one(int pixel) {
        if (getNumComponents() != 1) throw new IllegalArgumentException("Pixel has multiple components");
        Object result = array(null); SampleModel.transferSet(result, 0, pixel); return result;
    }
    private Object array(Object out) {
        if (out != null) return out;
        if (transferType == DataBuffer.TYPE_BYTE) return new byte[getNumComponents()];
        if (transferType == DataBuffer.TYPE_USHORT) return new short[getNumComponents()];
        if (transferType == DataBuffer.TYPE_INT) return new int[getNumComponents()];
        throw net.runelite.awt.AwtUnsupported.fail("java.awt.image.ComponentColorModel", "TYPE_FLOAT and TYPE_DOUBLE transfer types");
    }
    private double max(int component) { return Math.pow(2, sizes[component]) - 1; }
    @Override public int getRGB(Object data) {
        int colors = getNumColorComponents();
        double alpha = hasAlpha() ? SampleModel.transferGet(data, colors) / max(colors) : 1;
        float[] components = new float[colors];
        for (int i = 0; i < colors; i++) {
            double value = SampleModel.transferGet(data, i) / max(i);
            if (isAlphaPremultiplied()) value = alpha == 0 ? 0 : value / alpha;
            components[i] = (float) Math.max(0, Math.min(1, value));
        }
        float[] rgb = getColorSpace().toRGB(components);
        return (int)Math.round(alpha * 255) << 24 | channel(rgb[0]) << 16 | channel(rgb[1]) << 8 | channel(rgb[2]);
    }
    private static int channel(float value) { return Math.max(0, Math.min(255, Math.round(value * 255))); }
    @Override public Object getDataElements(int rgb, Object out) {
        Object data = array(out); int colors = getNumColorComponents();
        double alpha = (rgb >>> 24) / 255d;
        float[] components = getColorSpace().fromRGB(new float[]{(rgb >>> 16 & 255) / 255f, (rgb >>> 8 & 255) / 255f, (rgb & 255) / 255f});
        for (int i = 0; i < colors; i++) SampleModel.transferSet(data, i, (int)Math.round(components[i] * (isAlphaPremultiplied() ? alpha : 1) * max(i)));
        if (hasAlpha()) SampleModel.transferSet(data, colors, (int)Math.round(alpha * max(colors)));
        return data;
    }
    @Override public SampleModel createCompatibleSampleModel(int width, int height) {
        int[] offsets = new int[getNumComponents()]; for (int i = 0; i < offsets.length; i++) offsets[i] = i;
        return new PixelInterleavedSampleModel(transferType, width, height, offsets.length, width * offsets.length, offsets);
    }
    @Override public boolean isCompatibleRaster(Raster raster) { return raster.getTransferType() == transferType && raster.getNumBands() == getNumComponents(); }

    @Override public int[] getComponents(int pixel, int[] components, int offset) {
        if (getNumComponents() > 1) throw new IllegalArgumentException("More than one component per pixel");
        if (components == null) components = new int[offset + 1];
        components[offset] = pixel & ((1 << sizes[0]) - 1);
        return components;
    }

    @Override public int[] getComponents(Object pixel, int[] components, int offset) {
        int n = getNumComponents();
        if (components == null) components = new int[offset + n];
        for (int i = 0; i < n; i++) components[offset + i] = SampleModel.transferGet(pixel, i);
        return components;
    }

    @Override public int getDataElement(int[] components, int offset) {
        if (getNumComponents() == 1) return components[offset];
        throw new IllegalArgumentException("This model returns " + getNumComponents() + " elements in the pixel array.");
    }

    @Override public Object getDataElements(int[] components, int offset, Object obj) {
        Object data = array(obj);
        for (int i = 0; i < getNumComponents(); i++) SampleModel.transferSet(data, i, components[offset + i]);
        return data;
    }

    /*
     * OpenJDK 8u's "non-standard scale": integral samples of a colour space whose
     * range is not 0..1 (CIEXYZ, Lab) map linearly onto getMinValue..getMaxValue.
     */
    private boolean nonStdScale() {
        ColorSpace cs = getColorSpace();
        for (int i = 0; i < getNumColorComponents(); i++) {
            if (cs.getMinValue(i) != 0f || cs.getMaxValue(i) != 1f) return true;
        }
        return false;
    }

    @Override public float[] getNormalizedComponents(int[] components, int offset, float[] normComponents, int normOffset) {
        normComponents = super.getNormalizedComponents(components, offset, normComponents, normOffset);
        if (nonStdScale()) {
            ColorSpace cs = getColorSpace();
            for (int i = 0; i < getNumColorComponents(); i++) {
                float min = cs.getMinValue(i);
                normComponents[normOffset + i] = min + normComponents[normOffset + i] * (cs.getMaxValue(i) - min);
            }
        }
        return normComponents;
    }

    @Override public int[] getUnnormalizedComponents(float[] normComponents, int normOffset, int[] components, int offset) {
        if (nonStdScale()) {
            ColorSpace cs = getColorSpace();
            float[] unit = normComponents.clone();
            for (int i = 0; i < getNumColorComponents(); i++) {
                float min = cs.getMinValue(i);
                float v = (unit[normOffset + i] - min) / (cs.getMaxValue(i) - min);
                unit[normOffset + i] = Math.max(0f, Math.min(1f, v));
            }
            normComponents = unit;
        }
        return super.getUnnormalizedComponents(normComponents, normOffset, components, offset);
    }
}
