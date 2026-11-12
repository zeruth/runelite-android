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

/**
 * One data element per color or alpha component. Integral transfer types scale by the
 * component's bit size; {@code TYPE_SHORT} by 32767 (negative samples read as 0);
 * {@code TYPE_FLOAT} and {@code TYPE_DOUBLE} hold normalized values directly and have no
 * unnormalized form, as on the desktop.
 */
public class ComponentColorModel extends ColorModel {
    private final boolean integral;

    public ComponentColorModel(ColorSpace colorSpace, int[] bits, boolean hasAlpha, boolean isAlphaPremultiplied,
                               int transparency, int transferType) {
        super(bitsSum(bits, colorSpace, hasAlpha, transferType),
            bitsArray(bits, colorSpace, hasAlpha, transferType), colorSpace, hasAlpha, isAlphaPremultiplied,
            transparency, transferType);
        integral = transferType == DataBuffer.TYPE_BYTE || transferType == DataBuffer.TYPE_USHORT
            || transferType == DataBuffer.TYPE_INT;
    }

    public ComponentColorModel(ColorSpace colorSpace, boolean hasAlpha, boolean isAlphaPremultiplied,
                               int transparency, int transferType) {
        this(colorSpace, null, hasAlpha, isAlphaPremultiplied, transparency, transferType);
    }

    private static int[] bitsArray(int[] bits, ColorSpace space, boolean hasAlpha, int transferType) {
        if (transferType < DataBuffer.TYPE_BYTE || transferType > DataBuffer.TYPE_DOUBLE) {
            throw new IllegalArgumentException("This constructor is not compatible with transferType " + transferType);
        }
        int components = space.getNumComponents() + (hasAlpha ? 1 : 0);
        boolean integral = transferType == DataBuffer.TYPE_BYTE || transferType == DataBuffer.TYPE_USHORT
            || transferType == DataBuffer.TYPE_INT;
        if (bits != null && integral) {
            if (bits.length < components) {
                throw new IllegalArgumentException("Number of bits array elements should be " + components);
            }
            return bits;
        }
        int[] sizes = new int[components];
        java.util.Arrays.fill(sizes, DataBuffer.getDataTypeSize(transferType));
        return sizes;
    }

    private static int bitsSum(int[] bits, ColorSpace space, boolean hasAlpha, int transferType) {
        int sum = 0;
        int[] sizes = bitsArray(bits, space, hasAlpha, transferType);
        for (int i = 0; i < space.getNumComponents() + (hasAlpha ? 1 : 0); i++) sum += sizes[i];
        return sum;
    }

    // ---- normalization -------------------------------------------------------------------

    private float normalized(Object data, int i) {
        switch (transferType) {
            case DataBuffer.TYPE_SHORT: return Math.max(0, ((short[]) data)[i]) / 32767.0f;
            case DataBuffer.TYPE_FLOAT: return ((float[]) data)[i];
            case DataBuffer.TYPE_DOUBLE: return (float) ((double[]) data)[i];
            case DataBuffer.TYPE_INT: return (float) ((((int[]) data)[i] & 0xffffffffL) / (double) ((1L << getComponentSize(i)) - 1));
            default: return SampleModel.transferGet(data, i) / maxComponent(i);
        }
    }

    private void store(Object data, int i, float norm) {
        switch (transferType) {
            case DataBuffer.TYPE_SHORT: ((short[]) data)[i] = (short) (norm * 32767.0f + 0.5f); break;
            case DataBuffer.TYPE_FLOAT: ((float[]) data)[i] = norm; break;
            case DataBuffer.TYPE_DOUBLE: ((double[]) data)[i] = norm; break;
            default: SampleModel.transferSet(data, i, (int) (norm * maxComponent(i) + 0.5f)); break;
        }
    }

    private static int to8(float value) {
        return Math.max(0, Math.min(255, (int) (value * 255.0f + 0.5f)));
    }

    // ---- pixels ------------------------------------------------------------------------

    private Object onePixel(int pixel) {
        if (getNumComponents() > 1) {
            throw new IllegalArgumentException("More than one component per pixel");
        }
        if (!integral) {
            throw new IllegalArgumentException("This method has not been implemented for transferType " + transferType);
        }
        Object data = SampleModel.transferArray(transferType, null, 1);
        SampleModel.transferSet(data, 0, pixel);
        return data;
    }

    public int getRed(int pixel)   { return getRGB(onePixel(pixel)) >>> 16 & 0xff; }
    public int getGreen(int pixel) { return getRGB(onePixel(pixel)) >>> 8 & 0xff; }
    public int getBlue(int pixel)  { return getRGB(onePixel(pixel)) & 0xff; }
    public int getAlpha(int pixel) { return getRGB(onePixel(pixel)) >>> 24; }
    public int getRGB(int pixel)   { return getRGB(onePixel(pixel)); }

    public int getRed(Object inData)   { return getRGB(inData) >>> 16 & 0xff; }
    public int getGreen(Object inData) { return getRGB(inData) >>> 8 & 0xff; }
    public int getBlue(Object inData)  { return getRGB(inData) & 0xff; }
    public int getAlpha(Object inData) { return hasAlpha() ? to8(normalized(inData, getNumColorComponents())) : 0xff; }

    public int getRGB(Object inData) {
        int colors = getNumColorComponents();
        float alpha = hasAlpha() ? normalized(inData, colors) : 1.0f;
        float[] comps = new float[colors];
        for (int i = 0; i < colors; i++) {
            float value = normalized(inData, i);
            if (isAlphaPremultiplied()) value = alpha == 0.0f ? 0.0f : value / alpha;
            comps[i] = Math.max(0.0f, Math.min(1.0f, value));
        }
        int r;
        int g;
        int b;
        if (getColorSpace().isCS_sRGB()) {
            r = to8(comps[0]);
            g = to8(comps[1]);
            b = to8(comps[2]);
        } else {
            float[] rgb = getColorSpace().toRGB(comps);
            r = to8(rgb[0]);
            g = to8(rgb[1]);
            b = to8(rgb[2]);
        }
        return (to8(alpha) << 24) | (r << 16) | (g << 8) | b;
    }

    public Object getDataElements(int rgb, Object pixel) {
        Object data = SampleModel.transferArray(transferType, pixel, getNumComponents());
        int colors = getNumColorComponents();
        float alpha = ((rgb >>> 24) & 0xff) / 255.0f;
        float[] srgb = {((rgb >> 16) & 0xff) / 255.0f, ((rgb >> 8) & 0xff) / 255.0f, (rgb & 0xff) / 255.0f};
        float[] comps = getColorSpace().isCS_sRGB() ? srgb : getColorSpace().fromRGB(srgb);
        float factor = isAlphaPremultiplied() ? alpha : 1.0f;
        for (int i = 0; i < colors; i++) store(data, i, comps[i] * factor);
        if (hasAlpha()) store(data, colors, alpha);
        return data;
    }

    // ---- components --------------------------------------------------------------------

    public int[] getComponents(int pixel, int[] components, int offset) {
        Object data = onePixel(pixel);
        if (components == null) components = new int[offset + 1];
        components[offset] = SampleModel.transferGet(data, 0);
        return components;
    }

    public int[] getComponents(Object pixel, int[] components, int offset) {
        if (!integral) {
            throw new IllegalArgumentException("This method has not been implemented for transferType " + transferType);
        }
        int n = getNumComponents();
        if (components == null) components = new int[offset + n];
        for (int i = 0; i < n; i++) components[offset + i] = SampleModel.transferGet(pixel, i);
        return components;
    }

    public int[] getUnnormalizedComponents(float[] normComponents, int normOffset, int[] components, int offset) {
        if (!integral) {
            throw new IllegalArgumentException("This method has not been implemented for transferType " + transferType);
        }
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

    public float[] getNormalizedComponents(int[] components, int offset, float[] normComponents, int normOffset) {
        if (!integral) {
            throw new IllegalArgumentException("This method has not been implemented for transferType " + transferType);
        }
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

    public float[] getNormalizedComponents(Object pixel, float[] normComponents, int normOffset) {
        int n = getNumComponents();
        if (normComponents == null) normComponents = new float[normOffset + n];
        for (int i = 0; i < n; i++) normComponents[normOffset + i] = normalized(pixel, i);
        if (isAlphaPremultiplied()) {
            float alpha = normComponents[normOffset + getNumColorComponents()];
            for (int i = 0; i < getNumColorComponents(); i++) {
                normComponents[normOffset + i] = alpha == 0.0f ? 0.0f : normComponents[normOffset + i] / alpha;
            }
        }
        return normComponents;
    }

    public int getDataElement(int[] components, int offset) {
        if (getNumComponents() > 1) {
            throw new IllegalArgumentException("This model returns " + getNumComponents() + " elements in the pixel array.");
        }
        if (!integral) {
            throw new IllegalArgumentException("This method has not been implemented for transferType " + transferType);
        }
        return components[offset];
    }

    public Object getDataElements(int[] components, int offset, Object obj) {
        if (!integral) {
            throw new IllegalArgumentException("This method has not been implemented for transferType " + transferType);
        }
        int n = getNumComponents();
        Object data = SampleModel.transferArray(transferType, obj, n);
        for (int i = 0; i < n; i++) SampleModel.transferSet(data, i, components[offset + i]);
        return data;
    }

    public int getDataElement(float[] normComponents, int normOffset) {
        if (getNumComponents() > 1) {
            throw new IllegalArgumentException("More than one component per pixel");
        }
        return SampleModel.transferGet(getDataElements(normComponents, normOffset, null), 0);
    }

    public Object getDataElements(float[] normComponents, int normOffset, Object obj) {
        int n = getNumComponents();
        if (normComponents.length - normOffset < n) {
            throw new IllegalArgumentException("Incorrect number of components.  Expecting " + n);
        }
        Object data = SampleModel.transferArray(transferType, obj, n);
        float alpha = hasAlpha() ? normComponents[normOffset + getNumColorComponents()] : 1.0f;
        for (int i = 0; i < getNumColorComponents(); i++) {
            store(data, i, normComponents[normOffset + i] * (isAlphaPremultiplied() ? alpha : 1.0f));
        }
        if (hasAlpha()) store(data, getNumColorComponents(), alpha);
        return data;
    }

    // ---- rasters -----------------------------------------------------------------------

    public ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
        if (!hasAlpha() || isAlphaPremultiplied() == isAlphaPremultiplied) {
            return this;
        }
        coerceRaster(raster, isAlphaPremultiplied);
        return new ComponentColorModel(getColorSpace(), getComponentSize(), hasAlpha(), isAlphaPremultiplied,
            getTransparency(), transferType);
    }

    public boolean isCompatibleRaster(Raster raster) {
        SampleModel sm = raster.getSampleModel();
        if (!(sm instanceof ComponentSampleModel) || sm.getNumBands() != getNumComponents()) {
            return false;
        }
        for (int i = 0; i < getNumComponents(); i++) {
            if (sm.getSampleSize(i) < getComponentSize(i)) {
                return false;
            }
        }
        return raster.getTransferType() == transferType;
    }

    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        if (transferType == DataBuffer.TYPE_BYTE || transferType == DataBuffer.TYPE_USHORT) {
            return Raster.createInterleavedRaster(transferType, w, h, getNumComponents(), null);
        }
        SampleModel sm = createCompatibleSampleModel(w, h);
        return Raster.createWritableRaster(sm, sm.createDataBuffer(), null);
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        int n = getNumComponents();
        int[] bandOffsets = new int[n];
        for (int i = 0; i < n; i++) bandOffsets[i] = i;
        if (transferType == DataBuffer.TYPE_BYTE || transferType == DataBuffer.TYPE_USHORT) {
            return new PixelInterleavedSampleModel(transferType, w, h, n, w * n, bandOffsets);
        }
        return new ComponentSampleModel(transferType, w, h, n, w * n, bandOffsets);
    }

    public boolean isCompatibleSampleModel(SampleModel sm) {
        return sm instanceof ComponentSampleModel && sm.getNumBands() == getNumComponents()
            && sm.getTransferType() == transferType;
    }

    public WritableRaster getAlphaRaster(WritableRaster raster) {
        return lastBandChild(raster);
    }

    public boolean equals(Object obj) {
        return super.equals(obj) && obj.getClass() == getClass();
    }

    public int hashCode() {
        return super.hashCode();
    }
}
