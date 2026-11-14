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

/**
 * One sample per data element, each band at its own offset (and bank) from the pixel's
 * position. Every data type is supported, the floating-point ones through the
 * {@code getElemFloat}/{@code getElemDouble} accessors of the buffer.
 */
public class ComponentSampleModel extends SampleModel {
    protected int[] bandOffsets;
    protected int[] bankIndices;
    protected int numBanks = 1;
    protected int scanlineStride;
    protected int pixelStride;

    public ComponentSampleModel(int dataType, int w, int h, int pixelStride, int scanlineStride, int[] bandOffsets) {
        this(dataType, w, h, pixelStride, scanlineStride, new int[bandOffsets.length], bandOffsets);
    }

    public ComponentSampleModel(int dataType, int w, int h, int pixelStride, int scanlineStride,
                                int[] bankIndices, int[] bandOffsets) {
        super(dataType, w, h, bandOffsets.length);
        if (pixelStride < 0) {
            throw new IllegalArgumentException("Pixel stride must be >= 0");
        }
        if (scanlineStride < 0) {
            throw new IllegalArgumentException("Scanline stride must be >= 0");
        }
        if (dataType < DataBuffer.TYPE_BYTE || dataType > DataBuffer.TYPE_DOUBLE) {
            throw new IllegalArgumentException("Unsupported dataType.");
        }
        if (bankIndices.length != bandOffsets.length) {
            throw new IllegalArgumentException("Length of bandOffsets must equal length of bankIndices.");
        }
        this.pixelStride = pixelStride;
        this.scanlineStride = scanlineStride;
        this.bandOffsets = bandOffsets.clone();
        this.bankIndices = bankIndices.clone();
        int maxBank = bankIndices[0];
        for (int i = 0; i < bankIndices.length; i++) {
            if (bankIndices[i] < 0) {
                throw new IllegalArgumentException("Index of bank " + i + " is less than 0 (" + bankIndices[i] + ")");
            }
            maxBank = Math.max(maxBank, bankIndices[i]);
        }
        numBanks = maxBank + 1;
    }

    public final int getPixelStride()    { return pixelStride; }
    public final int getScanlineStride() { return scanlineStride; }
    public final int[] getBandOffsets()  { return bandOffsets.clone(); }
    public final int[] getBankIndices()  { return bankIndices.clone(); }
    public final int getNumDataElements() { return getNumBands(); }

    public int getOffset(int x, int y) {
        return y * scanlineStride + x * pixelStride + bandOffsets[0];
    }

    public int getOffset(int x, int y, int b) {
        return y * scanlineStride + x * pixelStride + bandOffsets[b];
    }

    public final int getSampleSize(int band) { return DataBuffer.getDataTypeSize(dataType); }

    public final int[] getSampleSize() {
        int[] sizes = new int[numBands];
        java.util.Arrays.fill(sizes, DataBuffer.getDataTypeSize(dataType));
        return sizes;
    }

    private int index(int x, int y, int b) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
        return y * scanlineStride + x * pixelStride + bandOffsets[b];
    }

    public int getSample(int x, int y, int b, DataBuffer data) {
        return data.getElem(bankIndices[b], index(x, y, b));
    }

    public float getSampleFloat(int x, int y, int b, DataBuffer data) {
        return data.getElemFloat(bankIndices[b], index(x, y, b));
    }

    public double getSampleDouble(int x, int y, int b, DataBuffer data) {
        return data.getElemDouble(bankIndices[b], index(x, y, b));
    }

    public void setSample(int x, int y, int b, int s, DataBuffer data) {
        data.setElem(bankIndices[b], index(x, y, b), s);
    }

    public void setSample(int x, int y, int b, float s, DataBuffer data) {
        data.setElemFloat(bankIndices[b], index(x, y, b), s);
    }

    public void setSample(int x, int y, int b, double s, DataBuffer data) {
        data.setElemDouble(bankIndices[b], index(x, y, b), s);
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        int[] offsets = new int[numBands];
        for (int i = 0; i < numBands; i++) offsets[i] = i;
        return new ComponentSampleModel(dataType, w, h, numBands, w * numBands, offsets);
    }

    public SampleModel createSubsetSampleModel(int[] bands) {
        if (bands.length > bankIndices.length) {
            throw new RasterFormatException("There are only " + bankIndices.length + " bands");
        }
        int[] offsets = new int[bands.length];
        int[] banks = new int[bands.length];
        for (int i = 0; i < bands.length; i++) {
            offsets[i] = bandOffsets[bands[i]];
            banks[i] = bankIndices[bands[i]];
        }
        return new ComponentSampleModel(dataType, width, height, pixelStride, scanlineStride, banks, offsets);
    }

    /** Elements one bank needs: the furthest band offset plus the last pixel's position. */
    final int bufferSize() {
        int maxOffset = bandOffsets[0];
        for (int offset : bandOffsets) maxOffset = Math.max(maxOffset, offset);
        long size = 0;
        if (maxOffset >= 0) size += maxOffset + 1;
        if (pixelStride > 0) size += (long) pixelStride * (width - 1);
        if (scanlineStride > 0) size += (long) scanlineStride * (height - 1);
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cannot allocate a data buffer of " + size + " elements");
        }
        return (int) size;
    }

    public DataBuffer createDataBuffer() {
        return createBuffer(dataType, bufferSize(), numBanks);
    }

    static DataBuffer createBuffer(int dataType, int size, int banks) {
        switch (dataType) {
            case DataBuffer.TYPE_BYTE: return new DataBufferByte(size, banks);
            case DataBuffer.TYPE_USHORT: return new DataBufferUShort(size, banks);
            case DataBuffer.TYPE_SHORT: return new DataBufferShort(size, banks);
            case DataBuffer.TYPE_INT: return new DataBufferInt(size, banks);
            case DataBuffer.TYPE_FLOAT: return new DataBufferFloat(size, banks);
            case DataBuffer.TYPE_DOUBLE: return new DataBufferDouble(size, banks);
            default: throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof ComponentSampleModel)) return false;
        ComponentSampleModel that = (ComponentSampleModel) o;
        return width == that.width && height == that.height && numBands == that.numBands
            && dataType == that.dataType && java.util.Arrays.equals(bandOffsets, that.bandOffsets)
            && java.util.Arrays.equals(bankIndices, that.bankIndices) && numBanks == that.numBanks
            && scanlineStride == that.scanlineStride && pixelStride == that.pixelStride;
    }

    public int hashCode() {
        int hash = width;
        hash = 31 * hash + height;
        hash = 31 * hash + numBands;
        hash = 31 * hash + dataType;
        hash = 31 * hash + java.util.Arrays.hashCode(bandOffsets);
        hash = 31 * hash + java.util.Arrays.hashCode(bankIndices);
        hash = 31 * hash + numBanks;
        hash = 31 * hash + scanlineStride;
        return 31 * hash + pixelStride;
    }
}
