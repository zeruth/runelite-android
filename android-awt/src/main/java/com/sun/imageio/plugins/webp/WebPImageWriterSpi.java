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

import java.awt.image.SampleModel;
import java.util.Locale;

import javax.imageio.IIOException;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

/** WebP writer provider, an Android extra next to the JDK's formats. */
public class WebPImageWriterSpi extends ImageWriterSpi {

    public WebPImageWriterSpi() {
        super(WebPImageReaderSpi.VENDOR, WebPImageReaderSpi.VERSION,
              WebPImageReaderSpi.NAMES, WebPImageReaderSpi.SUFFIXES,
              WebPImageReaderSpi.MIME_TYPES,
              "com.sun.imageio.plugins.webp.WebPImageWriter",
              new Class[] { ImageOutputStream.class },
              new String[] {"com.sun.imageio.plugins.webp.WebPImageReaderSpi"},
              false, null, null, null, null,
              false, null, null, null, null);
    }

    public String getDescription(Locale locale) {
        return "WebP Image Writer (Android platform codec)";
    }

    /** Anything that converts to 8-bit ARGB without losing precision. */
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        SampleModel sampleModel = type.getSampleModel();
        int[] sampleSize = sampleModel.getSampleSize();
        for (int size : sampleSize) {
            if (size < 1 || size > 8) {
                return false;
            }
        }
        return true;
    }

    public ImageWriter createWriterInstance(Object extension) throws IIOException {
        return new WebPImageWriter(this);
    }
}
