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

public class PixelInterleavedSampleModel extends ComponentSampleModel {
    public PixelInterleavedSampleModel(int type, int width, int height, int pixelStride, int scanlineStride, int[] offsets) {
        super(type, width, height, pixelStride, scanlineStride, offsets);
    }
    @Override public SampleModel createCompatibleSampleModel(int w, int h) {
        int minimum = Integer.MAX_VALUE;
        for (int offset : bandOffsets) minimum = Math.min(minimum, offset);
        int[] offsets = bandOffsets.clone(); for (int i = 0; i < offsets.length; i++) offsets[i] -= minimum;
        return new PixelInterleavedSampleModel(dataType, w, h, pixelStride, w * pixelStride, offsets);
    }
    @Override public SampleModel createSubsetSampleModel(int[] bands) {
        int[] offsets = new int[bands.length]; for (int i = 0; i < offsets.length; i++) offsets[i] = bandOffsets[bands[i]];
        return new PixelInterleavedSampleModel(dataType, width, height, pixelStride, scanlineStride, offsets);
    }
}
