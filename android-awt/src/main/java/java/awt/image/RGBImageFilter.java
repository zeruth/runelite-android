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

public abstract class RGBImageFilter extends ImageFilter {
    protected ColorModel origmodel;
    protected ColorModel newmodel;
    protected boolean canFilterIndexColorModel;

    public abstract int filterRGB(int x, int y, int rgb);

    public void filterRGBPixels(int x, int y, int w, int h, int[] pixels, int off, int scansize) {
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int i = off + row * scansize + col;
                pixels[i] = filterRGB(x + col, y + row, pixels[i]);
            }
        }
        if (consumer != null) consumer.setPixels(x, y, w, h, ColorModel.getRGBdefault(), pixels, off, scansize);
    }
    @Override public void setColorModel(ColorModel model) {
        if (canFilterIndexColorModel && model instanceof IndexColorModel) {
            IndexColorModel filtered = filterIndexColorModel((IndexColorModel) model);
            substituteColorModel(model, filtered);
            if (consumer != null) consumer.setColorModel(filtered);
        } else if (consumer != null) consumer.setColorModel(ColorModel.getRGBdefault());
    }
    public void substituteColorModel(ColorModel oldModel, ColorModel newModel) { origmodel = oldModel; newmodel = newModel; }
    public IndexColorModel filterIndexColorModel(IndexColorModel model) {
        int[] colors = new int[model.getMapSize()]; model.getRGBs(colors);
        for (int i = 0; i < colors.length; i++) colors[i] = filterRGB(-1, -1, colors[i]);
        return new IndexColorModel(model.getPixelSize(), colors.length, colors, 0, true, -1, model.getTransferType());
    }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int offset, int scansize) {
        if (model == origmodel && newmodel != null) { if (consumer != null) consumer.setPixels(x, y, w, h, newmodel, pixels, offset, scansize); return; }
        int[] rgb = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) rgb[row * w + col] = model.getRGB(pixels[offset + row * scansize + col]);
        filterRGBPixels(x, y, w, h, rgb, 0, w);
    }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int offset, int scansize) {
        if (model == origmodel && newmodel != null) { if (consumer != null) consumer.setPixels(x, y, w, h, newmodel, pixels, offset, scansize); return; }
        int[] rgb = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) rgb[row * w + col] = model.getRGB(pixels[offset + row * scansize + col] & 255);
        filterRGBPixels(x, y, w, h, rgb, 0, w);
    }
}
