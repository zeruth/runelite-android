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
    protected WritableRaster(SampleModel sm, Point origin) {
        super(sm, origin);
    }

    protected WritableRaster(SampleModel sm, DataBuffer db, Point origin) {
        super(sm, db, origin);
    }

    protected WritableRaster(SampleModel sm, DataBuffer db, Rectangle aRegion, Point sampleModelTranslate, WritableRaster parent) {
        super(sm, db, aRegion, sampleModelTranslate, parent);
    }

    public void setSample(int x, int y, int b, int s) {
        checkBounds(x,y);
        sampleModel.setSample(x - sampleModelTranslateX, y - sampleModelTranslateY, b, s, dataBuffer);
    }

    public void setPixel(int x, int y, int[] iArray) {
        checkBounds(x,y);
        sampleModel.setPixel(x - sampleModelTranslateX, y - sampleModelTranslateY, iArray, dataBuffer);
    }

    public void setPixels(int x, int y, int w, int h, int[] iArray) {
        int offset = 0;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                for (int b = 0; b < numBands; b++) {
                    setSample(x + col, y + row, b, iArray[offset++]);
                }
            }
        }
    }
    public void setDataElements(int x, int y, Object data) {
        checkBounds(x, y);
        sampleModel.setDataElements(x - sampleModelTranslateX, y - sampleModelTranslateY, data, dataBuffer);
    }
    public void setDataElements(int x, int y, int w, int h, Object data) {
        Object pixel = sampleModel.transferArray(null, numDataElements);
        int index = 0;
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            for (int element = 0; element < numDataElements; element++) SampleModel.transferSet(pixel, element, SampleModel.transferGet(data, index++));
            setDataElements(x + col, y + row, pixel);
        }
    }
    public WritableRaster createWritableChild(int x,int y,int w,int h,int childX,int childY,int[] bands){
        if(x<minX||y<minY||w<=0||h<=0||x+w>minX+width||y+h>minY+height)throw new IllegalArgumentException("child bounds");
        SampleModel childModel = bands == null ? sampleModel : sampleModel.createSubsetSampleModel(bands);
        return new WritableRaster(childModel,dataBuffer,new Rectangle(childX,childY,w,h),new Point(sampleModelTranslateX+childX-x,sampleModelTranslateY+childY-y),this);
    }
    public WritableRaster createWritableTranslatedChild(int x,int y){return createWritableChild(minX,minY,width,height,x,y,null);}
}
