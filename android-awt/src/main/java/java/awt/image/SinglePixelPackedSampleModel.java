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

public class SinglePixelPackedSampleModel extends SampleModel {
    private final int[] bitMasks;
    private final int[] bitOffsets;
    private final int[] bitSizes;
    private final int scanlineStride;

    public SinglePixelPackedSampleModel(int dataType, int w, int h, int[] bitMasks) {
        this(dataType, w, h, w, bitMasks);
    }

    public SinglePixelPackedSampleModel(int dataType, int w, int h, int scanlineStride, int[] bitMasks) {
        super(dataType, w, h, bitMasks.length);
        if (dataType != DataBuffer.TYPE_INT && dataType != DataBuffer.TYPE_USHORT && dataType != DataBuffer.TYPE_BYTE) {
            throw new IllegalArgumentException("Unsupported data type for SinglePixelPackedSampleModel: " + dataType);
        }
        this.scanlineStride = scanlineStride;
        this.bitMasks = bitMasks.clone();
        this.bitOffsets = new int[bitMasks.length];
        this.bitSizes = new int[bitMasks.length];
        for (int i = 0; i < bitMasks.length; i++) {
            int mask = bitMasks[i];
            int off = 0;
            while (mask != 0 && (mask & 1) == 0) { mask >>>= 1; off++; }
            int size = 0;
            while (mask != 0) { size++; mask >>>= 1; }
            bitOffsets[i] = off;
            bitSizes[i] = size;
        }
    }

    public int getScanlineStride() { return scanlineStride; }
    public int[] getBitMasks()     { return bitMasks.clone(); }
    public int[] getBitOffsets()   { return bitOffsets.clone(); }

    @Override
    public int getNumDataElements() {
        return 1;
    }

    @Override
    public int getSampleSize(int band) {
        return bitSizes[band];
    }

    @Override
    public int[] getSampleSize() {
        return bitSizes.clone();
    }

    public int getOffset(int x, int y) {
        return y * scanlineStride + x;
    }

    @Override
    public int getSample(int x, int y, int b, DataBuffer data) {
        checkPixel(x, y);
        return (data.getElem(y * scanlineStride + x) & bitMasks[b]) >>> bitOffsets[b];
    }

    private void checkPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
    }

    @Override
    public void setSample(int x, int y, int b, int s, DataBuffer data) {
        checkPixel(x, y);
        int idx = y * scanlineStride + x;
        int prev = data.getElem(idx);
        data.setElem(idx, (prev & ~bitMasks[b]) | ((s << bitOffsets[b]) & bitMasks[b]));
    }

    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        return new SinglePixelPackedSampleModel(dataType, w, h, bitMasks);
    }

    @Override
    public DataBuffer createDataBuffer() {
        switch (dataType) {
            case DataBuffer.TYPE_INT:
                return new DataBufferInt(scanlineStride * height);
            case DataBuffer.TYPE_BYTE: return new DataBufferByte(scanlineStride * height);
            case DataBuffer.TYPE_USHORT: return new DataBufferUShort(scanlineStride * height);
            default:
                throw new IllegalStateException("Unsupported data type: " + dataType);
        }
    }
    @Override public Object getDataElements(int x, int y, Object out, DataBuffer data) {
        checkPixel(x, y);
        Object result = transferArray(out, 1);
        transferSet(result, 0, data.getElem(y * scanlineStride + x));
        return result;
    }
    @Override public void setDataElements(int x, int y, Object in, DataBuffer data) { checkPixel(x, y); data.setElem(y * scanlineStride + x, transferGet(in, 0)); }
    @Override public SampleModel createSubsetSampleModel(int[] bands) {
        int[] masks = new int[bands.length];
        for (int i = 0; i < masks.length; i++) masks[i] = bitMasks[bands[i]];
        return new SinglePixelPackedSampleModel(dataType, width, height, scanlineStride, masks);
    }
}
