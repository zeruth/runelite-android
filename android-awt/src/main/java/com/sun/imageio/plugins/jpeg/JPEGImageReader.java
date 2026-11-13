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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageReadParam;
import javax.imageio.spi.ImageReaderSpi;

import net.runelite.awt.impl.PlatformImageReader;

/**
 * The JPEG reader, decoding through {@code BitmapFactory} and {@code
 * BitmapRegionDecoder} instead of the JDK's bundled libjpeg. The destination types
 * follow the JDK reader: TYPE_3BYTE_BGR first for colour images, TYPE_BYTE_GRAY for
 * single-component ones. Tables supplied in a {@link JPEGImageReadParam} are not
 * needed by the platform decoder (abbreviated streams carrying no tables do not
 * decode) and are ignored.
 */
public class JPEGImageReader extends PlatformImageReader {

    private JPEGMetadata header;

    public JPEGImageReader(ImageReaderSpi originator) {
        super(originator);
    }

    @Override
    protected String formatLabel() {
        return "JPEG";
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        header = null;
    }

    @Override
    protected int[] readSize(byte[] data) {
        header = JPEGMetadata.parse(data);
        if (header == null) {
            return null;
        }
        return new int[] {header.getWidth(), header.getHeight()};
    }

    @Override
    protected List<ImageTypeSpecifier> imageTypes(byte[] data) {
        List<ImageTypeSpecifier> types = new ArrayList<ImageTypeSpecifier>(3);
        if (header.getNumComponents() == 1) {
            types.add(ImageTypeSpecifier.createFromBufferedImageType(
                BufferedImage.TYPE_BYTE_GRAY));
        } else {
            types.add(ImageTypeSpecifier.createFromBufferedImageType(
                BufferedImage.TYPE_3BYTE_BGR));
            types.add(ImageTypeSpecifier.createFromBufferedImageType(
                BufferedImage.TYPE_INT_RGB));
            types.add(ImageTypeSpecifier.createFromBufferedImageType(
                BufferedImage.TYPE_INT_BGR));
        }
        return types;
    }

    @Override
    protected IIOMetadata imageMetadata(byte[] data) {
        return header;
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        return new JPEGImageReadParam();
    }
}
