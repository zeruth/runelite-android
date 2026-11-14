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

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.math.BigInteger;

/**
 * Pixel values index a palette of sRGB colors. As on the desktop, the palette array is
 * at least 256 entries (and {@code 1 << bits}) long, entries past the map size read as
 * transparent black, and a pixel is masked to its bit width before the lookup.
 * {@link #hasAlpha()} is true exactly when some entry is not opaque.
 */
public class IndexColorModel extends ColorModel {
    private final int[] rgb;
    private final int map_size;
    private final int pixel_mask;
    private final int transparent_index;
    private final boolean allgrayopaque;
    private final BigInteger validBits;

    private static final int[] opaqueBits = {8, 8, 8};
    private static final int[] alphaBits = {8, 8, 8, 8};

    public IndexColorModel(int bits, int size, byte[] r, byte[] g, byte[] b) {
        this(bits, size, pack(size, r, g, b, null), 0, false, -1, getDefaultTransferType(bits), null);
    }

    public IndexColorModel(int bits, int size, byte[] r, byte[] g, byte[] b, int trans) {
        this(bits, size, pack(size, r, g, b, null), 0, false, trans, getDefaultTransferType(bits), null);
    }

    public IndexColorModel(int bits, int size, byte[] r, byte[] g, byte[] b, byte[] a) {
        this(bits, size, pack(size, r, g, b, a), 0, true, -1, getDefaultTransferType(bits), null);
    }

    public IndexColorModel(int bits, int size, byte[] cmap, int start, boolean hasalpha) {
        this(bits, size, cmap, start, hasalpha, -1);
    }

    public IndexColorModel(int bits, int size, byte[] cmap, int start, boolean hasalpha, int trans) {
        this(bits, size, unpack(size, cmap, start, hasalpha), 0, hasalpha, trans, getDefaultTransferType(bits), null);
    }

    public IndexColorModel(int bits, int size, int[] cmap, int start, boolean hasalpha, int trans, int transferType) {
        this(bits, size, cmap, start, hasalpha, trans, transferType, null);
    }

    public IndexColorModel(int bits, int size, int[] cmap, int start, int transferType, BigInteger validBits) {
        this(bits, size, cmap, start, true, -1, transferType, validBits);
    }

    private IndexColorModel(int bits, int size, int[] cmap, int start, boolean hasalpha, int trans,
                            int transferType, BigInteger validBits) {
        this(palette(bits, size, cmap, start, hasalpha), bits, size, trans, transferType, validBits);
    }

    private IndexColorModel(int[] palette, int bits, int size, int trans, int transferType, BigInteger validBits) {
        super(checkBits(bits, size), alphaOf(palette, size, trans) == Transparency.OPAQUE ? opaqueBits : alphaBits,
            ColorSpace.getInstance(ColorSpace.CS_sRGB), alphaOf(palette, size, trans) != Transparency.OPAQUE,
            false, alphaOf(palette, size, trans), transferType);
        if (transferType != DataBuffer.TYPE_BYTE && transferType != DataBuffer.TYPE_USHORT) {
            throw new IllegalArgumentException("transferType must be either DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT");
        }
        this.map_size = size;
        int transIndex = -1;
        if (trans >= 0 && trans < size) {
            palette[trans] &= 0x00ffffff;
            transIndex = trans;
        } else {
            for (int i = 0; i < size; i++) {
                if ((palette[i] >>> 24) == 0) {
                    transIndex = i;
                    break;
                }
            }
        }
        this.rgb = palette;
        this.transparent_index = transIndex;
        int maskBits = bits == 3 ? 4 : (bits > 4 && bits < 8) ? 8 : bits;
        this.pixel_mask = (1 << maskBits) - 1;
        boolean gray = getTransparency() == Transparency.OPAQUE;
        for (int i = 0; gray && i < size; i++) {
            int c = palette[i];
            gray = ((c >> 16) & 0xff) == ((c >> 8) & 0xff) && ((c >> 8) & 0xff) == (c & 0xff);
        }
        this.allgrayopaque = gray;
        BigInteger kept = null;
        if (validBits != null) {
            for (int i = 0; i < size; i++) {
                if (!validBits.testBit(i)) {
                    kept = validBits;
                    break;
                }
            }
        }
        this.validBits = kept;
    }

    private static int checkBits(int bits, int size) {
        if (bits < 1 || bits > 16) {
            throw new IllegalArgumentException("Number of bits must be between 1 and 16.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Map size (" + size + ") must be >= 1");
        }
        return bits;
    }

    private static int[] pack(int size, byte[] r, byte[] g, byte[] b, byte[] a) {
        if (size < 1) {
            throw new IllegalArgumentException("Map size (" + size + ") must be >= 1");
        }
        int[] colors = new int[size];
        for (int i = 0; i < size; i++) {
            int alpha = a == null ? 0xff : a[i] & 0xff;
            colors[i] = (alpha << 24) | ((r[i] & 0xff) << 16) | ((g[i] & 0xff) << 8) | (b[i] & 0xff);
        }
        return colors;
    }

    private static int[] unpack(int size, byte[] cmap, int start, boolean hasalpha) {
        if (size < 1) {
            throw new IllegalArgumentException("Map size (" + size + ") must be >= 1");
        }
        int[] colors = new int[size];
        int j = start;
        for (int i = 0; i < size; i++) {
            int r = cmap[j++] & 0xff;
            int g = cmap[j++] & 0xff;
            int b = cmap[j++] & 0xff;
            int a = hasalpha ? cmap[j++] & 0xff : 0xff;
            colors[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return colors;
    }

    private static int[] palette(int bits, int size, int[] cmap, int start, boolean hasalpha) {
        checkBits(bits, size);
        int[] palette = new int[Math.max(Math.max(1 << bits, size), 256)];
        for (int i = 0; i < size; i++) {
            int c = cmap[start + i];
            palette[i] = hasalpha ? c : c | 0xff000000;
        }
        return palette;
    }

    private static int alphaOf(int[] palette, int size, int trans) {
        int transparency = Transparency.OPAQUE;
        for (int i = 0; i < size; i++) {
            int alpha = palette[i] >>> 24;
            if (alpha == 0 || i == trans) {
                if (transparency == Transparency.OPAQUE) transparency = Transparency.BITMASK;
            } else if (alpha != 0xff) {
                return Transparency.TRANSLUCENT;
            }
        }
        return transparency;
    }

    public final int getMapSize()          { return map_size; }
    public final int getTransparentPixel() { return transparent_index; }

    public final void getReds(byte[] r)   { channels(r, 16); }
    public final void getGreens(byte[] g) { channels(g, 8); }
    public final void getBlues(byte[] b)  { channels(b, 0); }
    public final void getAlphas(byte[] a) { channels(a, 24); }

    private void channels(byte[] out, int shift) {
        for (int i = 0; i < map_size; i++) out[i] = (byte) (rgb[i] >>> shift);
    }

    public final void getRGBs(int[] rgb) {
        System.arraycopy(this.rgb, 0, rgb, 0, map_size);
    }

    public final int getRed(int pixel)   { return (rgb[pixel & pixel_mask] >> 16) & 0xff; }
    public final int getGreen(int pixel) { return (rgb[pixel & pixel_mask] >> 8) & 0xff; }
    public final int getBlue(int pixel)  { return rgb[pixel & pixel_mask] & 0xff; }
    public final int getAlpha(int pixel) { return (rgb[pixel & pixel_mask] >> 24) & 0xff; }
    public final int getRGB(int pixel)   { return rgb[pixel & pixel_mask]; }

    public synchronized Object getDataElements(int rgb, Object pixel) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        int alpha = rgb >>> 24;
        int pix = 0;
        if (allgrayopaque) {
            // Gray opaque palettes match on luminance, the way the desktop does.
            int gray = (red * 77 + green * 150 + blue * 29 + 128) / 256;
            int minDist = 256;
            for (int i = 0; i < map_size; i++) {
                int d = (this.rgb[i] & 0xff) - gray;
                if (d < 0) d = -d;
                if (d < minDist) {
                    pix = i;
                    if (d == 0) break;
                    minDist = d;
                }
            }
        } else if (getTransparency() == Transparency.OPAQUE) {
            int smallest = Integer.MAX_VALUE;
            for (int i = 0; i < map_size; i++) {
                int c = this.rgb[i];
                if (c == (rgb | 0xff000000)) {
                    pix = i;
                    break;
                }
                int dr = ((c >> 16) & 0xff) - red;
                int dg = ((c >> 8) & 0xff) - green;
                int db = (c & 0xff) - blue;
                int d = dr * dr + dg * dg + db * db;
                if (d < smallest) {
                    pix = i;
                    smallest = d;
                }
            }
        } else if (alpha == 0 && transparent_index >= 0) {
            pix = transparent_index;
        } else {
            long smallest = Long.MAX_VALUE;
            for (int i = 0; i < map_size; i++) {
                int c = this.rgb[i];
                if (c == rgb) {
                    pix = i;
                    break;
                }
                long da = (c >>> 24) - alpha;
                long dr = ((c >> 16) & 0xff) - red;
                long dg = ((c >> 8) & 0xff) - green;
                long db = (c & 0xff) - blue;
                long d = da * da + dr * dr + dg * dg + db * db;
                if (d < smallest) {
                    pix = i;
                    smallest = d;
                }
            }
        }
        return storePixel(pix, pixel);
    }

    public int[] getComponents(int pixel, int[] components, int offset) {
        if (components == null) {
            components = new int[offset + getNumComponents()];
        }
        components[offset] = getRed(pixel);
        components[offset + 1] = getGreen(pixel);
        components[offset + 2] = getBlue(pixel);
        if (hasAlpha() && (components.length - offset) > 3) {
            components[offset + 3] = getAlpha(pixel);
        }
        return components;
    }

    public int[] getComponents(Object pixel, int[] components, int offset) {
        return getComponents(singlePixel(pixel), components, offset);
    }

    public int getDataElement(int[] components, int offset) {
        return singlePixel(getDataElements(components, offset, null));
    }

    public Object getDataElements(int[] components, int offset, Object pixel) {
        int argb = (components[offset] << 16) | (components[offset + 1] << 8) | components[offset + 2];
        argb |= hasAlpha() ? components[offset + 3] << 24 : 0xff000000;
        return getDataElements(argb, pixel);
    }

    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        if (pixel_bits == 1 || pixel_bits == 2 || pixel_bits == 4) {
            return Raster.createPackedRaster(DataBuffer.TYPE_BYTE, w, h, 1, pixel_bits, null);
        }
        if (pixel_bits <= 8) {
            return Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, w, h, 1, null);
        }
        if (pixel_bits <= 16) {
            return Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, w, h, 1, null);
        }
        throw new UnsupportedOperationException("This method is not supported for pixel bits > 16.");
    }

    public boolean isCompatibleRaster(Raster raster) {
        int size = raster.getSampleModel().getSampleSize(0);
        return raster.getTransferType() == transferType && raster.getNumBands() == 1 && (1 << size) >= map_size;
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        if (pixel_bits == 1 || pixel_bits == 2 || pixel_bits == 4) {
            return new MultiPixelPackedSampleModel(transferType, w, h, pixel_bits);
        }
        return new ComponentSampleModel(transferType, w, h, 1, w, new int[]{0});
    }

    public boolean isCompatibleSampleModel(SampleModel sm) {
        if (!(sm instanceof ComponentSampleModel) && !(sm instanceof MultiPixelPackedSampleModel)) {
            return false;
        }
        return sm.getTransferType() == transferType && sm.getNumBands() == 1;
    }

    /**
     * Returns a new <code>BufferedImage</code> of TYPE_INT_ARGB or
     * TYPE_INT_RGB that has a <code>Raster</code> with pixel data
     * computed by expanding the indices in the source <code>Raster</code>
     * using the color/alpha component arrays of this <code>ColorModel</code>.
     * If <code>forceARGB</code> is <code>true</code>, a TYPE_INT_ARGB image is
     * returned regardless of whether or not this <code>ColorModel</code>
     * has an alpha component array or a transparent pixel.
     *
     * @throws IllegalArgumentException if the raster argument is not
     *           compatible with this IndexColorModel
     */
    public BufferedImage convertToIntDiscrete(Raster raster, boolean forceARGB) {
        if (!isCompatibleRaster(raster)) {
            throw new IllegalArgumentException("This raster is not compatible" + "with this IndexColorModel.");
        }
        // Desktop also has a 25-bit BITMASK variant; the shadow's packed-int pipeline
        // only knows 8-bit alpha, so any transparency expands to full ARGB.
        boolean opaque = !forceARGB && getTransparency() == Transparency.OPAQUE;
        int w = raster.getWidth();
        int h = raster.getHeight();
        BufferedImage out = new BufferedImage(w, h, opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        int rX = raster.getMinX();
        int rY = raster.getMinY();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out.setRGB(x, y, getRGB(raster.getSample(rX + x, rY + y, 0)));
        }
        return out;
    }


    public boolean isValid(int pixel) {
        boolean validity = pixel >= 0 && pixel < map_size;
        if (validity && validBits != null) {
            validity = validBits.testBit(pixel);
        }
        return validity;
    }

    public boolean isValid() {
        return validBits == null;
    }

    public BigInteger getValidPixels() {
        if (validBits != null) {
            return validBits;
        }
        int numBytes = (map_size + 7) / 8;
        byte[] valid = new byte[numBytes];
        java.util.Arrays.fill(valid, (byte) 0xff);
        valid[0] = (byte) (0xff >>> (numBytes * 8 - map_size));
        return new BigInteger(1, valid);
    }

    public String toString() {
        return "IndexColorModel: #pixelBits = " + pixel_bits + " numComponents = " + getNumComponents()
            + " color space = " + getColorSpace() + " transparency = " + getTransparency()
            + " transIndex   = " + transparent_index + " has alpha = " + hasAlpha()
            + " isAlphaPre = " + isAlphaPremultiplied();
    }
}
