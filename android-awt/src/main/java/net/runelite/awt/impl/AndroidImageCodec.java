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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import javax.imageio.stream.ImageInputStream;

/**
 * The platform codec behind {@code javax.imageio.ImageIO}: {@link BitmapFactory} decodes
 * PNG/JPEG/WEBP/GIF/BMP into a TYPE_INT_ARGB {@link BufferedImage}, {@code Bitmap.compress}
 * encodes PNG/JPEG/WEBP. ImageIO uses it when no registered reader or writer takes the
 * data, which before the ported plugins is every call RuneLite makes.
 */
public final class AndroidImageCodec {
    private AndroidImageCodec() {
    }

    public static BufferedImage decode(InputStream input) throws IOException {
        return toBufferedImage(BitmapFactory.decodeStream(input));
    }

    public static BufferedImage decode(File input) throws IOException {
        if (!input.isFile()) {
            throw new IOException("Cannot read image file: " + input);
        }
        return toBufferedImage(BitmapFactory.decodeFile(input.getAbsolutePath()));
    }

    /** Decodes the bytes from the stream's current position to its end. */
    public static BufferedImage decode(ImageInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = input.read(chunk, 0, chunk.length)) > 0) {
            bytes.write(chunk, 0, n);
        }
        byte[] data = bytes.toByteArray();
        if (data.length == 0) {
            return null;
        }
        return toBufferedImage(BitmapFactory.decodeByteArray(data, 0, data.length));
    }

    public static boolean canEncode(String formatName) {
        return formatFor(formatName) != null;
    }

    /** Returns false, writing nothing, for a format Bitmap.compress has no encoder for. */
    public static boolean encode(RenderedImage im, String formatName, OutputStream output)
            throws IOException {
        if (im == null) {
            throw new IllegalArgumentException("im == null!");
        }
        Bitmap.CompressFormat fmt = formatFor(formatName);
        if (fmt == null) {
            return false;
        }
        int width = im.getWidth();
        int height = im.getHeight();
        int[] pixels;
        if (im instanceof BufferedImage) {
            pixels = ((BufferedImage) im).getRGB(0, 0, width, height, null, 0, width);
        } else {
            pixels = new int[width * height];
            Raster raster = im.getData();
            ColorModel model = im.getColorModel();
            Object element = null;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    element = raster.getDataElements(im.getMinX() + x, im.getMinY() + y, element);
                    pixels[y * width + x] = model.getRGB(element);
                }
            }
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        try {
            return bmp.compress(fmt, 100, output);
        } finally {
            bmp.recycle();
        }
    }

    private static BufferedImage toBufferedImage(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bitmap.getPixels(out.backingArray(), 0, w, 0, 0, w, h);
        // The caller owns the int[] inside the BufferedImage; the Bitmap is done.
        bitmap.recycle();
        return out;
    }

    private static Bitmap.CompressFormat formatFor(String name) {
        if (name == null) {
            throw new IllegalArgumentException("formatName == null!");
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("jpeg") || n.equals("jpg")) {
            return Bitmap.CompressFormat.JPEG;
        }
        if (n.equals("webp")) {
            // WEBP_LOSSLESS was split out of the deprecated WEBP enum in API 30. minSdk is
            // 26, so gate the newer constant and fall back to plain WEBP on older devices.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                return Bitmap.CompressFormat.WEBP_LOSSLESS;
            }
            @SuppressWarnings("deprecation")
            Bitmap.CompressFormat legacy = Bitmap.CompressFormat.WEBP;
            return legacy;
        }
        return n.equals("png") ? Bitmap.CompressFormat.PNG : null;
    }
}
