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
package com.sun.imageio.plugins.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageWriteParam;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;

import net.runelite.awt.impl.AndroidImageCodec;
import net.runelite.awt.impl.PlatformImageWriter;

/**
 * The JPEG writer, encoding through {@code Bitmap.compress}. An explicit compression
 * quality maps linearly onto the platform's 0..100 scale; any other compression mode
 * writes at the JDK's default quality of 0.75. The platform encoder writes baseline
 * JPEG only, so a progressive mode on the parameter yields a baseline stream (the
 * image is the same, only its scan order differs), and custom quantization or Huffman
 * tables are not used.
 */
public class JPEGImageWriter extends PlatformImageWriter {

    public JPEGImageWriter(ImageWriterSpi originator) {
        super(originator);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new JPEGImageWriteParam(getLocale());
    }

    @Override
    protected boolean encode(int[] argb, int w, int h, ImageWriteParam param,
                             ByteArrayOutputStream out) throws IOException {
        float quality = JPEG.DEFAULT_QUALITY;
        if (param != null && param.canWriteCompressed()
                && param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
            quality = param.getCompressionQuality();
        }
        // JPEG has no alpha; the colour channels are written as they are, as the
        // JDK writer does with the colour bands of its input.
        for (int i = 0; i < argb.length; i++) {
            argb[i] |= 0xff000000;
        }
        int q = Math.max(0, Math.min(100, Math.round(quality * 100)));
        return AndroidImageCodec.encodePixels(argb, w, h, "jpeg", q, out);
    }
}
