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
 * A {@link ComponentSampleModel} with a pixel stride of one: each band is a plane,
 * normally in a bank of its own.
 */
public final class BandedSampleModel extends ComponentSampleModel {

    public BandedSampleModel(int dataType, int w, int h, int numBands) {
        super(dataType, w, h, 1, w, indices(numBands), new int[numBands]);
    }

    public BandedSampleModel(int dataType, int w, int h, int scanlineStride, int[] bankIndices, int[] bandOffsets) {
        super(dataType, w, h, 1, scanlineStride, bankIndices, bandOffsets);
    }

    private static int[] indices(int numBands) {
        int[] indices = new int[numBands];
        for (int i = 0; i < numBands; i++) indices[i] = i;
        return indices;
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        int[] offsets;
        if (numBanks == 1) {
            // One shared bank: keep the planes in the order their offsets had, packed back to back.
            offsets = new int[bandOffsets.length];
            for (int i = 0; i < offsets.length; i++) {
                int rank = 0;
                for (int j = 0; j < offsets.length; j++) {
                    if (bandOffsets[j] < bandOffsets[i] || (bandOffsets[j] == bandOffsets[i] && j < i)) rank++;
                }
                offsets[i] = rank * w * h;
            }
        } else {
            offsets = new int[bandOffsets.length];
        }
        return new BandedSampleModel(dataType, w, h, w, bankIndices, offsets);
    }

    public SampleModel createSubsetSampleModel(int[] bands) {
        if (bands.length > bankIndices.length) {
            throw new RasterFormatException("There are only " + bankIndices.length + " bands");
        }
        int[] banks = new int[bands.length];
        int[] offsets = new int[bands.length];
        for (int i = 0; i < bands.length; i++) {
            banks[i] = bankIndices[bands[i]];
            offsets[i] = bandOffsets[bands[i]];
        }
        return new BandedSampleModel(dataType, width, height, scanlineStride, banks, offsets);
    }

    public int hashCode() {
        return super.hashCode() ^ 0x2;
    }
}
