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
 * Abstract layout of the samples of an image inside a {@link DataBuffer}. The generic
 * members below go through {@link #getSample}/{@link #setSample} one sample at a time,
 * as the JDK's do; subclasses override what they can do faster.
 */
public abstract class SampleModel {
    protected int width;
    protected int height;
    protected int numBands;
    protected int dataType;

    public SampleModel(int dataType, int w, int h, int numBands) {
        float size = (float) w * h;
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width (" + w + ") and height (" + h + ") must be > 0");
        }
        if (size >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Dimensions (width=" + w + " height=" + h + ") are too large");
        }
        if (dataType < DataBuffer.TYPE_BYTE
                || (dataType > DataBuffer.TYPE_DOUBLE && dataType != DataBuffer.TYPE_UNDEFINED)) {
            throw new IllegalArgumentException("Unsupported dataType: " + dataType);
        }
        if (numBands <= 0) {
            throw new IllegalArgumentException("Number of bands must be > 0");
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
    public int getTransferType()   { return dataType; }

    public abstract int getNumDataElements();
    public abstract int getSampleSize(int band);
    public abstract int[] getSampleSize();

    public abstract int getSample(int x, int y, int b, DataBuffer data);
    public abstract void setSample(int x, int y, int b, int s, DataBuffer data);

    public float getSampleFloat(int x, int y, int b, DataBuffer data) {
        return (float) getSample(x, y, b, data);
    }

    public double getSampleDouble(int x, int y, int b, DataBuffer data) {
        return (double) getSample(x, y, b, data);
    }

    public void setSample(int x, int y, int b, float s, DataBuffer data) {
        setSample(x, y, b, (int) s, data);
    }

    public void setSample(int x, int y, int b, double s, DataBuffer data) {
        setSample(x, y, b, (int) s, data);
    }

    public abstract SampleModel createCompatibleSampleModel(int w, int h);
    public abstract SampleModel createSubsetSampleModel(int[] bands);
    public abstract DataBuffer createDataBuffer();

    // ---- data elements -------------------------------------------------------------

    public Object getDataElements(int x, int y, Object obj, DataBuffer data) {
        int count = getNumDataElements();
        Object result = transferArray(obj, count);
        if (isFloating(getTransferType())) {
            for (int i = 0; i < count; i++) transferSet(result, i, getSampleDouble(x, y, i, data));
        } else {
            for (int i = 0; i < count; i++) transferSet(result, i, getSample(x, y, i, data));
        }
        return result;
    }

    public void setDataElements(int x, int y, Object obj, DataBuffer data) {
        int count = getNumDataElements();
        if (isFloating(getTransferType())) {
            for (int i = 0; i < count; i++) setSample(x, y, i, transferGetDouble(obj, i), data);
        } else {
            for (int i = 0; i < count; i++) setSample(x, y, i, transferGet(obj, i), data);
        }
    }

    public Object getDataElements(int x, int y, int w, int h, Object obj, DataBuffer data) {
        checkRegion(x, y, w, h);
        int count = getNumDataElements();
        Object result = transferArray(obj, count * w * h);
        Object pixel = null;
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                pixel = getDataElements(col, row, pixel, data);
                for (int e = 0; e < count; e++) transferCopy(pixel, e, result, index++);
            }
        }
        return result;
    }

    public void setDataElements(int x, int y, int w, int h, Object obj, DataBuffer data) {
        checkRegion(x, y, w, h);
        int count = getNumDataElements();
        Object pixel = transferArray(null, count);
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                for (int e = 0; e < count; e++) transferCopy(obj, index++, pixel, e);
                setDataElements(col, row, pixel, data);
            }
        }
    }

    // ---- pixels ------------------------------------------------------------------------

    public int[] getPixel(int x, int y, int[] iArray, DataBuffer data) {
        int[] pixel = iArray == null ? new int[numBands] : iArray;
        for (int i = 0; i < numBands; i++) pixel[i] = getSample(x, y, i, data);
        return pixel;
    }

    public float[] getPixel(int x, int y, float[] fArray, DataBuffer data) {
        float[] pixel = fArray == null ? new float[numBands] : fArray;
        for (int i = 0; i < numBands; i++) pixel[i] = getSampleFloat(x, y, i, data);
        return pixel;
    }

    public double[] getPixel(int x, int y, double[] dArray, DataBuffer data) {
        double[] pixel = dArray == null ? new double[numBands] : dArray;
        for (int i = 0; i < numBands; i++) pixel[i] = getSampleDouble(x, y, i, data);
        return pixel;
    }

    public void setPixel(int x, int y, int[] iArray, DataBuffer data) {
        for (int i = 0; i < numBands; i++) setSample(x, y, i, iArray[i], data);
    }

    public void setPixel(int x, int y, float[] fArray, DataBuffer data) {
        for (int i = 0; i < numBands; i++) setSample(x, y, i, fArray[i], data);
    }

    public void setPixel(int x, int y, double[] dArray, DataBuffer data) {
        for (int i = 0; i < numBands; i++) setSample(x, y, i, dArray[i], data);
    }

    public int[] getPixels(int x, int y, int w, int h, int[] iArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int[] pixels = iArray == null ? new int[numBands * w * h] : iArray;
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                for (int b = 0; b < numBands; b++) pixels[index++] = getSample(col, row, b, data);
            }
        }
        return pixels;
    }

    public float[] getPixels(int x, int y, int w, int h, float[] fArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        float[] pixels = fArray == null ? new float[numBands * w * h] : fArray;
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                for (int b = 0; b < numBands; b++) pixels[index++] = getSampleFloat(col, row, b, data);
            }
        }
        return pixels;
    }

    public double[] getPixels(int x, int y, int w, int h, double[] dArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        double[] pixels = dArray == null ? new double[numBands * w * h] : dArray;
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                for (int b = 0; b < numBands; b++) pixels[index++] = getSampleDouble(col, row, b, data);
            }
        }
        return pixels;
    }

    public void setPixels(int x, int y, int w, int h, int[] iArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                for (int b = 0; b < numBands; b++) setSample(col, row, b, iArray[index++], data);
            }
        }
    }

    public void setPixels(int x, int y, int w, int h, float[] fArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                for (int b = 0; b < numBands; b++) setSample(col, row, b, fArray[index++], data);
            }
        }
    }

    public void setPixels(int x, int y, int w, int h, double[] dArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) {
                for (int b = 0; b < numBands; b++) setSample(col, row, b, dArray[index++], data);
            }
        }
    }

    // ---- samples -----------------------------------------------------------------------

    public int[] getSamples(int x, int y, int w, int h, int b, int[] iArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int[] samples = iArray == null ? new int[w * h] : iArray;
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) samples[index++] = getSample(col, row, b, data);
        }
        return samples;
    }

    public float[] getSamples(int x, int y, int w, int h, int b, float[] fArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        float[] samples = fArray == null ? new float[w * h] : fArray;
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) samples[index++] = getSampleFloat(col, row, b, data);
        }
        return samples;
    }

    public double[] getSamples(int x, int y, int w, int h, int b, double[] dArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        double[] samples = dArray == null ? new double[w * h] : dArray;
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) samples[index++] = getSampleDouble(col, row, b, data);
        }
        return samples;
    }

    public void setSamples(int x, int y, int w, int h, int b, int[] iArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) setSample(col, row, b, iArray[index++], data);
        }
    }

    public void setSamples(int x, int y, int w, int h, int b, float[] fArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) setSample(col, row, b, fArray[index++], data);
        }
    }

    public void setSamples(int x, int y, int w, int h, int b, double[] dArray, DataBuffer data) {
        checkRegion(x, y, w, h);
        int index = 0;
        for (int row = y; row < y + h; row++) {
            for (int col = x; col < x + w; col++) setSample(col, row, b, dArray[index++], data);
        }
    }

    // ---- helpers shared by the package -------------------------------------------------

    private void checkRegion(int x, int y, int w, int h) {
        int x1 = x + w;
        int y1 = y + h;
        if (x < 0 || x >= width || w > width || x1 < 0 || x1 > width
                || y < 0 || y >= height || h > height || y1 < 0 || y1 > height) {
            throw new ArrayIndexOutOfBoundsException("Invalid coordinates.");
        }
    }

    static boolean isFloating(int type) {
        return type == DataBuffer.TYPE_FLOAT || type == DataBuffer.TYPE_DOUBLE;
    }

    final Object transferArray(Object out, int count) {
        return transferArray(getTransferType(), out, count);
    }

    static Object transferArray(int type, Object out, int count) {
        if (out != null) return out;
        switch (type) {
            case DataBuffer.TYPE_BYTE: return new byte[count];
            case DataBuffer.TYPE_USHORT:
            case DataBuffer.TYPE_SHORT: return new short[count];
            case DataBuffer.TYPE_INT: return new int[count];
            case DataBuffer.TYPE_FLOAT: return new float[count];
            case DataBuffer.TYPE_DOUBLE: return new double[count];
            default: throw new IllegalArgumentException("Unsupported transfer type " + type);
        }
    }

    /** Unsigned for byte and short arrays, as the integral transfer types are read. */
    static int transferGet(Object array, int i) {
        if (array instanceof byte[]) return ((byte[]) array)[i] & 255;
        if (array instanceof short[]) return ((short[]) array)[i] & 65535;
        if (array instanceof int[]) return ((int[]) array)[i];
        if (array instanceof float[]) return (int) ((float[]) array)[i];
        return (int) ((double[]) array)[i];
    }

    static double transferGetDouble(Object array, int i) {
        if (array instanceof float[]) return ((float[]) array)[i];
        if (array instanceof double[]) return ((double[]) array)[i];
        return transferGet(array, i);
    }

    static void transferSet(Object array, int i, int value) {
        if (array instanceof byte[]) ((byte[]) array)[i] = (byte) value;
        else if (array instanceof short[]) ((short[]) array)[i] = (short) value;
        else if (array instanceof int[]) ((int[]) array)[i] = value;
        else if (array instanceof float[]) ((float[]) array)[i] = value;
        else ((double[]) array)[i] = value;
    }

    static void transferSet(Object array, int i, double value) {
        if (array instanceof float[]) ((float[]) array)[i] = (float) value;
        else if (array instanceof double[]) ((double[]) array)[i] = value;
        else transferSet(array, i, (int) value);
    }

    static void transferCopy(Object from, int i, Object to, int j) {
        if (from instanceof float[] || from instanceof double[]) transferSet(to, j, transferGetDouble(from, i));
        else transferSet(to, j, transferGet(from, i));
    }
}
