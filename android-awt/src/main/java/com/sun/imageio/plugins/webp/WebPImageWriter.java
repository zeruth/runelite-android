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
package com.sun.imageio.plugins.webp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageWriteParam;
import javax.imageio.spi.ImageWriterSpi;

import net.runelite.awt.impl.AndroidImageCodec;
import net.runelite.awt.impl.PlatformImageWriter;

/**
 * Writes WebP through {@code Bitmap.compress}. Lossless unless the parameter asks for
 * the "Lossy" compression type explicitly, in which case its quality maps onto the
 * platform's 0..100 scale.
 */
public class WebPImageWriter extends PlatformImageWriter {

    static final String LOSSLESS = "Lossless";
    static final String LOSSY = "Lossy";

    public WebPImageWriter(ImageWriterSpi originator) {
        super(originator);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new WebPWriteParam(getLocale());
    }

    @Override
    protected boolean encode(int[] argb, int w, int h, ImageWriteParam param,
                             ByteArrayOutputStream out) throws IOException {
        if (param != null && param.canWriteCompressed()
                && param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT
                && LOSSY.equals(param.getCompressionType())) {
            int q = Math.max(0, Math.min(100, Math.round(param.getCompressionQuality() * 100)));
            return AndroidImageCodec.encodePixels(argb, w, h, "webp-lossy", q, out);
        }
        return AndroidImageCodec.encodePixels(argb, w, h, "webp", 100, out);
    }

    private static final class WebPWriteParam extends ImageWriteParam {
        WebPWriteParam(Locale locale) {
            super(locale);
            canWriteCompressed = true;
            compressionTypes = new String[] {LOSSLESS, LOSSY};
            compressionType = LOSSLESS;
            compressionQuality = 1.0F;
        }

        @Override
        public boolean isCompressionLossless() {
            return LOSSLESS.equals(getCompressionType());
        }
    }
}
