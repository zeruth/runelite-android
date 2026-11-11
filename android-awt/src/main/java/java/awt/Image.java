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
package java.awt;

import java.awt.image.ImageObserver;

public abstract class Image {
    public static final int SCALE_DEFAULT      = 1;
    public static final int SCALE_FAST         = 2;
    public static final int SCALE_SMOOTH       = 4;
    public static final int SCALE_REPLICATE    = 8;
    public static final int SCALE_AREA_AVERAGING = 16;

    public static final Object UndefinedProperty = new Object();

    public abstract int getWidth(ImageObserver observer);
    public abstract int getHeight(ImageObserver observer);
    public abstract Graphics getGraphics();

    public Object getProperty(String name, ImageObserver observer) {
        return UndefinedProperty;
    }

    public java.awt.image.ImageProducer getSource(){throw net.runelite.awt.AwtUnsupported.fail("java.awt.Image.getSource()", "only BufferedImage produces pixels");}

    public Image getScaledInstance(int width, int height, int hints) {
        java.awt.image.ProducerImage image=new java.awt.image.ProducerImage(getSource());
        java.awt.image.BufferedImage buffered=image.getBufferedImage();
        if(buffered==null)throw new IllegalStateException("Image is not loaded");
        return buffered.getScaledInstance(width,height,hints);
    }

    public void flush() {
    }
}
