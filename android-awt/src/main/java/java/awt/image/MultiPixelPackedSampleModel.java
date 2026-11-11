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

public class MultiPixelPackedSampleModel extends SampleModel {
    private final int bits, elementBits, scanlineStride, dataBitOffset, mask;
    public MultiPixelPackedSampleModel(int type, int w, int h, int bits) {
        this(type, w, h, bits, (w * bits + elementSize(type) - 1) / elementSize(type), 0);
    }
    public MultiPixelPackedSampleModel(int type, int w, int h, int bits, int stride, int offset) {
        super(type, w, h, 1);
        elementBits = elementSize(type);
        if (bits <= 0 || elementBits % bits != 0) throw new IllegalArgumentException("Invalid bit stride");
        this.bits = bits; scanlineStride = stride; dataBitOffset = offset; mask = (int)((1L << bits) - 1);
    }
    private static int elementSize(int type) {
        if (type == DataBuffer.TYPE_BYTE) return 8;
        if (type == DataBuffer.TYPE_USHORT) return 16;
        if (type == DataBuffer.TYPE_INT) return 32;
        throw new IllegalArgumentException("Packed data type");
    }
    public int getNumDataElements() { return 1; }
    public int getPixelBitStride() { return bits; }
    public int getScanlineStride() { return scanlineStride; }
    public int getDataBitOffset() { return dataBitOffset; }
    public int getOffset(int x, int y) { return y * scanlineStride + (x * bits + dataBitOffset) / elementBits; }
    public int getBitOffset(int x) { return (x * bits + dataBitOffset) % elementBits; }
    public int getTransferType() { return bits <= 8 ? DataBuffer.TYPE_BYTE : bits <= 16 ? DataBuffer.TYPE_USHORT : DataBuffer.TYPE_INT; }
    public int getSampleSize(int band) { if (band != 0) throw new ArrayIndexOutOfBoundsException(band); return bits; }
    public int[] getSampleSize() { return new int[]{bits}; }
    public int[] getSampleSize(int band, int[] out) { if (out == null) out = new int[1]; out[0] = getSampleSize(band); return out; }
    public int getSample(int x, int y, int band, DataBuffer data) { return data.getElem(getOffset(x, y)) >>> (elementBits - getBitOffset(x) - bits) & mask; }
    public void setSample(int x, int y, int band, int value, DataBuffer data) {
        int offset = getOffset(x, y), shift = elementBits - getBitOffset(x) - bits;
        data.setElem(offset, data.getElem(offset) & ~(mask << shift) | (value & mask) << shift);
    }
    public SampleModel createCompatibleSampleModel(int w, int h) { return new MultiPixelPackedSampleModel(dataType, w, h, bits); }
    public SampleModel createSubsetSampleModel(int[] bands) { if (bands.length != 1 || bands[0] != 0) throw new IllegalArgumentException("Packed band"); return new MultiPixelPackedSampleModel(dataType, width, height, bits, scanlineStride, dataBitOffset); }
    public DataBuffer createDataBuffer() {
        int size = scanlineStride * height + (dataBitOffset + elementBits - 1) / elementBits;
        if (dataType == DataBuffer.TYPE_BYTE) return new DataBufferByte(size);
        if (dataType == DataBuffer.TYPE_USHORT) return new DataBufferUShort(size);
        return new DataBufferInt(size);
    }
}
