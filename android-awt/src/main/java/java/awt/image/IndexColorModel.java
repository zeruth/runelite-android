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

public class IndexColorModel extends ColorModel {
    private final int[] palette;
    private final int transparent;
    public IndexColorModel(int bits, int size, byte[] red, byte[] green, byte[] blue) { this(bits, size, red, green, blue, -1); }
    public IndexColorModel(int bits, int size, byte[] red, byte[] green, byte[] blue, int transparent) {
        this(bits, size, palette(size, red, green, blue, null, transparent), 0, transparent >= 0, transparent, bits <= 8 ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT);
    }
    public IndexColorModel(int bits, int size, byte[] red, byte[] green, byte[] blue, byte[] alpha) {
        this(bits, size, palette(size, red, green, blue, alpha, -1), 0, true, -1, bits <= 8 ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT);
    }
    public IndexColorModel(int bits, int size, int[] colors, int offset, boolean alpha, int transparent, int transferType) {
        super(bits, new int[]{8, 8, 8, 8}, ColorSpace.getInstance(ColorSpace.CS_sRGB), alpha || transparent >= 0, false, transparency(colors, offset, size, alpha, transparent), transferType);
        if (bits < 1 || bits > 16 || size < 1 || size > (1 << bits)) throw new IllegalArgumentException("Invalid indexed palette");
        palette = java.util.Arrays.copyOfRange(colors, offset, offset + size);
        int transparentIndex = -1;
        for (int i = 0; i < palette.length; i++) {
            if (!alpha) palette[i] |= 0xff000000;
            if (i == transparent) palette[i] &= 0xffffff;
            if (transparentIndex < 0 && (palette[i] >>> 24) == 0) transparentIndex = i;
        }
        this.transparent = transparentIndex;
    }
    private static int transparency(int[] colors, int offset, int size, boolean alpha, int transparent) {
        boolean bitmask = transparent >= 0;
        if (alpha) for (int i = 0; i < size; i++) { int a = colors[offset + i] >>> 24; if (a != 0 && a != 255) return Transparency.TRANSLUCENT; bitmask |= a == 0; }
        return bitmask ? Transparency.BITMASK : Transparency.OPAQUE;
    }
    private static int[] palette(int size, byte[] r, byte[] g, byte[] b, byte[] alpha, int transparent) {
        int[] colors = new int[size];
        for (int i = 0; i < size; i++) colors[i] = (i == transparent ? 0 : alpha == null ? 255 : alpha[i] & 255) << 24 | (r[i] & 255) << 16 | (g[i] & 255) << 8 | b[i] & 255;
        return colors;
    }
    public final int getMapSize() { return palette.length; }
    public final int getTransparentPixel() { return transparent; }
    public final void getRGBs(int[] out) { System.arraycopy(palette, 0, out, 0, palette.length); }
    public final void getReds(byte[] out) { channels(out, 16); }
    public final void getGreens(byte[] out) { channels(out, 8); }
    public final void getBlues(byte[] out) { channels(out, 0); }
    public final void getAlphas(byte[] out) { channels(out, 24); }
    private void channels(byte[] out, int shift) { for (int i = 0; i < palette.length; i++) out[i] = (byte)(palette[i] >>> shift); }
    public final int getRGB(int index) { return index >= 0 && index < palette.length ? palette[index] : 0; }
    public final int getRed(int index) { return getRGB(index) >>> 16 & 255; }
    public final int getGreen(int index) { return getRGB(index) >>> 8 & 255; }
    public final int getBlue(int index) { return getRGB(index) & 255; }
    public final int getAlpha(int index) { return getRGB(index) >>> 24; }
    @Override public Object getDataElements(int rgb, Object out) {
        int best = 0; long bestDistance = Long.MAX_VALUE;
        if ((rgb >>> 24) == 0 && transparent >= 0) best = transparent;
        else for (int i = 0; i < palette.length; i++) {
            long distance = 0; for (int shift = 0; shift <= 24; shift += 8) { int delta = (rgb >>> shift & 255) - (palette[i] >>> shift & 255); distance += delta * delta; }
            if (distance < bestDistance) { bestDistance = distance; best = i; if (distance == 0) break; }
        }
        if (out == null) out = transferType == DataBuffer.TYPE_BYTE ? new byte[1] : new short[1];
        SampleModel.transferSet(out, 0, best); return out;
    }
    @Override public SampleModel createCompatibleSampleModel(int w, int h) { return new PixelInterleavedSampleModel(transferType, w, h, 1, w, new int[]{0}); }
    @Override public boolean isCompatibleRaster(Raster raster) { return raster.getTransferType() == transferType && raster.getNumBands() == 1 && raster.getSampleModel().getSampleSize(0) >= pixel_bits; }
}
