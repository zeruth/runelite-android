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

public class WritableRaster extends Raster {

    protected WritableRaster(SampleModel sampleModel, Point origin) {
        super(sampleModel, origin);
    }

    protected WritableRaster(SampleModel sampleModel, DataBuffer dataBuffer, Point origin) {
        super(sampleModel, dataBuffer, origin);
    }

    protected WritableRaster(SampleModel sampleModel, DataBuffer dataBuffer, Rectangle aRegion,
                             Point sampleModelTranslate, WritableRaster parent) {
        super(sampleModel, dataBuffer, aRegion, sampleModelTranslate, parent);
    }

    public WritableRaster getWritableParent() {
        return (WritableRaster) parent;
    }

    public WritableRaster createWritableTranslatedChild(int childMinX, int childMinY) {
        return createWritableChild(minX, minY, width, height, childMinX, childMinY, null);
    }

    public WritableRaster createWritableChild(int parentX, int parentY, int w, int h,
                                              int childMinX, int childMinY, int[] bandList) {
        return (WritableRaster) createChild(parentX, parentY, w, h, childMinX, childMinY, bandList);
    }

    // ---- data elements -----------------------------------------------------------------

    public void setDataElements(int x, int y, Object inData) {
        checkBounds(x, y);
        sampleModel.setDataElements(x - sampleModelTranslateX, y - sampleModelTranslateY, inData, dataBuffer);
    }

    public void setDataElements(int x, int y, Raster inRaster) {
        int dstOffX = x + inRaster.getMinX();
        int dstOffY = y + inRaster.getMinY();
        int w = inRaster.getWidth();
        int h = inRaster.getHeight();
        if (dstOffX < this.minX || dstOffY < this.minY
                || dstOffX + w > this.minX + this.width || dstOffY + h > this.minY + this.height) {
            throw new ArrayIndexOutOfBoundsException("Coordinate out of bounds!");
        }
        int srcOffX = inRaster.getMinX();
        int srcOffY = inRaster.getMinY();
        Object row = null;
        for (int startY = 0; startY < h; startY++) {
            row = inRaster.getDataElements(srcOffX, srcOffY + startY, w, 1, row);
            setDataElements(dstOffX, dstOffY + startY, w, 1, row);
        }
    }

    public void setDataElements(int x, int y, int w, int h, Object inData) {
        checkBounds(x, y, w, h);
        sampleModel.setDataElements(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, inData, dataBuffer);
    }

    public void setRect(Raster srcRaster) {
        setRect(0, 0, srcRaster);
    }

    public void setRect(int dx, int dy, Raster srcRaster) {
        int w = srcRaster.getWidth();
        int h = srcRaster.getHeight();
        int srcOffX = srcRaster.getMinX();
        int srcOffY = srcRaster.getMinY();
        int dstOffX = dx + srcOffX;
        int dstOffY = dy + srcOffY;
        if (dstOffX < this.minX) {
            int skipX = this.minX - dstOffX;
            w -= skipX;
            srcOffX += skipX;
            dstOffX = this.minX;
        }
        if (dstOffY < this.minY) {
            int skipY = this.minY - dstOffY;
            h -= skipY;
            srcOffY += skipY;
            dstOffY = this.minY;
        }
        if (dstOffX + w > this.minX + this.width) {
            w = this.minX + this.width - dstOffX;
        }
        if (dstOffY + h > this.minY + this.height) {
            h = this.minY + this.height - dstOffY;
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        switch (srcRaster.getSampleModel().getDataType()) {
            case DataBuffer.TYPE_FLOAT: {
                float[] row = null;
                for (int startY = 0; startY < h; startY++) {
                    row = srcRaster.getPixels(srcOffX, srcOffY + startY, w, 1, row);
                    setPixels(dstOffX, dstOffY + startY, w, 1, row);
                }
                break;
            }
            case DataBuffer.TYPE_DOUBLE: {
                double[] row = null;
                for (int startY = 0; startY < h; startY++) {
                    row = srcRaster.getPixels(srcOffX, srcOffY + startY, w, 1, row);
                    setPixels(dstOffX, dstOffY + startY, w, 1, row);
                }
                break;
            }
            default: {
                int[] row = null;
                for (int startY = 0; startY < h; startY++) {
                    row = srcRaster.getPixels(srcOffX, srcOffY + startY, w, 1, row);
                    setPixels(dstOffX, dstOffY + startY, w, 1, row);
                }
                break;
            }
        }
    }

    // ---- pixels and samples ------------------------------------------------------------

    public void setPixel(int x, int y, int[] iArray) {
        checkBounds(x, y);
        sampleModel.setPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, iArray, dataBuffer);
    }

    public void setPixel(int x, int y, float[] fArray) {
        checkBounds(x, y);
        sampleModel.setPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, fArray, dataBuffer);
    }

    public void setPixel(int x, int y, double[] dArray) {
        checkBounds(x, y);
        sampleModel.setPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, dArray, dataBuffer);
    }

    public void setPixels(int x, int y, int w, int h, int[] iArray) {
        checkBounds(x, y, w, h);
        sampleModel.setPixels(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, iArray, dataBuffer);
    }

    public void setPixels(int x, int y, int w, int h, float[] fArray) {
        checkBounds(x, y, w, h);
        sampleModel.setPixels(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, fArray, dataBuffer);
    }

    public void setPixels(int x, int y, int w, int h, double[] dArray) {
        checkBounds(x, y, w, h);
        sampleModel.setPixels(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, dArray, dataBuffer);
    }

    public void setSample(int x, int y, int b, int s) {
        checkBounds(x, y);
        sampleModel.setSample(x - sampleModelTranslateX, y - sampleModelTranslateY, b, s, dataBuffer);
    }

    public void setSample(int x, int y, int b, float s) {
        checkBounds(x, y);
        sampleModel.setSample(x - sampleModelTranslateX, y - sampleModelTranslateY, b, s, dataBuffer);
    }

    public void setSample(int x, int y, int b, double s) {
        checkBounds(x, y);
        sampleModel.setSample(x - sampleModelTranslateX, y - sampleModelTranslateY, b, s, dataBuffer);
    }

    public void setSamples(int x, int y, int w, int h, int b, int[] iArray) {
        checkBounds(x, y, w, h);
        sampleModel.setSamples(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, b, iArray, dataBuffer);
    }

    public void setSamples(int x, int y, int w, int h, int b, float[] fArray) {
        checkBounds(x, y, w, h);
        sampleModel.setSamples(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, b, fArray, dataBuffer);
    }

    public void setSamples(int x, int y, int w, int h, int b, double[] dArray) {
        checkBounds(x, y, w, h);
        sampleModel.setSamples(x - sampleModelTranslateX, y - sampleModelTranslateY, w, h, b, dArray, dataBuffer);
    }
}
