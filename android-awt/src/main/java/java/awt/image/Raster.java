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

import java.awt.Point;
import java.awt.Rectangle;

/**
 * A rectangle of pixels: a {@link SampleModel} over a {@link DataBuffer}, placed at
 * {@code (minX, minY)}. The JDK hands out {@code sun.awt.image} subclasses from the
 * factories; this shadow has none, so every factory and child of a writable raster
 * returns a plain {@link WritableRaster}, which is what callers casting the result of
 * {@code createRaster}/{@code createChild} on the desktop rely on.
 */
public class Raster {
    protected SampleModel sampleModel;
    protected DataBuffer dataBuffer;
    protected int minX;
    protected int minY;
    protected int width;
    protected int height;
    protected int sampleModelTranslateX;
    protected int sampleModelTranslateY;
    protected int numBands;
    protected int numDataElements;
    protected Raster parent;

    protected Raster(SampleModel sampleModel, Point origin) {
        this(sampleModel, sampleModel.createDataBuffer(),
            new Rectangle(origin.x, origin.y, sampleModel.getWidth(), sampleModel.getHeight()), origin, null);
    }

    protected Raster(SampleModel sampleModel, DataBuffer dataBuffer, Point origin) {
        this(sampleModel, dataBuffer,
            new Rectangle(origin.x, origin.y, sampleModel.getWidth(), sampleModel.getHeight()), origin, null);
    }

    protected Raster(SampleModel sampleModel, DataBuffer dataBuffer, Rectangle aRegion,
                     Point sampleModelTranslate, Raster parent) {
        if (sampleModel == null || dataBuffer == null || aRegion == null || sampleModelTranslate == null) {
            throw new NullPointerException("SampleModel, dataBuffer, aRegion and sampleModelTranslate cannot be null");
        }
        this.sampleModel = sampleModel;
        this.dataBuffer = dataBuffer;
        minX = aRegion.x;
        minY = aRegion.y;
        width = aRegion.width;
        height = aRegion.height;
        if (width <= 0 || height <= 0) {
            throw new RasterFormatException("negative or zero " + ((width <= 0) ? "width" : "height"));
        }
        if ((minX + width) < minX) {
            throw new RasterFormatException("overflow condition for X coordinates of Raster");
        }
        if ((minY + height) < minY) {
            throw new RasterFormatException("overflow condition for Y coordinates of Raster");
        }
        sampleModelTranslateX = sampleModelTranslate.x;
        sampleModelTranslateY = sampleModelTranslate.y;
        numBands = sampleModel.getNumBands();
        numDataElements = sampleModel.getNumDataElements();
        this.parent = parent;
    }

    // ---- factories -----------------------------------------------------------------------

    public static WritableRaster createInterleavedRaster(int dataType, int w, int h, int bands, Point location) {
        int[] bandOffsets = new int[bands];
        for (int i = 0; i < bands; i++) bandOffsets[i] = i;
        return createInterleavedRaster(dataType, w, h, w * bands, bands, bandOffsets, location);
    }

    public static WritableRaster createInterleavedRaster(int dataType, int w, int h, int scanlineStride,
                                                         int pixelStride, int[] bandOffsets, Point location) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width (" + w + ") and height (" + h + ") must be > 0");
        }
        long size = (long) scanlineStride * (h - 1) + (long) pixelStride * w;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Size of array must be smaller than Integer.MAX_VALUE.");
        }
        DataBuffer d;
        switch (dataType) {
            case DataBuffer.TYPE_BYTE: d = new DataBufferByte((int) size); break;
            case DataBuffer.TYPE_USHORT: d = new DataBufferUShort((int) size); break;
            default: throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        return createInterleavedRaster(d, w, h, scanlineStride, pixelStride, bandOffsets, location);
    }

    public static WritableRaster createBandedRaster(int dataType, int w, int h, int bands, Point location) {
        if (bands < 1) {
            throw new ArrayIndexOutOfBoundsException("Number of bands (" + bands + ") must be greater than 0");
        }
        int[] bankIndices = new int[bands];
        for (int i = 0; i < bands; i++) bankIndices[i] = i;
        return createBandedRaster(dataType, w, h, w, bankIndices, new int[bands], location);
    }

    public static WritableRaster createBandedRaster(int dataType, int w, int h, int scanlineStride,
                                                    int[] bankIndices, int[] bandOffsets, Point location) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width (" + w + ") and height (" + h + ") must be > 0");
        }
        if (bankIndices == null) {
            throw new ArrayIndexOutOfBoundsException("Bank indices array is null");
        }
        if (bandOffsets == null) {
            throw new ArrayIndexOutOfBoundsException("Band offsets array is null");
        }
        int maxBank = bankIndices[0];
        int maxBandOffset = bandOffsets[0];
        for (int i = 1; i < bandOffsets.length; i++) {
            maxBank = Math.max(maxBank, bankIndices[i]);
            maxBandOffset = Math.max(maxBandOffset, bandOffsets[i]);
        }
        int banks = maxBank + 1;
        long size = maxBandOffset + (long) scanlineStride * (h - 1) + w;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Size of array must be smaller than Integer.MAX_VALUE.");
        }
        DataBuffer d;
        switch (dataType) {
            case DataBuffer.TYPE_BYTE: d = new DataBufferByte((int) size, banks); break;
            case DataBuffer.TYPE_USHORT: d = new DataBufferUShort((int) size, banks); break;
            case DataBuffer.TYPE_INT: d = new DataBufferInt((int) size, banks); break;
            default: throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        return createBandedRaster(d, w, h, scanlineStride, bankIndices, bandOffsets, location);
    }

    public static WritableRaster createPackedRaster(int dataType, int w, int h, int[] bandMasks, Point location) {
        DataBuffer d;
        switch (dataType) {
            case DataBuffer.TYPE_BYTE: d = new DataBufferByte(w * h); break;
            case DataBuffer.TYPE_USHORT: d = new DataBufferUShort(w * h); break;
            case DataBuffer.TYPE_INT: d = new DataBufferInt(w * h); break;
            default: throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        return createPackedRaster(d, w, h, w, bandMasks, location);
    }

    public static WritableRaster createPackedRaster(int dataType, int w, int h, int bands, int bitsPerBand,
                                                    Point location) {
        if (bitsPerBand <= 0) {
            throw new IllegalArgumentException("Bits per band (" + bitsPerBand + ") must be > 0");
        }
        if (bands <= 0) {
            throw new IllegalArgumentException("Number of bands (" + bands + ") must be > 0");
        }
        if (dataType != DataBuffer.TYPE_BYTE && dataType != DataBuffer.TYPE_USHORT && dataType != DataBuffer.TYPE_INT) {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        if (bands != 1) {
            int[] masks = new int[bands];
            int mask = (1 << bitsPerBand) - 1;
            int shift = (bands - 1) * bitsPerBand;
            if (shift + bitsPerBand > DataBuffer.getDataTypeSize(dataType)) {
                throw new IllegalArgumentException("bitsPerBand(" + bitsPerBand + ") * bands is  greater than data type size.");
            }
            for (int i = 0; i < bands; i++) {
                masks[i] = mask << shift;
                shift -= bitsPerBand;
            }
            return createPackedRaster(dataType, w, h, masks, location);
        }
        double fw = w;
        int perElement = DataBuffer.getDataTypeSize(dataType) / bitsPerBand;
        if (perElement == 0) {
            throw new IllegalArgumentException("bitsPerBand(" + bitsPerBand + ") is greater than data type size.");
        }
        int size = (int) Math.ceil(fw / perElement) * h;
        DataBuffer d;
        switch (dataType) {
            case DataBuffer.TYPE_BYTE: d = new DataBufferByte(size); break;
            case DataBuffer.TYPE_USHORT: d = new DataBufferUShort(size); break;
            default: d = new DataBufferInt(size); break;
        }
        return createPackedRaster(d, w, h, bitsPerBand, location);
    }

    public static WritableRaster createInterleavedRaster(DataBuffer dataBuffer, int w, int h, int scanlineStride,
                                                         int pixelStride, int[] bandOffsets, Point location) {
        if (dataBuffer == null) {
            throw new NullPointerException("DataBuffer cannot be null");
        }
        int dataType = dataBuffer.getDataType();
        if (dataType != DataBuffer.TYPE_BYTE && dataType != DataBuffer.TYPE_USHORT) {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        PixelInterleavedSampleModel sm =
            new PixelInterleavedSampleModel(dataType, w, h, pixelStride, scanlineStride, bandOffsets);
        return new WritableRaster(sm, dataBuffer, origin(location));
    }

    public static WritableRaster createBandedRaster(DataBuffer dataBuffer, int w, int h, int scanlineStride,
                                                    int[] bankIndices, int[] bandOffsets, Point location) {
        if (dataBuffer == null) {
            throw new NullPointerException("DataBuffer cannot be null");
        }
        if (bandOffsets.length != bankIndices.length) {
            throw new IllegalArgumentException("bankIndices.length != bandOffsets.length");
        }
        int dataType = dataBuffer.getDataType();
        if (dataType != DataBuffer.TYPE_BYTE && dataType != DataBuffer.TYPE_USHORT && dataType != DataBuffer.TYPE_INT) {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        BandedSampleModel sm = new BandedSampleModel(dataType, w, h, scanlineStride, bankIndices, bandOffsets);
        return new WritableRaster(sm, dataBuffer, origin(location));
    }

    public static WritableRaster createPackedRaster(DataBuffer dataBuffer, int w, int h, int scanlineStride,
                                                    int[] bandMasks, Point location) {
        if (dataBuffer == null) {
            throw new NullPointerException("DataBuffer cannot be null");
        }
        int dataType = dataBuffer.getDataType();
        if (dataType != DataBuffer.TYPE_BYTE && dataType != DataBuffer.TYPE_USHORT && dataType != DataBuffer.TYPE_INT) {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        SinglePixelPackedSampleModel sm = new SinglePixelPackedSampleModel(dataType, w, h, scanlineStride, bandMasks);
        return new WritableRaster(sm, dataBuffer, origin(location));
    }

    public static WritableRaster createPackedRaster(DataBuffer dataBuffer, int w, int h, int bitsPerPixel,
                                                    Point location) {
        if (dataBuffer == null) {
            throw new NullPointerException("DataBuffer cannot be null");
        }
        int dataType = dataBuffer.getDataType();
        if (dataType != DataBuffer.TYPE_BYTE && dataType != DataBuffer.TYPE_USHORT && dataType != DataBuffer.TYPE_INT) {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        if (dataBuffer.getNumBanks() != 1) {
            throw new RasterFormatException("DataBuffer for packed Rasters must only have 1 bank.");
        }
        MultiPixelPackedSampleModel sm = new MultiPixelPackedSampleModel(dataType, w, h, bitsPerPixel);
        return new WritableRaster(sm, dataBuffer, origin(location));
    }

    public static Raster createRaster(SampleModel sm, DataBuffer db, Point location) {
        if (sm == null || db == null) {
            throw new NullPointerException("SampleModel and DataBuffer cannot be null");
        }
        return new WritableRaster(sm, db, origin(location));
    }

    public static WritableRaster createWritableRaster(SampleModel sm, Point location) {
        if (sm == null) {
            throw new NullPointerException("SampleModel cannot be null");
        }
        return createWritableRaster(sm, sm.createDataBuffer(), location);
    }

    public static WritableRaster createWritableRaster(SampleModel sm, DataBuffer db, Point location) {
        if (sm == null || db == null) {
            throw new NullPointerException("SampleModel and DataBuffer cannot be null");
        }
        return new WritableRaster(sm, db, origin(location));
    }

    private static Point origin(Point location) {
        return location == null ? new Point(0, 0) : location;
    }

    // ---- geometry and children ---------------------------------------------------------

    public Raster getParent()                 { return parent; }
    public final int getSampleModelTranslateX() { return sampleModelTranslateX; }
    public final int getSampleModelTranslateY() { return sampleModelTranslateY; }
    public final int getMinX()                { return minX; }
    public final int getMinY()                { return minY; }
    public final int getWidth()               { return width; }
    public final int getHeight()              { return height; }
    public final int getNumBands()            { return numBands; }
    public final int getNumDataElements()     { return sampleModel.getNumDataElements(); }
    public final int getTransferType()        { return sampleModel.getTransferType(); }
    public DataBuffer getDataBuffer()         { return dataBuffer; }
    public SampleModel getSampleModel()       { return sampleModel; }

    public Rectangle getBounds() {
        return new Rectangle(minX, minY, width, height);
    }

    public WritableRaster createCompatibleWritableRaster() {
        return new WritableRaster(sampleModel.createCompatibleSampleModel(width, height), new Point(0, 0));
    }

    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        if (w <= 0 || h <= 0) {
            throw new RasterFormatException("negative " + ((w <= 0) ? "width" : "height"));
        }
        return new WritableRaster(sampleModel.createCompatibleSampleModel(w, h), new Point(0, 0));
    }

    public WritableRaster createCompatibleWritableRaster(Rectangle rect) {
        if (rect == null) {
            throw new NullPointerException("Rect cannot be null");
        }
        return createCompatibleWritableRaster(rect.x, rect.y, rect.width, rect.height);
    }

    public WritableRaster createCompatibleWritableRaster(int x, int y, int w, int h) {
        WritableRaster ret = createCompatibleWritableRaster(w, h);
        return ret.createWritableChild(0, 0, w, h, x, y, null);
    }

    public Raster createTranslatedChild(int childMinX, int childMinY) {
        return createChild(minX, minY, width, height, childMinX, childMinY, null);
    }

    public Raster createChild(int parentX, int parentY, int width, int height,
                              int childMinX, int childMinY, int[] bandList) {
        checkChild(parentX, parentY, width, height);
        SampleModel subSampleModel = bandList == null ? sampleModel : sampleModel.createSubsetSampleModel(bandList);
        Rectangle region = new Rectangle(childMinX, childMinY, width, height);
        Point translate = new Point(sampleModelTranslateX + childMinX - parentX,
                                    sampleModelTranslateY + childMinY - parentY);
        if (this instanceof WritableRaster) {
            return new WritableRaster(subSampleModel, dataBuffer, region, translate, (WritableRaster) this);
        }
        return new Raster(subSampleModel, dataBuffer, region, translate, this);
    }

    final void checkChild(int parentX, int parentY, int width, int height) {
        if (parentX < this.minX) {
            throw new RasterFormatException("parentX lies outside raster");
        }
        if (parentY < this.minY) {
            throw new RasterFormatException("parentY lies outside raster");
        }
        if ((parentX + width < parentX) || (parentX + width > this.width + this.minX)) {
            throw new RasterFormatException("(parentX + width) is outside raster");
        }
        if ((parentY + height < parentY) || (parentY + height > this.height + this.minY)) {
            throw new RasterFormatException("(parentY + height) is outside raster");
        }
    }

    final void checkBounds(int x, int y) {
        if (x < minX || y < minY || x >= minX + width || y >= minY + height) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
    }

    final void checkBounds(int x, int y, int w, int h) {
        if (x < minX || y < minY || w < 0 || h < 0 || x + w > minX + width || y + h > minY + height
                || x + w < x || y + h < y) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
    }

    // ---- reads ---------------------------------------------------------------------------

    public Object getDataElements(int x, int y, Object outData) {
        checkBounds(x, y);
        return sampleModel.getDataElements(x - sampleModelTranslateX, y - sampleModelTranslateY, outData, dataBuffer);
    }

    public Object getDataElements(int x, int y, int w, int h, Object outData) {
        checkBounds(x, y, w, h);
        return sampleModel.getDataElements(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, outData, dataBuffer);
    }

    public int[] getPixel(int x, int y, int[] iArray) {
        checkBounds(x, y);
        return sampleModel.getPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, iArray, dataBuffer);
    }

    public float[] getPixel(int x, int y, float[] fArray) {
        checkBounds(x, y);
        return sampleModel.getPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, fArray, dataBuffer);
    }

    public double[] getPixel(int x, int y, double[] dArray) {
        checkBounds(x, y);
        return sampleModel.getPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, dArray, dataBuffer);
    }

    public int[] getPixels(int x, int y, int w, int h, int[] iArray) {
        checkBounds(x, y, w, h);
        return sampleModel.getPixels(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, iArray, dataBuffer);
    }

    public float[] getPixels(int x, int y, int w, int h, float[] fArray) {
        checkBounds(x, y, w, h);
        return sampleModel.getPixels(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, fArray, dataBuffer);
    }

    public double[] getPixels(int x, int y, int w, int h, double[] dArray) {
        checkBounds(x, y, w, h);
        return sampleModel.getPixels(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, dArray, dataBuffer);
    }

    public int getSample(int x, int y, int b) {
        checkBounds(x, y);
        return sampleModel.getSample(x - sampleModelTranslateX, y - sampleModelTranslateY, b, dataBuffer);
    }

    public float getSampleFloat(int x, int y, int b) {
        checkBounds(x, y);
        return sampleModel.getSampleFloat(x - sampleModelTranslateX, y - sampleModelTranslateY, b, dataBuffer);
    }

    public double getSampleDouble(int x, int y, int b) {
        checkBounds(x, y);
        return sampleModel.getSampleDouble(x - sampleModelTranslateX, y - sampleModelTranslateY, b, dataBuffer);
    }

    public int[] getSamples(int x, int y, int w, int h, int b, int[] iArray) {
        checkBounds(x, y, w, h);
        return sampleModel.getSamples(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, b, iArray, dataBuffer);
    }

    public float[] getSamples(int x, int y, int w, int h, int b, float[] fArray) {
        checkBounds(x, y, w, h);
        return sampleModel.getSamples(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, b, fArray, dataBuffer);
    }

    public double[] getSamples(int x, int y, int w, int h, int b, double[] dArray) {
        checkBounds(x, y, w, h);
        return sampleModel.getSamples(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, b, dArray, dataBuffer);
    }
}
