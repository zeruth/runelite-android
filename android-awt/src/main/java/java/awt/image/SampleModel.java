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

public abstract class SampleModel {
    protected int width;
    protected int height;
    protected int numBands;
    protected int dataType;

    public SampleModel(int dataType, int w, int h, int numBands) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be > 0");
        }
        this.dataType = dataType;
        this.width = w;
        this.height = h;
        this.numBands = numBands;
    }

    public final int getWidth()    { return width; }
    public final int getHeight()   { return height; }
    public final int getNumBands() { return numBands; }
    public final int getDataType() { return dataType; }
    public abstract int getNumDataElements();

    public abstract int getSampleSize(int band);
    public abstract int[] getSampleSize();

    public abstract int getSample(int x, int y, int b, DataBuffer data);
    public abstract void setSample(int x, int y, int b, int s, DataBuffer data);

    public int[] getPixel(int x, int y, int[] iArray, DataBuffer data) {
        int[] pixel = (iArray == null) ? new int[numBands] : iArray;
        for (int i = 0; i < numBands; i++) {
            pixel[i] = getSample(x, y, i, data);
        }
        return pixel;
    }

    public void setPixel(int x, int y, int[] iArray, DataBuffer data) {
        for (int i = 0; i < numBands; i++) {
            setSample(x, y, i, iArray[i], data);
        }
    }

    public abstract SampleModel createCompatibleSampleModel(int w, int h);

    public abstract DataBuffer createDataBuffer();

    public abstract int[] getSampleSize(int band, int[] iArray);
    public int getTransferType() { return dataType; }
    public Object getDataElements(int x, int y, Object out, DataBuffer data) {
        int count = getNumDataElements();
        Object result = transferArray(out, count);
        for (int band = 0; band < count; band++) transferSet(result, band, getSample(x, y, band, data));
        return result;
    }
    public void setDataElements(int x, int y, Object in, DataBuffer data) {
        for (int band = 0; band < getNumDataElements(); band++) setSample(x, y, band, transferGet(in, band), data);
    }
    final Object transferArray(Object out, int count) {
        if (out != null) return out;
        switch (getTransferType()) {
            case DataBuffer.TYPE_BYTE: return new byte[count];
            case DataBuffer.TYPE_USHORT: case DataBuffer.TYPE_SHORT: return new short[count];
            case DataBuffer.TYPE_INT: return new int[count];
            default: throw new UnsupportedOperationException("Floating point transfer arrays");
        }
    }
    static int transferGet(Object array, int i) {
        if (array instanceof byte[]) return ((byte[]) array)[i] & 255;
        if (array instanceof short[]) return ((short[]) array)[i] & 65535;
        return ((int[]) array)[i];
    }
    static void transferSet(Object array, int i, int value) {
        if (array instanceof byte[]) ((byte[]) array)[i] = (byte) value;
        else if (array instanceof short[]) ((short[]) array)[i] = (short) value;
        else ((int[]) array)[i] = value;
    }
    public SampleModel createSubsetSampleModel(int[] bands) { throw new UnsupportedOperationException("Sample model cannot select bands"); }
}
