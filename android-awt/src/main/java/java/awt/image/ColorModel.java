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
 * Translates pixel values to color and alpha components. The members the JDK leaves to
 * subclasses throw {@link UnsupportedOperationException} here with the JDK's message;
 * that is the desktop contract, not a gap of the shadow.
 */
public abstract class ColorModel implements Transparency {
    protected int pixel_bits;
    protected int transferType;
    private final int numComponents;
    private final int numColorComponents;
    private final boolean hasAlpha;
    private final boolean isAlphaPremultiplied;
    private final int transparency;
    private final ColorSpace colorSpace;
    private final int[] componentSizes;

    private static ColorModel RGBdefault;

    public ColorModel(int bits) {
        this(bits, new int[]{8, 8, 8, 8}, ColorSpace.getInstance(ColorSpace.CS_sRGB),
            true, false, Transparency.TRANSLUCENT, getDefaultTransferType(bits));
    }

    protected ColorModel(int pixel_bits, int[] bits, ColorSpace cspace, boolean hasAlpha,
                         boolean isAlphaPremultiplied, int transparency, int transferType) {
        if (cspace == null) {
            throw new IllegalArgumentException("ColorSpace cannot be null");
        }
        int colors = cspace.getNumComponents();
        int components = colors + (hasAlpha ? 1 : 0);
        if (bits != null && bits.length < components) {
            throw new IllegalArgumentException("Number of color/alpha components should be "
                + components + " but length of bits array is " + bits.length);
        }
        if (transparency < Transparency.OPAQUE || transparency > Transparency.TRANSLUCENT) {
            throw new IllegalArgumentException("Unknown transparency: " + transparency);
        }
        if (pixel_bits < 1) {
            throw new IllegalArgumentException("Number of bits must be > 0");
        }
        this.pixel_bits = pixel_bits;
        this.transferType = transferType;
        this.numColorComponents = colors;
        this.numComponents = components;
        this.hasAlpha = hasAlpha;
        this.isAlphaPremultiplied = hasAlpha && isAlphaPremultiplied;
        this.transparency = transparency;
        this.colorSpace = cspace;
        this.componentSizes = bits == null ? null : java.util.Arrays.copyOf(bits, components);
    }

    static int getDefaultTransferType(int pixel_bits) {
        if (pixel_bits <= 8) return DataBuffer.TYPE_BYTE;
        if (pixel_bits <= 16) return DataBuffer.TYPE_USHORT;
        if (pixel_bits <= 32) return DataBuffer.TYPE_INT;
        return DataBuffer.TYPE_UNDEFINED;
    }

    public static ColorModel getRGBdefault() {
        if (RGBdefault == null) {
            RGBdefault = new DirectColorModel(32, 0x00ff0000, 0x0000ff00, 0x000000ff, 0xff000000);
        }
        return RGBdefault;
    }

    public int getComponentSize(int componentIdx) {
        if (componentSizes == null) {
            throw new NullPointerException("Number of bits array is null.");
        }
        return componentSizes[componentIdx];
    }

    public int[] getComponentSize() {
        return componentSizes == null ? null : componentSizes.clone();
    }

    public final boolean hasAlpha()             { return hasAlpha; }
    public final boolean isAlphaPremultiplied() { return isAlphaPremultiplied; }
    public final int getTransferType()          { return transferType; }
    public int getPixelSize()                   { return pixel_bits; }
    public int getTransparency()                { return transparency; }
    public int getNumComponents()               { return numComponents; }
    public int getNumColorComponents()          { return numColorComponents; }
    public final ColorSpace getColorSpace()     { return colorSpace; }

    // ---- single-int pixels -------------------------------------------------------------

    public abstract int getRed(int pixel);
    public abstract int getGreen(int pixel);
    public abstract int getBlue(int pixel);
    public abstract int getAlpha(int pixel);

    public int getRGB(int pixel) {
        return (getAlpha(pixel) << 24) | (getRed(pixel) << 16) | (getGreen(pixel) << 8) | getBlue(pixel);
    }

    // ---- pixels in transfer-type arrays --------------------------------------------------

    /** The one-element pixel of {@code inData}, as the JDK's single-pixel defaults read it. */
    final int singlePixel(Object inData) {
        int pixel;
        int length;
        switch (transferType) {
            case DataBuffer.TYPE_BYTE: {
                byte[] data = (byte[]) inData;
                pixel = data[0] & 0xff;
                length = data.length;
                break;
            }
            case DataBuffer.TYPE_USHORT: {
                short[] data = (short[]) inData;
                pixel = data[0] & 0xffff;
                length = data.length;
                break;
            }
            case DataBuffer.TYPE_INT: {
                int[] data = (int[]) inData;
                pixel = data[0];
                length = data.length;
                break;
            }
            default:
                throw new UnsupportedOperationException("This method has not been implemented for transferType " + transferType);
        }
        if (length != 1) {
            throw new UnsupportedOperationException("This method is not supported by this color model");
        }
        return pixel;
    }

    /** Stores {@code pixel} into a one-element array of the transfer type. */
    final Object storePixel(int pixel, Object obj) {
        switch (transferType) {
            case DataBuffer.TYPE_BYTE: {
                byte[] data = obj == null ? new byte[1] : (byte[]) obj;
                data[0] = (byte) pixel;
                return data;
            }
            case DataBuffer.TYPE_USHORT: {
                short[] data = obj == null ? new short[1] : (short[]) obj;
                data[0] = (short) pixel;
                return data;
            }
            case DataBuffer.TYPE_INT: {
                int[] data = obj == null ? new int[1] : (int[]) obj;
                data[0] = pixel;
                return data;
            }
            default:
                throw new UnsupportedOperationException("This method has not been implemented for transferType " + transferType);
        }
    }

    public int getRed(Object inData)   { return getRed(singlePixel(inData)); }
    public int getGreen(Object inData) { return getGreen(singlePixel(inData)); }
    public int getBlue(Object inData)  { return getBlue(singlePixel(inData)); }
    public int getAlpha(Object inData) { return getAlpha(singlePixel(inData)); }

    public int getRGB(Object inData) {
        return (getAlpha(inData) << 24) | (getRed(inData) << 16) | (getGreen(inData) << 8) | getBlue(inData);
    }

    public Object getDataElements(int rgb, Object pixel) {
        throw new UnsupportedOperationException("This method is not supported by this color model.");
    }

    // ---- components --------------------------------------------------------------------

    public int[] getComponents(int pixel, int[] components, int offset) {
        throw new UnsupportedOperationException("This method is not supported by this color model.");
    }

    public int[] getComponents(Object pixel, int[] components, int offset) {
        throw new UnsupportedOperationException("This method is not supported by this color model.");
    }

    final float maxComponent(int component) {
        return (float) ((1L << componentSizes[component]) - 1);
    }

    public int[] getUnnormalizedComponents(float[] normComponents, int normOffset, int[] components, int offset) {
        if (componentSizes == null) {
            throw new UnsupportedOperationException("This method is not supported.  Unable to determine #bits per component.");
        }
        if ((normComponents.length - normOffset) < numComponents) {
            throw new IllegalArgumentException("Incorrect number of components.  Expecting " + numComponents);
        }
        if (components == null) {
            components = new int[offset + numComponents];
        }
        if (hasAlpha && isAlphaPremultiplied) {
            float normAlpha = normComponents[normOffset + numColorComponents];
            for (int i = 0; i < numColorComponents; i++) {
                components[offset + i] = (int) (normComponents[normOffset + i] * maxComponent(i) * normAlpha + 0.5f);
            }
            components[offset + numColorComponents] = (int) (normAlpha * maxComponent(numColorComponents) + 0.5f);
        } else {
            for (int i = 0; i < numComponents; i++) {
                components[offset + i] = (int) (normComponents[normOffset + i] * maxComponent(i) + 0.5f);
            }
        }
        return components;
    }

    public float[] getNormalizedComponents(int[] components, int offset, float[] normComponents, int normOffset) {
        if (componentSizes == null) {
            throw new UnsupportedOperationException("This method is not supported.  Unable to determine #bits per component.");
        }
        if ((components.length - offset) < numComponents) {
            throw new IllegalArgumentException("Incorrect number of components.  Expecting " + numComponents);
        }
        if (normComponents == null) {
            normComponents = new float[numComponents + normOffset];
        }
        if (hasAlpha && isAlphaPremultiplied) {
            float normAlpha = components[offset + numColorComponents] / maxComponent(numColorComponents);
            for (int i = 0; i < numColorComponents; i++) {
                normComponents[normOffset + i] = normAlpha != 0.0f
                    ? components[offset + i] / (normAlpha * maxComponent(i)) : 0.0f;
            }
            normComponents[normOffset + numColorComponents] = normAlpha;
        } else {
            for (int i = 0; i < numComponents; i++) {
                normComponents[normOffset + i] = components[offset + i] / maxComponent(i);
            }
        }
        return normComponents;
    }

    public int getDataElement(int[] components, int offset) {
        throw new UnsupportedOperationException("This method is not supported by this color model.");
    }

    public Object getDataElements(int[] components, int offset, Object obj) {
        throw new UnsupportedOperationException("This method has not been implemented for this color model.");
    }

    public int getDataElement(float[] normComponents, int normOffset) {
        return getDataElement(getUnnormalizedComponents(normComponents, normOffset, null, 0), 0);
    }

    public Object getDataElements(float[] normComponents, int normOffset, Object obj) {
        return getDataElements(getUnnormalizedComponents(normComponents, normOffset, null, 0), 0, obj);
    }

    public float[] getNormalizedComponents(Object pixel, float[] normComponents, int normOffset) {
        return getNormalizedComponents(getComponents(pixel, null, 0), 0, normComponents, normOffset);
    }

    // ---- rasters -----------------------------------------------------------------------

    public ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
        throw new UnsupportedOperationException("This method is not supported by this color model");
    }

    public boolean isCompatibleRaster(Raster raster) {
        throw new UnsupportedOperationException("This method has not been implemented for this ColorModel.");
    }

    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        throw new UnsupportedOperationException("This method is not supported by this color model");
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        throw new UnsupportedOperationException("This method is not supported by this color model");
    }

    public boolean isCompatibleSampleModel(SampleModel sm) {
        throw new UnsupportedOperationException("This method is not supported by this color model");
    }

    public WritableRaster getAlphaRaster(WritableRaster raster) {
        return null;
    }

    /** The alpha band as a one-band child, for the models that keep alpha in the last band. */
    final WritableRaster lastBandChild(WritableRaster raster) {
        if (!hasAlpha) {
            return null;
        }
        int x = raster.getMinX();
        int y = raster.getMinY();
        return raster.createWritableChild(x, y, raster.getWidth(), raster.getHeight(), x, y,
            new int[]{raster.getNumBands() - 1});
    }

    /**
     * Multiplies the color bands of {@code raster} by alpha, or divides them out, in place.
     * Alpha is the last band, as in every direct and component layout. Divided values are
     * clamped to the band's range so a color brighter than its alpha cannot wrap.
     */
    final void coerceRaster(WritableRaster raster, boolean premultiply) {
        int x0 = raster.getMinX();
        int y0 = raster.getMinY();
        int w = raster.getWidth();
        int h = raster.getHeight();
        int alphaBand = numColorComponents;
        if (SampleModel.isFloating(raster.getTransferType())) {
            double[] pixel = null;
            for (int y = y0; y < y0 + h; y++) {
                for (int x = x0; x < x0 + w; x++) {
                    pixel = raster.getPixel(x, y, pixel);
                    double alpha = pixel[alphaBand];
                    if (premultiply) {
                        for (int c = 0; c < alphaBand; c++) pixel[c] *= alpha;
                    } else if (alpha != 0.0) {
                        for (int c = 0; c < alphaBand; c++) pixel[c] /= alpha;
                    }
                    raster.setPixel(x, y, pixel);
                }
            }
            return;
        }
        int[] sizes = raster.getSampleModel().getSampleSize();
        boolean signed = raster.getTransferType() == DataBuffer.TYPE_SHORT;
        float[] max = new float[numComponents];
        for (int c = 0; c < numComponents; c++) {
            max[c] = signed ? 32767f : (float) ((1L << Math.min(sizes[c], componentSizes == null ? 8 : componentSizes[c])) - 1);
        }
        float alphaScale = 1.0f / max[alphaBand];
        int[] pixel = null;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                pixel = raster.getPixel(x, y, pixel);
                float normAlpha = pixel[alphaBand] * alphaScale;
                if (premultiply) {
                    for (int c = 0; c < alphaBand; c++) pixel[c] = (int) (pixel[c] * normAlpha + 0.5f);
                } else if (normAlpha != 0.0f) {
                    float invAlpha = 1.0f / normAlpha;
                    for (int c = 0; c < alphaBand; c++) {
                        pixel[c] = (int) Math.min(max[c], (int) (pixel[c] * invAlpha + 0.5f));
                    }
                }
                raster.setPixel(x, y, pixel);
            }
        }
    }

    // ---- identity ------------------------------------------------------------------------

    public boolean equals(Object obj) {
        if (!(obj instanceof ColorModel)) {
            return false;
        }
        ColorModel cm = (ColorModel) obj;
        if (this == cm) {
            return true;
        }
        if (hasAlpha != cm.hasAlpha() || isAlphaPremultiplied != cm.isAlphaPremultiplied()
                || pixel_bits != cm.getPixelSize() || transparency != cm.getTransparency()
                || numComponents != cm.getNumComponents()) {
            return false;
        }
        return java.util.Arrays.equals(componentSizes, cm.componentSizes);
    }

    public int hashCode() {
        int result = 0;
        result = (hasAlpha ? 2 : 3) + (isAlphaPremultiplied ? 4 : 5) + pixel_bits * 6 + transparency * 7
            + numComponents * 8;
        if (componentSizes != null) {
            for (int i = 0; i < numComponents; i++) result = result + componentSizes[i] * (i + 9);
        }
        return result;
    }

    public String toString() {
        return "ColorModel: #pixelBits = " + pixel_bits + " numComponents = " + numComponents
            + " color space = " + colorSpace + " transparency = " + transparency
            + " has alpha = " + hasAlpha + " isAlphaPre = " + isAlphaPremultiplied;
    }
}
