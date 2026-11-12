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
package net.runelite.awt.impl;

import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 * An {@link ImageReader} for a single-image format the platform decodes (JPEG, WebP):
 * the stream is read to its end once, {@code BitmapFactory} supplies the size and
 * pixels, and the read parameters (source region, subsampling, destination, offset)
 * are applied here with {@code ImageReader}'s own region helpers, so they mean what
 * they mean for the ported Java readers. Band selection is not supported and is
 * rejected rather than ignored.
 */
public abstract class PlatformImageReader extends ImageReader {
    private ImageInputStream stream;
    private byte[] data;
    private int width;
    private int height;

    protected PlatformImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    /** "JPEG", "WebP": used in error messages. */
    protected abstract String formatLabel();

    /**
     * The destination types for the image whose bytes are {@code data}, preferred first.
     * Called after the size is known.
     */
    protected abstract List<ImageTypeSpecifier> imageTypes(byte[] data);

    /** Image metadata for the decoded stream, or null when the format keeps none. */
    protected IIOMetadata imageMetadata(byte[] data) {
        return null;
    }

    /** {width, height} of the image, or null when the bytes are not this format. */
    protected int[] readSize(byte[] data) {
        return AndroidImageCodec.decodeBounds(data);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        stream = (ImageInputStream) input;
        data = null;
    }

    @Override
    public void reset() {
        super.reset();
        stream = null;
        data = null;
    }

    @Override
    public void dispose() {
        stream = null;
        data = null;
    }

    /** The encoded bytes from the stream position at setInput to the stream's end. */
    protected final byte[] data() throws IOException {
        if (data == null) {
            if (stream == null) {
                throw new IllegalStateException("Input not set!");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = stream.read(chunk, 0, chunk.length)) > 0) {
                bytes.write(chunk, 0, n);
            }
            byte[] read = bytes.toByteArray();
            int[] size = read.length == 0 ? null : readSize(read);
            if (size == null) {
                throw new IIOException("Not a decodable " + formatLabel() + " stream");
            }
            width = size[0];
            height = size[1];
            data = read;
        }
        return data;
    }

    private static void checkIndex(int imageIndex) {
        if (imageIndex != 0) {
            throw new IndexOutOfBoundsException("imageIndex != 0!");
        }
    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        if (stream == null) {
            throw new IllegalStateException("Input not set!");
        }
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        data();
        return width;
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        data();
        return height;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return imageTypes(data()).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return imageMetadata(data());
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        checkIndex(imageIndex);
        byte[] bytes = data();
        if (param == null) {
            param = getDefaultReadParam();
        }
        if (param.getSourceBands() != null || param.getDestinationBands() != null) {
            throw new IIOException(formatLabel() + " reader does not support band selection");
        }
        clearAbortRequest();
        processImageStarted(imageIndex);

        BufferedImage dest = getDestination(param, getImageTypes(imageIndex), width, height);
        Rectangle src = new Rectangle(0, 0, 0, 0);
        Rectangle dst = new Rectangle(0, 0, 0, 0);
        computeRegions(param, width, height, dest, src, dst);
        int periodX = param.getSourceXSubsampling();
        int periodY = param.getSourceYSubsampling();

        int[] pixels = AndroidImageCodec.decodePixels(bytes, src.x, src.y, src.width, src.height,
                                                      width, height);
        if (pixels == null) {
            throw new IIOException("Unable to decode " + formatLabel() + " data");
        }

        WritableRaster raster = dest.getRaster();
        // A gray JPEG decodes to r == g == b == Y; storing Y as the sample keeps the
        // value the JDK reader produces, where setRGB would run a colour conversion.
        boolean gray = raster.getNumBands() == 1
            && dest.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;
        int[] row = new int[dst.width];
        for (int j = 0; j < dst.height; j++) {
            int base = j * periodY * src.width;
            for (int k = 0; k < dst.width; k++) {
                row[k] = pixels[base + k * periodX];
            }
            if (gray) {
                for (int k = 0; k < dst.width; k++) {
                    raster.setSample(dst.x + k, dst.y + j, 0, row[k] & 0xff);
                }
            } else {
                dest.setRGB(dst.x, dst.y + j, dst.width, 1, row, 0, dst.width);
            }
            processImageProgress(100.0F * (j + 1) / dst.height);
            if (abortRequested()) {
                processReadAborted();
                return dest;
            }
        }
        processImageComplete();
        return dest;
    }
}
