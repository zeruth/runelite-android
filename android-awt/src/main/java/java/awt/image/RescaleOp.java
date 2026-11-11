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

import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * RescaleOp: applies scale + offset per band. Implementation re-scales ARGB int pixels
 * directly; alpha is preserved (so a tinted button overlay still respects transparency).
 */
public class RescaleOp implements BufferedImageOp {
    private final float[] scaleFactors;
    private final float[] offsets;
    private final RenderingHints hints;

    public RescaleOp(float[] scaleFactors, float[] offsets, RenderingHints hints) {
        int count = Math.min(scaleFactors.length, offsets.length);
        if (count == 0) throw new IllegalArgumentException("No scale factors");
        this.scaleFactors = java.util.Arrays.copyOf(scaleFactors, count);
        this.offsets = java.util.Arrays.copyOf(offsets, count);
        this.hints = hints == null ? null : (RenderingHints) hints.clone();
    }

    public RescaleOp(float scaleFactor, float offset, RenderingHints hints) {
        this(new float[]{scaleFactor}, new float[]{offset}, hints);
    }

    public final float[] getScaleFactors(float[] dst) {
        if (dst == null) dst = new float[scaleFactors.length];
        System.arraycopy(scaleFactors, 0, dst, 0, scaleFactors.length);
        return dst;
    }

    public final float[] getOffsets(float[] dst) {
        if (dst == null) dst = new float[offsets.length];
        System.arraycopy(offsets, 0, dst, 0, offsets.length);
        return dst;
    }

    public final int getNumFactors() { return scaleFactors.length; }

    @Override
    public BufferedImage filter(BufferedImage src, BufferedImage dest) {
        BufferedImage out = (dest != null) ? dest
            : new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        if (out.getWidth() != src.getWidth() || out.getHeight() != src.getHeight())
            throw new IllegalArgumentException("Image dimensions differ");
        int count = scaleFactors.length;
        int colors = src.getColorModel().getNumColorComponents();
        if (count != 1 && count != colors && count != src.getColorModel().getNumComponents())
            throw new IllegalArgumentException("Wrong number of scale factors");
        int[] source = src.getRGB(0, 0, src.getWidth(), src.getHeight(), null, 0, src.getWidth());
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
            int p = source[y * src.getWidth() + x];
            int a = p >>> 24;
            int r = adjust((p >>> 16) & 255, 0);
            int g = adjust((p >>> 8) & 255, count == 1 ? 0 : 1);
            int b = adjust(p & 255, count == 1 ? 0 : 2);
            if (count == 4) a = adjust(a, 3);
            out.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
        }
        return out;
    }

    private int adjust(int value, int band) { return clamp((int) (value * scaleFactors[band] + offsets[band])); }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    @Override public Rectangle2D getBounds2D(BufferedImage src) { return new Rectangle2D.Float(0, 0, src.getWidth(), src.getHeight()); }
    @Override public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
        ColorModel cm = destCM == null ? src.getColorModel() : destCM;
        return new BufferedImage(cm, cm.createCompatibleWritableRaster(src.getWidth(), src.getHeight()), cm.isAlphaPremultiplied(), null);
    }
    @Override public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        if (dstPt == null) dstPt = new Point2D.Double();
        dstPt.setLocation(srcPt.getX(), srcPt.getY());
        return dstPt;
    }
    @Override public RenderingHints getRenderingHints() { return hints == null ? null : (RenderingHints) hints.clone(); }
}
