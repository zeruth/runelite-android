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

public class ComponentSampleModel extends SampleModel {
    protected final int pixelStride, scanlineStride;
    protected final int[] bankIndices, bandOffsets;
    public ComponentSampleModel(int type, int width, int height, int pixelStride, int scanlineStride, int[] offsets) {
        this(type, width, height, pixelStride, scanlineStride, new int[offsets.length], offsets);
    }
    public ComponentSampleModel(int type, int width, int height, int pixelStride, int scanlineStride, int[] banks, int[] offsets) {
        super(type, width, height, offsets.length);
        if (banks.length != offsets.length || pixelStride < 0 || scanlineStride < 0) throw new IllegalArgumentException("Invalid component layout");
        this.pixelStride = pixelStride; this.scanlineStride = scanlineStride;
        bankIndices = banks.clone(); bandOffsets = offsets.clone();
    }
    public final int getPixelStride() { return pixelStride; }
    public final int getScanlineStride() { return scanlineStride; }
    public final int[] getBandOffsets() { return bandOffsets.clone(); }
    public final int[] getBankIndices() { return bankIndices.clone(); }
    public int getNumDataElements() { return numBands; }
    public int getSampleSize(int band) { return dataType == DataBuffer.TYPE_BYTE ? 8 : dataType == DataBuffer.TYPE_INT ? 32 : 16; }
    public int[] getSampleSize() { int[] sizes = new int[numBands]; java.util.Arrays.fill(sizes, getSampleSize(0)); return sizes; }
    public int[] getSampleSize(int band, int[] out) { if (out == null) out = new int[1]; out[0] = getSampleSize(band); return out; }
    public int getSample(int x, int y, int band, DataBuffer data) { return data.getElem(bankIndices[band], y * scanlineStride + x * pixelStride + bandOffsets[band]); }
    public void setSample(int x, int y, int band, int value, DataBuffer data) { data.setElem(bankIndices[band], y * scanlineStride + x * pixelStride + bandOffsets[band], value); }
    public SampleModel createCompatibleSampleModel(int w, int h) {
        int[] offsets = new int[numBands]; for (int i = 0; i < numBands; i++) offsets[i] = i;
        return new ComponentSampleModel(dataType, w, h, numBands, w * numBands, offsets);
    }
    public SampleModel createSubsetSampleModel(int[] bands) {
        int[] offsets = new int[bands.length], banks = new int[bands.length];
        for (int i = 0; i < bands.length; i++) { offsets[i] = bandOffsets[bands[i]]; banks[i] = bankIndices[bands[i]]; }
        return new ComponentSampleModel(dataType, width, height, pixelStride, scanlineStride, banks, offsets);
    }
    public DataBuffer createDataBuffer() {
        int maxOffset = 0, banks = 1;
        for (int i = 0; i < numBands; i++) { maxOffset = Math.max(maxOffset, bandOffsets[i]); banks = Math.max(banks, bankIndices[i] + 1); }
        int size = (height - 1) * scanlineStride + (width - 1) * pixelStride + maxOffset + 1;
        if (dataType == DataBuffer.TYPE_BYTE) return new DataBufferByte(size, banks);
        if (dataType == DataBuffer.TYPE_USHORT) return new DataBufferUShort(size, banks);
        if (dataType == DataBuffer.TYPE_INT) return new DataBufferInt(size, banks);
        throw new UnsupportedOperationException("Unsupported component data type: " + dataType);
    }
}
