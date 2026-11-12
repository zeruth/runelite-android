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

    protected Raster(SampleModel sm, Point origin) {
        this(sm, sm.createDataBuffer(), new Rectangle(origin.x, origin.y, sm.getWidth(), sm.getHeight()), origin, null);
    }

    protected Raster(SampleModel sm, DataBuffer db, Point origin) {
        this(sm, db, new Rectangle(origin.x, origin.y, sm.getWidth(), sm.getHeight()), origin, null);
    }

    protected Raster(SampleModel sm, DataBuffer db, Rectangle aRegion, Point sampleModelTranslate, Raster parent) {
        this.sampleModel = sm;
        this.dataBuffer = db;
        this.minX = aRegion.x;
        this.minY = aRegion.y;
        this.width = aRegion.width;
        this.height = aRegion.height;
        this.sampleModelTranslateX = sampleModelTranslate.x;
        this.sampleModelTranslateY = sampleModelTranslate.y;
        this.numBands = sm.getNumBands();
        this.numDataElements = sm.getNumDataElements();
        this.parent = parent;
    }

    public static WritableRaster createWritableRaster(SampleModel sm, DataBuffer db, Point location) {
        Point loc = (location == null) ? new Point(0, 0) : location;
        return new WritableRaster(sm, db, new Rectangle(loc.x, loc.y, sm.getWidth(), sm.getHeight()), loc, null);
    }

    public static WritableRaster createWritableRaster(SampleModel sm, Point location) {
        return createWritableRaster(sm, sm.createDataBuffer(), location);
    }

    public static WritableRaster createInterleavedRaster(int dataType, int w, int h, int bands,
                                                         Point location) {
        int[] bandOffsets = new int[bands];
        for (int i = 0; i < bands; i++) {
            bandOffsets[i] = i;
        }
        return createInterleavedRaster(dataType, w, h, w * bands, bands, bandOffsets, location);
    }

    public static WritableRaster createInterleavedRaster(int dataType, int w, int h,
                                                         int scanlineStride, int pixelStride,
                                                         int[] bandOffsets, Point location) {
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width (" + w + ") and height (" + h
                + ") must be > 0");
        }
        if (dataType != DataBuffer.TYPE_BYTE && dataType != DataBuffer.TYPE_USHORT) {
            throw new IllegalArgumentException("Unsupported data type " + dataType);
        }
        SampleModel sm = new PixelInterleavedSampleModel(dataType, w, h, pixelStride,
                                                         scanlineStride, bandOffsets);
        return createWritableRaster(sm, location);
    }

    public SampleModel getSampleModel() { return sampleModel; }
    public DataBuffer getDataBuffer()   { return dataBuffer; }
    public int getMinX()                { return minX; }
    public int getMinY()                { return minY; }
    public int getWidth()               { return width; }
    public int getHeight()              { return height; }
    public int getNumBands()            { return numBands; }
    public int getNumDataElements()     { return numDataElements; }
    public int getSampleModelTranslateX() { return sampleModelTranslateX; }
    public int getSampleModelTranslateY() { return sampleModelTranslateY; }
    public Raster getParent()           { return parent; }

    public Rectangle getBounds() {
        return new Rectangle(minX, minY, width, height);
    }

    public int getSample(int x, int y, int b) {
        checkBounds(x,y);
        return sampleModel.getSample(x - sampleModelTranslateX, y - sampleModelTranslateY, b, dataBuffer);
    }

    public float getSampleFloat(int x, int y, int b) {
        checkBounds(x,y);
        return sampleModel.getSampleFloat(x - sampleModelTranslateX, y - sampleModelTranslateY, b, dataBuffer);
    }

    public int[] getPixel(int x, int y, int[] iArray) {
        checkBounds(x,y);
        return sampleModel.getPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, iArray, dataBuffer);
    }
    public int getTransferType() { return sampleModel.getTransferType(); }
    protected final void checkBounds(int x, int y) {
        if (x < minX || y < minY || x >= minX + width || y >= minY + height) throw new ArrayIndexOutOfBoundsException("Raster bounds");
    }
    public Object getDataElements(int x, int y, Object out) {
        checkBounds(x, y);
        return sampleModel.getDataElements(x - sampleModelTranslateX, y - sampleModelTranslateY, out, dataBuffer);
    }
    public Object getDataElements(int x, int y, int w, int h, Object out) {
        Object result = sampleModel.transferArray(out, w * h * numDataElements), pixel = null;
        int index = 0;
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            pixel = getDataElements(x + col, y + row, pixel);
            for (int element = 0; element < numDataElements; element++) SampleModel.transferSet(result, index++, SampleModel.transferGet(pixel, element));
        }
        return result;
    }
    public WritableRaster createCompatibleWritableRaster(){return createCompatibleWritableRaster(width,height);}
    public WritableRaster createCompatibleWritableRaster(int w,int h){SampleModel sm=sampleModel.createCompatibleSampleModel(w,h);return createWritableRaster(sm,sm.createDataBuffer(),new Point(0,0));}
}
