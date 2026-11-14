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
 * Color and alpha packed into one pixel value, one contiguous bit mask per component.
 */
public abstract class PackedColorModel extends ColorModel {
    final int[] maskArray;
    final int[] maskOffsets;
    final float[] scaleFactors;

    public PackedColorModel(ColorSpace space, int bits, int[] colorMaskArray, int alphaMask,
                            boolean isAlphaPremultiplied, int trans, int transferType) {
        super(bits, bitsArray(colorMaskArray, alphaMask), space, alphaMask != 0, isAlphaPremultiplied,
            alphaMask != 0 && Integer.bitCount(alphaMask) == 1 ? Transparency.BITMASK : trans, transferType);
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException("Number of bits must be between 1 and 32.");
        }
        int components = getNumComponents();
        maskArray = new int[components];
        maskOffsets = new int[components];
        scaleFactors = new float[components];
        for (int i = 0; i < getNumColorComponents(); i++) {
            decompose(colorMaskArray[i], i);
        }
        if (alphaMask != 0) {
            decompose(alphaMask, getNumColorComponents());
        }
    }

    public PackedColorModel(ColorSpace space, int bits, int rmask, int gmask, int bmask, int amask,
                            boolean isAlphaPremultiplied, int trans, int transferType) {
        this(space, bits, rgbMasks(space, rmask, gmask, bmask), amask, isAlphaPremultiplied, trans, transferType);
    }

    private static int[] rgbMasks(ColorSpace space, int rmask, int gmask, int bmask) {
        if (space.getType() != ColorSpace.TYPE_RGB) {
            throw new IllegalArgumentException("ColorSpace must be TYPE_RGB.");
        }
        return new int[]{rmask, gmask, bmask};
    }

    private static int[] bitsArray(int[] colorMaskArray, int alphaMask) {
        int colors = colorMaskArray.length;
        int[] bits = new int[colors + (alphaMask == 0 ? 0 : 1)];
        for (int i = 0; i < colors; i++) {
            bits[i] = countBits(colorMaskArray[i]);
            if (bits[i] < 0) {
                throw new IllegalArgumentException("Noncontiguous color mask (" + Integer.toHexString(colorMaskArray[i])
                    + "at index " + i);
            }
        }
        if (alphaMask != 0) {
            bits[colors] = countBits(alphaMask);
            if (bits[colors] < 0) {
                throw new IllegalArgumentException("Noncontiguous alpha mask (" + Integer.toHexString(alphaMask));
            }
        }
        return bits;
    }

    /** Bits in a contiguous mask, or -1 when the mask has a gap. */
    private static int countBits(int mask) {
        if (mask == 0) {
            return 0;
        }
        int shifted = mask >>> Integer.numberOfTrailingZeros(mask);
        return (shifted & (shifted + 1)) == 0 ? Integer.bitCount(mask) : -1;
    }

    private void decompose(int mask, int idx) {
        maskArray[idx] = mask;
        maskOffsets[idx] = mask == 0 ? 0 : Integer.numberOfTrailingZeros(mask);
        int bits = Integer.bitCount(mask);
        scaleFactors[idx] = bits == 0 ? 1.0f : 255.0f / (float) ((1L << bits) - 1);
    }

    public final int getMask(int index) {
        return maskArray[index];
    }

    public final int[] getMasks() {
        return maskArray.clone();
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        return new SinglePixelPackedSampleModel(transferType, w, h, maskArray);
    }

    public boolean isCompatibleSampleModel(SampleModel sm) {
        if (!(sm instanceof SinglePixelPackedSampleModel)) {
            return false;
        }
        if (getNumComponents() != sm.getNumBands() || sm.getTransferType() != transferType) {
            return false;
        }
        int[] bitMasks = ((SinglePixelPackedSampleModel) sm).getBitMasks();
        if (bitMasks.length != maskArray.length) {
            return false;
        }
        int maxMask = (int) ((1L << DataBuffer.getDataTypeSize(transferType)) - 1);
        for (int i = 0; i < bitMasks.length; i++) {
            if ((maxMask & bitMasks[i]) != (maxMask & maskArray[i])) {
                return false;
            }
        }
        return true;
    }

    public WritableRaster getAlphaRaster(WritableRaster raster) {
        return lastBandChild(raster);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof PackedColorModel) || !super.equals(obj)) {
            return false;
        }
        return java.util.Arrays.equals(maskArray, ((PackedColorModel) obj).maskArray);
    }

    public int hashCode() {
        return 31 * super.hashCode() + java.util.Arrays.hashCode(maskArray);
    }
}
