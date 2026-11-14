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
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

/**
 * An {@link ImageWriter} for a format {@code Bitmap.compress} encodes (JPEG, WebP): the
 * source region and subsampling of the write parameter select the ARGB pixels, the
 * subclass maps the compression settings onto the platform encoder. Metadata is not
 * written (the platform encoders take none), so the metadata factories return null as
 * the ImageWriter contract allows for a writer without metadata support.
 */
public abstract class PlatformImageWriter extends ImageWriter {

    protected PlatformImageWriter(ImageWriterSpi originatingProvider) {
        super(originatingProvider);
    }

    /**
     * Encodes w x h non-premultiplied ARGB pixels to {@code out} as {@code param}
     * asks, returning false when the platform encoder fails.
     */
    protected abstract boolean encode(int[] argb, int w, int h, ImageWriteParam param,
                                      ByteArrayOutputStream out) throws IOException;

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType,
                                               ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType,
                                            ImageWriteParam param) {
        return null;
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param)
            throws IOException {
        ImageOutputStream out = (ImageOutputStream) getOutput();
        if (out == null) {
            throw new IllegalStateException("Output has not been set!");
        }
        if (image == null) {
            throw new IllegalArgumentException("image == null!");
        }
        if (image.hasRaster()) {
            throw new UnsupportedOperationException("Cannot write rasters!");
        }
        RenderedImage im = image.getRenderedImage();
        clearAbortRequest();
        processImageStarted(0);

        Rectangle src = new Rectangle(im.getMinX(), im.getMinY(), im.getWidth(), im.getHeight());
        int periodX = 1;
        int periodY = 1;
        if (param != null) {
            Rectangle region = param.getSourceRegion();
            if (region != null) {
                src = src.intersection(region);
            }
            periodX = param.getSourceXSubsampling();
            periodY = param.getSourceYSubsampling();
            int gridX = param.getSubsamplingXOffset();
            int gridY = param.getSubsamplingYOffset();
            src.x += gridX;
            src.y += gridY;
            src.width -= gridX;
            src.height -= gridY;
        }
        int w = (src.width + periodX - 1) / periodX;
        int h = (src.height + periodY - 1) / periodY;
        if (src.width <= 0 || src.height <= 0 || w <= 0 || h <= 0) {
            throw new IIOException("Empty source region!");
        }

        int[] argb = new int[w * h];
        if (im instanceof BufferedImage) {
            BufferedImage bi = (BufferedImage) im;
            int[] row = new int[src.width];
            for (int j = 0; j < h; j++) {
                bi.getRGB(src.x, src.y + j * periodY, src.width, 1, row, 0, src.width);
                for (int k = 0; k < w; k++) {
                    argb[j * w + k] = row[k * periodX];
                }
            }
        } else {
            Raster raster = im.getData(src);
            ColorModel model = im.getColorModel();
            Object element = null;
            for (int j = 0; j < h; j++) {
                for (int k = 0; k < w; k++) {
                    element = raster.getDataElements(src.x + k * periodX, src.y + j * periodY,
                                                     element);
                    argb[j * w + k] = model.getRGB(element);
                }
            }
        }
        if (abortRequested()) {
            processWriteAborted();
            return;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!encode(argb, w, h, param, bytes)) {
            throw new IIOException("The platform encoder failed to write the image");
        }
        out.write(bytes.toByteArray());
        processImageProgress(100.0F);
        processImageComplete();
    }
}
