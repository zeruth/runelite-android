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
import net.runelite.awt.AwtUnsupported;

public abstract class ColorModel implements Transparency {
    protected int pixel_bits;
    protected int transferType;
    private final int numComponents;
    private final int numColorComponents;
    private final boolean hasAlpha;
    private final boolean isAlphaPremultiplied;
    private final int transparency;
    private final ColorSpace colorSpace;
    private final int[] componentSizes;

    public ColorModel(int bits) {
        this(bits, new int[]{8, 8, 8, 8}, ColorSpace.getInstance(ColorSpace.CS_sRGB),
            true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_INT);
    }

    protected ColorModel(int pixel_bits, int[] bits, ColorSpace cspace, boolean hasAlpha,
                         boolean isAlphaPremultiplied, int transparency, int transferType) {
        this.pixel_bits = pixel_bits;
        this.transferType = transferType;
        this.numColorComponents = cspace.getNumComponents();
        this.numComponents = numColorComponents + (hasAlpha ? 1 : 0);
        this.hasAlpha = hasAlpha;
        this.isAlphaPremultiplied = isAlphaPremultiplied;
        this.transparency = transparency;
        this.colorSpace = cspace;
        this.componentSizes = bits == null ? null : bits.clone();
    }

    public int getComponentSize(int component) { return componentSizes[component]; }
    public int[] getComponentSize() { return componentSizes.clone(); }

    public final int getPixelSize()       { return pixel_bits; }
    public final ColorSpace getColorSpace() { return colorSpace; }
    public final int getNumComponents()   { return numComponents; }
    public final int getNumColorComponents() { return numColorComponents; }
    public final boolean hasAlpha()       { return hasAlpha; }
    public final boolean isAlphaPremultiplied() { return isAlphaPremultiplied; }
    @Override public final int getTransparency() { return transparency; }
    public final int getTransferType()    { return transferType; }

    public abstract int getRed(int pixel);
    public abstract int getGreen(int pixel);
    public abstract int getBlue(int pixel);
    public abstract int getAlpha(int pixel);

    public int getRGB(int pixel) {
        return (getAlpha(pixel) << 24) | (getRed(pixel) << 16) | (getGreen(pixel) << 8) | getBlue(pixel);
    }

    private static final ColorModel RGB=new DirectColorModel(32,0xff0000,0xff00,0xff,0xff000000);
    public static ColorModel getRGBdefault(){return RGB;}
    public Object getDataElements(int rgb,Object pixel){int[] p=pixel instanceof int[]?(int[])pixel:new int[1];p[0]=rgb;return p;}
    public int getRGB(Object pixel){if(pixel instanceof int[])return getRGB(((int[])pixel)[0]);if(pixel instanceof byte[])return getRGB(((byte[])pixel)[0]&255);if(pixel instanceof short[])return getRGB(((short[])pixel)[0]&65535);throw new IllegalArgumentException("pixel data");}
    public boolean isCompatibleRaster(Raster raster) {
        throw AwtUnsupported.fail("java.awt.image.ColorModel.isCompatibleRaster(Raster)", "only subclasses answer");
    }

    public WritableRaster createCompatibleWritableRaster(int w, int h) {
        SampleModel sm = createCompatibleSampleModel(w, h);
        return Raster.createWritableRaster(sm, sm.createDataBuffer(), new java.awt.Point(0, 0));
    }

    public SampleModel createCompatibleSampleModel(int w, int h) {
        throw AwtUnsupported.fail("java.awt.image.ColorModel.createCompatibleSampleModel(int, int)", "only subclasses answer");
    }
    public ColorModel coerceData(WritableRaster raster, boolean premultiplied) {
        if (!hasAlpha() || isAlphaPremultiplied() == premultiplied) return this;
        ColorModel target;
        if (this instanceof DirectColorModel) {
            DirectColorModel direct = (DirectColorModel)this;
            target = new DirectColorModel(colorSpace, pixel_bits, direct.getRedMask(), direct.getGreenMask(), direct.getBlueMask(), direct.getAlphaMask(), premultiplied, transferType);
        } else if (this instanceof ComponentColorModel) target = new ComponentColorModel(colorSpace, componentSizes, true, premultiplied, transparency, transferType);
        else throw AwtUnsupported.fail("java.awt.image.ColorModel.coerceData(WritableRaster, boolean)", "only DirectColorModel and ComponentColorModel coerce alpha");
        Object pixel = null;
        for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
            pixel = raster.getDataElements(x, y, pixel);
            raster.setDataElements(x, y, target.getDataElements(getRGB(pixel), null));
        }
        return target;
    }
}
