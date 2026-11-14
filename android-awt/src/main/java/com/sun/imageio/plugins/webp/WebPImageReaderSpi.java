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

import java.io.IOException;
import java.util.Locale;

import javax.imageio.IIOException;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 * WebP reader provider, an Android extra: the desktop JDK has no WebP plugin, the
 * platform decodes it natively. Registered through IIORegistry's built-in list.
 */
public class WebPImageReaderSpi extends ImageReaderSpi {

    static final String VENDOR = "runelite-android";
    static final String VERSION = "1.0";
    static final String[] NAMES = {"webp", "WEBP"};
    static final String[] SUFFIXES = {"webp"};
    static final String[] MIME_TYPES = {"image/webp"};

    public WebPImageReaderSpi() {
        super(VENDOR, VERSION, NAMES, SUFFIXES, MIME_TYPES,
              "com.sun.imageio.plugins.webp.WebPImageReader",
              new Class[] { ImageInputStream.class },
              new String[] {"com.sun.imageio.plugins.webp.WebPImageWriterSpi"},
              false, null, null, null, null,
              false, null, null, null, null);
    }

    public String getDescription(Locale locale) {
        return "WebP Image Reader (Android platform codec)";
    }

    /** A RIFF container whose form type is "WEBP". */
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream)) {
            return false;
        }
        ImageInputStream stream = (ImageInputStream) source;
        byte[] b = new byte[12];
        stream.mark();
        try {
            stream.readFully(b);
        } catch (java.io.EOFException e) {
            return false;
        } finally {
            stream.reset();
        }
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    public ImageReader createReaderInstance(Object extension) throws IIOException {
        return new WebPImageReader(this);
    }
}
