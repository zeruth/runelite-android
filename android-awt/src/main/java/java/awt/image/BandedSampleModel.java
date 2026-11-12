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
 * A {@link ComponentSampleModel} that stores each band in its own bank, pixel stride 1,
 * as {@code javax.imageio.ImageTypeSpecifier.createBanded} needs. The layout and the
 * public constructors follow OpenJDK 8u; sample access goes through the shared
 * ComponentSampleModel path rather than the JDK's per-type fast loops.
 */
public final class BandedSampleModel extends ComponentSampleModel {

    public BandedSampleModel(int dataType, int w, int h, int numBands) {
        this(dataType, w, h, w, createIndicesArray(numBands), createOffsetArray(numBands));
    }

    public BandedSampleModel(int dataType, int w, int h, int scanlineStride,
                             int[] bankIndices, int[] bandOffsets) {
        super(dataType, w, h, 1, scanlineStride, bankIndices, bandOffsets);
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        int[] bandOffs = new int[bandOffsets.length];
        int[] banks = new int[bankIndices.length];
        for (int i = 0; i < banks.length; i++) {
            banks[i] = bankIndices[i];
        }
        return new BandedSampleModel(dataType, w, h, w, banks, bandOffs);
    }

    public SampleModel createSubsetSampleModel(int[] bands) {
        if (bands.length > bankIndices.length) {
            throw new RasterFormatException("There are only " + bankIndices.length + " bands");
        }
        int[] newBankIndices = new int[bands.length];
        int[] newBandOffsets = new int[bands.length];
        for (int i = 0; i < bands.length; i++) {
            newBankIndices[i] = bankIndices[bands[i]];
            newBandOffsets[i] = bandOffsets[bands[i]];
        }
        return new BandedSampleModel(dataType, width, height, scanlineStride,
                                     newBankIndices, newBandOffsets);
    }

    public DataBuffer createDataBuffer() {
        int banks = 1;
        int maxOffset = 0;
        for (int i = 0; i < numBands; i++) {
            banks = Math.max(banks, bankIndices[i] + 1);
            maxOffset = Math.max(maxOffset, bandOffsets[i]);
        }
        int size = maxOffset + scanlineStride * (height - 1) + width;
        switch (dataType) {
        case DataBuffer.TYPE_BYTE:
            return new DataBufferByte(size, banks);
        case DataBuffer.TYPE_USHORT:
            return new DataBufferUShort(size, banks);
        case DataBuffer.TYPE_INT:
            return new DataBufferInt(size, banks);
        default:
            throw new IllegalArgumentException("dataType is not one of the supported types.");
        }
    }

    private static int[] createOffsetArray(int numBands) {
        return new int[numBands];
    }

    private static int[] createIndicesArray(int numBands) {
        int[] indices = new int[numBands];
        for (int i = 0; i < numBands; i++) {
            indices[i] = i;
        }
        return indices;
    }
}
