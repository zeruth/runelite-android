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

import java.util.Hashtable;

public class ImageFilter implements ImageConsumer, Cloneable {
    protected ImageConsumer consumer;

    @Override public void setDimensions(int width, int height) { if (consumer != null) consumer.setDimensions(width, height); }
    @Override public void setProperties(Hashtable<?, ?> props) { if (consumer != null) consumer.setProperties(props); }
    @Override public void setColorModel(ColorModel model) { if (consumer != null) consumer.setColorModel(model); }
    @Override public void setHints(int hintflags) { if (consumer != null) consumer.setHints(hintflags); }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off, int scansize) {
        if (consumer != null) consumer.setPixels(x, y, w, h, model, pixels, off, scansize);
    }
    @Override public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {
        if (consumer != null) consumer.setPixels(x, y, w, h, model, pixels, off, scansize);
    }
    @Override public void imageComplete(int status) { if (consumer != null) consumer.imageComplete(status); }

    public void resendTopDownLeftRight(ImageProducer source) { source.requestTopDownLeftRightResend(this); }

    public ImageFilter getFilterInstance(ImageConsumer ic) {
        try {
            ImageFilter copy = (ImageFilter) clone();
            copy.consumer = ic;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
