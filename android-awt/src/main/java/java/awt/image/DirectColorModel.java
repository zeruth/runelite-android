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

public class DirectColorModel extends ColorModel {
    private final int redMask;
    private final int greenMask;
    private final int blueMask;
    private final int alphaMask;
    private final int redShift;
    private final int greenShift;
    private final int blueShift;
    private final int alphaShift;

    public DirectColorModel(int bits, int rmask, int gmask, int bmask) {
        this(bits, rmask, gmask, bmask, 0);
    }

    public DirectColorModel(int bits, int rmask, int gmask, int bmask, int amask) {
        this(ColorSpace.getInstance(ColorSpace.CS_sRGB), bits, rmask, gmask, bmask, amask, false, DataBuffer.TYPE_INT);
    }

    public DirectColorModel(ColorSpace space, int bits, int rmask, int gmask, int bmask, int amask,
                            boolean isAlphaPremultiplied, int transferType) {
        super(bits, computeBitSizes(rmask, gmask, bmask, amask),
            space, amask != 0,
            isAlphaPremultiplied,
            amask == 0 ? Transparency.OPAQUE : Transparency.TRANSLUCENT,
            transferType);
        this.redMask = rmask;
        this.greenMask = gmask;
        this.blueMask = bmask;
        this.alphaMask = amask;
        this.redShift = trailingZeros(rmask);
        this.greenShift = trailingZeros(gmask);
        this.blueShift = trailingZeros(bmask);
        this.alphaShift = amask == 0 ? 0 : trailingZeros(amask);
    }

    public final int getRedMask()   { return redMask; }
    public final int getGreenMask() { return greenMask; }
    public final int getBlueMask()  { return blueMask; }
    public final int getAlphaMask() { return alphaMask; }

    @Override
    public int getRed(int pixel) {
        return getColorSpace().isCS_sRGB() ? unpremultiply(component(pixel,redMask), pixel) : getRGB(pixel) >>> 16 & 255;
    }

    @Override
    public int getGreen(int pixel) {
        return getColorSpace().isCS_sRGB() ? unpremultiply(component(pixel,greenMask), pixel) : getRGB(pixel) >>> 8 & 255;
    }

    @Override
    public int getBlue(int pixel) {
        return getColorSpace().isCS_sRGB() ? unpremultiply(component(pixel,blueMask), pixel) : getRGB(pixel) & 255;
    }

    @Override
    public int getAlpha(int pixel) {
        return alphaMask == 0 ? 255 : component(pixel,alphaMask);
    }

    @Override
    public int getRGB(int pixel) {
        int a=getAlpha(pixel),r=unpremultiply(component(pixel,redMask),pixel),g=unpremultiply(component(pixel,greenMask),pixel),b=unpremultiply(component(pixel,blueMask),pixel);
        if(!getColorSpace().isCS_sRGB()){
            float[] rgb=getColorSpace().toRGB(new float[]{r/255f,g/255f,b/255f});
            r=Math.max(0,Math.min(255,Math.round(rgb[0]*255)));g=Math.max(0,Math.min(255,Math.round(rgb[1]*255)));b=Math.max(0,Math.min(255,Math.round(rgb[2]*255)));
        }
        return a<<24|r<<16|g<<8|b;
    }

    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        int[] masks = alphaMask == 0
            ? new int[]{redMask, greenMask, blueMask}
            : new int[]{redMask, greenMask, blueMask, alphaMask};
        return new SinglePixelPackedSampleModel(transferType, w, h, masks);
    }

    private int unpremultiply(int component, int pixel) { int alpha = getAlpha(pixel); return !isAlphaPremultiplied() ? component : alpha == 0 ? 0 : Math.min(255, (component * 255 + alpha / 2) / alpha); }
    private static int component(int pixel,int mask){if(mask==0)return 0;int shift=Integer.numberOfTrailingZeros(mask);long max=(mask&0xffffffffL)>>>shift;return (int)((((pixel&mask)&0xffffffffL)>>>shift)*255/max);}
    private static int pack(int value,int mask){if(mask==0)return 0;int shift=Integer.numberOfTrailingZeros(mask);long max=(mask&0xffffffffL)>>>shift;return (int)((value*max+127)/255)<<shift;}
    @Override public Object getDataElements(int rgb,Object pixel){
        Object p = pixel != null ? pixel : transferType == DataBuffer.TYPE_BYTE ? new byte[1] : transferType == DataBuffer.TYPE_USHORT ? new short[1] : new int[1];int a=rgb>>>24,r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
        if(!getColorSpace().isCS_sRGB()){
            float[] components=getColorSpace().fromRGB(new float[]{r/255f,g/255f,b/255f});
            r=Math.round(components[0]*255);g=Math.round(components[1]*255);b=Math.round(components[2]*255);
        }
        if(isAlphaPremultiplied()){r=(r*a+127)/255;g=(g*a+127)/255;b=(b*a+127)/255;}
        SampleModel.transferSet(p, 0, pack(r,redMask)|pack(g,greenMask)|pack(b,blueMask)|pack(a,alphaMask));return p;
    }
    private int[] masks() {
        return alphaMask == 0 ? new int[]{redMask, greenMask, blueMask}
            : new int[]{redMask, greenMask, blueMask, alphaMask};
    }

    /** Unnormalized fields of the packed pixel, in mask order (OpenJDK 8u). */
    @Override public int[] getComponents(int pixel, int[] components, int offset) {
        int[] masks = masks();
        if (components == null) components = new int[offset + masks.length];
        for (int i = 0; i < masks.length; i++) {
            components[offset + i] = (pixel & masks[i]) >>> trailingZeros(masks[i]);
        }
        return components;
    }

    @Override public int[] getComponents(Object pixel, int[] components, int offset) {
        int intpixel;
        switch (transferType) {
            case DataBuffer.TYPE_BYTE: intpixel = ((byte[]) pixel)[0] & 0xff; break;
            case DataBuffer.TYPE_USHORT: intpixel = ((short[]) pixel)[0] & 0xffff; break;
            case DataBuffer.TYPE_INT: intpixel = ((int[]) pixel)[0]; break;
            default: throw new UnsupportedOperationException("This method has not been "
                + "implemented for transferType " + transferType);
        }
        return getComponents(intpixel, components, offset);
    }

    @Override public int getDataElement(int[] components, int offset) {
        int[] masks = masks();
        int pixel = 0;
        for (int i = 0; i < masks.length; i++) {
            pixel |= (components[offset + i] << trailingZeros(masks[i])) & masks[i];
        }
        return pixel;
    }

    @Override public Object getDataElements(int[] components, int offset, Object obj) {
        int pixel = getDataElement(components, offset);
        switch (transferType) {
            case DataBuffer.TYPE_BYTE: {
                byte[] out = obj == null ? new byte[1] : (byte[]) obj;
                out[0] = (byte) pixel;
                return out;
            }
            case DataBuffer.TYPE_USHORT: {
                short[] out = obj == null ? new short[1] : (short[]) obj;
                out[0] = (short) pixel;
                return out;
            }
            case DataBuffer.TYPE_INT: {
                int[] out = obj == null ? new int[1] : (int[]) obj;
                out[0] = pixel;
                return out;
            }
            default: throw new UnsupportedOperationException("This method has not been "
                + "implemented for transferType " + transferType);
        }
    }

    @Override public boolean isCompatibleRaster(Raster raster) {
        if (!(raster.getSampleModel() instanceof SinglePixelPackedSampleModel) || raster.getTransferType() != transferType) return false;
        int[] masks = ((SinglePixelPackedSampleModel)raster.getSampleModel()).getBitMasks();
        int[] expected = alphaMask == 0 ? new int[]{redMask, greenMask, blueMask} : new int[]{redMask, greenMask, blueMask, alphaMask};
        return java.util.Arrays.equals(masks, expected);
    }

    // OpenJDK 8u PackedColorModel.isCompatibleSampleModel: same packing, band count and masks.
    @Override public boolean isCompatibleSampleModel(SampleModel sm) {
        if (!(sm instanceof SinglePixelPackedSampleModel)) return false;
        if (sm.getNumBands() != getNumComponents() || sm.getTransferType() != transferType) return false;
        int[] masks = ((SinglePixelPackedSampleModel)sm).getBitMasks();
        int[] expected = alphaMask == 0 ? new int[]{redMask, greenMask, blueMask} : new int[]{redMask, greenMask, blueMask, alphaMask};
        return java.util.Arrays.equals(masks, expected);
    }
    private static int trailingZeros(int mask) {
        if (mask == 0) return 0;
        int n = 0;
        while ((mask & 1) == 0) { mask >>>= 1; n++; }
        return n;
    }

    private static int[] computeBitSizes(int rm, int gm, int bm, int am) {
        int[] out = am == 0 ? new int[3] : new int[4];
        out[0] = bitCount(rm);
        out[1] = bitCount(gm);
        out[2] = bitCount(bm);
        if (am != 0) out[3] = bitCount(am);
        return out;
    }

    private static int bitCount(int mask) {
        int n = 0;
        while (mask != 0) { if ((mask & 1) != 0) n++; mask >>>= 1; }
        return n;
    }
}
